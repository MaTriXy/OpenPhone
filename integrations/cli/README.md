# OpenPhone Runtime CLI

Developer CLI for the OpenPhone Runtime Agent protocol.

The CLI reads `runtime/protocol/openphone-commands.json` for tool metadata and
uses ADB as its first transport.

Examples:

```sh
node integrations/cli/src/index.mjs runtime status
node integrations/cli/src/index.mjs runtime select --chat openclaw --volume phone
node integrations/cli/src/index.mjs runtime configure openclaw --url ws://127.0.0.1:8787
node integrations/cli/src/index.mjs tool list
node integrations/cli/src/index.mjs tool invoke openphone.screen.get '{"include_screenshot":false}'
node integrations/cli/src/index.mjs mcp serve
```

Set `ANDROID_SERIAL` for a specific connected phone. Use `--dry-run` to test
command parsing without contacting ADB.

For the OpenPhone SDK phone emulator, target the emulator ADB serial:

```sh
node integrations/cli/src/index.mjs --serial emulator-5584 runtime status --json
node integrations/cli/src/index.mjs --serial emulator-5584 tool invoke \
  openphone.screen.get '{"include_screenshot":false}' --json
```

The emulator setup and first-test workflow are documented in
[../../docs/EMULATOR.md](../../docs/EMULATOR.md).
