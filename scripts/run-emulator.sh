#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

usage() {
  cat <<'USAGE'
Usage: scripts/run-emulator.sh [--arch arm64|x86_64] [--variant eng|userdebug] [-- <emulator args>]

Runs a previously built OpenPhone SDK phone image in the Android emulator.

Examples:
  scripts/run-emulator.sh
  scripts/run-emulator.sh --arch x86_64 -- -wipe-data
  scripts/run-emulator.sh -- -no-window -gpu swiftshader_indirect
USAGE
}

detect_emulator_arch() {
  case "$(uname -m)" in
    arm64|aarch64) printf 'arm64' ;;
    x86_64|amd64) printf 'x86_64' ;;
    *) die "unsupported host architecture: $(uname -m). Pass --arch arm64 or --arch x86_64." ;;
  esac
}

arch=""
variant="eng"
emulator_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --arch)
      [[ $# -ge 2 ]] || die "--arch requires a value"
      arch="$2"
      shift 2
      ;;
    --variant)
      [[ $# -ge 2 ]] || die "--variant requires a value"
      variant="$2"
      shift 2
      ;;
    --)
      shift
      emulator_args=("$@")
      break
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

arch="${arch:-$(detect_emulator_arch)}"
case "$arch" in
  arm64|x86_64) ;;
  *) die "unsupported emulator arch: $arch" ;;
esac

case "$variant" in
  eng|userdebug) ;;
  *) die "unsupported emulator build variant: $variant" ;;
esac

[[ -d "$OPENPHONE_ANDROID_DIR" ]] || die "Android tree not found: $OPENPHONE_ANDROID_DIR"
[[ -f "$OPENPHONE_ANDROID_DIR/build/envsetup.sh" ]] || die "missing build/envsetup.sh; run scripts/sync.sh first"

target="openphone_sdk_phone_${arch}-${OPENPHONE_RELEASE}-${variant}"

info "Android tree: $OPENPHONE_ANDROID_DIR"
info "Emulator target: $target"

cd "$OPENPHONE_ANDROID_DIR"

set +e +u
# shellcheck disable=SC1091
source build/envsetup.sh
lunch "$target"
setup_status=$?
set -euo pipefail

if [[ $setup_status -ne 0 ]]; then
  die "build environment setup failed for $target"
fi

if ! command -v emulator >/dev/null 2>&1; then
  die "Android SDK emulator binary not found in PATH. Install Android Studio/SDK Emulator, or use the built sdk-repo-linux-system-images.zip with an SDK/AVD install."
fi

exec emulator "${emulator_args[@]}"
