# Changelog

All notable OpenPhone-owned changes should be documented here.

OpenPhone uses semantic versioning for public project releases. Early releases
below `1.0.0` are developer previews and may change architecture, APIs, build
flow, and device support.

## [Unreleased]

### Added

- User-facing OpenPhone Assistant control surface.
- Active-agent status indication and assistant-owned cursor overlay.
- Development voice capture and OpenAI transcription path.
- Assistant-side trajectory logging for agent runs, including screenshot file
  extraction from framework screenshot payloads.
- Assistant-side accessibility UI tree capture merged into model screen
  context requests.
- Initial eval task definitions for the CUA-informed phone agent loop.
- Planning cleanup for the CUA-informed agent work and public GitHub project
  setup.
- Initial GitHub CI and release documentation.
- Pixel 9a DTB preparation and generated `vendor_kernel_boot.img`
  verification for `openphone_tegu` and `openphone_tegu_smoke` target-files/OTA
  builds.
- OpenPhone Assistant package version bumped to `0.1.1-dev` / `37` so
  system-package reparsing picks up newly declared privileged components during
  development OTAs.
- OpenPhone Assistant package version bumped to `0.1.2-dev` / `38` for the
  cancellation, structured local-result, and accessibility re-enable OTA.
- OpenPhone Assistant package version bumped to `0.1.3-dev` / `39` with a
  packaged parse marker so development OTAs force PackageManager to reparse the
  system APK when deterministic timestamps and similar APK sizes would otherwise
  keep stale metadata.
- OpenPhone Assistant package version bumped to `0.1.4-dev` / `40` for the
  trajectory export build.
- OpenPhone Assistant package version bumped to `0.1.12-dev` / `48` for the
  model disclosure and trajectory metadata build.
- Settings/About phone OpenPhone version and support rows.
- Settings homepage OpenPhone dashboard with assistant, task-grant,
  audit-evidence, and support entry points.
- Settings-hosted OpenPhone task-grant and audit status pages.
- SystemUI-owned OpenPhone Quick Settings tile that exposes agent status,
  launches the assistant when idle, and stops the active task when running.
- OpenPhone Assistant broker/proxy transport option for cloud model calls,
  allowing development builds to use an OpenPhone-controlled endpoint with a
  session token instead of putting provider API keys on the phone.
- Dependency-free reference model broker service with bearer-token auth,
  request size limits, in-memory request-count and byte-volume rate limiting,
  no request-body logging, and OpenAI Responses/transcription proxy endpoints.
- Signed expiring development-token minting for the model broker, so broker
  sessions do not have to rely only on static shared tokens.
- Admin-authenticated model broker `/v1/session_tokens` endpoint for minting
  signed expiring session tokens over HTTP.
- JSON provider/model registry support for the model broker, with the existing
  environment model allowlist retained as a local override.
- JSON device-subject registry support for the model broker token issuer, so
  hosted development brokers can reject unknown token subjects.
- Optional per-subject development HMAC proof for model broker token issuance,
  so registered device subjects can be required to prove possession of a
  device secret before receiving a signed session token.
- First-pass model broker deployment artifacts: hardened systemd unit,
  secret-free environment template, and Linux deployment notes.
- nginx TLS reverse-proxy template for hosted development model broker
  deployments.
- Model broker secret rotation helper for token-signing and admin-token
  rotation without modifying provider keys.
- Provider-key rotation mode for the model broker secret rotation helper.
- certbot/nginx setup helper for model broker TLS configuration and renewal
  validation.
- Release-signing preparation helper that creates a private key workspace and
  Android releasetools key map outside the repository.
- Release OTA signing wrapper around Android releasetools with in-repo key
  refusal and dry-run validation.
- OTA feed schema, generator, and validator for future on-device updater
  clients.
- First-pass sensitive-screen handling for the assistant-side accessibility
  UI tree: password-field labels are redacted, and screenshot tools are blocked
  on password/payment/account-like screens.
- Assistant fallback policy now covers `share.content`, and repo checks verify
  that `PolicyEngine` risk classes stay aligned with
  `openphone_capabilities.json`.
- Added a model-tool registry and schema. Repo checks now verify tool-to-
  capability mappings, framework executor coverage, OpenAI adapter coverage,
  and absence of stale model capability IDs.
- Model `open_url` tools now route through a framework-mediated `network.use`
  action with policy/audit handling instead of direct assistant intent launch.
- Repo checks now validate that assistant-emitted framework action types are in
  the action-request schema and have framework patch-stack handling.
- `FrameworkToolExecutor` now enforces non-empty model-visible reasons for
  model tools marked `requires_reason` in the model-tool registry.
- Audit-event schema now covers framework screen capture/watch and task stop
  events, with repo checks validating schema coverage for framework
  `recordAudit(...)` event names.
- Added a trajectory-event schema and schema marker for assistant
  `events.jsonl` exports, with repo checks validating recorder event coverage.
- Expanded the screen-context schema to cover assistant accessibility UI-tree
  windows, element state, sensitivity hints, and risk flags, with repo checks
  for the key emitted fields.
- Added `scripts/validate-trajectory-export.sh` for assistant trajectory
  evidence directories/zips, including event ordering, screenshot reference,
  and leakage checks.
- Added `docs/contracts/audit-evidence.schema.json` and
  `scripts/validate-audit-evidence-export.sh` for framework audit evidence
  exports.
- Added `docs/contracts/agent-eval-report.schema.json` and
  `scripts/validate-agent-eval-report.sh` so physical agent eval runs can be
  recorded with validated trajectory and audit evidence references.
- Added `scripts/collect-agent-eval.sh` to pull the latest phone-exported
  trajectory/audit files, create `agent-eval.json`, and validate the complete
  eval bundle.
- Added `scripts/diagnose-device-connection.sh` to classify Pixel bringup
  connection states across host USB, ADB, fastboot, shell, and logcat probes.
- Model broker hardening for JSONL request-outcome audit events and optional
  Responses API model allowlisting without logging request bodies.
- Model broker privacy gate for OpenPhone task metadata, sensitive-screen risk
  flags, and maximum screenshot/image count before Responses provider
  forwarding.
- Model broker bounded provider retry for transient upstream 429/5xx failures,
  with retry attempts recorded in body-free audit events.
- Automated model broker smoke test covering broker health, auth rejection,
  token issuer auth, device-proof rejection, signed token minting/acceptance,
  JSON/model/body-size/content-type rejections, audit event writing, and no
  request-body logging.
- Updated v0.0.1 preview release notes and release staging workflow around the
  current Pixel 9a OTA candidate.
- Assistant model provider, model name, cloud/local mode, and privacy
  disclosure shown in the UI and written to trajectory start events.
- Assistant task grant defaults now persist across app launches.
- Settings-owned durable OpenPhone task-grant defaults backed by
  `Settings.Secure`, with switches for input/navigation, screenshots,
  clipboard, sharing, and network/web-link behavior.
- First per-app capability policy seed installed under `system_ext`, plus
  assistant preflight enforcement using the current foreground package from the
  accessibility screen tree.
- Durable `Settings.Secure` app-policy override contract
  `openphone_app_policy_overrides`, evaluated before the built-in per-app
  policy seed.
- Added `scripts/generate-app-policy-override.sh` for creating and installing
  development per-app capability overrides.
- First assistant-owned preview OTA client for generated OpenPhone OTA feeds.
  It checks the feed device, downloads the selected OTA ZIP to
  `Downloads/OpenPhone`, and verifies size/SHA-256 before manual installation.
- OpenPhone Assistant development package bumped to `versionCode=52` /
  `versionName=0.1.16-dev` for the preview OTA client build.

### Changed

- The OpenPhone framework service status JSON now includes active task count
  and task IDs so trusted OS surfaces can show and stop current work.
- The OpenPhone framework service SELinux label now lives in private platform
  policy, and the service is listed in the sepolicy fuzzer-binding map so full
  Pixel 9a OTA builds pass release hygiene gates.
- OpenPhone Assistant can export the latest trajectory as a zip file under
  `Downloads/OpenPhone`, giving physical evals a non-root evidence path.
- `docs/PLAN.md` is now the canonical active plan.
- `docs/ROADMAP.md` is now the short public roadmap.
- Stopping an active agent run now cancels the model adapter, interrupts the
  run thread, prevents stale generations from updating the UI, and treats
  disconnected OpenAI requests as cancellation rather than network failure.
- The local heuristic adapter now emits structured JSON results that the
  user-facing task surface can interpret cleanly.
- The assistant now attempts to re-enable its accessibility service on resume
  as well as at first launch, which helps after fresh OTAs or onboarding resets.

## [0.0.1] - Unreleased

Initial developer preview target.

Planned scope:

- Source-available OpenPhone repository.
- Pixel 9a `tegu` development target documentation.
- Privileged OpenPhone Assistant app.
- Hidden framework service for task, screen, action, policy, confirmation, and
  audit plumbing.
- Lightweight CI checks for repository hygiene.
- Release notes, changelog, and release process documentation.
