# OpenPhone Runtime MCP Server

MCP server exposing OpenPhone runtime tools from
`runtime/protocol/openphone-commands.json`.

The first transport is ADB, so this is useful for local development, Hermes
tool access, and other MCP-capable agents that need phone inspection or simple
phone actions without a custom Android runtime adapter.

Run:

```sh
node integrations/mcp-server/src/index.mjs
```

Set `ANDROID_SERIAL` to target a specific USB-connected phone. Set
`OPENPHONE_DRY_RUN=1` for parser/protocol tests without ADB.

Boundary:

- MCP exposes tools.
- Runtime sessions, volume-button attention, watcher pushes, and Dynamic Island
  lifecycle are still owned by the OpenPhone runtime protocol.
