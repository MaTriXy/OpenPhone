# OpenPhone Examples

These examples are prompts and workflows for exercising the current OpenPhone
agent on a development device. They are not product guarantees; use them to
find bugs, create evals, and improve tool behavior.

## Screen Understanding

```text
Can you see my screen?
Summarize what is open right now.
What app am I using?
What can I tap from here?
```

## App Control

```text
Open Settings and show me the Apps page.
Open Spotify and play something upbeat.
Open Messages and draft a reply to the latest unread text.
Open the Play Store and search for Signal.
```

State-changing actions such as sending messages should require review unless
the current policy explicitly allows the action.

## Watchers

```text
Watch for missed calls and tell me who called.
Create a watcher for new text messages from Alice and summarize them.
Turn off all watchers.
Show me my active watchers.
```

A watcher is durable monitoring state. It should observe future events and then
queue reviewed agent work when the trigger fires. A background run is the actual
queued agent task created from a watcher, commitment, schedule, or deferred
request.

## Realtime Voice

```text
Start realtime mode.
Interrupt the assistant while it is speaking.
Ask it to open an app, then continue the conversation.
```

Realtime mode is for continuous voice interaction. The regular agent path is
better for bounded tasks that need deterministic tool traces and release evals.

## Turning Examples Into Evals

When a task matters, capture it as an eval:

```bash
scripts/run-assistant-task.sh \
  --goal "Open Settings and show me the Apps page." \
  --wait 120

scripts/pull-latest-trajectory.sh \
  --output-dir .worktree/evals/latest-assistant-run

scripts/validate-trajectory-export.sh .worktree/evals/latest-assistant-run
```
