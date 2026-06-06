# Roadmap

The canonical active engineering plan lives in [PLAN.md](PLAN.md). This file is
the short public roadmap.

## 0.0.1 Developer Preview

Status: in progress.

- Public repository cleanup.
- License, notice, contribution, and security docs.
- Changelog and release process.
- GitHub CI for repository checks.
- Pixel 9a development target documentation.
- Current privileged assistant, framework-service, Settings, SystemUI tile, and
  model-broker source.

## Agent v1

Status: in progress.

- CUA-informed observe/reason/act loop implemented on the phone, not through
  ADB.
- Structured model tool schema.
- Screenshot-based one-step action selection.
- Multi-step task loop with max step/time limits.
- Trajectory logging for screenshots, model calls, actions, policy decisions,
  and failures.
- Repeatable eval tasks.
- User-facing chat surface for text/voice task entry.

## OS Integration v1

Status: partially implemented.

- Privileged assistant app.
- Hidden framework manager API.
- `system_server` OpenPhone agent service.
- Screen context and screenshot payloads.
- OS-mediated action execution.
- Policy, confirmation, pointer event, and audit plumbing.
- Assistant-owned cursor/status surface.
- Settings-owned OpenPhone dashboard, task-grant defaults, and audit pages.
- SystemUI Quick Settings tile.

Remaining:

- Production framework-owned UI hierarchy/OCR extraction. Current semantic
  element targeting uses the assistant accessibility service.
- Richer SystemUI-owned active agent surface.
- Full Settings-owned per-app/per-capability grant editor.
- Richer confirmation UX.

## Device and Release Hardening

Status: early.

- Harden the automated Pixel 9a DTB preparation and generated boot-chain
  verification.
- Build reproducible Pixel 9a release artifacts on Linux.
- Add release checksums and notes.
- Add self-hosted Android build CI later.
- Validate Wi-Fi, cellular, camera, microphone, GPS, fingerprint, encryption,
  and reboot stability.
- Decide and document the production app-store/default-app strategy.
