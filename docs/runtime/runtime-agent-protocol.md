# OpenPhone Runtime Agent Protocol

OpenPhone exposes the phone as a runtime endpoint. A runtime can be local
(`Phone`) or remote (`OpenClaw`, future `Hermes`, MCP clients, CLI tools).
Android owns the screen/action surface, session records, and user confirmation
boundary; remote runtimes own agent reasoning.

## Flow

1. A phone surface creates an attention request: chat, volume voice, Dynamic
   Island, watcher, job, or notification.
2. `RuntimeManager` creates a phone execution session and sends the request to
   the selected `RuntimeAdapter`.
3. The adapter maps the generic request to the runtime's wire protocol.
4. The runtime may answer directly or request phone tools.
5. `RuntimeToolBridge` validates the command, applies autonomy policy, creates
   confirmations for mutating actions, runs the phone tool, and returns the
   result.

## Stable Concepts

- Runtime: an agent backend selected by the user.
- Surface: the phone source that initiated a request.
- Phone session: durable Android-owned execution state.
- Runtime session: backend-owned session/thread/run key.
- Attention request: phone-to-runtime user request.
- Tool request: runtime-to-phone inspect/action request.
- Confirmation: Android-local approval for risky actions.

## Autonomy

- `observe_only`: read-only tools may run; mutating tools are denied.
- `ask_before_action`: mutating tools require Android confirmation.
- `trusted_actions`: low/medium-risk mutating tools may run without OpenPhone
  confirmation, but high-risk tools and OS/app confirmations still require
  confirmation.

If a runtime tool call omits autonomy, OpenPhone inherits the phone execution
session's autonomy. If no session is available, it falls back to
`ask_before_action`.

## Single Sources Of Truth

- Commands: `runtime/protocol/openphone-commands.json`
- Events: `runtime/protocol/openphone-events.json`
- Capabilities: `runtime/protocol/openphone-capabilities.json`
- Shape reference: `runtime/protocol/openphone-runtime.schema.json`
