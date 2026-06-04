#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/push-assistant-apk.sh <OpenPhoneAssistant.apk>

Pushes a focused OpenPhoneAssistant APK build into /system_ext on a userdebug
device, then reboots. This is the fast assistant-code iteration path for the
persistent privileged assistant app, because Android refuses `adb install -r`
for persistent apps.

Prerequisites:
  - Device is already running OpenPhone.
  - Developer Options -> Rooted debugging is enabled.
  - adb root and adb remount are allowed.

Use full OTA instead for framework patches, sysconfig/privapp permission
changes, sepolicy, boot/recovery behavior, or first install.
EOF
}

apk="${1:-}"
if [[ "$apk" == "-h" || "$apk" == "--help" ]]; then
  usage
  exit 0
fi
[[ -n "$apk" ]] || {
  usage >&2
  exit 2
}
[[ -f "$apk" ]] || die "missing APK: $apk"

need_cmd adb
need_cmd sha256sum

local_sha="$(sha256sum "$apk" | awk '{print $1}')"

adb wait-for-device >/dev/null
if ! adb root; then
  die "adb root failed. Enable Developer Options -> Rooted debugging on the device."
fi
sleep 2
adb wait-for-device >/dev/null
adb remount
adb push "$apk" /system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk
adb shell sync

remote_sha="$(
  adb shell sha256sum /system_ext/priv-app/OpenPhoneAssistant/OpenPhoneAssistant.apk |
    awk '{print $1}' | tr -d '\r'
)"
[[ "$remote_sha" == "$local_sha" ]] || {
  die "pushed APK hash mismatch: got $remote_sha expected $local_sha"
}

adb reboot
printf 'Pushed assistant APK and rebooted device.\n'
