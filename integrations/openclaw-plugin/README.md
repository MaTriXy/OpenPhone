# OpenPhone Android OpenClaw Plugin

This package is the OpenPhone-owned OpenClaw integration boundary. It lives in
the OpenPhone repo, but it is installed into OpenClaw as a normal ClawHub/NPM
plugin.

OpenPhone connects to OpenClaw as a normal node and sends user turns through
OpenClaw's stock `agent.request` node event. This plugin only owns OpenPhone
node command policy: it lets OpenClaw forward OpenPhone-specific node commands
to paired OpenPhone Android nodes without adding OpenPhone behavior to
OpenClaw core.

## Commands

Installing the plugin default-enables only low-blast-radius OpenPhone runtime
commands for paired Android nodes that declare OpenPhone-specific commands:

- `openphone.apps.search`
- `openphone.screen.get`
- `openphone.screen.understand_local`
- `openphone.local.screen_understanding`
- `openphone.jobs.list`

Private reads and phone actions are registered as dangerous plugin commands and
still require explicit `gateway.nodes.allowCommands` opt-in in OpenClaw:

- `openphone.device.status`
- `openphone.notifications.*`
- `openphone.contacts.search`
- `openphone.calendar.search`
- `openphone.messages.search`
- `openphone.calls.search`
- `openphone.memory.search`
- `openphone.watchers.list`
- `openphone.app.open`
- `openphone.url.open`
- `openphone.ui.*`
- `openphone.input.press_key`
- `openphone.clipboard.*`
- `openphone.share.text`
- `openphone.calendar.*`
- `openphone.messages.*`
- `openphone.calls.place`
- `openphone.memory.save`
- `openphone.watchers.create`
- `openphone.watchers.stop`
- `openphone.jobs.create`
- `openphone.jobs.stop`

OpenPhone also keeps local phone-side policy, autonomy checks, and confirmation
checks for mutating commands.

## Local Install

From an OpenClaw checkout or installation:

```sh
openclaw plugins install /path/to/OpenPhone/integrations/openclaw-plugin
```

The package entrypoint is compiled JavaScript in `dist/index.js`. Do not publish
the package as a source-only `index.ts` plugin.

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
