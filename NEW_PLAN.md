# OpenPhone Runtime Agent Plan

## Executive Summary

OpenPhone should become a standard phone runtime endpoint, not an
OpenClaw-specific Android client.

The user-facing model should be simple:

```text
Phone = built-in local Android runtime
OpenClaw = remote personal-agent runtime
Hermes = future remote/local personal-agent runtime
MCP/CLI = generic tool access paths for developers and other agents
```

The engineering model should be:

```text
OpenPhone Runtime Protocol
  -> Android runtime core
  -> runtime adapters: Phone, OpenClaw, later Hermes
  -> phone tool bridge with confirmations and audit logs
  -> generated command/event/capability manifests
  -> OpenClaw plugin, MCP server, CLI all derived from the same manifests
```

OpenClaw is the first external runtime. Hermes comes later, using the same
Android-side runtime contract. We should not build another Android pathway for
Hermes.

## Non-Negotiables

- Do not push work-in-progress branches publicly until the user explicitly says
  to push. This repo is open source and public, so WIP implementation details
  should stay local until the branch is reviewable.
- Do Android compilation on EC2, not locally.
- The Pixel is connected by USB-C and is the target device for smoke tests.
- Keep OpenPhone generic. OpenClaw-specific behavior belongs in the OpenClaw
  adapter, OpenClaw plugin, OpenClaw docs, and OpenClaw tests only.
- Do not patch OpenClaw core for OpenPhone-specific behavior. If OpenClaw is
  missing a generic node/plugin API, make a small generic OpenClaw PR with
  maintainer sponsorship.
- Security and user control matter more than demo convenience. Remote runtimes
  must not silently bypass local phone confirmations.

## Background

OpenPhone already has three agent concepts that users need to understand:

- **Phone runtime**: the local Android agent loop. This is what the user thinks
  of as the native OpenPhone assistant.
- **Simple model loop**: screenshot/text request-response behavior, useful for
  local or configured model providers.
- **External runtime**: a full personal-agent runtime such as OpenClaw or,
  later, Hermes.

The product problem is not just "connect OpenClaw." The product problem is:

- Which runtime handles chat?
- Which runtime handles volume-button voice activation?
- Which runtime handles watchers/background jobs?
- When is the phone acting locally, and when is a remote runtime controlling the
  phone through tools?
- How does the user see, switch, trust, and audit that runtime?

The right answer is a Runtime layer inside OpenPhone. The runtime layer makes
Phone/OpenClaw/Hermes peers from the user's point of view, while preserving
different capabilities under the hood.

## Architecture Decision

Use **Remote Runtime / Peripheral Mode** first.

In this mode, OpenPhone remains the trusted phone endpoint:

- It captures screen/accessibility context.
- It exposes phone tools.
- It enforces local confirmations.
- It owns volume-button triggers, Dynamic Island state, watchers, jobs, and
  Android notifications.
- It connects to OpenClaw as a node/runtime endpoint.

OpenClaw remains the remote agent brain:

- It receives attention requests from the phone.
- It decides whether to answer, inspect, or act.
- It calls phone tools through the protocol.
- It maintains its own session/memory/tooling model.

This is better than embedding OpenClaw first because:

- It gets to an end-to-end integration faster.
- It does not require running the whole OpenClaw stack on Android.
- It keeps phone permissions and confirmations local.
- It lets Hermes use the same pattern later.
- It allows OpenClaw maintainers to accept plugin/community integration instead
  of OpenPhone-specific core changes.

Embedded runtime remains a later option, not the first milestone.

## Repo Design

Everything stays in this repo, but ownership boundaries need to be obvious.

```text
OpenPhone/
  runtime/
    protocol/
      openphone-commands.json
      openphone-events.json
      openphone-capabilities.json
      openphone-runtime.schema.json
    docs/
      runtime-agent-protocol.md
      security-model.md
      openclaw-integration.md
      hermes-integration.md
      mcp-bridge.md

  overlay/packages/apps/OpenPhoneAssistant/
    src/org/openphone/assistant/runtime/
      RuntimeManager.java
      RuntimeAdapter.java
      RuntimeSessionStore.java
      RuntimeToolBridge.java
      RuntimeConfirmationBroker.java
      RuntimeTransport.java
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
    smoke-test-openclaw-runtime.sh
```

## Current Branch Status

Branch: `openclaw-runtime-integration`

The current branch has already moved most of the prototype toward the intended
shape:

- Generic Android runtime classes exist under `runtime/*`.
- OpenClaw-specific Android code is scoped under
  `runtime/adapters/openclaw/*`.
- Runtime settings use `openphone_runtime_*` keys with legacy fallback.
- The Runtime UI and Dynamic Island expose runtime selection.
- The OpenClaw plugin lives in `integrations/openclaw-plugin/`.
- MCP and CLI integration packages exist under `integrations/`.
- Runtime protocol manifests and checks exist under `runtime/protocol/` and
  `scripts/check-runtime-protocol.sh`.
- OpenClaw-compatible Android smoke tests pass.
- EC2 focused Android build has passed.
- The rebuilt assistant APK has been pushed to the USB-connected Pixel and
  hash-verified.

Live OpenClaw validation has also progressed:

- A fresh OpenClaw main checkout was launched locally with an isolated state
  directory.
- The OpenPhone OpenClaw plugin was installed into that OpenClaw profile.
- The Pixel paired as an OpenClaw node.
- OpenClaw could invoke `openphone.screen.get` for metadata-only screen reads.
- OpenClaw could invoke `openphone.screen.get` with screenshot capture.
- Mutating tool calls correctly required local phone confirmation and timed out
  when not approved.
- Phone-to-OpenClaw `agent.request` reached a real OpenClaw agent/model run.

Known live-runtime gap:

- OpenPhone currently sends `chat.subscribe` plus `agent.request` with
  `deliver:false`.
- OpenClaw canonicalizes the agent session to the default agent-scoped key,
  such as `agent:main:openphone:<node>:<phone-session>`, while the first
  OpenPhone proof subscribed to the unscoped key
  `openphone:<node>:<phone-session>`.
- The Android OpenClaw adapter now subscribes to both the unscoped request key
  and the default OpenClaw agent-scoped key. This keeps the compatibility
  workaround isolated to the OpenClaw adapter.
- Longer term, OpenClaw should probably canonicalize `chat.subscribe` keys the
  same way it canonicalizes `agent.request` keys. That would be a generic
  OpenClaw improvement, not an OpenPhone-specific core case.

Current validated fixes:

- Remove hardcoded OpenClaw `thinking: low` from Android `agent.request`.
- Let OpenClaw/provider configuration choose thinking level.
- Reason: local Ollama `qwen2.5:0.5b` only supports `thinking: off`, and the
  hardcoded value broke real OpenClaw runs.
- Subscribe OpenPhone's OpenClaw node to both request and default agent-scoped
  session keys so live OpenClaw agent/chat fanout can reach Android.
- EC2 rebuilt `OpenPhoneAssistant` for `openphone_tegu-bp4a-userdebug`.
- The rebuilt APK was pushed to the USB-connected Pixel and hash-verified:
  `3d4bdbcba109cffb40fa4b1385df4431fa40aae9a2790f0437626b0ea92f93fe`.
- OpenClaw-compatible device smoke passed with scoped `chat.subscribe`, stock
  `agent.request`, no forced thinking value, final fanout, unknown-command
  rejection, confirmation deny/approve, idempotency replay, and timeout.
- Live OpenClaw proof now delivers terminal runtime messages back to Android on
  the `agent:main:openphone:<node>:<phone-session>` fanout key.

## Product Model

OpenPhone should expose a clear runtime selector.

Runtime cards:

- `Phone`
- `OpenClaw`
- later `Hermes`

Surfaces:

- Chat
- Volume voice
- Watchers/background jobs

Default behavior:

- The selected default runtime handles chat and volume unless the user chooses
  per-surface overrides.
- The built-in runtime is called `Phone`, not `Local Phone Runtime`.
- OpenClaw uses its runtime identity, icon/color, and connection label, but it
  should not change the generic runtime model.

Dynamic Island:

- Add a Runtime tab next to Chat, Watchers, and Runs.
- Show concise runtime cards/labels such as:
  - `⚡ Phone`
  - `🦞 OpenClaw 127.0.0.1`
- Tapping a runtime card changes the active/default runtime.
- Keep detailed config in the full app Runtime screen.

## Runtime Agent Protocol

The protocol is the stable contract between OpenPhone and any runtime.

### Core Objects

Runtime:

- A local or remote agent backend.
- Examples: Phone, OpenClaw, Hermes.

Surface:

- The OpenPhone source that produced the request.
- Examples: chat, volume, dynamic island, watcher, background job,
  notification.

Session:

- A durable phone-side execution context.
- Stores runtime id, runtime session id, phone session id, source, autonomy
  mode, state, summary, timestamps, and audit id.

Attention request:

- A phone-originated request asking a runtime to handle something.
- Carries user text, source, autonomy, optional screen context, voice mode, and
  session metadata.

Tool request:

- A runtime-originated request asking the phone to inspect or act.
- Carries command, params, reason, idempotency key, timeout, session id, and
  caller identity.

Confirmation:

- A phone-side user approval flow for risky actions.
- Remote runtimes cannot bypass it.

### Standard Events

Minimum event set:

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

### Command Manifest

`runtime/protocol/openphone-commands.json` is the command source of truth.

Every command must define:

- name,
- description,
- category,
- input schema,
- output schema,
- risk level,
- confirmation policy,
- phone capability,
- remote-runtime availability,
- MCP availability,
- safe default exposure.

Initial command groups:

- screen: `openphone.screen.get`, `openphone.screen.understand_local`
- apps/URL: `openphone.apps.search`, `openphone.app.open`,
  `openphone.url.open`
- UI: `openphone.ui.tap`, `openphone.ui.type_text`,
  `openphone.input.press_key`
- notifications: `openphone.notifications.list`,
  `openphone.notifications.search`, `openphone.notifications.open`
- user data: contacts, calendar, messages, calls
- jobs/watchers: list/create/stop
- memory: search/save

The Android bridge, OpenClaw plugin, MCP server, CLI, docs, and tests should be
generated from or mechanically checked against this manifest.

## Transport Strategy

Use two planes.

### Session/Event Plane

Used for:

- phone-originated attention,
- runtime replies,
- status,
- subscriptions,
- voice lifecycle,
- Dynamic Island updates,
- watcher/background pushes.

Initial transport:

- WebSocket JSON-RPC for remote runtimes.

Internal Android transport:

- service intents/binders between UI, assistant service, jobs, and Dynamic
  Island.

### Tool Plane

Used for runtime-originated phone calls.

Initial transports:

- WebSocket JSON-RPC for connected runtimes.
- MCP server for MCP-speaking agents.
- CLI over ADB for development and smoke tests.

MCP is useful for tools, but not enough as the entire runtime protocol because
MCP does not naturally own volume-button attention, Dynamic Island status,
watcher pushes, voice lifecycle, or durable phone sessions.

## Android Implementation

### Generic Runtime Core

Core classes should stay generic:

- `RuntimeManager`
- `RuntimeAdapter`
- `RuntimeSessionStore`
- `RuntimeToolBridge`
- `RuntimeConfirmationBroker`
- `RuntimeTransport`
- `RuntimeCommandRegistry`
- `RuntimeToolRequest`
- `RuntimeToolResult`

Responsibilities:

- `RuntimeManager`: adapter lifecycle, status aggregation, selection, routing.
- `RuntimeAdapter`: common interface for Phone/OpenClaw/Hermes.
- `RuntimeToolBridge`: validates and dispatches phone tools.
- `RuntimeConfirmationBroker`: pending confirmations, timeout, idempotency.
- `RuntimeSessionStore`: durable session state.
- `RuntimeCommandRegistry`: command metadata from protocol manifest.
- `RuntimeTransport`: transport policy and helpers.

### OpenClaw Adapter

Location:

```text
overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/runtime/adapters/openclaw/
```

Responsibilities:

- Connect to OpenClaw gateway.
- Authenticate/pair as a node.
- Advertise OpenPhone commands from the shared manifest.
- Convert OpenPhone attention requests into stock OpenClaw `agent.request`.
- Subscribe to session/final-message fanout.
- Convert OpenClaw node command invokes into `RuntimeToolRequest`.
- Return tool results through OpenClaw's node result path.
- Keep OpenClaw protocol mapping out of generic runtime core.

Adapter rules:

- Do not hardcode provider/model options such as thinking level.
- Do not hardcode OpenPhone-specific OpenClaw core cases.
- Do not auto-approve mutating tools.
- Do not expose private screen/user-data reads as safe defaults.

### Hermes Adapter

Do not implement Hermes in this milestone.

Add only a placeholder/design note under:

```text
runtime/adapters/hermes/
```

Hermes should later implement the same `RuntimeAdapter` interface:

- connect,
- disconnect,
- status,
- request attention,
- receive messages,
- execute phone tool callbacks,
- expose capabilities.

If Hermes can speak MCP earlier than it can speak the full runtime protocol,
Hermes can use the MCP bridge for phone tools as an interim path.

## OpenClaw Plugin

Location:

```text
integrations/openclaw-plugin/
```

Why it exists:

- OpenClaw maintainers do not want OpenPhone-specific behavior in OpenClaw
  core.
- The correct boundary is plugin/ClawHub/community package.
- The plugin registers policy and metadata for OpenPhone Android nodes.
- OpenPhone remains the owner of the OpenPhone integration.

Responsibilities:

- Register OpenPhone node command policy.
- Use generated command metadata from `runtime/protocol/openphone-commands.json`.
- Separate safe reads, private reads, and dangerous actions.
- Require explicit operator opt-in for dangerous commands.
- Match only OpenPhone Android node metadata.
- Provide install instructions for local development and ClawHub publication.

Required package shape:

- `src/index.ts` source.
- `dist/index.js` compiled output.
- `package.json` points `main` and `openclaw.extensions` to `dist/index.js`.
- Tests import the built plugin and verify command policy.
- README documents installation, security model, and expected node metadata.

OpenClaw core changes are allowed only if the missing behavior is generic. The
current candidate is node session final-message fanout for subscribed nodes.

## MCP Bridge

Location:

```text
integrations/mcp-server/
```

Purpose:

- Let any MCP-capable agent inspect/control OpenPhone tools.
- Give Hermes and other agents a low-friction integration path.
- Provide a stable local/dev bridge independent of OpenClaw.

Initial tools:

- `openphone.screen.get`
- `openphone.screen.understand_local`
- `openphone.app.open`
- `openphone.url.open`
- `openphone.ui.tap`
- `openphone.ui.type_text`
- `openphone.notifications.list`
- `openphone.jobs.list`

Boundary:

- MCP exposes tools.
- Runtime protocol owns attention, sessions, voice, watchers, and Dynamic
  Island state.

## CLI Bridge

Location:

```text
integrations/cli/
```

Purpose:

- Developer workflow.
- Local smoke tests.
- Demo support without demo-specific Android code.

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

Initial transport:

- ADB.

Future transport:

- local socket/service transport if needed.

## Voice Plan

Short term:

- Phone captures voice.
- Phone performs STT through configured local/BYO/realtime provider.
- Transcript routes to selected runtime.
- Runtime returns text.
- If source is volume voice and runtime returns only text, OpenPhone performs
  local Android TTS.

Long term:

- Add runtime voice capability metadata:
  - text input,
  - text output,
  - audio input,
  - audio output,
  - realtime multimodal session,
  - tool calling.
- Allow runtimes to provide audio output when they support it.
- Allow OpenAI Realtime, Gemini Live, Qwen Omni, OpenClaw voice, and Hermes
  voice to fit the same capability model.

Important product rule:

- If a runtime cannot provide audio output, volume voice should still feel
  complete by using OpenPhone TTS on the runtime text reply.

## Security Model

### Required Before Merge

1. Confirmation identity must be strict.
   - A mutating request approval must not satisfy another invoke id, tool,
     runtime, session, or params body.
   - Key confirmations by runtime + session + invoke id + tool + params digest,
     or avoid idempotency coalescing for mutating tools.

2. Autonomy must be enforced in the phone.
   - `observe_only`: deny mutating tools.
   - `ask_before_action`: require local confirmation.
   - `trusted_actions`: still require confirmation for high-risk commands.

3. Runtime adapter shared state must be thread-safe.
   - Do not mutate access-order maps across threads without a lock.
   - Use synchronized blocks or concurrent structures with bounded eviction.

4. Debug smoke receiver must not ship as an enabled production receiver.
   - Keep it debug-only or disabled by default.
   - Do not rely only on runtime checks inside an exported receiver.

5. OpenClaw node policy must come from the plugin.
   - No OpenPhone-specific default command allowlist in OpenClaw core.
   - No private screen/user-data reads default-allowed without plugin policy.

6. Runtime reload must be reliable.
   - If settings change while the service is alive, adapters reload/restart.

7. Background jobs must reflect real dispatch state.
   - Do not mark complete because an intent was sent.
   - Track sent, pending, completed, failed, retryable.

8. Prompt injection from runtime-controlled context must be contained.
   - Do not concatenate untrusted runtime context as raw authority-bearing
     instructions.
   - Prefer structured fields or escaped/labeled context.

9. Audit logging must cover remote tool use.
   - Log runtime, session, tool, risk, confirmation state, result, and error.

### Required Before Non-Debug Enablement

- Store signing keys with AndroidKeyStore or equivalent strong storage.
- Replace or independently review hand-rolled WebSocket code.
- Add structured runtime error codes.
- Add payload size limits.
- Add rate limits.
- Add cert/TLS policy for non-loopback remote endpoints.

## Code Quality Plan

Keep names generic:

- Good: `RuntimeToolBridge`, `RuntimeManager`, `RuntimeAdapter`.
- Bad in core: `OpenClawToolBridge`, `OpenClawRuntimeManager`.

Split large classes where it pays off:

- `OpenClawRuntimeAdapter`: lifecycle only.
- `OpenClawProtocolMapper`: OpenClaw frame/event mapping.
- `OpenClawMessageParser`: runtime message extraction.
- `OpenClawNodeCommandMapper`: command mapping from manifest.
- `OpenClawDeviceIdentity`: node identity/auth.
- `RuntimeConfirmationBroker`: confirmation lifecycle.

Centralize schema helpers:

- `JsonFields.pickString(...)`
- `JsonFields.pickObject(...)`
- `JsonFields.pickArray(...)`

Reduce silent failures:

- Replace unexpected `catch (JSONException ignored)` with `Log.w` or structured
  protocol errors.
- Keep silent fallback only where a missing optional field is genuinely
  expected.

## Implementation Phases

### Phase 0: Stabilize the Plan

Tasks:

- Keep WIP local unless the user approves a push.
- Commit `NEW_PLAN.md` locally.
- Treat the current branch as the implementation branch, but continue cleaning
  it toward the generic runtime architecture.

Exit criteria:

- Plan is the source of truth.
- Branch status is understood.

### Phase 1: Protocol Source of Truth

Tasks:

- Finalize `runtime/protocol/openphone-commands.json`.
- Finalize `runtime/protocol/openphone-events.json`.
- Finalize `runtime/protocol/openphone-capabilities.json`.
- Validate duplicate commands, aliases, risk classes, and generated outputs.
- Ensure Android/OpenClaw/MCP/CLI command lists are generated or checked from
  the manifests.

Exit criteria:

- `scripts/check-runtime-protocol.sh` passes.
- No unpinned command-list drift.

### Phase 2: Generic Runtime Core

Tasks:

- Ensure all core classes are generic runtime classes.
- Keep OpenClaw-specific code only under the OpenClaw adapter.
- Make runtime status typed and consistent.
- Make `statusJson()` pure.
- Ensure settings reload restarts adapters when necessary.

Exit criteria:

- Generic runtime core has no OpenClaw-specific command policy.
- Phone/OpenClaw can both appear as runtimes.

### Phase 3: Security Fixes

Tasks:

- Fix confirmation/idempotency identity.
- Enforce autonomy modes.
- Fix runtime adapter map synchronization.
- Lock down debug smoke receiver.
- Add/verify audit logs.
- Sanitize or structure untrusted runtime context.

Exit criteria:

- Security review must-fix items are closed.
- Smoke tests cover approve, deny, timeout, replay, and observe-only denial.

### Phase 4: OpenClaw Adapter

Tasks:

- Keep `agent.request` stock and generic.
- Remove provider/model hardcoding such as `thinking: low`.
- Ensure screen attachments are included only when requested and model/runtime
  supports them.
- Fix live final-message delivery:
  - first verify whether current OpenClaw has a supported node delivery route;
  - if yes, use that route from Android;
  - if no, propose a small generic OpenClaw node session fanout API.
- Verify OpenClaw can call phone tools and receive results.

Exit criteria:

- Phone-originated text attention returns a visible OpenClaw answer.
- Screen read with screenshot works against live OpenClaw.
- Mutating action approval/deny/timeout works against live OpenClaw.

### Phase 5: OpenClaw Plugin

Tasks:

- Keep plugin in `integrations/openclaw-plugin/`.
- Build `src/index.ts` to `dist/index.js`.
- Verify install into a fresh OpenClaw profile.
- Verify plugin metadata/policy is active.
- Add README instructions for local install and eventual ClawHub publishing.

Exit criteria:

- Plugin import test passes.
- Fresh OpenClaw can install the local plugin path.
- Policy appears in OpenClaw runtime inspection.

### Phase 6: Runtime UI

Tasks:

- Runtime tab in Dynamic Island.
- Concise cards/labels for Phone and OpenClaw.
- Runtime screen in app with per-surface defaults.
- Clear selected state.
- Ability to switch back to Phone.

Exit criteria:

- User can tell which runtime handles chat/volume/background.
- User can switch runtime without ADB.

### Phase 7: Voice

Tasks:

- Route volume transcript to selected runtime.
- For OpenClaw text replies, use Android TTS when source is volume voice.
- Add capability labels for text/audio/realtime support.
- Do not block the first milestone on OpenClaw-native audio if local TTS covers
  the product experience.

Exit criteria:

- Volume button -> OpenClaw -> audible answer works.
- Runtime UI does not imply unsupported audio capabilities.

### Phase 8: MCP and CLI

Tasks:

- Keep MCP tools manifest-backed.
- Keep CLI commands manifest-backed.
- Add dry-run/fake transport tests.
- Use CLI for local smoke workflows, not production-only behavior.

Exit criteria:

- MCP tests pass.
- CLI tests pass.
- A developer can inspect status, get screen context, and invoke a safe tool.

### Phase 9: EC2 Build and Pixel Smoke

Tasks:

- Sync code to EC2.
- Build with the correct product:

```sh
ALLOW_MISSING_DEPENDENCIES=true \
OPENPHONE_BUILD_GOAL=OpenPhoneAssistant \
./scripts/build.sh openphone_tegu-bp4a-userdebug
```

- Pull the built APK back.
- Push it to the USB-connected Pixel.
- Hash-verify installation.
- Restore `adb reverse` for OpenClaw gateway when needed.

Required smoke:

- Runtime list shows Phone and OpenClaw.
- Runtime tab can switch Phone/OpenClaw.
- OpenClaw connects.
- Phone attention reaches OpenClaw.
- OpenClaw final answer appears on phone.
- OpenClaw can read screen metadata.
- OpenClaw can read screenshot when requested.
- OpenClaw mutating action requires approval.
- Approval executes.
- Denial does not execute.
- Timeout does not execute.
- Volume voice gets audible response.
- Switching back to Phone works.

Exit criteria:

- Pixel smoke passes with real OpenClaw, not only a shim.

### Phase 10: PR Readiness

Tasks:

- `git diff --check`
- secret scan over branch diff
- `scripts/check-runtime-protocol.sh`
- `scripts/check-assistant-java.sh`
- `scripts/check.sh`
- `npm test --prefix integrations/openclaw-plugin`
- `npm test --prefix integrations/mcp-server`
- `npm test --prefix integrations/cli`
- EC2 Android build
- Pixel smoke notes
- Update PR description with validation and security notes

Exit criteria:

- No secrets.
- No demo-only production paths.
- No OpenPhone-specific OpenClaw core changes.
- Branch has a coherent review story.
- User explicitly approves push.

## Live OpenClaw Demo Definition

A real demo should show:

1. Runtime tab shows `Phone` and `OpenClaw`.
2. User taps `OpenClaw`.
3. User invokes the assistant from chat or volume.
4. OpenClaw receives the request as the brain.
5. OpenClaw asks for screen context.
6. Phone returns accessibility/screenshot context.
7. OpenClaw explains what it sees or chooses an action.
8. If it wants to act, OpenPhone asks the user for confirmation.
9. User approves.
10. Phone executes the action.
11. User switches back to `Phone`.

This demonstrates the meaningful integration: OpenClaw is not just replying in
chat; it is using OpenPhone as a trusted Android endpoint with screen/action
tools and local confirmation.

## Acceptance Criteria

Milestone 1 is complete when:

- OpenPhone runtime core is generic.
- OpenClaw is implemented as an adapter plus plugin.
- Command/event/capability metadata is manifest-backed.
- Runtime UI clearly supports Phone and OpenClaw.
- Chat and volume can route to OpenClaw.
- OpenClaw can inspect the screen.
- OpenClaw can request phone actions.
- OpenPhone enforces confirmations.
- Voice has an audible path through Android TTS when needed.
- MCP and CLI remain generic tool access paths.
- EC2 build passes.
- Pixel real OpenClaw smoke passes.
- No secrets or WIP artifacts are staged.

Hermes starts after Milestone 1, using the same Runtime Agent Protocol.
