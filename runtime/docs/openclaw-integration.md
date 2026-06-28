# OpenClaw Integration

OpenClaw support is intentionally split between Android and an installable
OpenClaw plugin.

## Android Adapter

Path:

```text
overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/runtime/adapters/openclaw/
```

Responsibilities:

- Connect to an OpenClaw gateway.
- Authenticate as an OpenPhone Android node.
- Convert phone attention requests into stock OpenClaw `agent.request` events.
- Convert OpenClaw `node.invoke.request` events into generic
  `RuntimeToolRequest`s.
- Return tool results through `node.invoke.result`.
- Send confirmation required/resolved events through existing node event paths.

The adapter should not define OpenClaw core policy. Policy belongs in the plugin.

## Plugin

Path:

```text
integrations/openclaw-plugin/
```

Responsibilities:

- Register OpenPhone Android node command policy.
- Keep screen/app reads separate from private reads and dangerous actions.
- Use existing OpenClaw plugin and node-invoke policy surfaces.

This keeps OpenPhone out of OpenClaw core while still giving OpenClaw a
reviewable integration point.

## Validation

The device smoke uses an OpenClaw-compatible shim for deterministic protocol and
failure-mode checks. A final demo/PR proof should also run against a real
OpenClaw runtime with this plugin installed.
