# Device Matrix

| Device | Codename | State | Upstream | Notes |
| --- | --- | --- | --- | --- |
| Generic ARM64 product | `openphone_arm64` | bringup | LineageOS `lineage-23.2` | Build bootstrap target, not a supported phone. |
| Google Pixel 9a | `tegu` | verified bringup, agent validation in progress | LineageOS `lineage-23.2` | First physical target. Full `openphone_tegu` builds have booted on hardware; assistant, dynamic island, framework services, watcher runtime, and GMS sideload recovery flows are under active validation. |
