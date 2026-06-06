# Implementation Status

This document tracks current implementation evidence against `SPEC.md`.

## Current Snapshot

As of the current repository manifest, the assistant package is
`versionCode=57`, `versionName=0.1.21-dev`.

Current physically validated Pixel 9a baseline:

- `openphone_tegu` boots on the purchased Pixel 9a and exposes the
  `openphone_agent` framework Binder service.
- The privileged assistant APK can be rebuilt on the Linux Android build host,
  pushed into `/system_ext/priv-app/OpenPhoneAssistant/` with
  `scripts/push-assistant-apk.sh`, and validated without a full OTA loop for
  assistant-only changes.
- The latest assistant UI pass is installed on the connected Pixel 9a as
  `.worktree/artifacts/tegu/OpenPhoneAssistant-v91-chat-icons.apk`.
- The assistant main screen is now a clean chat-style surface with:
  - one text composer;
  - a stateful icon action button: mic when empty, send when text is present,
    stop while listening or running;
  - a profile icon for the advanced/model/settings surface;
  - keyboard-aware bottom insets so the composer stays above the keyboard;
  - outside-tap keyboard dismissal;
  - the service/dynamic island hidden while the app itself is foregrounded to
    avoid competing assistant surfaces.
- On-device screenshots were captured for normal, keyboard-open, send-state,
  and keyboard-dismissed states under `.worktree/artifacts/tegu/`; no recent
  `FATAL EXCEPTION` / `AndroidRuntime` crash signatures appeared in logcat
  after the UI exercise.
- User-supplied MindTheGapps sideload after the OpenPhone OTA has been
  documented and validated as a developer-device path. OpenPhone still does not
  redistribute Google packages.

## Implemented in This Repository

- Canonical OpenPhone project structure.
- Dual-license source-available licensing boundary for OpenPhone-owned
  materials: PolyForm Noncommercial for non-commercial use and separate written
  license for commercial use.
- LineageOS upstream sync script.
- OpenPhone local manifest hook.
- Patch-stack directory layout and patch application script.
- Generic `openphone_arm64` product definition.
- `vendor/openphone` common product layer.
- Initial capability and policy JSON, with repo validation that the assistant
  fallback `PolicyEngine` covers every registered capability at the same risk
  class.
- Initial model-tool registry, with repo validation that registered tools map
  to known capabilities and are covered by the framework tool executor and
  OpenAI adapter. The tool executor now rejects reason-required model tools
  that omit a non-empty model-visible reason.
- Privileged permission allowlist seed file.
- Persistent privileged `OpenPhoneAssistant` app with a user-facing chat
  surface, task entry, voice/text start path, stop control, model settings,
  input-grant controls, OTA-preview controls, trace/audit export, and advanced
  developer diagnostics.
- Assistant Binder interface seed.
- In-app bootstrap policy evaluator and audit logger.
- Privileged assistant boot receiver that starts the assistant service after
  `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, and package replacement.
- Signature permission contract declared by the assistant for future OS-level
  screen context, action execution, task management, and audit access.
- Hidden `android.openphone.OpenPhoneAgentManager` framework manager API.
- Hidden `android.openphone.IOpenPhoneAgentService` Binder contract.
- `OpenPhoneAgentManagerService` registered from `system_server`.
- Platform-owned OpenPhone signature permissions declared in
  `frameworks/base`.
- Privileged assistant bridge to the OpenPhone framework manager API.
- Framework screen context now reports focused and visible activities from
  `ActivityTaskManagerInternal`.
- Framework action execution supports `open_app`, Back, Home, Recents, tap,
  long press, scroll, text input, web-link launch, clipboard write, clipboard
  paste, and confirmed share chooser launch through mediated OS APIs.
- Task-scoped `input.perform` grants are stored by the framework service from
  `approved_capabilities` / `granted_capabilities` on task creation. Pointer
  and text input fail closed without that task grant.
- Framework seed policy returns allow/confirmation/deny decisions for the
  initial capability classes.
- Framework action execution now creates one-shot pending action IDs for
  confirmation-required actions and exposes `confirmAction(...)` for trusted
  OpenPhone components to approve or deny them.
- Framework audit endpoint records recent task, screen, policy, and action
  events in a bounded in-memory cache backed by
  `/data/system/openphone/audit-log.json`.
- Assistant Binder methods now route task, context, action, policy, and audit
  calls through the framework manager when available.
- Assistant UI can start an audited visible task, explicitly approve
  `input.perform`, read current screen context, execute a sample Back action,
  request a confirmable action, approve or deny the pending action, and browse
  recent durable audit events from the framework service.
- Assistant-owned cursor overlay now renders action-specific feedback: tap
  ripples, long-press emphasis, swipe trails, typing indication, and transient
  action labels while the agent is controlling the phone.
- JSON contracts for tasks, screen context, action requests, and audit events.
- Framework integration contract and first nine `frameworks/base` patch sets.
- Framework `getScreen(...)`, `watchScreen(...)`, `stopTask(...)`, and
  `getPointerEvents(...)` manager APIs with active-task enforcement.
- Active screen observation. The framework now gates screen reads on active
  tasks, returns current foreground/visible activity context, and can return an
  opt-in downscaled JPEG screenshot payload as base64 from `getScreen(...)`.
- Pointer-event publishing for tap, long press, swipe, and typing actions.
- Assistant task console controls for starting/stopping a task, refreshing
  screen state, running a proof-of-loop agent, navigating Back, requesting a
  confirmable action, approving/denying pending actions, and refreshing audit.
- Assistant-owned visible cursor/status overlay for the current task.
- Persistent assistant notification with start/stop actions.
- Quick Settings tile for opening/starting the assistant.
- Model adapter boundary with a local heuristic development adapter and a
  development OpenAI Responses vision adapter.
- Assistant-side trajectory logging for agent runs. Each run records task
  start, tool calls, tool results, final agent result, and stores screenshot
  payloads as files when returned by the framework. Task start events include
  the selected provider, model, cloud/local mode, and disclosure text.
  `events.jsonl` records now include the `openphone.trajectory_event.v1`
  schema marker, and `docs/contracts/trajectory-event.schema.json` defines the
  first trajectory event contract.
- Assistant trajectory export to `Downloads/OpenPhone` as a zip file, so
  physical eval evidence can be retrieved without ADB root or a debuggable
  assistant package. `scripts/validate-trajectory-export.sh` validates exported
  trajectory directories or zips before they are used as eval/release evidence.
- Assistant audit evidence export to `Downloads/OpenPhone` is now backed by
  `docs/contracts/audit-evidence.schema.json` and
  `scripts/validate-audit-evidence-export.sh`.
- Assistant-side accessibility screen tree capture. The privileged assistant
  now declares an OpenPhone accessibility service, auto-enables it with
  `WRITE_SECURE_SETTINGS`, records visible text and interactive elements, and
  merges that UI tree into model `get_screen` results when requested. The first
  privacy guardrail now flags password/payment/account-like UI, redacts
  password-field labels from the UI tree, and blocks screenshot tool results on
  flagged screens while still returning redacted UI-tree context. The screen
  context schema now covers the assistant UI-tree snapshot fields for source,
  timestamp, windows, element state, view/window IDs, sensitivity, and risk
  hints.
- Framework tool executor now covers the initial plan vocabulary including
  `get_screen`, bounded `watch_screen`, `wait`, and explicit
  `ask_user_confirmation` handoff results. The first machine-readable
  `openphone_model_tools.json` registry now maps those model-visible tools to
  product capabilities, and reason-required tools are enforced at execution
  time.
- Framework tool execution now supports semantic UI targets through
  `tap_element` and `long_press_element`. The model can select an
  accessibility `interactive_elements[].id`, the assistant resolves that ID
  against the current UI-tree snapshot, validates that the element is enabled
  and bounded, and dispatches the action through the same OS-mediated input
  path as normal taps/long presses. This was physically validated on Pixel 9a
  by opening Settings and navigating to the Apps page through a labeled
  `Apps | Recent apps, default apps` element instead of raw coordinate choice.
- The development OpenAI loop now records lightweight after-action progress
  verification for state-changing tools. After an action, it captures the next
  screen, records before/after foreground app, activity, visible-text
  signature, and interactive-element signature in the step record, and stops
  with `no_progress` after repeated unchanged screens. This verifier is
  repo-checked but still needs a fresh assistant APK build and Pixel eval before
  it is treated as physically validated.
- Model `open_url` tools now route through the framework `open_url` action and
  `network.use` capability instead of direct assistant-side intent launches.
- Repo checks now verify that action types emitted by `FrameworkToolExecutor`
  are included in `docs/contracts/action-request.schema.json` and have matching
  framework patch-stack handling.
- Repo checks now verify that framework `recordAudit(...)` event names are
  represented in `docs/contracts/audit-event.schema.json`.
- Repo checks now verify that assistant trajectory event names emitted by
  `TrajectoryRecorder` are represented in
  `docs/contracts/trajectory-event.schema.json`.
- Repo checks now exercise `scripts/validate-trajectory-export.sh` against
  both a sample unpacked trajectory directory and zip.
- Repo checks now exercise `scripts/validate-audit-evidence-export.sh` against
  a sample framework audit evidence export.
- Agent eval reporting now has a public evidence contract at
  `docs/contracts/agent-eval-report.schema.json`.
  `scripts/validate-agent-eval-report.sh` validates eval report structure,
  checks assistant package metadata against the repo manifest, rejects absolute
  or parent-traversing evidence paths, and can validate referenced trajectory
  and framework audit evidence together.
- `scripts/collect-agent-eval.sh` now provides the host-side physical eval
  bridge: after the assistant exports trace/audit files, it pulls the newest
  evidence from `Downloads/OpenPhone`, records device/build/model metadata,
  writes `agent-eval.json`, and runs the eval evidence validator.
- `scripts/diagnose-device-connection.sh` now captures the current host/device
  connection state for bringup blockers. It records macOS USB visibility when
  available, ADB device state, fastboot visibility, shell/logcat probes, and a
  concrete diagnosis such as no USB enumeration, fastboot-visible,
  ADB unauthorized, ADB-shell-unusable, partial ADB, or ready.
- Repo checks now verify that the screen-context schema covers the key
  assistant accessibility UI-tree fields and risk flags.
- The assistant now shows a user-facing approval surface for pending framework
  actions and model confirmation requests. It summarizes risk, capability, and
  requested action outside the raw developer JSON view and keeps approve/deny
  controls visible in the main task flow.
- The development OpenAI loop now has a deterministic pre-action guardrail for
  risky screen/action combinations. If a model tries to act on install/update,
  payment/subscription, destructive data, messaging/sharing/calling, or
  account/login/password screens, the assistant asks for confirmation before
  executing the tool.
- The assistant task surface now has per-task grant controls for
  input/navigation, screenshots, clipboard, sharing, and web links. The
  selected grants are sent to the framework task request, and the assistant
  locally stops model tools that exceed the selected grants before they reach
  framework execution. Grant defaults are now persisted through Settings-owned
  `Settings.Secure` keys with app-private fallback for migration/development.
- A first per-app capability policy seed exists at
  `overlay/vendor/openphone/config/openphone_app_policy.json` and is installed
  into `system_ext`. The assistant-side accessibility screen tree now reports
  `foreground_package` and `root_packages`; assistant model-tool preflight
  first checks durable `Settings.Secure` app-policy overrides under
  `openphone_app_policy_overrides`, then uses the seed plus package context to
  require confirmation or deny sensitive package/capability combinations before
  framework execution. The seed covers Settings, permission prompts, Play
  Store, Google account/payment surfaces, and lock-credential/password surfaces
  as a conservative v1 baseline. `scripts/generate-app-policy-override.sh`
  generates and can install valid override JSON for development/eval use, and
  repo checks validate the generated payload against known capabilities. The
  Settings-owned full per-app editor is deliberately deferred until the core
  agent loop has stronger physical evidence.
- The assistant fallback policy now includes `share.content` as a high-risk
  capability, matching the product capability registry and framework share
  action path.
- Assistant model runs now have stop/cancel wiring: the active adapter can be
  cancelled, the model thread is interrupted, stale run generations cannot
  update the UI, and disconnected OpenAI calls return `cancelled` instead of a
  misleading network failure.
- The development OpenAI loop now has first-pass retry/failure handling: screen
  capture is retried once and repeated tool failures end the run with an
  explicit `action_failed` status instead of silently spinning.
- The local heuristic development adapter now returns structured JSON results
  with `task.finished` and step records, so offline evals can be interpreted by
  the user-facing result surface.
- Assistant model adapters now expose provider/model/cloud metadata and
  disclosure text. The user-facing model panel and active task surface disclose
  when OpenPhone is using the development OpenAI cloud path, what model is in
  use, what screen/task data may be sent, and that the dev API key is kept only
  in memory.
- Assistant cloud model transport now supports a broker/proxy mode. In broker
  mode, the assistant sends the same task-scoped Responses and transcription
  request shapes to an OpenPhone-controlled endpoint with a session token, so
  provider API keys can stay server-side. Direct phone-to-OpenAI remains as a
  development option.
- A first dependency-free model broker reference server exists under
  `services/model-broker/`. It validates bearer session tokens, applies
  coarse body-size plus per-token/IP request-count and byte-volume rate limits,
  avoids request-body logging,
  proxies `/v1/responses` and `/v1/audio/transcriptions` to OpenAI, and can
  mint signed expiring development session tokens through both a CLI helper and
  an admin-authenticated `/v1/session_tokens` endpoint. It also supports
  structured JSONL request-outcome audit events, a JSON provider/model registry,
  a JSON device-subject registry for token issuance, optional per-subject
  development HMAC device proofs before token minting, and an optional
  environment override for Responses API model allowlisting. First-pass Linux
  deployment hardening artifacts live under `services/model-broker/deploy/`:
  a locked-down systemd unit, an environment template that keeps secrets out of
  the repository, an nginx TLS reverse-proxy template, and installation notes
  for localhost binding behind HTTPS. `scripts/rotate-model-broker-secrets.sh`
  provides a first operational helper for rotating broker token-signing and
  admin-token secrets without modifying provider keys, and for rotating the
  provider key without modifying broker token/admin secrets.
  `scripts/setup-model-broker-tls.sh` provides a first certbot/nginx helper for
  rendering broker-domain TLS config and running or printing certificate
  issuance and renewal-validation commands.
- Automated model broker smoke coverage exists at
  `scripts/smoke-test-model-broker.sh` and is wired into `scripts/check.sh`.
  It verifies local health, admin authorization failure, token minting through
  `/v1/session_tokens`, required/invalid device-attestation rejection, signed
  token acceptance, malformed JSON rejection, device-registry-backed subject
  rejection, registry-backed model allowlist rejection, request-size and
  byte-rate rejection, transcription content-type enforcement,
  OpenPhone metadata requirements, sensitive-screen rejection, image-count
  limits, bounded provider retry on transient 429/5xx failures, body-free
  audit events, and no request-body leakage into audit/server logs.
- Settings now exposes OpenPhone as a first-class OS surface: About phone has
  OpenPhone version/support rows and the Settings homepage has an OpenPhone page
  with assistant, task-grant, audit-evidence, and support entry points. Settings
  also has dedicated OpenPhone task-grant and audit pages; the task-grant page
  stores durable defaults in `Settings.Secure`, and the audit page reads
  framework service status and recent audit events directly through
  `OpenPhoneAgentManager` when the service is available.
- Build, flash, and scaffold validation scripts.
- Pixel 9a hardware smoke-test evidence script. The script captures automated
  diagnostics for identity, Wi-Fi, Bluetooth, cellular/SIM, camera service,
  location/GPS service, fingerprint service, audio, sensors, encryption/lock
  state, battery/thermal state, and OpenPhone runtime, and writes a report under
  `.worktree/reports/`.
- Release artifact manifest generator. `scripts/generate-release-manifest.sh`
  writes `SHA256SUMS` and `ARTIFACTS.md` for a release artifact directory.
  It has been smoke-tested against the local Pixel 9a artifact cache; actual
  releases should use a clean staging directory containing only publishable
  artifacts.
- Release artifact validation gate. `scripts/validate-release-artifacts.sh`
  verifies staged release checksums, OTA ZIP integrity, required manifests, and
  obvious secret/key mistakes before publication.
- OTA feed contract and tooling. `docs/contracts/ota-feed.schema.json` defines
  the first server-side update feed for future updater clients.
  `scripts/generate-ota-feed.sh` writes feed JSON for a staged OTA, and
  `scripts/validate-ota-feed.sh` verifies feed structure plus local artifact
  size/SHA-256 when an artifact directory is provided.
- Release draft preparation. `scripts/prepare-github-release.sh` validates a
  staged release directory and writes an inspectable GitHub CLI draft-release
  command plus asset list for the release.
- Current v0.0.1 preview release staging evidence:
  - Clean local staging directory:
    `.worktree/releases/v0.0.1-preview`.
  - Staged OTA:
    `openphone_tegu-settings-grants-v55-ota.zip`.
  - Staged OTA SHA-256:
    `c2f08cad2b5247eb88982c4799901fb5f70d451ffde8cb3fde0e0b463f95a443`.
  - Generated manifest:
    `.worktree/releases/v0.0.1-preview/release-v0.0.1-preview/ARTIFACTS.md`.
  - Generated checksums:
    `.worktree/releases/v0.0.1-preview/release-v0.0.1-preview/SHA256SUMS`.
  - Generated GitHub draft helper:
    `.worktree/releases/v0.0.1-preview/release-v0.0.1-preview/gh-release-draft.sh`.
  - `scripts/validate-release-artifacts.sh .worktree/releases/v0.0.1-preview`
    passed.
- Local JSON/XML/shell scaffold checks.
- macOS case-sensitive Android build volume helper.
- Sync preflight checks for case-sensitive filesystem and Git LFS.
- Build preflight for GNU coreutils on macOS.
- Darwin Soong bootstrap test skip for local macOS builds.
- Verified Lineage `repo sync` on a case-sensitive APFS sparse volume.
- Verified `lunch openphone_arm64 bp4a userdebug` against the synced tree.
- Verified overlay and patch replay against the synced Lineage tree.
- Verified focused `OpenPhoneAssistant` module build inside the Android tree.
- Verified focused `services.core-android_common-checkbuild` build for the
  OpenPhone framework manager and `system_server` service.
- Generated privileged assistant APK at
  `out/soong/.intermediates/packages/apps/OpenPhoneAssistant/OpenPhoneAssistant/android_common/OpenPhoneAssistant.apk`.
- Verified generic `openphone_arm64` `droid` build graph on macOS/Darwin.
- Darwin compatibility patches for host build bootstrap, macOS SDK 26, Lineage
  host tools, optional kernel vars, expresscatalog int64 formatting, f2fs host
  tooling, debuggerd host tooling, SELinux host tooling, fs_config host tooling,
  and Rust `ring` host assembly selection.
- SELinux service label for the OpenPhone framework Binder service
  `openphone_agent`.
- Pixel 9a `openphone_tegu` full OTA build and physical boot.
- Verified physical Pixel 9a runtime for:
  - full OpenPhone product identity,
  - privileged `org.openphone.assistant` package,
  - running `org.openphone.assistant/.OpenPhoneAssistantService`, and
  - registered `openphone_agent` Binder service.
- Device support matrix and Pixel 9a bringup notes.

## Not Yet Implemented

- Hardware validation on the Pixel 9a. Full OpenPhone now boots and the
  assistant/framework service are verified, and a repeatable smoke-test report
  script exists, but Wi-Fi, cellular, camera, microphone, GPS, fingerprint,
  encryption, and reboot stability still need physical pass/fail evidence.
- Fully reproducible device-specific flashable image generation. The Pixel 9a
  build path now has first automation for extracting the known-good DTB before
  target-files/OTA-producing goals and verifying generated
  `vendor_kernel_boot.img` afterward, and first private release-signing
  workspace/signing wrapper support exists. Actually producing and validating a
  signed release OTA and clean-room reproducibility still need work.
- Typed framework parcelables for screen context, action requests, policy
  decisions, and audit events. The current Binder contract intentionally uses
  JSON strings while the service boundary is still stabilizing.
- Full screen understanding service with OCR, notifications, content-provider
  image references, framework-owned UI hierarchy, and scoped data
  minimization. Current screen observation supports foreground/visible activity
  metadata, opt-in JPEG screenshot payloads, and a first assistant-side
  accessibility UI tree for visible text and interactive elements.
- Production model transport. A development OpenAI Responses vision adapter is
  physically validated and the assistant now has a first broker/proxy transport
  option plus a reference broker with signed session-token minting and a first
  provider/model registry and device-subject registry, but a production build
  still needs stronger device attestation, retry policy, stronger rate limits,
  and stronger privacy controls.
- Vision-based action selection. The development OpenAI adapter can now run a
  bounded screenshot/action loop, but this remains a dev path that needs
  stronger physical eval evidence, production transport, and safer confirmation
  UX before it is release-grade.
- Full action execution service for notification actions, app-specific
  integrations, and richer input targeting. Current framework/assistant action
  execution supports launcher actions, navigation keys, pointer gestures,
  scroll gestures, keyboard text, clipboard text actions, confirmed share
  chooser launch, and first semantic accessibility-element targeting through
  `tap_element` / `long_press_element`, but does not yet have notification
  integrations or production-grade app-specific actions.
- Full confirmation UX and grant lifecycle for medium/high-risk capabilities.
  A basic assistant approve/deny path exists for pending actions, but there is
  no system modal, timeout, per-app grant editor, or high-friction payment/
  messaging flow.
- Full Settings-hosted per-app/per-capability grant editor. Settings now
  exposes top-level OpenPhone, task-grant, and audit pages, and it can edit
  global durable task-grant defaults. Per-app/per-capability policy remains a
  seed plus development override contract until the core agent loop is
  physically stronger.
- SystemUI background task surface. A first native `openphone_agent` Quick
  Settings tile is implemented and builds; it shows agent availability, opens
  the assistant when idle, and stops the active framework task when running.
  The assistant-owned island/cursor overlay exists for development and active
  task feedback, but a production SystemUI-owned status-bar/dynamic-island
  surface remains pending.
- Remaining SELinux policy for richer action execution and future services.
- On-device OTA client and actual signed release artifact validation.
- Kernel source publication flow.
- Vendor blob extraction and redistribution policy per device.

## Current Build Evidence

Validated commands:

```bash
./scripts/check.sh
OPENPHONE_ANDROID_DIR="$PWD/.worktree/OpenPhoneAndroid/android" ./scripts/apply-patches.sh
OPENPHONE_ANDROID_DIR="$PWD/.worktree/OpenPhoneAndroid/android" OPENPHONE_BUILD_GOAL=droid ./scripts/build.sh openphone_arm64
OPENPHONE_ANDROID_DIR="$PWD/.worktree/OpenPhoneAndroid/android" OPENPHONE_BUILD_GOAL=services.core-android_common-checkbuild ./scripts/build.sh openphone_arm64
OPENPHONE_ANDROID_DIR="$PWD/.worktree/OpenPhoneAndroid/android" OPENPHONE_BUILD_GOAL=OpenPhoneAssistant ./scripts/build.sh openphone_arm64
```

The generic `openphone_arm64` `droid` graph now completes on the prepared
macOS build host. This validates the OpenPhone overlay, product definition,
privileged assistant package, and current Darwin compatibility patch stack.
The focused `services.core-android_common-checkbuild` target validates the
hidden framework manager API, signature permissions, `SystemServiceRegistry`
registration, `system_server` service startup wiring, foreground activity
context, seed policy, durable audit logging, and the mediated `open_app` and
task-scoped input/clipboard/share action paths, including pending action
confirmation.

One broader `framework-minus-apex-android_common-checkbuild` attempt reached
the OpenPhone framework compile checkpoints but failed later in an unrelated
Darwin host-link step for Cronet protobuf (`ld64.lld` rejected GNU-style linker
flags). The narrower `services.core` check above avoids that unrelated host
tool path and validates the OpenPhone framework/service code directly.

This is not yet a phone-ready release. The generic target currently produces
build metadata under `out/target/product/generic_arm64`, but not a validated
flashable target image. An explicit `systemimage` build goal is not available
for this target in the current tree.

Pixel 9a `tegu` smoke-build evidence:

- Official Lineage 23.2 boots on the purchased Pixel 9a.
- OpenPhone smoke dynamic partitions boot when paired with the official Lineage
  `vendor_kernel_boot.img`.
- The first OpenPhone smoke OTA failed because generated
  `vendor_kernel_boot.img` had a zero-byte DTB.
- The DTB-fixed OpenPhone smoke OTA boots successfully on slot `_a`.
- The root cause and current DTB extraction fix are documented in
  [TEGU_BOOTCHAIN.md](TEGU_BOOTCHAIN.md) and
  [BRINGUP_LOG.md](BRINGUP_LOG.md).

Pixel 9a `tegu` full-product evidence:

- Built full `openphone_tegu` OTA:
  `.worktree/artifacts/tegu/openphone_tegu-bp4a-v1-dev-ota.zip`.
- First full OTA exposed full product identity but crashed in
  `system_server` because SELinux denied the `openphone_agent` service
  registration.
- Added `patches/system_sepolicy/0001-OpenPhone-label-agent-manager-service.patch`.
- Rebuilt and sideloaded:
  `.worktree/artifacts/tegu/openphone_tegu-bp4a-v1-dev-selinuxfix-ota.zip`.
- SELinux-fixed OTA SHA-256:
  `d6a6a6153af8c37fd03ddd2e4144aa51c9a79662cf936210bd0b4c9d546316f8`.
- Verified on the physical Pixel 9a:
  - `ro.product.model=OpenPhone Pixel 9a`
  - `ro.openphone.version=0.1.0-dev`
  - `ro.openphone.smoke_build=` empty
  - `sys.boot_completed=1`
  - `pm list packages` includes `org.openphone.assistant`
  - `service list` includes
    `openphone_agent: [android.openphone.IOpenPhoneAgentService]`
  - `service check openphone_agent` reports `found`
  - `dumpsys activity services` shows
    `org.openphone.assistant/.OpenPhoneAssistantService`
  - `monkey -p org.openphone.assistant 1` displays
    `org.openphone.assistant/.MainActivity`

Post-plan implementation evidence on the EC2 Linux build host:

- `./scripts/check.sh` passes after adding the assistant task console,
  notification trigger, Quick Settings tile, model adapter boundary, cursor
  overlay, and framework task-screen/pointer APIs.
- `OPENPHONE_RELEASE=bp4a OPENPHONE_BUILD_GOAL=OpenPhoneAssistant ./scripts/build.sh openphone_tegu`
  generates:
  `out/target/product/tegu/system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk`.
- `OPENPHONE_RELEASE=bp4a OPENPHONE_BUILD_GOAL=MODULES-IN-frameworks-base-services-core ./scripts/build.sh openphone_tegu`
  validates the new framework manager/service API additions, including the
  opt-in screenshot payload path.
- Built v44 development OTA after adding retry/failure handling, exposing
  bounded `watch_screen` as a model tool, adding the assistant-owned
  confirmation review surface, and improving the assistant-owned cursor overlay
  with action labels, tap ripples, long-press emphasis, swipe trails, and a
  typing indicator:
  `.worktree/artifacts/tegu/openphone_tegu-action-overlay-v44-ota.zip`.
- v44 OTA SHA-256:
  `b439308f518e2fa30ffc7d33ed923b4961b323ca02e9d6911f75ca62874781b5`.
- v44 assistant APK metadata from the EC2 output tree:
  `versionCode=44`, `versionName=0.1.8-dev`.
- v44 assistant APK SHA-256:
  `d248cfd7d439d0c0c7cbdc5c14c00d80eca3aec7cddb1cbd5c715c4e00330e54`.
- v44 physical Pixel 9a sideload/runtime validation is still pending because
  the current post-wipe device state lists over ADB but closes `adb shell`
  immediately.
- Built v45 development OTA after adding assistant-owned redacted framework
  audit evidence export to `Downloads/OpenPhone`:
  `.worktree/artifacts/tegu/openphone_tegu-audit-export-v45-ota.zip`.
- v45 OTA SHA-256:
  `9602bb786d81bf412fe2115f62448cc86e56061f7f491b26db3a666e9c6a4111`.
- v45 assistant APK metadata from the EC2 output tree:
  `versionCode=45`, `versionName=0.1.9-dev`.
- v45 assistant APK SHA-256:
  `89e5fee729cff2fab1b6d87598d3fc942e2d90fbb8c2683e2fee7dec9fc7a5ee`.
- v45 OTA zip integrity check passes locally with `unzip -tq`.
- v45 physical Pixel 9a sideload/runtime validation is still pending because
  the current post-wipe device state lists over ADB but closes `adb shell`
  immediately.
- Built v46 development OTA after adding the deterministic risky-action
  pre-execution guardrail to the OpenAI development loop:
  `.worktree/artifacts/tegu/openphone_tegu-risk-guardrail-v46-ota.zip`.
- v46 OTA SHA-256:
  `15fefaac1139867e733edd53f4215a9d2ac9bd1c8d19234dbd53e9be094c1421`.
- v46 assistant APK metadata from the EC2 output tree:
  `versionCode=46`, `versionName=0.1.10-dev`.
- v46 assistant APK SHA-256:
  `ca35d8020d7ddc11b0d6c06f288472691d7ef1630f004c299bf5cd8a72d048de`.
- v46 OTA zip integrity check passes locally with `unzip -tq`.
- v46 physical Pixel 9a sideload/runtime validation is still pending because
  the current post-wipe device state lists over ADB but closes `adb shell`
  immediately.
- Built v47 development OTA after adding assistant-owned per-task grant
  controls for input/navigation, screenshot capture, clipboard, sharing, and
  web links, plus local pre-framework denial when a model tool exceeds the
  selected task grants:
  `.worktree/artifacts/tegu/openphone_tegu-task-grants-v47-ota.zip`.
- v47 OTA SHA-256:
  `798124756751aa5f164393fcdabd1c02e97870382269f0729b6f89e6ee823d47`.
- v47 assistant APK metadata from the EC2 output tree:
  `versionCode=47`, `versionName=0.1.11-dev`.
- v47 assistant APK SHA-256:
  `fad48c8d6ef484cccbe377bd3cfbf1e72fa8e490684117ee17b0bb2c3afb6122`.
- v47 OTA zip integrity check passes locally with `unzip -tq`.
- v47 physical Pixel 9a sideload/runtime validation is still pending because
  the current device state lists over ADB but closes `adb shell` immediately.
- Added `patches/packages_apps_Settings/0001-OpenPhone-add-About-phone-version-surface.patch`
  so Settings/About phone exposes OpenPhone identity in normal UI:
  - `OpenPhone version`, backed by `ro.openphone.version`
  - `OpenPhone support`, linking to the public project repository
- Focused `Settings` module build passed on the EC2 Linux Android tree and
  generated:
  `out/target/product/tegu/system_ext/priv-app/Settings/Settings.apk`.
- Settings APK SHA-256:
  `e7ab8fb153177e4e41710a3165b9e83b8a04efb01cd09689862c88ef57516a48`.
- Built v48 development OTA after adding the Settings/About OpenPhone identity
  surface:
  `.worktree/artifacts/tegu/openphone_tegu-settings-about-v48-ota.zip`.
- v48 OTA SHA-256:
  `cdb717dc27bca5786c85cd7dd3cf9a3e43092fb6fa1077ee64df84958b289cbf`.
- v48 assistant APK metadata from the EC2 output tree remains:
  `versionCode=47`, `versionName=0.1.11-dev`.
- v48 assistant APK SHA-256:
  `fad48c8d6ef484cccbe377bd3cfbf1e72fa8e490684117ee17b0bb2c3afb6122`.
- v48 OTA zip integrity check passes locally with `unzip -tq`.
- v48 physical Pixel 9a sideload/runtime validation is pending; specifically,
  the Settings/About row still needs to be verified on-device.
- Added `patches/packages_apps_Settings/0002-OpenPhone-add-settings-dashboard.patch`
  so Settings exposes OpenPhone as a top-level page with rows for:
  - Assistant
  - Task grants
  - Audit evidence
  - OpenPhone support
- Focused `Settings` module build passed on the EC2 Linux Android tree after
  adding the OpenPhone dashboard patch.
- Built v49 development OTA after adding the Settings homepage OpenPhone
  dashboard:
  `.worktree/artifacts/tegu/openphone_tegu-settings-dashboard-v49-ota.zip`.
- v49 OTA SHA-256:
  `6fcd646f90f83d954924b86e4ab421e41ce1ad57cbbac9835ed0be901e438f83`.
- v49 Settings APK SHA-256:
  `376e16651b2e07e4e3b69f086a40bc0905fd77baec5f48fe47a406bfe0cac958`.
- v49 assistant APK metadata from the EC2 output tree remains:
  `versionCode=47`, `versionName=0.1.11-dev`.
- v49 assistant APK SHA-256:
  `fad48c8d6ef484cccbe377bd3cfbf1e72fa8e490684117ee17b0bb2c3afb6122`.
- v49 OTA zip integrity check passes locally with `unzip -tq`.
- v49 physical Pixel 9a sideload/runtime validation is pending; ADB still lists
  the device but closes shell sessions with `error: closed`.
- Added `patches/packages_apps_Settings/0003-OpenPhone-add-Settings-hosted-audit-and-grant-pages.patch`
  so Settings hosts dedicated OpenPhone subpages:
  - Task grants explains active-task-scoped input/navigation, screenshot,
    clipboard, sharing, and link grants.
  - Audit evidence reads `OpenPhoneAgentManager.getServiceStatus()` and
    `getAuditLog(10)` directly from Settings, with assistant export retained as
    a handoff for writing evidence files.
- Focused `Settings` module build passed on the EC2 Linux Android tree after
  adding the Settings-hosted audit/grant pages.
- Built v50 development OTA after adding the Settings-hosted audit/grant pages:
  `.worktree/artifacts/tegu/openphone_tegu-settings-audit-grants-v50-ota.zip`.
- v50 OTA SHA-256:
  `29302a533e25a97dbfc856c37d13b1fb30b8125a53af12126bd929fb1bdb13f8`.
- v50 Settings APK SHA-256:
  `a10f539872d5ed9afda1519a8c2675c4860c6ab7b42d2b1e60dd6639dcddfd75`.
- v50 assistant APK metadata from the EC2 output tree remains:
  `versionCode=47`, `versionName=0.1.11-dev`.
- v50 assistant APK SHA-256:
  `fad48c8d6ef484cccbe377bd3cfbf1e72fa8e490684117ee17b0bb2c3afb6122`.
- v50 OTA zip integrity check passes locally with `unzip -tq`.
- v50 physical Pixel 9a sideload/runtime validation is pending; ADB still lists
  the device but closes shell sessions with `error: closed`.
- Updated `patches/system_sepolicy/0001-OpenPhone-label-agent-manager-service.patch`
  after the full Pixel 9a build exposed release hygiene gates. The OpenPhone
  service label is now private platform policy, avoiding public SELinux API
  freeze/compat failures, and `openphone_agent` is listed in
  `service_fuzzer_bindings.go` with `EXCEPTION_NO_FUZZER` until the Java
  service has a dedicated fuzz target.
- Built v51 target-files and OTA on the EC2 Linux host after adding model
  disclosure metadata and the sepolicy hygiene fix:
  `.worktree/artifacts/tegu/openphone_tegu-model-disclosure-sepolicy-v51-ota.zip`.
- v51 OTA SHA-256:
  `b93db84907523b8e37816abab0a315b1f62b82321755612e82d057fd8f80e866`.
- v51 assistant APK metadata from the EC2 output tree:
  `versionCode=48`, `versionName=0.1.12-dev`.
- v51 assistant APK SHA-256:
  `bf3120926a087fb8c9e29acf910b7027e446c57737c678e79a15581549fae681`.
- v51 Settings APK SHA-256 remains:
  `a10f539872d5ed9afda1519a8c2675c4860c6ab7b42d2b1e60dd6639dcddfd75`.
- v51 OTA zip integrity check passes locally with `unzip -tq`.
- v51 physical Pixel 9a sideload/runtime validation is pending; ADB still lists
  the device but closes shell sessions with `error: closed`.
- v52 SystemUI agent tile build evidence:
  - EC2 focused `SystemUI` build passed for `openphone_tegu-bp4a-userdebug`.
  - EC2 full `otapackage` build passed for `openphone_tegu-bp4a-userdebug`.
  - Local OTA staged at
    `.worktree/artifacts/tegu/openphone_tegu-systemui-agent-tile-v52-ota.zip`.
  - OTA SHA-256:
    `97a08c5bceb062f53769988b432d64aebd51cf5e9217c9eb9db55d076b38f2b2`.
  - `unzip -tq` passed for the local OTA copy.
  - Added `patches/frameworks_base/0011-OpenPhone-add-SystemUI-agent-QS-tile.patch`.
  - Physical sideload/runtime validation is pending. Earlier post-wipe checks
    reached an `adb shell` `error: closed` state; the latest local retry does
    not enumerate the Pixel over USB at all.
- v53 assistant broker transport evidence:
  - Added assistant-side `ModelEndpointConfig` and broker/proxy mode for
    `/v1/responses` and `/v1/audio/transcriptions` request shapes.
  - Assistant direct OpenAI mode remains available for development.
  - EC2 focused `OpenPhoneAssistant` build passed for
    `openphone_tegu-bp4a-userdebug`.
  - EC2 full `otapackage` build passed for `openphone_tegu-bp4a-userdebug`.
  - Local OTA staged at
    `.worktree/artifacts/tegu/openphone_tegu-broker-systemui-v53-ota.zip`.
  - OTA SHA-256:
    `f72000529942ab728512a8c8a49a7e42ed311c0db861c79237abdbd5bab25a9a`.
  - `unzip -tq` passed for both the EC2 OTA and local OTA copy.
  - EC2 APK metadata:
    `versionCode=49`, `versionName=0.1.13-dev`.
  - EC2 APK SHA-256:
    `e5dd7d5cc26c052aa792670142f3c1e24bedc5ccf5eeedd3413f005aee2d5020`.
  - Added `services/model-broker/openphone_model_broker.py`; local repo checks
    compile it with `python3 -m py_compile`.
  - Local broker smoke test passed for `GET /healthz`, unauthorized
    `/v1/responses` rejection, signed-token minting/authentication, and
    `/v1/audio/transcriptions` content-type validation.
  - Local broker hardening smoke test passed for malformed JSON rejection,
    disallowed response-model rejection, and JSONL audit event writing without
    request bodies.
  - Added `scripts/smoke-test-model-broker.sh` and wired it into
    `scripts/check.sh` so the broker smoke coverage runs as part of normal repo
    validation.
  - Added admin-authenticated `POST /v1/session_tokens` to the broker so
    hosted development services can mint signed, expiring device/session tokens
    through HTTP instead of using only the CLI helper. The smoke test now
    verifies unauthorized issuer rejection and successful issuer minting before
    using the minted token against model endpoints.
  - Added `services/model-broker/providers.example.json` and registry loading
    through `OPENPHONE_BROKER_PROVIDER_REGISTRY`; the broker smoke test now
    uses the registry-backed model allowlist before provider forwarding.
  - Added `services/model-broker/devices.example.json` and registry loading
    through `OPENPHONE_BROKER_DEVICE_REGISTRY`; when configured, the token
    issuer rejects unknown subjects before minting session tokens. Registry
    entries can also reference a per-subject attestation secret env var; when
    present, `/v1/session_tokens` requires a fresh HMAC proof from that
    subject before minting. The broker smoke test now verifies allowed,
    rejected, missing-attestation, and invalid-attestation paths.
  - Added first-pass deployment artifacts under
    `services/model-broker/deploy/`: hardened systemd unit, environment
    template, and deployment README for running the broker as a restricted
    Linux service behind TLS.
  - Added `services/model-broker/deploy/nginx-openphone-model-broker.conf`, an
    nginx HTTPS reverse-proxy template that redirects HTTP, exposes `/healthz`
    and `/v1/`, sets no-store/security headers, aligns body-size limits, and
    proxies to the localhost-bound broker.
  - Added `scripts/rotate-model-broker-secrets.sh`, which can print fresh
    broker secrets or atomically rotate `OPENPHONE_BROKER_TOKEN_SECRET` and
    `OPENPHONE_BROKER_ADMIN_TOKENS` in a deployed env file while preserving
    provider keys. The helper can also rotate `OPENAI_API_KEY` while
    preserving broker token/admin secrets. `scripts/check.sh` validates both
    modes against temporary env files.
  - Added `scripts/setup-model-broker-tls.sh`, which renders the nginx broker
    TLS template for a domain/email and prints or applies the certbot/nginx
    commands for certificate issuance and renewal dry-run validation.
    `scripts/check.sh` validates the render path.
  - Added `scripts/prepare-release-signing.sh`, which creates a private
    release-signing workspace outside the repository with a `.gitignore`,
    `README.md`, and Android releasetools key map. `scripts/check.sh` validates
    the helper against a temporary directory.
  - Added `scripts/sign-release-ota.sh`, a private-build-environment wrapper
    around Android `sign_target_files_apks` and `ota_from_target_files`.
    It refuses in-repo key directories, verifies required key material before
    real signing, writes a signed OTA checksum, and has a `--dry-run` mode
    covered by `scripts/check.sh`.
  - Added `docs/contracts/ota-feed.schema.json`,
    `scripts/generate-ota-feed.sh`, and `scripts/validate-ota-feed.sh` for the
    first server-side OTA feed contract. `scripts/check.sh` validates feed
    generation and local artifact matching against a temporary OTA file.
  - Added first assistant-side sensitive-screen handling for the accessibility
    UI-tree path. Password fields are redacted in model-visible UI-tree
    context; password/payment/account-like risk flags cause screenshot capture
    tools to return `screen.blocked` instead of a base64 screenshot.
  - Added repo validation that `PolicyEngine` stays in sync with
    `openphone_capabilities.json`, and fixed the missing `share.content`
    high-risk mapping in the assistant fallback policy.
  - Added `overlay/vendor/openphone/config/openphone_model_tools.json` and
    `docs/contracts/model-tool.schema.json`. Repo checks now verify every
    registered model tool maps to a known capability and is covered by
    `FrameworkToolExecutor` plus the OpenAI adapter's allowed/terminal tool
    handling. Fixed stale OpenAI adapter capability IDs for share and text
    input.
  - Added `patches/frameworks_base/0012-OpenPhone-add-mediated-open-url-action.patch`
    so model web-link launches are mediated by `system_server`, require the
    `network.use` capability path, and write framework audit events. The
    assistant tool executor no longer starts web intents directly.
  - Extended repo checks so assistant-emitted framework action types must be
    listed in `docs/contracts/action-request.schema.json` and present in the
    framework patch stack with expected capability mappings.
  - Enforced `requires_reason` from the model-tool contract in
    `FrameworkToolExecutor`; local heuristic and OpenAI development paths now
    send model-visible reasons for `get_screen`, app/web launches, confirmation
    requests, and other reason-required tools.
  - Expanded `docs/contracts/audit-event.schema.json` to cover framework screen
    capture/watch and task stop events, and added CI validation that the schema
    stays aligned with framework `recordAudit(...)` event names.
  - Added `docs/contracts/trajectory-event.schema.json` and schema markers for
    assistant trajectory JSONL events. CI validates that trajectory recorder
    event names stay aligned with the contract.
  - Added `scripts/validate-trajectory-export.sh` for exported assistant trace
    evidence. It checks trajectory JSONL schema markers, event order, required
    task/result events, screenshot file references, and obvious secret/raw
    base64 leakage. `scripts/check.sh` exercises both directory and zip inputs.
  - Added `docs/contracts/audit-evidence.schema.json` and
    `scripts/validate-audit-evidence-export.sh` for framework audit evidence
    exports. The validator checks the schema marker, service status, audit event
    names, redaction, and obvious secret/raw-base64 leakage.
  - Expanded `docs/contracts/screen-context.schema.json` to cover the
    assistant accessibility UI-tree snapshot shape, and added CI checks for key
    emitted root/window/element fields plus sensitive-screen risk flags.
  - Physical sideload/runtime validation is pending. Earlier post-wipe checks
    reached an `adb shell` `error: closed` state; the latest local retry does
    not enumerate the Pixel over USB at all.
- v54 persisted assistant task-grant defaults evidence:
  - Added assistant app-private persistence for input/navigation, screenshot,
    clipboard, share-sheet, and web-link grant defaults.
  - Assistant task requests now include `grant_defaults_source` so trajectory
    and framework evidence can distinguish persisted defaults from one-off UI
    state.
  - EC2 focused `OpenPhoneAssistant` build passed for
    `openphone_tegu-bp4a-userdebug`.
  - EC2 full `otapackage` build passed for `openphone_tegu-bp4a-userdebug`.
  - EC2 APK metadata:
    `versionCode=50`, `versionName=0.1.14-dev`.
  - EC2 APK SHA-256:
    `5205949d1c6060fdecb25c88bd28cff4f02aa6daf3b86b5a87a3c5c0981fb191`.
  - Local OTA staged at
    `.worktree/artifacts/tegu/openphone_tegu-persisted-grants-v54-ota.zip`.
  - OTA SHA-256:
    `23dcd90f8ab2d532fe5732311a6828a1a6165f64f173881d73ef65255f226132`.
  - `unzip -tq` passed for both the EC2 OTA and local OTA copy.
  - Local APK staged at
    `.worktree/artifacts/tegu/OpenPhoneAssistant-persisted-grants-v54.apk`.
  - Physical install/runtime validation is pending while ADB service channels
    still close with `error: closed`.
- v55 Settings-owned durable task-grant defaults evidence:
  - Added `patches/packages_apps_Settings/0004-OpenPhone-add-durable-task-grant-defaults.patch`.
  - Settings task grants are now editable switches backed by `Settings.Secure`
    keys:
    `openphone_task_grant_input`,
    `openphone_task_grant_screenshot`,
    `openphone_task_grant_clipboard`,
    `openphone_task_grant_share`, and
    `openphone_task_grant_network`.
  - The assistant reads the same secure keys, keeps app-private fallback for
    migration/development, and marks new task requests with
    `grant_defaults_source=settings_secure`.
  - EC2 focused `Settings OpenPhoneAssistant` build passed for
    `openphone_tegu-bp4a-userdebug`.
  - EC2 APK metadata:
    `versionCode=51`, `versionName=0.1.15-dev`.
  - EC2 assistant APK SHA-256:
    `311d6bab821573b1654ddb73bf33278937d483efac11f2ee0c8e474688fa9027`.
  - EC2 Settings APK SHA-256:
    `d4e296a5af8742c211ad54c5ac025bd8e5326d76af9f10ae03ffb3ccbe7d0c4e`.
  - EC2 full `otapackage` build passed for `openphone_tegu-bp4a-userdebug`.
  - Local OTA staged at
    `.worktree/artifacts/tegu/openphone_tegu-settings-grants-v55-ota.zip`.
  - OTA SHA-256:
    `c2f08cad2b5247eb88982c4799901fb5f70d451ffde8cb3fde0e0b463f95a443`.
  - `unzip -tq` passed for the local OTA copy.
  - Current clean v0.0.1 preview staging was regenerated from this OTA and
    passed `scripts/validate-release-artifacts.sh`.
  - Physical install/runtime validation is pending. After onboarding and USB
    debugging were re-enabled locally, the device previously reported as
    `device` while `adb shell` returned `error: closed`; the latest local retry
    no longer enumerates the Pixel over USB, so cable/port/device USB state must
    be recovered first.
- v56 preview OTA client implementation evidence:
  - Added `OtaUpdateClient` to the privileged assistant app.
  - The assistant Advanced panel now has a Preview OTA Updates surface with OTA
    feed URL input, feed check, and verified download actions.
  - Feed checks require `schema_version=1`, target the current `Build.DEVICE`,
    and parse the first update from `docs/contracts/ota-feed.schema.json`.
  - OTA downloads write to `Downloads/OpenPhone` through `MediaStore`, remain
    pending until complete, and are deleted if the downloaded size or SHA-256
    does not match the feed.
  - Installation is intentionally still manual for the preview; recovery
    sideload or the host flashing flow remains the supported installation path.
  - Assistant package metadata is bumped to `versionCode=52`,
    `versionName=0.1.16-dev`.
  - Physical install/runtime validation is pending until host USB/ADB
    enumeration is recovered.

Post-plan physical Pixel 9a evidence:

- Built fresh target-files and OTA on the EC2 Linux host:
  `.worktree/artifacts/tegu/openphone_tegu-bp4a-assistant-v1-ota.zip`.
- OTA SHA-256:
  `25983a9f3099e9493c94f4d78b2eb81140ad99688493e8a2e3a8dca4cf2096a5`.
- Validated generated `vendor_kernel_boot.img` DTB before sideload:
  `dtb size: 1546258`.
- Sideloaded the OTA to the physical Pixel 9a and booted successfully.
- Verified over ADB after boot:
  - `ro.openphone.version=0.1.0-dev`
  - `ro.lineage.version=23.2-20260531-UNOFFICIAL-tegu`
  - `service check openphone_agent` reports `found`
  - `org.openphone.assistant/.OpenPhoneQuickSettingsTileService` is registered
  - assistant has new `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `INTERNET`,
    and `RECORD_AUDIO` permissions
  - persistent OpenPhone notification posts with `Stop` and `Open` actions
- Verified assistant UI task flow on-device:
  - `Start` creates a framework task with `screen.read.visible` and
    `tasks.observe`
  - `Screen` calls the new `getScreen(...)` framework API and returns
    `screen.captured.metadata_only` with foreground app/activity metadata
  - `Run Agent` with goal `settings` executes the local heuristic model loop
    and opens `com.android.settings/.Settings`

Additional Pixel 9a screenshot and OpenAI evidence:

- Built and sideloaded screenshot-fix OTA:
  `.worktree/artifacts/tegu/openphone_tegu-openai-vision-test-screenshotfix2-ota.zip`.
- OTA SHA-256:
  `65dedb7fe4abad5ca9d4edec4a4e24c54336cb38a3318f84f4eec7e68c2bee40`.
- Verified `getScreen(..., {"include_screenshot": true})` on the physical
  Pixel 9a from the assistant `Shot` button.
- The returned screen payload included a downscaled JPEG screenshot:
  `width=228`, `height=512`, `quality=65`, and redacted UI display
  `<base64 chars=24208>`.
- Fixed screenshot capture permission failure by applying
  `patches/frameworks_base/0010-OpenPhone-capture-screenshots-as-system-server.patch`.
- Built and sideloaded background-thread OpenAI OTA:
  `.worktree/artifacts/tegu/openphone_tegu-openai-bgthread-ota.zip`.
- OTA SHA-256:
  `1792d9bcf904146d3de17cf10ecb66e362ca100b19d54ca56b8e1c4cead3a32c`.
- Fixed `NetworkOnMainThreadException` by running model calls on an assistant
  background thread.
- Verified OpenAI Responses vision path on the physical Pixel 9a over Wi-Fi:
  - `status=model_response`
  - `provider=openai-responses-vision-dev`
  - `model=gpt-4.1-mini`
  - OpenAI response id:
    `resp_072056aadeb3322d006a1c286f5eb8819fba6ebcf3cd3fed20`
  - Model output described the visible OpenPhone Assistant screen from the
    screenshot and suggested a next safe action.
- Built and sideloaded agent-loop OTA:
  `.worktree/artifacts/tegu/openphone_tegu-agent-loop-ota.zip`.
- OTA SHA-256:
  `4839a81c151f2bbff1c3218389c69f2e405196e8db201b0e834e684f98b82016`.
- Verified closed-loop model/tool execution on the physical Pixel 9a:
  - goal: `Open Settings and finish when Settings is visible`
  - model step 1 selected `open_app`
  - framework executed `apps.launch` for `com.android.settings`
  - model step 2 saw `foreground_app=com.android.settings`
  - model step 2 selected `finish_task`
  - final status: `task.finished`

Pixel 9a build reproducibility evidence:

- Added `scripts/build.sh` automation for `openphone_tegu` and
  `openphone_tegu_smoke` target-files/OTA-producing goals:
  - prepares `device/google/tegu-kernels/6.1/tegu.dtb` from the upstream
    prebuilt `vendor_kernel_boot.img`
  - verifies generated `vendor_kernel_boot.img` contains a non-empty DTB with
    SHA-256
    `f1aed2bc4c07d3cb1e610f5227a566f22e995dfe05341ca6bf14805be6928688`
- Added manual helpers:
  - `scripts/prepare-tegu-dtb.sh`
  - `scripts/verify-tegu-bootchain.sh`
- Validated on the EC2 Linux Android tree:
  - `./scripts/check.sh`
  - `OPENPHONE_ANDROID_DIR=$PWD/.worktree/android ./scripts/prepare-tegu-dtb.sh`
  - `OPENPHONE_ANDROID_DIR=$PWD/.worktree/android ./scripts/verify-tegu-bootchain.sh openphone_tegu`
  - `OPENPHONE_ANDROID_DIR=$PWD/.worktree/android OPENPHONE_RELEASE=bp4a OPENPHONE_BUILD_GOAL=target-files-package ./scripts/build.sh openphone_tegu`

Pixel 9a assistant accessibility/UI-tree OTA evidence:

- Built full `openphone_tegu` OTA on the EC2 Linux Android tree after adding
  the assistant accessibility service, UI-tree context path, trajectory
  improvements, and package manifest version bump.
- Copied local artifact:
  `.worktree/artifacts/tegu/openphone_tegu-agent-ui-tree-v37-ota.zip`.
- OTA SHA-256:
  `db4867c90acde0294ce81ce8e890df39219184a1ab80ee9825171d95c880dbd2`.
- Sideload completed successfully with `Total xfer: 1.00x`.
- Device booted afterward with:
  - `ro.product.model=OpenPhone Pixel 9a`
  - `ro.openphone.version=0.1.0-dev`
  - `ro.lineage.version=23.2-20260601-UNOFFICIAL-tegu`
  - `service check openphone_agent` reporting `found`
- Pulled on-device APK from
  `/system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk`.
- On-device APK SHA-256 matched the EC2 build output:
  `4c53c3aae5aa1e7793b6f2c5a311d7067fe25a1b0e5acc62e657f505123c5881`.
- EC2 `aapt2 dump badging` verified the built APK manifest contains:
  - `versionCode='37'`
  - `versionName='0.1.1-dev'`
  - `.OpenPhoneAccessibilityService`
  - `android.permission.BIND_ACCESSIBILITY_SERVICE`
- Post-wipe PackageManager reparse is verified on the Pixel 9a:
  - `cmd package list packages --show-versioncode org.openphone.assistant`
    reports `versionCode:37`
  - `dumpsys package org.openphone.assistant` lists
    `.OpenPhoneAccessibilityService`
  - `service check openphone_agent` reports `found`
- A command-driven recovery wipe temporarily left ADB in a half-connected
  fresh-onboarding state. After onboarding and USB debugging authorization,
  shell/logcat/install channels recovered.
- Added `scripts/verify-tegu-device.sh` to make Pixel 9a post-flash validation
  repeatable. The script now passes on the Pixel 9a v37 OTA with
  `enabled_accessibility_services=org.openphone.assistant/org.openphone.assistant.OpenPhoneAccessibilityService`
  and `accessibility_enabled=1`.
- Eval 2 physical result:
  - local adapter goal: `settings`
  - task opened Settings through framework-mediated `open_app`
  - foreground UI after the run was `com.android.settings`
- Eval 1 physical result:
  - local adapter observed the screen and produced a trajectory path
  - current flashed v37 build displayed it as "Needs review" because the local
    adapter returned a transcript, not JSON
  - source now fixes this by returning structured `task.finished` JSON; module
    build passed, but a new OTA/install is needed before retesting the UI result

Pixel 9a assistant cancellation/local-JSON OTA evidence:

- Built and sideloaded full `openphone_tegu` OTA:
  `.worktree/artifacts/tegu/openphone_tegu-agent-cancel-v38-ota.zip`.
- OTA SHA-256:
  `b4643a7d68620818b9dd59ada577d2f4392f4c2a21f74a5668b6608fdc2a2f02`.
- The build host APK reported:
  - `versionCode='38'`
  - `versionName='0.1.2-dev'`
- Sideload completed successfully with `Total xfer: 1.00x`.
- The phone booted and `service check openphone_agent` still reported `found`.
- The live `/system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk`
  bytes matched the EC2 build output, but PackageManager still reported the
  previous `versionCode=37` / `versionName=0.1.1-dev`.

Pixel 9a assistant package-cache-buster OTA evidence:

- Built and sideloaded full `openphone_tegu` OTA:
  `.worktree/artifacts/tegu/openphone_tegu-agent-cachebuster-v39-ota.zip`.
- OTA SHA-256:
  `8ea2451c2b1a6ce0a98884f7aac1e57fb180088f564131e1e20bc855f50ad346`.
- Added `res/raw/package_parse_marker.txt` so the packaged APK size changes
  across the development OTA.
- The build host APK reported:
  - `versionCode='39'`
  - `versionName='0.1.3-dev'`
  - size `103061`
  - SHA-256
    `57b4781ff3265425c06651942fbc9cdd11b26b13cddf393a8c75f08f8a9899b0`
- Sideload completed successfully with `Total xfer: 1.00x`.
- The live phone APK at
  `/system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk` matched the
  v39 SHA-256 above, proving the OTA updated the system partition.
- PackageManager still reported `versionCode=37` / `versionName=0.1.1-dev`
  after the v39 OTA, proving the remaining issue is persisted package metadata
  rather than stale system partition bytes.
- `scripts/verify-tegu-device.sh` now derives the expected assistant package
  version from the repo manifest and fails if PackageManager reports stale
  metadata.
- A command-driven recovery wipe left the phone in a state where
  `adb devices -l` and `adb get-state` report `device`, but `adb shell`,
  `adb exec-out`, and `adb logcat` do not provide usable channels. This matches
  the documented fresh-onboarding/authorization state and requires physical
  onboarding plus USB debugging reauthorization before the next verification.

Pixel 9a assistant trajectory-export OTA evidence:

- Built full `openphone_tegu` OTA on the EC2 Linux Android tree after adding
  the assistant trajectory export path and bumping the assistant package to
  `0.1.4-dev` / `40`.
- Copied local artifact:
  `.worktree/artifacts/tegu/openphone_tegu-agent-export-v40-ota.zip`.
- OTA SHA-256:
  `50b175d95ce57139824c7c7bc896e8391c8543ea979891f12ae6c99eb9efa50e`.
- EC2 `aapt2 dump badging` verified the built APK manifest contains:
  - `versionCode='40'`
  - `versionName='0.1.4-dev'`
- EC2 APK SHA-256:
  `f9dc7cd063d8321457ab1a55e9fc6d9205189c5c3284d36ed2f0003b30ab1c7f`.
- EC2 APK size: `107157`.
- Build script verified generated Pixel 9a `vendor_kernel_boot.img` contains
  the known-good DTB.
- This historical OTA was built but not physically sideloaded at the time
  because the phone was in the post-wipe ADB shell-closed state. The phone later
  recovered ADB shell/logcat and now supports privileged assistant APK
  fast-iteration for assistant/model-loop changes.

## Next Engineering Step

Move from boot/runtime verification to capability validation on the physical
Pixel 9a:

1. Use privileged assistant APK push for assistant UI, model-loop, prompt,
   policy, and trajectory changes. Build/flash a full OTA only for framework,
   sepolicy, Settings, SystemUI, boot-chain, or first-install changes.
2. Run the core Agent v1 eval set from `docs/TESTING.md`: observe current
   screen, open Settings, browser search, text-field drafting without
   submission, Back/Home navigation, and risky-action confirmation. App
   installation is deferred until OpenPhone has a proper app-store strategy.
3. Re-run `./scripts/smoke-test-tegu-hardware.sh`; the latest automated
   baseline completed with Wi-Fi, Bluetooth, camera service, sensors, battery,
   thermal, framework service, shell, and logcat visible, while SIM/calls/SMS,
   audio playback/capture, camera capture, fingerprint, reboot, and factory
   reset remain manual-required sections.
4. Use the assistant's Export Audit control to collect framework audit evidence
   without ADB root.
6. Inspect trajectory files and framework audit events for screenshot payloads,
   UI-tree context, model tool calls, policy decisions, and action results.
7. Continue from local evals to cloud model evals with a production-safe key
   flow or explicit development broker.
