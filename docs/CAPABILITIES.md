# Capability Model

OpenPhone capabilities are explicit grants. The assistant should never receive
undefined ambient authority just because it is a privileged component.

## Capability Catalog

```text
screen.read.visible
screen.capture
ui.target.resolve
input.perform
apps.launch
tasks.observe
notifications.read
notifications.act
clipboard.read
clipboard.write
share.content
files.read.scoped
contacts.read
calendar.read
calendar.write
messages.draft
messages.send
calls.place
settings.read
settings.write
background.run
network.use
account.access
```

## Risk Levels

Low-risk actions may run after task-level approval:

- Open app.
- Scroll.
- Navigate back/home.
- Summarize currently visible screen.

Medium-risk actions normally require contextual confirmation:

- Draft message.
- Modify calendar event.
- Change device setting.
- Download file.
- Read clipboard content.

High-risk actions always require explicit confirmation:

- Send message.
- Place call.
- Purchase item.
- Transfer money.
- Delete data.
- Share private content externally.

## Initial Config

The bootstrap policy file lives at:

```text
overlay/vendor/openphone/config/openphone_policy.json
```

This file is only declarative seed data. Real enforcement must be implemented
inside OpenPhone framework/system services.

The first per-app policy seed lives at:

```text
overlay/vendor/openphone/config/openphone_app_policy.json
```

It lets OpenPhone require stronger review for a capability when the foreground
package is known to be sensitive. The initial seed covers Settings, Android
permission prompts, Google account/payment surfaces, and the Play Store. The
assistant preflight already honors this seed for model tools by reading the
assistant-side screen tree's `foreground_package`.

The enforcement path also checks a durable `Settings.Secure` override before
the seed policy:

```text
openphone_app_policy_overrides
```

The value uses the same JSON shape as `openphone_app_policy.json`. This gives a
future Settings editor a stable storage contract for per-app capability rules
while the current product uses the built-in seed plus development overrides.
The built-in seed still applies when no override is present.

For development builds with ADB shell access, generate and optionally install a
single override with:

```bash
scripts/generate-app-policy-override.sh \
  --package com.example.sensitive \
  --match prefix \
  --capability input.perform \
  --capability screen.capture \
  --decision explicit_confirm \
  --reason "Sensitive app requires explicit review" \
  --install-adb
```
