# MCP Bridge

The MCP bridge exposes the same command manifest used by remote runtimes. It is
not a separate permission model.

## Local Transport

`integrations/adb/openphone-adb-transport.mjs` is the first transport. It uses
ADB to read runtime status, select routes, configure OpenClaw, and invoke phone
tools for development and local MCP clients.

## Server

`integrations/mcp-server` turns manifest-backed OpenPhone commands into MCP
tools. Tool schemas come from `openphone-commands.json`; future transports can
replace ADB without changing the tool list.

## CLI

`integrations/cli` shares the same transport and manifests for local testing:

```sh
openphone runtime status --json
openphone runtime select --chat openclaw --volume openclaw
openphone runtime configure openclaw --url ws://127.0.0.1:18789 --token TOKEN
openphone screen get --json
openphone mcp serve
```
