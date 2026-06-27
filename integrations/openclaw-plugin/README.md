# OpenPhone Android OpenClaw Plugin

This package is the OpenPhone-owned OpenClaw integration boundary.

OpenPhone connects to OpenClaw as a normal node and sends user turns through
OpenClaw's stock `agent.request` node event. This plugin only owns OpenPhone
node command policy: it lets OpenClaw forward OpenPhone-specific node commands
to paired OpenPhone Android nodes without adding OpenPhone behavior to
OpenClaw core.

## Commands

Installing the plugin enables these OpenPhone read commands for paired Android
nodes that identify with `deviceFamily: "OpenPhone"`:

- `openphone.screen.get`
- `openphone.local.screen_understanding`
- `openphone.jobs.list`

Phone actions are registered as dangerous plugin commands and still require
explicit `gateway.nodes.allowCommands` opt-in in OpenClaw:

- `openphone.app.open`
- `openphone.url.open`
- `openphone.ui.*`
- `openphone.input.press_key`
- `openphone.clipboard.*`
- `openphone.share.text`
- `openphone.jobs.create`
- `openphone.jobs.stop`
- `notifications.open`
- calendar/SMS/call write commands

OpenPhone also keeps local phone-side policy and confirmation checks for
mutating commands.

## Local Install

From an OpenClaw checkout or installation:

```sh
openclaw plugins install /path/to/OpenPhone/integrations/openclaw-plugin
```

For a demo profile that allows selected phone actions:

```json
{
  "gateway": {
    "nodes": {
      "allowCommands": [
        "openphone.app.open",
        "openphone.url.open",
        "openphone.ui.tap",
        "openphone.ui.type_text"
      ]
    }
  }
}
```

Publish this package to ClawHub/NPM as `@openphone/openclaw-plugin` when the
integration is ready for public installation.
