# Security Policy

OpenPhone is an early developer preview project. Do not use current builds as a
daily-driver secure phone OS.

## Reporting Security Issues

Do not file public GitHub issues for vulnerabilities, leaked secrets, bypasses,
or exploitable device-control bugs.

Report privately through GitHub private vulnerability reporting when available,
or through the maintainer's existing private contact channel until a dedicated
security address is published.

## Current Security Posture

- The bootloader on development devices is expected to be unlocked.
- Development builds are not production signed.
- Play Integrity compatibility is not a goal for early releases.
- The assistant and framework service are experimental.
- Background agent jobs are reviewed-conservative: they may observe and
  summarize, but state-changing background tools are blocked until a foreground
  reviewed approval flow exists.
- Production key management for model providers is not implemented.

## Secrets

Never commit:

- API keys,
- private SSH keys,
- signing keys,
- vendor credentials,
- personal device data,
- generated Android build outputs.
