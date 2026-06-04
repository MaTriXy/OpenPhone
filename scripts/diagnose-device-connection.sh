#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/diagnose-device-connection.sh [--output <report.txt>]

Diagnoses the host/device connection state for Pixel 9a OpenPhone bringup.
It does not modify the phone. It records host USB visibility, adb state,
fastboot visibility, shell/logcat/install channel probes, and concrete next
steps for the observed failure class.
EOF
}

output=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      output="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ -z "$output" ]]; then
  output="$root/.worktree/reports/device-connection-$timestamp.txt"
fi
mkdir -p "$(dirname "$output")"

adb_cmd() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" "$@"
  else
    adb "$@"
  fi
}

fastboot_cmd() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    fastboot -s "$ANDROID_SERIAL" "$@"
  else
    fastboot "$@"
  fi
}

section() {
  printf '\n== %s ==\n' "$*"
}

record_command() {
  local title="$1"
  shift
  section "$title"
  printf '$'
  printf ' %q' "$@"
  printf '\n'
  "$@" 2>&1 || printf 'RESULT: command_failed\n'
}

classify() {
  local usb_seen="$1"
  local adb_state="$2"
  local fastboot_seen="$3"
  local shell_ok="$4"
  local logcat_ok="$5"

  section "Diagnosis"
  if [[ "$usb_seen" != "yes" && "$adb_state" != "device" && "$fastboot_seen" != "yes" ]]; then
    cat <<'EOF'
state=no_usb_enumeration
The host does not see a Google/Android USB device through adb, fastboot, or the
macOS USB device tree.

Next steps:
- Try a known data-capable USB-C cable.
- Connect directly to the Mac, not through a monitor/hub.
- Unlock the phone and set USB mode to File Transfer if Android is booted.
- Toggle USB debugging off/on after the device is visible in macOS USB.
- If the phone is in bootloader, check `fastboot devices -l` again.
EOF
    return
  fi

  if [[ "$fastboot_seen" == "yes" && "$adb_state" != "device" ]]; then
    cat <<'EOF'
state=fastboot_visible
The phone is visible in bootloader/fastboot mode but Android ADB is not active.

Next steps:
- Use the phone's bootloader menu Start option to boot Android.
- If Android cannot boot, use Recovery Mode and sideload a known-good OTA.
- After Android boots, enable Developer Options and USB debugging again.
EOF
    return
  fi

  if [[ "$adb_state" == "unauthorized" ]]; then
    cat <<'EOF'
state=adb_unauthorized
ADB can see the phone but the host key is not authorized.

Next steps:
- Unlock the phone.
- Accept the USB debugging RSA prompt.
- If no prompt appears, revoke USB debugging authorizations, toggle USB
  debugging off/on, reconnect the cable, then retry.
EOF
    return
  fi

  if [[ "$adb_state" == "device" && "$shell_ok" != "yes" ]]; then
    cat <<'EOF'
state=adb_shell_unusable
ADB transport reports `device`, but shell channels are not usable.

Next steps:
- Finish Android onboarding after a wipe.
- Re-enable Developer Options and USB debugging.
- Accept the USB debugging prompt.
- Reboot Android once from the UI if shell/logcat/install channels still close.
- Do not treat physical evals as validated until shell, logcat, and pull/install
  channels work.
EOF
    return
  fi

  if [[ "$adb_state" == "device" && "$shell_ok" == "yes" && "$logcat_ok" != "yes" ]]; then
    cat <<'EOF'
state=adb_partial
ADB shell works, but logcat did not return cleanly.

Next steps:
- Retry after boot completes.
- Run `adb shell getprop sys.boot_completed`.
- Physical evals can start only after the required evidence channels work.
EOF
    return
  fi

  if [[ "$adb_state" == "device" && "$shell_ok" == "yes" ]]; then
    cat <<'EOF'
state=adb_ready
ADB shell is usable. Continue with:
- ./scripts/verify-tegu-device.sh
- ./scripts/smoke-test-tegu-hardware.sh
- the agent evals in docs/TESTING.md
EOF
    return
  fi

  cat <<EOF
state=unknown
adb_state=$adb_state
usb_seen=$usb_seen
fastboot_seen=$fastboot_seen
shell_ok=$shell_ok
logcat_ok=$logcat_ok
EOF
}

run() {
  section "Host"
  printf 'timestamp=%s\n' "$timestamp"
  printf 'host_uname='
  uname -a
  printf 'android_serial=%s\n' "${ANDROID_SERIAL:-}"

  local usb_seen="unknown"
  if command -v system_profiler >/dev/null 2>&1; then
    section "macOS USB"
    if system_profiler SPUSBDataType 2>/dev/null |
        grep -Ei 'google|pixel|android|18d1|05c6|fastboot' -C 3; then
      usb_seen="yes"
    else
      usb_seen="no"
      printf 'No Google/Pixel/Android-looking USB entry found.\n'
    fi
  else
    usb_seen="unknown"
    section "Host USB"
    printf 'system_profiler unavailable; USB tree probe skipped.\n'
  fi

  local adb_state="missing_adb"
  local shell_ok="no"
  local logcat_ok="no"
  if command -v adb >/dev/null 2>&1; then
    record_command "ADB devices" adb devices -l
    adb_state="$(adb_cmd get-state 2>/dev/null || true)"
    printf '\nadb_state=%s\n' "$adb_state"
    if [[ "$adb_state" == "device" ]]; then
      section "ADB shell probe"
      if adb_cmd shell 'echo shell-ok; getprop sys.boot_completed; getprop ro.product.device' 2>&1; then
        shell_ok="yes"
      else
        printf 'RESULT: shell_failed\n'
      fi
      section "ADB logcat probe"
      if adb_cmd logcat -d -t 1 >/dev/null 2>&1; then
        logcat_ok="yes"
        printf 'logcat_ok=yes\n'
      else
        printf 'logcat_ok=no\n'
      fi
    fi
  else
    section "ADB"
    printf 'adb is not installed or not on PATH.\n'
  fi

  local fastboot_seen="no"
  if command -v fastboot >/dev/null 2>&1; then
    section "Fastboot devices"
    if fastboot_output="$(fastboot_cmd devices -l 2>&1)"; then
      printf '%s\n' "$fastboot_output"
      if [[ -n "$fastboot_output" ]]; then
        fastboot_seen="yes"
      fi
    else
      printf '%s\n' "$fastboot_output"
      printf 'RESULT: fastboot_failed\n'
    fi
  else
    section "Fastboot"
    printf 'fastboot is not installed or not on PATH.\n'
  fi

  classify "$usb_seen" "$adb_state" "$fastboot_seen" "$shell_ok" "$logcat_ok"
}

run | tee "$output"
printf '\nDevice connection report written to %s\n' "$output"
