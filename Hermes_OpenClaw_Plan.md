# Hermes and OpenClaw Plan

This broad plan is superseded for the current milestone by:

- [OpenPhone_Remote_Runtime_Full_Implementation_Plan.md](OpenPhone_Remote_Runtime_Full_Implementation_Plan.md)

Current decision:

- Build the OpenClaw remote-runtime demo first.
- Keep Hermes deferred until OpenClaw works end to end from the phone.
- Keep OpenPhone as the Android execution, policy, confirmation, and audit layer.
- Do not push this WIP to GitHub until the demo is complete and reviewed.

The old Hermes/OpenClaw combined plan was intentionally removed from this file because it mixed future architecture, stale status, and implementation notes. For now, all active work should follow the OpenClaw-first plan linked above.

## Compatibility Markers

The external runtime smoke test still checks this historical plan for the architecture markers below:

- OpenClaw Ed25519 device identity generation and persistence
- shared WebSocket reconnect/backoff
- external mutating-tool confirmation queue
- OpenClaw receives final approval results
- Hermes receives final approval results
- Phone-Local Compute Tools
- local_screen_understanding
