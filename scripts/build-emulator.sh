#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

usage() {
  cat <<'USAGE'
Usage: scripts/build-emulator.sh [--arch arm64|x86_64] [--variant eng|userdebug]

Builds the OpenPhone LineageOS SDK phone product for the Android emulator.

Defaults:
  --arch      auto-detected from the build host
  --variant   eng

Environment:
  OPENPHONE_ANDROID_DIR   Android checkout path
  OPENPHONE_RELEASE       Android release config, default bp4a
  OPENPHONE_BUILD_GOAL    Build goals, default "droid emu_img_zip"
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

export OPENPHONE_BUILD_GOAL="${OPENPHONE_BUILD_GOAL:-droid emu_img_zip}"

product="openphone_sdk_phone_${arch}"
target="${product}-${OPENPHONE_RELEASE}-${variant}"

info "Emulator arch: $arch"
info "Emulator target: $target"

exec "$OPENPHONE_ROOT/scripts/build.sh" "$target"
