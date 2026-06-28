# MCP Bridge

The MCP bridge exposes the same command manifest used by remote runtimes. It is
not a separate permission model.

## Local Transport

`integrations/adb/openphone-adb-transport.mjs` is the first transport. It uses
ADB to read runtime status, select routes, configure OpenClaw, and invoke phone
tools for development and local MCP clients.

The transport works with USB devices and OpenPhone SDK phone emulators. Use
`ANDROID_SERIAL=emulator-5584` or the CLI `--serial emulator-5584` option when
more than one ADB target is connected.

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

Against the emulator first-test path:

```sh
node integrations/cli/src/index.mjs \
  --serial emulator-5584 \
  tool invoke openphone.screen.get '{"include_screenshot":false}' \
  --json

ANDROID_SERIAL=emulator-5584 \
ADB="$ANDROID_HOME/platform-tools/adb" \
node integrations/mcp-server/src/index.mjs
```

See [../EMULATOR.md](../EMULATOR.md) for the full AVD setup and runtime smoke
path.
