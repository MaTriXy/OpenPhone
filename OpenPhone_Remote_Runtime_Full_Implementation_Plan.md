# OpenPhone + OpenClaw Demo Completion Plan

Last updated: 2026-06-26

This is the canonical plan for the current milestone. The milestone is OpenClaw-first remote runtime support for OpenPhone. Hermes and embedded runtime work are intentionally deferred until the OpenClaw phone demo is real, reliable, and reviewable.

## Decision

We are building OpenPhone as a remote-runtime peripheral for OpenClaw.

OpenClaw is the remote brain. OpenPhone remains the phone body:

- screen and app context
- Android action execution
- local confirmation UI
- policy and permission enforcement
- audit trail
- session continuity on the device

This is not a pure remote chat integration. The demo must show OpenClaw using the phone through OpenPhone tools.

## Public Repo Safety

Do not push this WIP to GitHub until the end-to-end demo works and the changes have been reviewed.

The repositories are public/open source now, so keep WIP private:

- Do not push partial branches.
- Do not push EC2 hostnames, IPs, pairing tokens, OAuth stores, logs, screenshots, device IDs, or local config.
- Do not publish demo state files.
- Keep OpenPhone and OpenClaw changes separate.
- Only prepare public PRs after the demo path is complete and the diff is intentionally cleaned.

## Product Model

OpenPhone should expose a user-visible runtime model:

```text
Agent Runtime = who thinks
Phone Runtime = who sees/touches the Android device
```

For this milestone:

- `Local Phone Runtime` is always present and built in.
- `OpenClaw` is an optional remote Agent Runtime.
- `Hermes` is not part of the first working demo.
- The Local Phone Runtime remains the execution and safety layer even when OpenClaw is selected as the brain.

The UI should not make the user think OpenClaw has direct, uncontrolled access to Android. OpenClaw asks; OpenPhone executes or refuses.

## Runtime UX Target

Add an `Agent Runtimes` or `Runtimes` surface in the Dynamic Island/OpenPhone app.

Required entries for V1:

- `Local Phone Runtime`
  - built in
  - always available
  - default fallback
- `OpenClaw`
  - remote
  - connected/disconnected/auth/error status
  - selected or not selected as the default chat brain

Optional later entry:

- `Hermes`
  - hidden or disabled for this milestone
  - do not spend implementation time on it now

Required settings:

- `Default Chat Runtime`
  - V1 choices: `Local Phone Runtime`, `OpenClaw`
- `Volume Trigger Runtime`
  - V1 default: `Local Phone Runtime`
  - OpenClaw voice can come later through transcript routing
- `Background Runtime`
  - V1 default: `Local Phone Runtime`
  - remote watcher orchestration can come later

Required visible behavior:

- Chat and Dynamic Island should show which runtime is currently answering.
- If OpenClaw is selected but unavailable, the UI should show a clear disconnected state and fall back deliberately, not silently.
- The hidden setting `openphone_assistant_brain=openclaw` should become a visible user-facing runtime choice.

## Demo Definition

The first credible demo is phone driven. After setup, the user should not need to operate the OpenClaw CLI live during the demo.

Demo flow 1: screen understanding

```text
User selects OpenClaw as the Chat Runtime on the phone.
User asks from the phone: "What is on my screen?"
OpenPhone sends an attention/request turn to OpenClaw.
OpenClaw calls the phone screen tool through the OpenPhone node.
OpenPhone captures the real screen and returns it.
OpenClaw answers based on the actual phone screen.
The answer appears back on the phone.
```

Demo flow 2: phone action with confirmation

```text
User asks from the phone: "Open https://openphone.com"
OpenClaw decides to call a phone action tool.
OpenPhone shows local confirmation.
User approves on the phone.
OpenPhone performs the action.
OpenClaw receives the result and reports completion back to the phone.
```

Demo flow 3: runtime visibility

```text
User opens the Runtimes surface.
Local Phone Runtime is visible.
OpenClaw is visible with connection status.
OpenClaw is selected as the default Chat Runtime.
Volume trigger remains marked as Local Phone Runtime for V1.
```

## Current Verified State

OpenPhone side:

- OpenPhone external runtime code exists locally.
- Pixel is connected over USB-C.
- Android/OpenPhone APK build was done on EC2, not locally.
- Latest installed demo APK was EC2 built.
- Final installed APK SHA-256:
  - `12ffcc179c508dad4d6c5b3632f2c9a9180d8a3f266b7ba74be91ce9812aef0f`
- Device settings can select OpenClaw as the assistant brain through hidden config.
- Phone can connect to OpenClaw through the ADB reverse plus SSH tunnel path.
- Device smoke test passed for:
  - gateway offline handling
  - insecure transport denial
  - bad auth failure
  - attention/final fanout
  - unknown command denial
  - denied confirmation
  - approved confirmation
  - confirmation timeout
  - idempotency retry handling

OpenClaw side:

- OpenClaw runs on the private EC2 demo host.
- Gateway is bound to loopback and reached from the phone through tunnel/reverse networking.
- Pixel is paired as an OpenClaw node.
- OpenClaw can directly invoke `openphone.screen.get` on the Pixel.
- OpenClaw can directly invoke `canvas.snapshot` on the Pixel.
- OpenClaw can invoke a mutating phone action and have OpenPhone hold the original tool call pending until local confirmation resolves.
- Phone-originated attention reaches OpenClaw and can return a terminal model answer to the phone.
- Focused OpenClaw tests passed:
  - `pnpm exec vitest run src/gateway/server-node-events.test.ts src/gateway/node-command-policy.test.ts`
  - 85 tests

What this proves:

- Pairing works.
- Transport works.
- Direct node tool invocation works.
- Phone confirmation works.
- Phone-to-OpenClaw session fanout works.
- OpenPhone can display remote runtime results.

What this does not fully prove yet:

- A natural phone-originated OpenClaw prompt reliably causes OpenClaw to call the phone screen/action tools.
- The runtime choice is understandable to a user.
- The demo can be run cleanly without hidden settings and manual recovery steps.

## Current Gap

The remaining core gap is reliability of phone-originated tool use.

Direct OpenClaw-to-phone tool calls work. A phone attention event also reaches OpenClaw and returns an answer. The missing piece is making the OpenClaw run consistently understand:

```text
This user turn came from this phone.
This phone is available as an OpenClaw node.
For screen/app/UI questions, inspect the phone through node.invoke before answering.
For phone actions, call the OpenPhone action tools and let OpenPhone handle confirmation.
```

Without that, the demo can degrade into remote chat, which is not the point.

## Implementation Plan

### 1. Finish The OpenClaw Phone-Tool Reliability Patch

Status: drafted locally in OpenClaw and focused tests passed. It still needs to be cleanly deployed/restarted on the EC2 OpenClaw demo host and re-tested from the phone.

OpenClaw files:

- `/Users/adamcohenhillel/Developer/AMBIENT/openclaw/src/gateway/server-node-events.ts`
- `/Users/adamcohenhillel/Developer/AMBIENT/openclaw/src/gateway/server-node-events.test.ts`

Required behavior:

- `openphone.attention.requested` creates an OpenClaw run tied to the phone node/session.
- The generated user message includes explicit OpenPhone device-control instructions.
- The instructions identify the target node.
- The instructions tell the agent to use the `nodes` tool with `action="invoke"`.
- For screen questions, the instructions specifically direct the agent to call `openphone.screen.get`.
- For mutating actions, the instructions state that Android-side confirmation may be required.

Validation:

- Re-run focused OpenClaw tests locally.
- Sync the patch to the private EC2 OpenClaw demo tree.
- Rebuild/restart OpenClaw on EC2.
- From the phone, ask: `What is on my screen?`
- Confirm gateway logs show `node.invoke` for `openphone.screen.get`.
- Confirm phone logs show the screen tool execution.
- Confirm final answer on the phone is based on the actual screen.

### 2. Add Deterministic Screen Preflight If The Model Still Skips Tools

For the demo, do not rely only on prompt compliance if it remains inconsistent.

If the model answers without inspecting the phone, add deterministic preflight for phone-originated attention:

- When `include_screen=true`, fetch `openphone.screen.get` before starting or completing the agent turn.
- Also trigger preflight when the user prompt clearly asks about screen/app/UI state.
- Inject the screen observation into the OpenClaw run as structured context.
- Keep the phone node tool available so OpenClaw can call additional tools if needed.

This gives us a reliable demo while preserving the real remote-runtime model. OpenClaw is still the brain, but OpenPhone guarantees the run has phone context when the user asks about the phone.

Acceptance:

- The phone prompt `What is on my screen?` succeeds three times in a row without CLI intervention.
- The answer changes when the visible phone screen changes.
- The system still handles action requests through the normal OpenPhone confirmation path.

### 3. Implement The Runtime UI In OpenPhone

OpenPhone files likely involved:

- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/AssistantBrainConfig.java`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/AssistantActivityBackend.java`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/state/AssistantUiState.kt`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/state/AssistantViewModel.kt`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/ui/AssistantApp.kt`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/ui/chat/ChatScreen.kt`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/ui/runtimes/`
- `overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/`

Required UI:

- Add a `Runtimes` tab/surface.
- Show `Local Phone Runtime`.
- Show `OpenClaw`.
- Show OpenClaw state:
  - disconnected
  - connecting
  - connected
  - auth error
  - transport error
- Allow selecting default Chat Runtime.
- Show that Volume Trigger Runtime is local for V1.
- Show active runtime in chat/Dynamic Island responses.

Do not implement Hermes UI beyond a disabled/hidden placeholder unless it is nearly free. The demo is OpenClaw.

### 4. Finalize Routing Semantics

V1 routing:

- Chat typed in OpenPhone:
  - uses selected Chat Runtime
  - `Local Phone Runtime` means current local agent behavior
  - `OpenClaw` means send `openphone.attention.requested`
- Dynamic Island OpenClaw messages:
  - display OpenClaw progress/final responses
  - label them as OpenClaw
- Double volume trigger / realtime Android agent:
  - remains `Local Phone Runtime` for V1
  - do not claim OpenClaw is the realtime voice brain yet
- Classic voice transcript:
  - can route the final transcript to selected Chat Runtime if already supported cleanly
  - otherwise keep local for V1
- Background watchers:
  - remain local for V1
  - future work can let OpenClaw create/manage watcher tasks through an explicit OpenPhone watcher API

This avoids confusing three different things:

- local realtime Android agent
- local screenshot-loop/simple agent
- remote OpenClaw runtime

### 5. Harden The Demo Runbook

The demo should have a short preflight checklist:

- EC2 OpenClaw gateway running.
- OpenClaw model auth configured in private state.
- SSH tunnel running.
- ADB reverse active.
- Pixel connected over USB-C.
- OpenPhone APK installed from EC2 build.
- OpenClaw runtime shows connected in the phone UI.
- Phone is paired and approved as an OpenClaw node.
- `What is on my screen?` succeeds from the phone.
- Mutating action confirmation succeeds from the phone.

Keep exact hostnames, IPs, tokens, and node IDs out of public markdown.

### 6. Build And Install Policy

Do not compile Android/OpenPhone locally for this milestone.

Allowed locally:

- static Java/source checks
- shell smoke tests that do not build Android
- editing and reading files

Android APK/system image build:

- run on EC2
- install to the Pixel over USB-C after the EC2 artifact is available
- record final APK hash in private notes or local status, not in public release docs unless intentionally useful

### 7. OpenClaw Contribution Process

OpenClaw changes must follow the OpenClaw repository contribution process.

Expected PR scope:

- Gateway handling for OpenPhone Android node attention events.
- Phone-node tool-use instructions or preflight behavior.
- OpenPhone Android node command policy only where necessary.
- Focused tests.
- Focused docs if maintainers want them.

Do not include:

- Hermes work.
- OpenPhone repo changes.
- EC2 deployment details.
- credentials or state.
- unrelated refactors.
- changelog edits unless maintainers explicitly request them.

Before any public push:

- rebase or update from latest `origin/main`
- run focused tests
- run broader checks as feasible
- review the diff for secrets and local identifiers
- include clear PR evidence
- mark AI-assisted if required by the repo/process

## Validation Checklist

OpenClaw:

```bash
pnpm exec vitest run src/gateway/server-node-events.test.ts src/gateway/node-command-policy.test.ts
```

OpenPhone static/smoke:

```bash
scripts/check-assistant-java.sh
scripts/smoke-test-external-runtimes.sh
scripts/smoke-test-openclaw-device-failures.sh
```

Device demo checks:

```text
1. Open Runtimes on the phone.
2. Confirm OpenClaw is connected.
3. Select OpenClaw as Chat Runtime.
4. Ask: "What is on my screen?"
5. Confirm answer is based on actual screen content.
6. Ask: "Open https://openphone.com"
7. Approve on the phone.
8. Confirm browser/action opens and OpenClaw reports completion.
```

Pass condition:

- The demo works from the phone UI.
- OpenClaw is visibly the selected brain.
- OpenPhone remains visibly responsible for phone execution and confirmation.
- No CLI is needed during the live demo except for setup/preflight.

## Files To Keep Separate

OpenPhone repo:

- runtime selection UI
- external runtime adapters
- OpenClaw Android adapter
- phone tool bridge
- confirmation and audit behavior
- smoke tests

OpenClaw repo:

- node event handling
- node command policy
- agent prompt/context for OpenPhone node tool use
- OpenClaw tests

Do not mix repo-specific changes into the other repo.

## Out Of Scope For This Milestone

- Hermes implementation.
- Hermes deployment.
- Embedded Hermes/OpenClaw runtime on Android.
- Remote realtime voice as the main brain.
- Remote background watcher orchestration.
- Public launch docs.
- Publishing WIP branches.

## Completion Criteria

This milestone is complete when:

- The phone has a visible Runtimes surface.
- OpenClaw can be selected as the default Chat Runtime.
- The phone shows OpenClaw connected/disconnected state.
- From the phone, OpenClaw can answer screen questions using real phone screen context.
- From the phone, OpenClaw can request an Android action and OpenPhone can confirm/execute it locally.
- The final Android artifact was built on EC2 and installed on the Pixel.
- OpenClaw focused tests pass.
- OpenPhone static/smoke checks pass.
- The demo runbook works without manual OpenClaw CLI intervention during the live flow.
- No WIP has been pushed to public GitHub.
