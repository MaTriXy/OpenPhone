#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/pull-latest-trajectory.sh [--output-dir <dir>]

Pulls the newest assistant trajectory zip exported by Advanced -> Export Trace
from /sdcard/Download/OpenPhone, validates it, and unpacks it for inspection.

Options:
  --output-dir <dir>   Defaults to .worktree/evals/latest-trajectory.
  -h, --help           Show this help.
EOF
}

output_dir="$root/.worktree/evals/latest-trajectory"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir)
      [[ $# -ge 2 ]] || die "--output-dir requires a value"
      output_dir="$2"
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

need_cmd adb
need_cmd unzip

adb wait-for-device >/dev/null
latest="$(
  adb shell 'ls -t /sdcard/Download/OpenPhone/*.zip 2>/dev/null | head -1' |
    tr -d '\r'
)"
[[ -n "$latest" ]] || die "no exported trajectory zip found in /sdcard/Download/OpenPhone"

mkdir -p "$output_dir"
zip_path="$output_dir/openphone-trajectory.zip"
rm -f "$zip_path"
rm -rf "$output_dir/unzipped"

adb pull "$latest" "$zip_path" >/dev/null
"$root/scripts/validate-trajectory-export.sh" "$zip_path"

mkdir -p "$output_dir/unzipped"
unzip -q "$zip_path" -d "$output_dir/unzipped"

printf 'Pulled: %s\n' "$latest"
printf 'Saved: %s\n' "$zip_path"
find "$output_dir/unzipped" -maxdepth 2 -type f -print
