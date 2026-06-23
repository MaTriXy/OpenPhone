# OpenPhone Plan

This is the canonical working plan for OpenPhone. Older task-specific plans
have been folded into this document, `docs/ROADMAP.md`,
`docs/IMPLEMENTATION_STATUS.md`, `docs/BUILD.md`, and the device notes.

This file should answer three questions for a new contributor:

- What are we building and why?
- What is already proven on real hardware?
- What should be implemented next?

## Goal

Build the first usable AI-first Android phone OS:

```text
user trigger -> active task -> observe screen -> reason -> request action ->
policy check -> visible action -> audit -> repeat
```

OpenPhone is not an app-only assistant. The agent runs through OS-mediated
capabilities exposed by a privileged OpenPhone framework service and a
privileged assistant app.

## Current Baseline

Already proven:

- Pixel 9a `tegu` can boot official LineageOS and the OpenPhone product.
- The verified first device is Google Pixel 9a, codename `tegu`, SKU `GTF7P`,
  on LineageOS `lineage-23.2` / Android 16 `bp4a`.
- `openphone_tegu` includes the privileged `org.openphone.assistant` app.
- `OpenPhoneAgentManagerService` registers as the `openphone_agent` Binder
  service in `system_server`.
- The framework service supports task creation, screen context, opt-in
  screenshot payloads, policy decisions, action execution, pending
  confirmations, pointer events, and durable audit logging.
- The assistant can start tasks, show a user-facing control surface, display an
  active-task indicator, show a cursor overlay, capture voice for a short
  command, transcribe through the development OpenAI path, and run basic
  proof-of-loop actions.
- The assistant main screen has moved from a developer console toward a clean
  chat surface: a single composer accepts text, an icon action switches between
  mic/send/stop based on state, the input stays above the keyboard, tapping
  outside dismisses the keyboard, and model/task controls live behind the
  profile/advanced surface.
- Privileged assistant APK fast-iteration is validated on the Pixel 9a for UI
  and model-loop work; full OTA/recovery loops are reserved for framework,
  sepolicy, boot-chain, Settings/SystemUI, and first-install changes.
- The first Pixel 9a boot-chain issue was diagnosed: generated
  `vendor_kernel_boot.img` had an empty DTB. The current known-good workaround
  extracts `tegu.dtb` from the upstream prebuilt before target-files/OTA
  generation.
- The first full-product crash was diagnosed as missing SELinux labeling for
  the `openphone_agent` service and fixed in the sepolicy patch stack.
- A development OpenAI vision/tool loop has been physically validated on the
  Pixel 9a for basic observe/action/finish behavior.
- The first semantic UI-targeting agent path has been physically validated on
  the Pixel 9a: the model opened Settings, selected the Apps row by
  accessibility element ID through `tap_element`, verified the Apps page, and
  finished the task from trajectory evidence.

Still missing:

- A stronger agent loop that reliably plans and executes multi-step phone
  tasks from the current screen across apps, not just bounded Settings-style
  tasks.
- OCR-style screen understanding and production framework-owned UI hierarchy.
  A first assistant-side accessibility UI tree path now exists for development.
- Production model transport, rate limits, and privacy controls. A first
  broker/proxy transport path now exists so the phone can call an
  OpenPhone-controlled endpoint with a session token instead of holding a
  provider API key. A first dependency-free reference broker exists under
  `services/model-broker/` with an admin-authenticated signed session-token
  issuer, provider/model registry, device-subject registry, and development
  HMAC device-proof gate for registered subjects, but hardware-backed device
  attestation, managed deployment operations, and production privacy policy
  enforcement remain.
- SystemUI-owned active agent surface. First Settings-owned OpenPhone,
  task-grant, and audit surfaces exist. A first durable task-grant defaults
  editor exists, but a full per-app/per-capability grant manager remains.
- Reproducible release packaging, changelog discipline, and GitHub automation.
- A production app-store/default-app strategy. Current docs support no-Google
  mode and user-supplied Google services for developer devices, but OpenPhone
  does not redistribute Google apps or Play services.

Supporting context:

- Detailed boot-chain history lives in `docs/BRINGUP_LOG.md` and
  `docs/TEGU_BOOTCHAIN.md`.
- Build and flash commands live in `docs/BUILD.md` and `devices/tegu.md`.
- Implementation evidence lives in `docs/IMPLEMENTATION_STATUS.md`.
- Repeatable eval tasks live in `docs/TESTING.md`.

## Architecture Direction

Keep the phone runtime native to Android:

```text
OpenPhoneAssistant
  - user trigger
  - task UI
  - voice/text input
  - model adapters
  - confirmation UI
  - audit viewer

OpenPhoneAgentManager
  - hidden framework API
  - trusted client boundary

OpenPhoneAgentManagerService
  - active task registry
  - screen observer
  - policy engine
  - action executor
  - pointer event publisher
  - audit log

Android system services
  - ActivityTaskManager
  - WindowManager/InputManager
  - ClipboardManager
  - PackageManager
  - SystemUI and Settings later
```

The model never controls Android directly. It emits structured tool requests.
The framework service validates and executes them.

## CUA-Informed Agent Improvements

CUA is useful as a reference for the agent contract, trajectory logging, and
testing mindset. It should not become the OpenPhone runtime because its Android
path is emulator/ADB oriented. OpenPhone should implement the same class of
capabilities inside the phone through privileged OS services.

Borrow from CUA:

- Observation/action loop: screenshot -> model -> action -> screenshot.
- Small action vocabulary: `tap`, `long_press`, `swipe`, `type_text`, `back`,
  `home`, `wait`, `screenshot`, `finish_task`, `fail_task`.
- Trajectory records containing screenshots, model messages, tool calls, action
  results, timing, and policy decisions.
- Repeatable eval tasks with pass/fail evidence.
- Host-side adapters for testing the phone during development.
- Semantic action grounding: prefer accessibility element IDs and visible UI
  labels over raw coordinates, then fall back to coordinates only when no
  semantic target exists.

Do not borrow:

- ADB as the production execution path.
- Emulator assumptions as the product architecture.
- Python/liteLLM as an on-device runtime dependency.
- Default telemetry behavior.
- Optional heavy or license-sensitive dependencies unless explicitly reviewed.

## Product Experience Bar

The assistant must feel like a consumer phone feature, not a bringup console.
The main screen should have one obvious job: accept a task and run it. Debug
controls are necessary for development, but they must stay behind a developer
surface.

V1 product behavior:

- One task input for speech or text.
- Chat-style home surface that looks and behaves like a consumer assistant,
  not a raw debug panel.
- Clear active state while the agent is observing or acting.
- Visible cursor/action feedback over the current app.
- Human-readable progress and final result.
- Confirmation cards for risky actions.
- Debug tools, model settings, traces, raw JSON, and audit logs hidden under
  Developer.
- No app-install demo path until OpenPhone has a real app-store strategy.

Agent reliability should be judged by whether a normal user can give a task,
put the phone down, and understand what happened afterward. A beautiful UI
does not compensate for poor task execution, and a good agent loop should not
be buried behind ugly developer controls.

### Active Screen Observation

The agent should see the screen while it is actively doing a user-approved
task. It should not stream the screen while idle or while merely listening.

Default observation policy:

```text
Idle:
  no screen capture

Listening without active task:
  no screen capture

Task active:
  capture at task start
  capture after each action
  capture after app/window changes
  capture when the model asks for current state
  optionally sample at low rate while waiting

High-attention burst:
  short 2-5 fps sampling for loading screens, animations, maps, scrolling
  lists, camera flows, and other rapidly changing UI
```

Default guardrails:

- active task required,
- visible indicator required,
- default watch cadence around 1 fps,
- short burst cadence capped around 5 fps,
- every capture audited,
- sensitive screens blocked or redacted where possible. Status: the first
  assistant-side accessibility guardrail now flags password/payment/account-like
  UI, redacts password-field labels from the UI tree, and blocks screenshot
  tool results when those risk flags are present.

### Agent v1 Scope

The first real agent should handle a bounded task set well:

- Start from user speech or text.
- Capture the current screen as a screenshot payload.
- Ask a model to return one structured next action.
- Execute the action through `OpenPhoneAgentManagerService`.
- Capture the next screen.
- Repeat until the task finishes, fails, or asks for confirmation.
- Show a visible cursor/status indication throughout the task.
- Write a trajectory for debugging.
- Prefer `tap_element` / `long_press_element` when the UI tree exposes a
  matching interactive element, so actions are tied to labels and bounds rather
  than guessed pixels.

Initial demo tasks:

- Open Settings and toggle a simple setting.
- Open an installed app from the launcher.
- Search in the browser.
- Type into a text field and stop before sending/posting.

Current physical evidence:

- `Open Settings.` passes on Pixel 9a with the development OpenAI path.
- `Open Settings, open the Apps settings page, then finish when the Apps page
  is visible.` passes on Pixel 9a. Trajectory
  `.worktree/evals/20260604-semantic-elements-pass/trajectory` records
  `open_app`, `tap_element` on `el-6` labeled
  `Apps | Recent apps, default apps`, then `finish_task` after observing
  `com.android.settings/.SubSettings` with Apps-page text.
- `browser-open-wikipedia` reached Wikipedia on the live Pixel 9a, but exposed
  a benchmark/trajectory gap: the assistant reported `task.finished` before
  durable final text evidence was present in the pulled trajectory. The
  benchmark runner now captures final device UI after the task, and the adapter
  has a finish-evidence guard that must be validated in the next APK build.

Next agent-quality tasks:

- Harden the new lightweight verifier into a goal-aware verifier that scores
  whether the observed screen moved toward the goal, not only whether the
  screen changed.
- Add a small benchmark suite for common phone tasks and require trajectory
  evidence for pass/fail.
- Fuse screenshot vision, accessibility labels, package/activity state, and
  optional OCR into one compact observation object.
- Improve recovery behavior when an action does not change the screen:
  retry with a different target, scroll, go back, or ask the user.
- Move from prompt-shaped JSON toward strict tool/function calling wherever
  the selected model transport supports it.
- Fix the assistant-only build loop so Java/UI/agent changes can produce an APK
  without invoking the Pixel 9a kernel/device Bazel dist path.

Deferred demo tasks:

- App store / Play Store / APK installation flows. OpenPhone should eventually
  support these through a proper app-store strategy and explicit installation
  policy, but Agent v1 should not spend engineering cycles trying to install
  third-party apps. For now, the agent may open official web pages and stop.

### Agent Contracts

Initial model tools:

```text
get_screen()
watch_screen(fps, duration_ms, reason)
open_app(package_or_label, reason)
tap(x, y, reason)
long_press(x, y, duration_ms, reason)
swipe(start_x, start_y, end_x, end_y, duration_ms, reason)
type_text(text, reason)
press_key(key, reason)
set_clipboard(text, reason)
paste(reason)
share_text(text, chooser_title, reason)
wait(duration_ms, reason)
ask_user_confirmation(summary, risk, action_json)
finish_task(summary)
fail_task(reason)
```

Every tool request must include:

- active task ID,
- model-visible reason,
- capability mapping,
- policy decision,
- audit result.

Risky tools require confirmation. Unknown tools fail closed.

Status: `overlay/vendor/openphone/config/openphone_model_tools.json` is now the
machine-readable registry for the first model tool vocabulary. Repo checks
verify that every registered tool maps to a known capability, is implemented by
`FrameworkToolExecutor`, is allowed or terminal-handled by the OpenAI adapter,
and does not use stale capability IDs. The executor now rejects model tools
marked `requires_reason` when the request omits a non-empty model-visible
reason, and CI checks that reason-required registered tools are covered by that
enforcement path.

### Model Provider Order

Keep the OS service model-agnostic. The assistant owns provider adapters; the
framework owns capabilities.

Implementation order:

1. Text prompt -> model -> one structured tool call -> action.
2. Screenshot/vision -> model -> one structured tool call -> action.
3. Bounded multi-step loop using the same tool interface.
4. Realtime voice using the same tool interface.

Provider direction:

- OpenAI text/vision path first because it is easiest to debug.
- OpenAI Realtime for the more impressive voice-first UX once the tool loop is
  reliable.
- Claude/Gemini adapters later for model comparison.
- Local model adapter later for privacy/offline/OEM differentiation.

### Action Execution Validation

The framework already has early support for:

- app launch,
- Back, Home, Recents,
- tap,
- long press,
- scroll/swipe,
- text input,
- clipboard write,
- clipboard paste,
- confirmed share action.

Physical Pixel 9a validation still needs to verify:

- coordinate system,
- tap timing,
- swipe duration,
- keyboard/text entry behavior,
- behavior across Settings, browser, launcher, and common text fields,
- audit events for success and failure cases.

### Visible Cursor and Agent Presence

OpenPhone should show a cursor whenever the agent controls the phone. Do not
depend on Android's physical mouse cursor for V1.

V1 behavior:

- show active-agent chip or dynamic-island-style status,
- show cursor dot,
- animate cursor movement before taps,
- show tap ripple,
- show swipe trail,
- show typing indicator,
- expose stop control through the app, notification, or SystemUI.

Implementation path:

- V1: assistant-owned overlay using privileged overlay permissions and
  framework pointer events.
- V1.5: move the active-agent surface into SystemUI.

### User Triggers

V1 triggers:

- assistant launcher icon,
- persistent notification,
- Quick Settings tile,
- voice button inside the assistant.

Later triggers:

- long-press power button,
- lockscreen affordance,
- hardware button gesture,
- wake word,
- notification-based suggestions.

### Policy, Confirmation, Audit, and Privacy

Actions should fail closed unless:

- task is active,
- assistant is trusted,
- capability is granted,
- policy allows the action or the user confirmed it,
- display is controllable,
- target screen is not blocked.

The source capability registry is `overlay/vendor/openphone/config/`
`openphone_capabilities.json`. Repo checks now verify that the assistant
fallback `PolicyEngine` covers every registered capability with the same risk
class, so registry and app-side policy drift fails CI.

Confirmation is required for:

- sending or posting content,
- purchases/payments,
- deleting data,
- sharing files/private text,
- calling or messaging people,
- changing account/security settings,
- installing/uninstalling apps,
- granting permissions.

App install flows are out of active Agent v1 scope. The policy still treats
install/download screens as high risk so the agent stops safely when it reaches
them, but app-store integration belongs to a later product track.

Every task should record:

- user request,
- start/end timestamps,
- apps observed,
- screen captures or capture references,
- model tool calls,
- policy decisions,
- user confirmations,
- actions executed,
- failures and retries.

Default privacy stance:

- no screen capture outside active tasks,
- clear visible indicator while observing,
- immediate stop control,
- user-visible audit log,
- sensitive screen block/redaction hooks,
- explicit model provider disclosure,
- cloud model use disclosed,
- future controls for blocked apps, always-confirm apps, audit deletion, and
  audit export.

## V1 Done Criteria

V1 is achieved when a Pixel 9a can be flashed with an OpenPhone OTA and pass
this demo:

1. Boot into OpenPhone without manual partition repair.
2. Show OpenPhone identity in system properties and Settings/About.
3. Start the OpenPhone assistant as a privileged system component.
4. Let the user start a visible assistant task.
5. Read foreground app/screen context through the framework service.
6. Capture a task-scoped screenshot payload.
7. Ask a model for a structured next action.
8. Perform low-risk actions across apps through OS mediation.
9. Require explicit task grants for input actions.
10. Require confirmation for medium/high-risk actions.
11. Persist an audit log showing task, context, policy, action, confirmation,
    and result events.
12. Survive reboot with the assistant and framework service available.

V1 should feel like this:

```text
The phone is running OpenPhone.
The assistant is part of the OS.
The assistant can see what app/screen is active.
The assistant can act across apps after user-approved grants.
The user can see and inspect what happened.
```

## Public Project and Release Plan

OpenPhone should now become a proper public GitHub project while remaining
honest about maturity. The first public version is `0.0.1`.

### Version 0.0.1 Definition

`0.0.1` is a developer preview, not a consumer ROM.

It should include:

- Public repository structure and documentation.
- Clear source-available non-commercial licensing.
- Build and flash instructions for Pixel 9a.
- Documented known-good artifact hashes when releases are published.
- Current OpenPhone assistant and framework service source.
- CI checks for repository hygiene.
- Changelog and release notes.
- Device support matrix showing Pixel 9a as the first development target.

It does not need:

- Production signing. Status: first private signing workspace/key-map
  preparation helper and signed OTA releasetools wrapper exist, but published
  preview artifacts are still test-key/development artifacts until a signed
  target-files/OTA run is performed and validated.
- OTA updater. Status: first server-side OTA feed schema, generator, and
  validator exist. A first assistant-owned preview OTA client can check an OTA
  feed, verify the device, download the selected OTA to `Downloads/OpenPhone`,
  and validate size/SHA-256 before exposing it for manual recovery/host
  installation. Silent A/B installation from the phone remains pending.
- Play Integrity compatibility.
- Broad device support.
- Fully autonomous agent tasks.

### Release Artifacts

For each tagged release:

- GitHub release notes.
- Source archive from GitHub.
- Generated OTA artifact when available.
- SHA-256 checksums.
- Device-specific flashing notes.
- Known issues.
- Upgrade/wipe guidance.

### CI/CD

Start with lightweight CI that can run on GitHub-hosted runners:

- shell syntax checks,
- JSON validation,
- XML validation when `xmllint` is available,
- required-file checks,
- markdown/documentation sanity checks where practical.

Do not attempt full Android image builds in GitHub Actions yet. Full device
builds need a prepared Linux Android build host with substantial disk, RAM, and
cache.

Later CI/CD:

- self-hosted Linux builder for `openphone_tegu`,
- signed development artifacts,
- checksum generation. Status: local release manifest/checksum generator
  implemented for release artifact directories,
- release artifact validation. Status: local staged-artifact validation checks
  SHA-256 entries, OTA ZIP integrity, and obvious secret/key mistakes before
  publication,
- release draft creation. Status: local GitHub CLI draft command generation is
  implemented for staged release artifacts and release notes,
- current v0.0.1 preview staging. Status: a clean local preview staging
  directory has been generated for the current Pixel 9a OTA candidate with
  `SHA256SUMS`, `ARTIFACTS.md`, release validation, and an inspectable GitHub
  draft command,
- on-device update checks. Status: assistant-owned preview client can consume
  the generated OTA feed, verify that the feed targets the current device,
  download the OTA ZIP into `Downloads/OpenPhone`, and reject size/SHA-256
  mismatches before manual installation,
- flash smoke-test checklist,
- optional device-farm/manual validation report upload.

## Task Backlog

### Track A: CUA-Informed Agent v1

Status: in progress.

- Define the final phone-side model tool schema. Status: first version
  implemented; `get_screen` and bounded `watch_screen` are exposed through the
  assistant tool executor. A machine-readable model-tool registry now maps the
  initial tool vocabulary to product capabilities, and CI validates registry,
  executor, and OpenAI adapter coverage.
- Add trajectory logging for screenshots, model messages, tool calls, action
  results, policy decisions, and failures. Status: first assistant-side
  recorder implemented; assistant can export the latest trajectory zip to
  `Downloads/OpenPhone`. Trajectory `events.jsonl` entries now carry the
  `openphone.trajectory_event.v1` schema marker, and
  `docs/contracts/trajectory-event.schema.json` defines the first public event
  contract for task start, tool call, tool result, and agent result events.
  CI validates that recorder event names stay aligned with the schema.
  `scripts/validate-trajectory-export.sh` validates exported trajectory
  directories or zips for schema markers, event ordering, screenshot file
  references, and obvious secret/raw-base64 leakage.
- Implement one-step vision action selection using the existing screenshot
  payload path. Status: development OpenAI vision path implemented; needs
  physical eval evidence.
- Add a bounded multi-step agent loop with max steps, max duration, and stop
  control. Status: max steps, duration, task stop, stale-result suppression,
  and in-flight model cancellation are implemented in the assistant/module
  build.
- Add action retry/failure handling. Status: first pass implemented for the
  development OpenAI loop; screen capture gets one retry and repeated tool
  failures stop the run with `action_failed` evidence.
- Add UI hierarchy/OCR extraction path. Status: first assistant-side
  accessibility UI tree path implemented. It now marks password/payment/account
  risk hints, redacts sensitive input labels, and blocks screenshot capture
  through the tool executor on flagged screens. Framework-owned extraction and
  OCR remain. `docs/contracts/screen-context.schema.json` now covers the
  assistant UI-tree snapshot fields for source, timestamp, windows,
  interactive element state, view/window IDs, sensitivity flags, and risk
  hints; CI validates that those emitted fields stay represented in the
  contract.
- Improve cursor overlay so every action is visible and understandable. Status:
  assistant-owned overlay now shows tap ripples, long-press emphasis, swipe
  trails, typing indication, and action labels for model/developer actions.
- Add safer confirmation UX for app installs, messaging, sharing, payments,
  settings/security changes, and destructive actions. Status: first
  assistant-owned approval surface implemented; framework pending actions and
  model confirmation requests now surface risk, capability, and action summary
  outside the raw developer JSON view. The development OpenAI loop also has a
  deterministic pre-action guardrail that converts install, payment,
  destructive, messaging/sharing, and login/password screen actions into
  confirmation requests before execution. App installation itself is deferred;
  Agent v1 should stop at official web/app-store surfaces rather than trying to
  complete installs.
- Require explicit task grants for input and data-moving actions. Status:
  assistant-owned task grant controls now split input/navigation, task-scoped
  screenshots, clipboard, share sheet, and web-link capabilities; model tools
  that exceed the selected grants are stopped before framework execution.
  Grant defaults are persisted through Settings-owned `Settings.Secure` keys
  with app-private fallback for migration/development. The assistant fallback
  policy now includes the full product capability registry, including
  `share.content`, and CI checks registry/policy risk consistency. A first
  machine-readable per-app capability policy seed now ships under
  `system_ext`; assistant preflight first checks the durable
  `Settings.Secure` `openphone_app_policy_overrides` JSON contract, then falls
  back to the seed while using the current foreground package to require
  confirmation or deny sensitive package/capability combinations.
  `scripts/generate-app-policy-override.sh` can generate and install a valid
  override for development/eval use while the Settings editor is deferred. A
  full Settings-owned per-app/per-capability grant manager remains pending.
- Keep model-triggered app and web launches framework-mediated. Status:
  `open_url` now routes through a framework `open_url` action with
  `network.use` capability, task-grant/policy evaluation, and framework audit;
  CI prevents the assistant tool executor from launching web intents directly.
  CI also verifies that every action type emitted by `FrameworkToolExecutor` is
  allowed by `docs/contracts/action-request.schema.json` and has corresponding
  framework patch-stack handling.
- Keep audit evidence contract-aligned. Status: the audit-event schema now
  covers task start/stop, screen context/capture/watch, capability evaluation,
  action confirmation, action execution, and rejection events emitted by the
  framework patch stack. CI extracts `recordAudit(...)` event names from the
  framework patches and fails if the public audit schema is missing one.
- Disclose provider/model/cloud behavior in the task UI and debug evidence.
  Status: assistant model adapters expose provider, model, cloud/local mode,
  and disclosure text; the active task surface and trajectory start event now
  include that metadata.
- Add production-safe model transport/key flow. Status: first assistant-side
  broker/proxy transport implemented for the cloud vision loop and voice
  transcription request path. The broker uses `/v1/responses` and
  `/v1/audio/transcriptions` proxy-shaped endpoints with a session token so
  provider API keys can stay server-side. A first reference broker server is
  implemented in `services/model-broker/` with bearer-token auth, coarse
  body-size limits, in-memory per-token/IP request-count and byte-volume
  rate limiting, no request-body
  logging, OpenAI proxying, signed expiring development tokens, structured
  JSONL request-outcome audit events, and a Responses API model allowlist.
  It now also exposes an admin-authenticated `/v1/session_tokens` endpoint for
  minting signed expiring session tokens without putting provider keys on the
  phone, and it can load an explicit JSON provider registry for OpenAI
  endpoint/model configuration plus a JSON device registry that restricts
  token issuance to known subjects. Device registry entries can require a
  development HMAC proof before session-token minting. Automated broker smoke
  coverage verifies health, admin auth rejection, attestation-required and
  invalid-attestation rejection, token minting, signed token acceptance,
  unknown device-subject rejection, malformed JSON rejection, registry-backed
  model allowlisting, body-size limits, transcription content-type enforcement,
  OpenPhone metadata requirements, sensitive-screen rejection, image-count
  limits, bounded provider retry on transient 429/5xx failures, audit event
  writing, and no request-body logging. First-pass deployment
  hardening now exists as a locked-down systemd unit, env template, and
  deployment guide under `services/model-broker/deploy/`. A first nginx TLS
  reverse-proxy template exists for hosted development deployments. A broker
  secret rotation helper now rotates token-signing/admin secrets without
  touching provider keys and can rotate provider keys without touching broker
  token/admin secrets. A first certbot/nginx setup helper renders broker-domain
  TLS config and prints or applies the certificate issuance/renewal validation
  commands. Hardware-backed device attestation and managed production
  operations remain pending.
- Add a supported way to export framework audit evidence without ADB root.
  Status: assistant-owned export implemented; it writes a redacted framework
  audit evidence JSON file to `Downloads/OpenPhone`.
  `docs/contracts/audit-evidence.schema.json` defines the export envelope, and
  `scripts/validate-audit-evidence-export.sh` validates exported audit evidence
  for schema marker, service status, audit event names, redaction, and obvious
  secret/raw-base64 leakage.
- Add eval tasks and record expected outcomes in `docs/TESTING.md`. Status:
  initial eval suite documented; local Eval 2 opens Settings on Pixel 9a.
  `docs/contracts/agent-eval-report.schema.json` and
  `scripts/validate-agent-eval-report.sh` now define and validate the
  per-run evidence envelope, including links to exported trajectory and audit
  files. `scripts/collect-agent-eval.sh` now pulls the latest exported
  trajectory/audit files from a connected phone, creates `agent-eval.json`, and
  validates the bundle once ADB shell is available.

### Track B: OS Product and Device v1

Status: in progress.

- Automate the Pixel 9a DTB extraction or replace it with a cleaner build-time
  source of truth. Status: first `scripts/build.sh` automation implemented.
- Add a build-time assertion that generated `vendor_kernel_boot.img` contains a
  non-empty DTB with the expected hash for the current Pixel 9a prebuilts.
  Status: first post-build verifier implemented for `openphone_tegu` and
  `openphone_tegu_smoke`.
- Keep `openphone_tegu_smoke` as the minimal bootability target and
  `openphone_tegu` as the full assistant/framework target.
- Verify OpenPhone identity in normal UI, not only system properties. Status:
  Settings/About source integration and a flashable OTA are built; physical UI
  validation is pending.
- Add Settings/About OpenPhone version and support links. Status: implemented
  as a `packages/apps/Settings` patch; About phone now includes an OpenPhone
  version row backed by `ro.openphone.version` and an OpenPhone support link.
- Add Settings-owned OpenPhone audit and task-grant surfaces. Status: first
  Settings-owned surfaces implemented and built; Settings now has a top-level
  OpenPhone page, an editable task-grant defaults page backed by
  `Settings.Secure`, and an audit page that reads framework service
  status/recent audit events when available. The first per-app policy seed,
  assistant enforcement path, and development override helper exist, but a full
  editable per-app/per-capability grant manager remains pending.
- Move active agent chip/stop affordance into SystemUI after the assistant-owned
  overlay is stable. Status: first SystemUI-owned surface implemented as a
  native `openphone_agent` Quick Settings tile. It is stock/default, reads
  framework service status, opens the assistant when idle, and stops the active
  framework task when running. Full status-bar/dynamic-island treatment remains
  future work.
- Run and document hardware smoke tests for Wi-Fi, Bluetooth, cellular,
  calls/SMS when available, camera, microphone/speaker, fingerprint, GPS,
  screen lock/unlock, USB/ADB, reboot, factory reset, battery, and thermal
  behavior. Status: repeatable Pixel 9a smoke-test script and evidence-report
  workflow implemented. Latest physical baseline has ADB shell/logcat ready and
  automated probes completed; manual SIM/call/SMS/audio/camera/fingerprint/
  reboot sections still need human pass/fail recording.
- Document rollback and recovery from boot loops, recovery sideload failures,
  SPL downgrade errors, and `init_user0_failed` data issues.

### Track C: Public Project v0.0.1

Status: in progress.

- Make `docs/PLAN.md` the canonical plan. Status: done.
- Keep `docs/ROADMAP.md` short and public-facing. Status: done.
- Add `CHANGELOG.md`. Status: done.
- Add release process documentation. Status: done.
- Add GitHub Actions CI for `scripts/check.sh`. Status: done.
- Add GitHub issue templates. Status: done.
- Add pull request template. Status: done.
- Add security policy and vulnerability reporting guidance. Status: done.
- Define `v0.0.1` release checklist. Status: done.
- Tag and publish `v0.0.1` only after the repo passes CI and the current Pixel
  9a state is accurately documented.

## Plan Consolidation Notes

`PLAN_MAKE_ASSISTANT_WORK.md` and `docs/V1_AI_PHONE_PLAN.md` were removed
because they overlapped and were starting to drift. Their durable content now
lives here or in focused docs:

- product goal, OS-level control boundary, active-task screen observation,
  model/tool contracts, visible cursor, triggers, policy, audit, and privacy:
  this file;
- build, flash, recovery, and Linux build-host workflow: `docs/BUILD.md` and
  `devices/tegu.md`;
- Pixel 9a boot-chain diagnosis, DTB handling, SPL/data-wipe issues, and
  recovery notes: `docs/TEGU_BOOTCHAIN.md` and `docs/BRINGUP_LOG.md`;
- implementation evidence and current pass/fail status:
  `docs/IMPLEMENTATION_STATUS.md`;
- repeatable agent and device evals: `docs/TESTING.md`;
- public release process, changelog, and release notes:
  `docs/RELEASE_PROCESS.md`, `CHANGELOG.md`, and `docs/releases/`.

## Immediate Next Steps

1. Fix the assistant-only build loop. Current `openphone_tegu` module builds can
   accidentally trigger Pixel kernel/Bazel work, while `openphone_arm64` is
   blocked by a missing generic `Calendar` module. The next engineering task is
   a reliable Java/app-only APK build path for `OpenPhoneAssistant`, followed by
   `scripts/push-assistant-apk.sh` onto the Pixel 9a.
2. Build and install the upgraded agent APK that includes the 25-step budget,
   AndroidWorld-style prompt contract, no-progress verifier, and finish-evidence
   guard. The semantic `tap_element` path is already physically validated; the
   upgraded long-horizon loop still needs physical validation.
3. Run `scripts/run-agent-benchmark.sh --benchmark
   docs/agent-benchmarks/openphone-v0.json` on the Pixel 9a. Treat the
   benchmark summary as the source of truth, not single hand-run demos.
4. Triage failures by category:
   - observation failure: missing screenshot, missing UI tree, stale final
     evidence, or browser/WebView text not captured;
   - planning failure: wrong app, wrong subgoal, premature finish, repeated
     no-op;
   - action failure: bad element target, coordinate fallback mistake, keyboard
     input issue, scroll direction issue;
   - policy failure: false risky-screen block or missing confirmation resume.
5. Expand the benchmark from browser/settings into AndroidWorld-style buckets:
   create, edit, delete safe test records, query/retrieve, system settings,
   files/media, communication draft-only, and cross-app copy/use workflows.
6. Add durable success checks for each benchmark task. Use final live UI as a
   temporary check, but prefer OS/app state where possible: settings values,
   package/activity state, files, clipboard, app databases, or exported audit
   evidence.
7. Only after the benchmark loop is stable, improve the consumer UI again:
   task progress, interruption, confirmation resume, trace viewer, and visible
   cursor polish.
