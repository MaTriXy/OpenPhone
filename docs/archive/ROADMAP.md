# Roadmap

OpenPhone is in developer preview. The roadmap below is intentionally public
and product-facing; historical plans and implementation logs live in
[archive](archive/).

## 0.0.1 Public Developer Preview

Status: in progress.

- Publish a clean source-available repository with clear build, testing,
  release, contribution, security, and commercial-use docs.
- Keep Pixel 9a (`tegu`) as the first verified physical device target.
- Ship the current privileged assistant, framework services, model/tool
  registry, policy config, dynamic island, watcher runtime, and release tooling
  as developer-preview source.
- Publish release notes, checksums, known issues, and validation evidence for
  any device artifact.

## Agent And Voice

Status: active.

- Make the regular agent better at long-running multi-step phone tasks.
- Keep OpenAI Realtime voice available as an optional demo mode alongside the
  existing traceable regular agent path.
- Improve interruption, turn-taking, audio lifecycle, and visible mode
  distinction between realtime and regular sessions.
- Expand repeatable evals for screen understanding, app control, watcher
  creation, and failure recovery.

## Proactive Runtime

Status: active.

- Make watchers and commitments easier to inspect, disable, and debug from the
  dynamic island and assistant surfaces.
- Keep watcher triggers generic: they should observe phone events, queue
  reviewed agent work, and use normal model tools rather than one-off code
  paths.
- Add clearer background-run state, retry state, and failure evidence for tasks
  that continue after the current chat.
- Improve policy handling for state-changing background actions that require
  user review.

## OS Integration

Status: partially implemented.

- Move more screen context and UI-tree ownership into framework/system services
  instead of assistant-side development plumbing.
- Improve OS-mediated action execution, element targeting, and accessibility
  fallback behavior.
- Harden SELinux, persistence, audit evidence export, and framework service
  contracts.
- Build a richer SystemUI-owned active-agent surface after the assistant-owned
  dynamic island proves the interaction model.

## Devices And Releases

Status: early.

- Keep Pixel 9a boot-chain and hardware validation current.
- Document clean flash, OTA, wipe, GMS sideload, and recovery flows without
  redistributing restricted Google or vendor materials.
- Improve self-hosted release and physical-device eval automation.
- Add new device targets only when exact model, codename, kernel, device tree,
  vendor blob, flash, and validation notes are ready.

## Community Work

Good external contribution areas:

- Reproducible agent evals and failing task traces.
- Device support research and exact bringup checklists.
- UI polish for the dynamic island, assistant chat, watcher state, and
  approval flows.
- Documentation that makes the Android ROM workflow easier to reproduce.
- Contract and validator improvements for actions, tools, audit, trajectories,
  and release artifacts.
