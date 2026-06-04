# Architecture

OpenPhone is built as an Android/LineageOS-derived ROM with an OpenPhone-owned
AI layer added as privileged OS components.

## Layers

```text
Applications
  third-party apps
  system apps
  OpenPhoneAssistant

OpenPhone AI Layer
  assistant UI
  agent orchestrator
  screen understanding
  action execution
  policy and consent
  audit log
  model runtime adapter

Android Framework
  ActivityTaskManager
  WindowManager
  InputManager
  NotificationManager
  PackageManager
  PermissionManager

System
  system_server
  Binder
  init
  SELinux
  HAL/vendor interfaces
  kernel

Device
  device tree
  kernel config
  vendor blobs
  firmware expectations
```

## Initial Implementation

The current repo implements the first OpenPhone product layer:

- `vendor/openphone` product config.
- `OpenPhoneAssistant` privileged app with task, grant, screen context, and
  audit controls.
- Initial capability and policy config files. `scripts/check.sh` validates that
  the assistant fallback `PolicyEngine` covers every capability in
  `openphone_capabilities.json` with the same risk class.
- Initial model-tool registry. `openphone_model_tools.json` maps model-visible
  tools to product capabilities, and repo checks validate that the framework
  tool executor and OpenAI adapter cover the registered vocabulary. The
  executor enforces non-empty model-visible reasons for tools marked
  `requires_reason`.
- Local manifest and patch-stack workflow.
- Hidden OpenPhone framework manager and Binder service.
- `system_server` OpenPhone agent manager service.
- Foreground/visible activity context from ActivityTaskManager.
- First mediated action path for opening apps.
- Mediated web-link launch path for model `open_url` tools, using the
  framework `network.use` action policy/audit path instead of direct assistant
  intent launching.
- Contract validation that action types emitted by the assistant framework tool
  executor are present in `docs/contracts/action-request.schema.json` and have
  matching framework patch-stack handling.
- Audit contract validation that framework `recordAudit(...)` event names are
  represented in `docs/contracts/audit-event.schema.json`.
- Audit evidence export validation for assistant-exported framework audit JSON.
- Trajectory event contract validation for assistant-exported `events.jsonl`
  traces.
- Trajectory export validation for zipped or unpacked eval evidence, including
  screenshot references and leakage checks.
- Screen-context contract coverage for assistant accessibility UI-tree
  snapshots, including window metadata, element state, sensitivity flags, and
  risk hints.
- Task-scoped input action execution for navigation, pointer gestures, scroll,
  and text.
- Confirmed clipboard write and paste actions.
- Confirmed share chooser actions.
- One-shot pending action confirmation through the assistant control surface.
- Durable framework audit log under `/data/system/openphone/`.
- Model adapter transport split between direct development provider calls and
  an OpenPhone broker/proxy mode. The broker mode keeps provider API keys off
  the phone by sending Responses/transcription-shaped requests to an
  OpenPhone-controlled endpoint with a session token.
- First reference model broker under `services/model-broker/` for development
  deployments. It accepts phone-side broker requests, validates bearer session
  tokens, applies coarse size/rate limits, avoids request-body logging, and
  forwards to OpenAI. It supports static development tokens and signed expiring
  HMAC session tokens minted either by CLI or an admin-authenticated
  `/v1/session_tokens` endpoint, structured JSONL request-outcome audit events,
  a JSON provider/model registry with an environment model-allowlist override
  for local testing, and a JSON device-subject registry with optional
  development HMAC proof for restricting session token issuance.
- Settings-owned OpenPhone task-grant defaults and first app capability policy
  editor. The app policy editor writes `openphone_app_policy_overrides` in
  `Settings.Secure`, using the same JSON contract consumed by assistant-side
  preflight before the system_ext seed policy.

This is enough to start producing OpenPhone-flavored builds once the Android
tree is synced and to validate the first OS-level agent capability path. It is
not yet the final AI-native OS integration.

## Required Framework Work

The following components still need real Android framework implementation:

- Window/UI hierarchy extraction and screenshot/OCR perception.
- UI-element target resolution for input actions.
- Notification and app-specific action integrations.
- Rich confirmation and grant lifecycle UI.
- Privilege separation between assistant UI, orchestrator, and executors.
- SELinux domains and neverallow-compatible policy.
- Production model broker identity and operations: managed certificate
  rollout, abuse controls, stronger device attestation, admin-token and
  provider-secret rotation, and production privacy enforcement. A first helper
  exists for rotating broker token/admin secrets and provider keys, and a first
  certbot/nginx helper exists for broker TLS certificate setup.
- Richer Settings-hosted durable grant editor for app-specific rules.
- Richer background task visibility in SystemUI.

The detailed framework target is tracked in
[FRAMEWORK_PLAN.md](FRAMEWORK_PLAN.md).

## Design Rule

All sensitive actions must pass through policy below the assistant UI layer.
The assistant UI may request an action, but it must not be the authority that
decides whether the action is allowed.
