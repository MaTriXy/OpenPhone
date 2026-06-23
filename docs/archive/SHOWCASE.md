# Showcase

OpenPhone is still a developer preview, so the best public demos should be
honest: show what runs on a real Pixel 9a today, show where the OS integration
is different from an app-only assistant, and keep known rough edges visible.

## Current Demo Surface

- Dynamic island style assistant presence for idle, listening, thinking,
  replies, approval, watcher, and background-run states.
- Voice-triggered regular agent sessions through the volume chord.
- OpenAI Realtime voice sessions for back-and-forth spoken demos.
- Screen-aware questions such as "can you see my screen?"
- Multi-step app control through model tools and OS-mediated input.
- Watchers that monitor future device events and trigger reviewed agent work.
- Recent chat, watcher, and run status tabs inside the expanded island.
- Audit and trajectory export for debugging and release evidence.

## Demo Ideas

- Ask the assistant what is visible on the current screen.
- Ask it to open an app and complete a short UI task.
- Create a watcher, inspect it in the island, then disable it.
- Start a realtime voice session and demonstrate interruption/continuation.
- Export a trajectory after a failed task and validate it with
  `scripts/validate-trajectory-export.sh`.

More task examples live in [../examples/README.md](../examples/README.md).

## Screenshots And Assets

The repository currently includes the public GitHub hero asset at
[assets/github_hero.png](assets/github_hero.png). Product screenshots should go
under `docs/assets/screenshots/` once they are intentionally captured for
public use.

Before adding screenshots:

- Remove personal notifications, phone numbers, emails, keys, and account data.
- Prefer real device screenshots over desk photos when UI detail matters.
- Name files by surface and date, for example
  `dynamic-island-chat-2026-06.png`.
- Reference the exact OpenPhone commit or release in the caption.

## What Not To Claim Yet

- Do not call current builds consumer-ready.
- Do not imply broad Android device support beyond documented targets.
- Do not claim Google Mobile Services are redistributed by OpenPhone.
- Do not claim background tools can perform every sensitive action without
  foreground review.
- Do not hide agent failures; use them to improve evals and policy.
