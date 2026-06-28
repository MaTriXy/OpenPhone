# OpenPhone Runtime Agent Plan

## Goal

OpenPhone should not be designed as an OpenClaw-specific Android client. OpenPhone should become a standard phone runtime endpoint that any external agent runtime can attach to, inspect, and control through a stable protocol.

OpenClaw is the first runtime we will integrate. Hermes should fit the same shape later without another Android-side rewrite. MCP and CLI should also fit as generic access paths for agents, developers, and local tools.

The target product model is:

```text
OpenPhone = phone runtime endpoint
Phone Runtime Protocol = standard session, event, tool, and confirmation contract
OpenClaw adapter = one backend runtime implementation
OpenClaw plugin = OpenClaw-side policy and command registration package
Hermes adapter = future backend runtime implementation
MCP/CLI = generic tool access paths over the same phone runtime contract
```

## Why This Design

The current prototype proved the important pieces:

- Android can connect to a remote runtime.
- OpenPhone can send attention requests from chat, volume, and background surfaces.
- A remote runtime can call back into phone tools.
- The phone can enforce local confirmations for risky actions.
- Dynamic Island/runtime UI can expose runtime selection.
- OpenClaw can be supported without changing OpenClaw core if we use `agent.request`, `node.invoke`, and an installable plugin.

But the prototype is still shaped too much around the demo and OpenClaw-specific naming. If we merge it as-is, future Hermes/MCP/CLI support will either duplicate the same logic or require another large refactor. The right move is to turn the prototype into a generic Runtime Agent layer now, then keep OpenClaw as one adapter and one plugin package.

## Public Repo Policy

This repository is open source and public. Do not push work-in-progress implementation branches until the integration is in a reviewable state and the user explicitly approves the push. Local commits are fine when useful, but public pushes should happen only after:

- secret scan passes,
- generated/build artifacts are intentional,
- no private keys or deployment credentials are staged,
- the branch has a coherent PR story,
- the demo/prototype-only paths have been removed or isolated behind debug-only gates.

## Current Branch Status

Current branch: `openclaw-runtime-integration`

This branch started as a prototype and source-material branch. It has now been
hardened toward the final merge shape, but it should still stay local or
unpublished until the remaining live-runtime validation items pass and the user
explicitly approves a push.

What is useful from the prototype:

- Runtime manager/session concepts.
- OpenClaw WebSocket adapter proof.
- OpenPhone tool bridge.
- Local confirmation flow.
- Runtime selection UI.
- Dynamic Island runtime tab.
- Smoke tests for OpenClaw connection, auth, confirmation, timeout, and unsafe transport behavior.
- OpenClaw plugin package skeleton.

Implemented in the current hardening pass:

- Android runtime classes moved from `external/*` into generic `runtime/*`.
- Public runtime core names now use `RuntimeManager`, `RuntimeToolBridge`,
  `RuntimeToolRequest`, `RuntimeToolResult`, and related generic types.
- OpenClaw-specific Android code now lives under
  `runtime/adapters/openclaw/*`; generic runtime core imports it only as one
  adapter implementation.
- Runtime settings use new `openphone_runtime_*` keys while preserving
  `openphone_external_*` legacy fallback.
- OpenClaw device identity storage moved to `openphone/runtime/...` with legacy
  fallback migration.
- OpenClaw signing seeds are written encrypted with AndroidKeyStore AES-GCM;
  legacy plaintext seed files are migrated and removed after encrypted write
  succeeds.
- Protocol manifests now cover commands, events, and capabilities.
- Runtime protocol docs and a shape schema now live under `runtime/docs/*` and
  `runtime/protocol/openphone-runtime.schema.json`.
- Command manifest uses canonical `openphone.*` names, keeps OpenClaw/legacy
  aliases, and includes descriptions plus input/output schemas from the
  installed action registry.
- OpenClaw plugin is packaged as `src/index.ts` plus `dist/index.js`.
- Plugin policy is split into safe default reads, private reads, and dangerous
  actions.
- `scripts/check-runtime-protocol.sh` validates manifest/plugin/adapter drift
  and imports the compiled plugin through a smoke test.
- `integrations/mcp-server` exposes manifest-backed OpenPhone tools over MCP
  with ADB as the first local transport.
- `integrations/cli` provides runtime status/list/select/configure, tool list,
  tool invoke, screen get, and `mcp serve` commands over the same ADB
  transport.
- MCP and CLI smoke tests run without a connected phone through dry-run/fake
  transports.
- OpenClaw tool-call autonomy now inherits the phone execution session when
  omitted, otherwise falls back to `ask_before_action`; mutating tools still
  require local confirmation unless the session is explicitly trusted.
- EC2 focused Android build passes for `OpenPhoneAssistant`.
- The rebuilt APK was pushed to the USB-connected Pixel and hash-verified.
- Pixel OpenClaw-compatible protocol smoke passes for offline, insecure
  transport denial, bad auth, attention routing, final message fanout, unknown
  command rejection, confirmation approve, confirmation deny, idempotency cache,
  and confirmation timeout.
- Local repo checks, runtime protocol checks, plugin smoke, MCP smoke, CLI
  smoke, and assistant Java checks pass.

What still needs to change before merge:

- A live OpenClaw runtime, not only the OpenClaw-compatible smoke shim, should
  be launched and used for the final demo proof.
- OpenClaw command policy should be reviewed with maintainers against the
  plugin/ClawHub path.
- Large runtime classes can still be split further after the generic boundary is
  accepted.

## Repo Structure

Keep everything in this repo, but separate runtime protocol, Android implementation, and external integrations.

```text
OpenPhone/
  runtime/
    protocol/
      openphone-runtime.schema.json
      openphone-commands.json
      openphone-events.json
      openphone-capabilities.json
    docs/
      runtime-agent-protocol.md
      security-model.md
      mcp-bridge.md
      openclaw-integration.md
      hermes-integration.md
    codegen/
      generate-runtime-bindings.ts

  overlay/packages/apps/OpenPhoneAssistant/
    src/org/openphone/assistant/runtime/
      RuntimeManager.java
      RuntimeAdapter.java
      RuntimeSessionStore.java
      RuntimeToolBridge.java
      RuntimeConfirmationBroker.java
      RuntimeTransport.java
      RuntimeEvent.java
      RuntimeToolRequest.java
      RuntimeToolResult.java
      RuntimeCommandRegistry.java
    src/org/openphone/assistant/runtime/adapters/openclaw/
      OpenClawRuntimeAdapter.java
      OpenClawProtocolMapper.java
      OpenClawDeviceIdentity.java
    src/org/openphone/assistant/runtime/adapters/hermes/
      README.md

  integrations/
    openclaw-plugin/
      src/index.ts
      dist/index.js
      package.json
      openclaw.plugin.json
      README.md
      tests/
    mcp-server/
      src/index.ts
      package.json
      README.md
      tests/
    cli/
      src/index.ts
      package.json
      README.md

  scripts/
    check-runtime-protocol.sh
    smoke-test-runtime-agent.sh
    smoke-test-openclaw-runtime.sh
```

## Runtime Agent Protocol

The Android app should expose a generic Runtime Agent Protocol. OpenClaw, Hermes, MCP, and CLI should all consume the same concepts.

### Core Concepts

Runtime:

- A local or remote agent backend that can handle user requests.
- Examples: Phone, OpenClaw, Hermes.

Surface:

- The OpenPhone UI/input source that produced the request.
- Examples: chat, volume, dynamic island, watcher, background job, notification.

Session:

- A durable phone-side execution context.
- Stores runtime kind, runtime session id, phone session id, source, autonomy mode, status, summary, timestamps, audit id.

Attention request:

- A phone-originated request asking a runtime to handle something.
- Carries user text, source, autonomy, optional screen context, and session metadata.

Tool request:

- A runtime-originated request asking the phone to inspect or act.
- Carries command, params, reason, idempotency key, timeout, session id, and caller identity.

Confirmation:

- A phone-side user approval flow for risky actions.
- Runtime cannot bypass it.

### Events

Standard events should include:

- `runtime.presence.online`
- `runtime.presence.offline`
- `runtime.attention.requested`
- `runtime.attention.accepted`
- `runtime.attention.failed`
- `runtime.message.delta`
- `runtime.message.final`
- `runtime.tool.requested`
- `runtime.tool.result`
- `runtime.confirmation.required`
- `runtime.confirmation.resolved`
- `runtime.session.updated`

### Commands

The command manifest should be the single source of truth:

```text
runtime/protocol/openphone-commands.json
```

Every command should define:

- name,
- description,
- category,
- input schema,
- output schema,
- risk level,
- required phone capability,
- confirmation policy,
- whether it is safe for default exposure,
- whether it is available to MCP,
- whether it is available to remote runtimes.

Initial command groups:

- `openphone.screen.get`
- `openphone.screen.understand_local`
- `openphone.apps.search`
- `openphone.app.open`
- `openphone.url.open`
- `openphone.ui.tap`
- `openphone.ui.tap_element`
- `openphone.ui.long_press`
- `openphone.ui.swipe`
- `openphone.ui.type_text`
- `openphone.input.press_key`
- `openphone.clipboard.set`
- `openphone.clipboard.paste`
- `openphone.share.text`
- `openphone.notifications.list`
- `openphone.notifications.search`
- `openphone.notifications.open`
- `openphone.contacts.search`
- `openphone.calendar.search`
- `openphone.calendar.add`
- `openphone.calendar.update`
- `openphone.calendar.delete`
- `openphone.messages.search`
- `openphone.messages.draft`
- `openphone.messages.send`
- `openphone.calls.search`
- `openphone.calls.place`
- `openphone.memory.search`
- `openphone.memory.save`
- `openphone.watchers.list`
- `openphone.watchers.create`
- `openphone.watchers.stop`
- `openphone.jobs.list`
- `openphone.jobs.create`
- `openphone.jobs.stop`

The Java runtime bridge, OpenClaw plugin, MCP server, CLI help, and docs should all derive from this manifest.

## Transport Strategy

Use a two-plane model.

### Session/Event Plane

Used for phone-originated attention, runtime replies, status, subscriptions, voice lifecycle, dynamic island updates, and background events.

Initial transports:

- WebSocket JSON-RPC for live remote runtimes.
- Android service intents internally between UI/service.

Future transports:

- HTTP/SSE if useful for hosted runtimes.
- gRPC only if we need typed streaming and binary performance later.

### Tool Plane

Used for runtime-originated phone tool calls.

Initial transports:

- WebSocket JSON-RPC for connected runtimes.
- MCP server for agents that already speak MCP.
- CLI bridge for local/dev.

MCP is a strong fit for phone tools, but not enough by itself because MCP does not naturally handle volume-button attention, Dynamic Island status, watcher pushes, and live voice/session lifecycle. MCP should be a tool bridge, not the whole runtime protocol.

## Android Runtime Architecture

### Generic Runtime Core

Refactor prototype `external/*` classes into `runtime/*`.

Rename:

- `ExternalRuntimeManager` -> `RuntimeManager`
- `RuntimeAdapter` stays generic
- `ExternalRuntimeRequest` -> `RuntimeToolRequest`
- `ExternalRuntimeResult` -> `RuntimeToolResult`
- `OpenPhoneToolBridge` -> `RuntimeToolBridge`
- `PhoneSessionStore` -> `RuntimeSessionStore`
- `ExternalPendingConfirmation` -> `RuntimePendingConfirmation`
- `ExternalConfirmationResolution` -> `RuntimeConfirmationResolution`

Responsibilities:

- `RuntimeManager`: owns configured adapters, status aggregation, routing, reloads.
- `RuntimeAdapter`: interface for OpenClaw/Hermes/etc.
- `RuntimeToolBridge`: validates and executes phone tools.
- `RuntimeConfirmationBroker`: owns pending confirmations, timeouts, idempotency, callbacks.
- `RuntimeSessionStore`: durable session state.
- `RuntimeCommandRegistry`: generated command metadata from protocol manifest.
- `RuntimeTransport`: WebSocket/transport policy helpers.

### Runtime Selection

Runtime selection should be generic:

- Chat runtime.
- Volume runtime.
- Background/watchers runtime.

Built-in runtime should be named `Phone`.

OpenClaw should appear only as one runtime card:

- `Phone`
- `OpenClaw`
- later `Hermes`

Dynamic Island runtime tab should show concise runtime cards and let the user switch all default surfaces quickly. The full app Runtime screen can expose per-surface selection.

### Voice

Short term:

- Phone does STT locally or through configured model provider.
- Transcript is routed to selected runtime.
- Runtime text response is shown in chat/island.
- If source is volume voice, OpenPhone should use local Android TTS for runtime text replies unless the runtime returns audio.

Long term:

- Runtime capability model:
  - text-in/text-out,
  - voice-in/text-out,
  - voice-in/audio-out,
  - realtime multimodal session.
- OpenAI Realtime, Gemini Live, Qwen Omni, OpenClaw voice, and Hermes voice should all fit behind the same capability model.

## OpenClaw Integration

OpenClaw support has two pieces in this repo.

### Android Adapter

Location:

```text
overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/runtime/adapters/openclaw/
```

Responsibilities:

- Connect to OpenClaw gateway.
- Authenticate as a node.
- Advertise OpenPhone runtime commands from the shared manifest.
- Convert OpenPhone attention requests into stock OpenClaw `agent.request`.
- Subscribe to OpenClaw session fanout.
- Convert OpenClaw `node.invoke.request` into generic `RuntimeToolRequest`.
- Send tool results through OpenClaw `node.invoke.result`.
- Never hardcode OpenPhone command policy that belongs in OpenClaw plugin.

### OpenClaw Plugin

Location:

```text
integrations/openclaw-plugin/
```

Responsibilities:

- Register OpenPhone command policy with OpenClaw.
- Use generated command list from `runtime/protocol/openphone-commands.json`.
- Safe read commands can be default-enabled for OpenPhone Android nodes.
- Mutating actions must be marked dangerous and require explicit OpenClaw operator opt-in.
- Enforce node metadata check: only OpenPhone Android nodes receive OpenPhone commands.
- Provide docs and install metadata for ClawHub/NPM.

Package requirements:

- Source lives in `src/index.ts`.
- Build outputs `dist/index.js`.
- `package.json` points `main` and `openclaw.extensions` to `./dist/index.js`.
- Include a test that imports the plugin and verifies registered command policy.

OpenClaw core should not be modified for OpenPhone-specific behavior unless we discover a missing generic plugin API and have maintainer sponsorship.

## Hermes Integration

Do not implement Hermes in the first hardening pass. Design for it.

Expected shape:

```text
runtime/adapters/hermes/
```

Hermes should implement the same `RuntimeAdapter` interface as OpenClaw:

- connect,
- disconnect,
- report status,
- request attention,
- receive runtime messages,
- execute phone tool callbacks,
- expose capabilities.

If Hermes already speaks MCP, it can initially use the MCP bridge for phone tools while a richer Hermes runtime adapter is built later.

## MCP Bridge

Location:

```text
integrations/mcp-server/
```

Purpose:

- Let any MCP-capable agent inspect and control OpenPhone.
- Make Hermes and other agents easy to support without custom Android protocol work.
- Provide a local/dev path for tools and tests.

Initial tools:

- `openphone.screen.get`
- `openphone.screen.understand_local`
- `openphone.app.open`
- `openphone.url.open`
- `openphone.ui.tap`
- `openphone.ui.type_text`
- `openphone.notifications.list`
- `openphone.jobs.list`

Important boundary:

- MCP exposes tools.
- Runtime protocol handles phone-originated sessions, attention, voice lifecycle, watcher pushes, and Dynamic Island updates.

## CLI Bridge

Location:

```text
integrations/cli/
```

Purpose:

- Developer workflow.
- Local testing.
- Demo support without hardcoding demo behavior into the Android app.

Example commands:

```sh
openphone runtime status
openphone runtime list
openphone runtime select --chat openclaw
openphone runtime select --volume phone
openphone screen get --screenshot
openphone tool invoke openphone.app.open '{"package":"com.android.settings","reason":"test"}'
openphone mcp serve
```

The CLI can use ADB during development and a socket/service transport later.

## Security Model

### Required Before Merge

Fix these before merging the branch:

1. Idempotency and confirmation safety.
   - Do not let one mutating request approval satisfy a different invoke id, tool, session, or params.
   - Either do not coalesce mutating idempotency at all, or key by runtime + session + tool + params digest + idempotency key.

2. Synchronization.
   - Runtime adapter maps must not be access-order `LinkedHashMap`s touched across threads without a lock.
   - Use synchronized access or concurrent structures with bounded eviction.

3. Debug receiver exposure.
   - `OpenPhoneSmokeControlReceiver` must not ship as an enabled exported production receiver.
   - Keep smoke controls debug-only through manifest/build variant or disabled-by-default component toggling.

4. OpenClaw policy/default mismatch.
   - Ensure OpenPhone node metadata resolves to the plugin default policy in OpenClaw.
   - Either advertise metadata OpenClaw recognizes as Android while preserving OpenPhone family metadata elsewhere, or update plugin/OpenClaw policy through a generic supported mechanism.

5. Plugin packaging.
   - Build to `dist/index.js`.
   - Do not publish source-only `index.ts`.

6. Runtime reload.
   - If OpenClaw settings are changed while service is alive, attention requests should reload or restart runtime adapters as needed.

7. Background jobs.
   - Do not mark a background job completed just because an intent was sent.
   - Mark as sent/pending, completed, failed, or retryable based on real runtime dispatch result.

8. Autonomy enforcement.
   - `observe_only` must deny mutating tools, not just ask for confirmation.
   - `ask_before_action` requires local confirmation.
   - `trusted_actions` may still require confirmation for high-risk actions based on command metadata.

### Required Before Non-Debug Enablement

These can follow the first merge if the runtime is still debug/private, but must happen before broader release:

- Store OpenClaw/Hermes device signing keys using AndroidKeyStore or another stronger storage path.
- Replace hand-rolled WebSocket with OkHttp WebSocket or run an independent protocol/security review.
- Add structured runtime error codes instead of keyword sniffing error strings.
- Add rate limits and payload size limits for runtime tool calls.
- Add audit log entries for every remote runtime tool request/result.

## Code Quality Plan

### Simplify Names

Core Android code should use generic runtime terms. OpenClaw names should appear only in:

- OpenClaw adapter,
- OpenClaw plugin,
- OpenClaw docs/tests,
- runtime card label.

### Split Large Classes

`OpenClawRuntimeAdapter` should be split:

- `OpenClawRuntimeAdapter`: lifecycle and adapter interface.
- `OpenClawProtocolMapper`: OpenClaw frame/event mapping.
- `OpenClawMessageParser`: chat/agent event extraction.
- `OpenClawNodeCommandMapper`: command-to-tool mapping from generated manifest.
- `OpenClawDeviceIdentity`: auth identity.

`RuntimeToolBridge` should be split:

- `RuntimeToolBridge`: validates and dispatches tool calls.
- `RuntimeConfirmationBroker`: pending confirmations, timeouts, idempotency.
- `RuntimeToolResultNormalizer`: framework tool result normalization.

### Centralize Schema Helpers

Avoid repeated field fallback logic like `sessionKey` vs `session_key`. Create helpers:

- `JsonFields.pickString(...)`
- `JsonFields.pickObject(...)`
- `JsonFields.pickArray(...)`

But prefer pinned schemas wherever possible.

### Reduce Silent Failures

Replace most `catch (JSONException ignored)` with:

- local fallback where genuinely harmless,
- structured error result for protocol paths,
- `Log.w` for unexpected schema failures.

## Implementation Phases

### Phase 0: Freeze Prototype

Purpose:

- Stop treating current branch as the merge target.
- Use it as implementation reference.

Tasks:

- Keep current branch local unless explicitly approved for public push.
- Record review findings in the PR or plan.
- Decide whether to continue on this branch or create a new hardening branch.

Exit criteria:

- Team agrees current shape is prototype.
- `NEW_PLAN.md` is committed locally.

### Phase 1: Protocol Manifest

Purpose:

- Create the source of truth for phone runtime commands/events.

Tasks:

- Add `runtime/protocol/openphone-commands.json`.
- Add `runtime/protocol/openphone-events.json`.
- Add `runtime/protocol/openphone-capabilities.json`.
- Document command risk classes and confirmation policies.
- Add a script that validates manifest JSON and duplicate command names.

Exit criteria:

- Manifest validates.
- Android/OpenClaw command lists can be generated or mechanically checked from it.

### Phase 2: Generic Android Runtime Core

Purpose:

- Remove OpenClaw-specific concepts from OpenPhone core.

Tasks:

- Move `external/*` into `runtime/*`.
- Rename classes to generic runtime names.
- Add `RuntimeCommandRegistry` backed by generated manifest data.
- Make `RuntimeManager` support named adapters without hardcoded OpenClaw logic outside adapter registration.
- Add generic runtime status model using enum-like constants, not arbitrary string soup.
- Make `statusJson()` pure or at least avoid surprising status mutation during reads.

Exit criteria:

- Android code compiles on EC2.
- No OpenClaw-specific command names in generic runtime core except adapter registration.

### Phase 3: Security Fixes

Purpose:

- Close merge blockers.

Tasks:

- Fix mutating idempotency/confirmation reuse.
- Synchronize or replace unsafe runtime adapter maps.
- Make smoke receiver debug-only.
- Fix runtime reload on config changes.
- Fix background job state model for remote runtime dispatch.
- Enforce autonomy mode in `RuntimeToolBridge`.
- Add audit log entries for remote runtime tool requests/results.

Exit criteria:

- Smoke tests cover approval, denial, timeout, idempotency replay, observe-only denial, and background dispatch failure.
- No known must-fix security blockers remain.

### Phase 4: OpenClaw Adapter Cleanup

Purpose:

- Keep OpenClaw support as an adapter, not as OpenPhone core.

Tasks:

- Move OpenClaw adapter into `runtime/adapters/openclaw`.
- Split protocol mapper/parser/command mapper if needed.
- Ensure attention requests use stock OpenClaw `agent.request`.
- Ensure phone tool calls use OpenClaw `node.invoke.request/result`.
- Fix metadata/default-policy compatibility.
- Remove demo-only prompt hacks.
- Keep instructions concise and structured.

Exit criteria:

- OpenClaw can answer from phone-originated chat.
- OpenClaw can inspect screen through phone tool.
- OpenClaw can request an action and receive local confirmation result.

### Phase 5: OpenClaw Plugin Package

Purpose:

- Make the OpenClaw plugin a real installable package in this repo.

Tasks:

- Move plugin source to `integrations/openclaw-plugin/src/index.ts`.
- Add build config.
- Output `dist/index.js`.
- Update `package.json` `main` and `openclaw.extensions`.
- Generate command policy from protocol manifest.
- Add plugin tests.
- Add README with install, config, and security notes.

Exit criteria:

- Plugin builds.
- Plugin import test passes.
- OpenClaw can install local plugin path.
- Dangerous commands require explicit allowlist.

### Phase 6: Runtime UI

Purpose:

- Make the user model clear.

Tasks:

- Runtime tab in app:
  - Phone,
  - OpenClaw,
  - later Hermes.
- Dynamic Island runtime tab:
  - concise runtime cards,
  - selected state,
  - tap to switch defaults.
- Full Runtime screen:
  - per-surface selection for chat, volume, background.
- Remove confusing labels like "Selected for Chat" if they imply volume behavior.
- Use `Phone`, not `Local Phone Runtime`.

Exit criteria:

- User can switch back to Phone.
- User can select OpenClaw for chat and volume.
- UI accurately reflects what is implemented.

### Phase 7: MCP Bridge

Purpose:

- Make OpenPhone easy for Hermes and other agents.

Tasks:

- Add `integrations/mcp-server`.
- Generate MCP tool list from protocol manifest.
- Implement local transport to phone runtime.
- Add tests for tool listing and sample tool invocation.
- Document MCP limitations versus full runtime session protocol.

Exit criteria:

- MCP client can list OpenPhone tools.
- MCP client can call screen read.
- MCP client can call a safe no-op/read tool in test.

### Phase 8: CLI Bridge

Purpose:

- Improve local development and demo workflow without adding demo hacks to Android.

Tasks:

- Add `integrations/cli`.
- Implement runtime status/list/select commands.
- Implement screen/tool invoke commands.
- Implement `openphone mcp serve`.
- Support ADB transport first.

Exit criteria:

- Developer can inspect runtime status from terminal.
- Developer can request screen context from terminal.
- Developer can invoke a safe tool through CLI.

### Phase 9: Voice Runtime Capabilities

Purpose:

- Move from "voice-in only" to explicit runtime voice capabilities.

Tasks:

- Add runtime capability metadata:
  - text input,
  - text output,
  - audio input,
  - audio output,
  - realtime multimodal,
  - tool calling.
- For OpenClaw short term:
  - use OpenPhone STT,
  - route transcript,
  - use local Android TTS for terminal runtime replies from volume voice.
- Long term:
  - support runtime-provided audio output or realtime session.

Exit criteria:

- Volume-to-OpenClaw produces audible response when selected.
- Capability display is clear in Runtime UI.

### Phase 10: End-to-End Validation

Purpose:

- Prove the integration is stable enough to merge and demo.

Use EC2 for Android compilation/build checks. Do not rely on local Android compilation. The Pixel is connected by USB-C for device smoke tests.

Required checks:

- `git diff --check`
- secret scan over branch diff
- protocol manifest validation
- plugin build/test
- Android assistant Java/Kotlin checks on EC2
- full repo check on EC2 when feasible
- OpenClaw local install/import test
- Pixel smoke:
  - configure runtime,
  - connect OpenClaw,
  - phone-originated attention request,
  - screen read,
  - mutating action confirmation approve,
  - mutating action confirmation deny,
  - confirmation timeout,
  - observe-only mutation denial,
  - runtime switch back to Phone,
  - background dispatch failure/retry.

Current validation already completed on this branch:

- `git diff --check`
- lightweight secret scan over changed runtime/integration files
- `scripts/check-runtime-protocol.sh`
- `npm test --prefix integrations/openclaw-plugin`
- `npm test --prefix integrations/mcp-server`
- `npm test --prefix integrations/cli`
- `scripts/check-assistant-java.sh`
- `scripts/check.sh`
- EC2 focused build:
  `ALLOW_MISSING_DEPENDENCIES=true OPENPHONE_BUILD_GOAL=OpenPhoneAssistant ./scripts/build.sh openphone_tegu-bp4a-userdebug`
- Pixel APK install hash:
  `ee4dabae23a83b22ea7d33a12cbbde5fad71f9757b7e796721e460fbe9c436ab`
- `scripts/smoke-test-openclaw-device-failures.sh`
- Device identity storage check: only
  `openphone/runtime/openclaw_device_identity.json` remains, and it contains
  `privateKeySeedEncryptedBase64`/`privateKeySeedIvBase64` rather than the
  legacy `privateKeySeedBase64` field.

Current validation still required before claiming full OpenClaw production
integration:

- Launch a real OpenClaw runtime with the OpenPhone plugin installed.
- Pair the Pixel/OpenPhone runtime with that OpenClaw runtime.
- Repeat attention, screen read, confirmation approve/deny, voice-in, and
  voice-out checks against that live runtime.
- Capture demo notes/screenshots/logs that are inspectable in the PR.

Exit criteria:

- PR has validation notes.
- No WIP-only public behavior.
- No secrets.
- Demo script is reproducible.

## Acceptance Criteria

This work is complete when:

- OpenPhone runtime core is generic and not OpenClaw-shaped.
- OpenClaw works through an adapter and plugin.
- Java and TypeScript command lists come from the same manifest.
- Runtime selector supports Phone and OpenClaw clearly.
- Volume routing to OpenClaw works with voice-in and audible voice-out fallback.
- OpenClaw can inspect the current screen and perform confirmed actions.
- MCP bridge can expose phone tools for future Hermes support.
- CLI bridge supports local runtime testing.
- Security blockers are fixed.
- EC2 build/check path passes.
- Pixel end-to-end smoke passes.

## First Practical Milestone

Milestone 1 should not try to finish Hermes. It should deliver:

- generic runtime core refactor,
- protocol manifest,
- hardened OpenClaw adapter,
- packaged OpenClaw plugin,
- runtime UI cleanup,
- OpenClaw chat/volume/screen/action confirmation end-to-end,
- EC2 and Pixel validation.

After Milestone 1, Hermes can be added cleanly through either:

- MCP bridge first,
- native Hermes runtime adapter later.
