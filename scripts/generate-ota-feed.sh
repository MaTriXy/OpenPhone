#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage:
  scripts/generate-ota-feed.sh \
    --version <version> \
    --channel <channel> \
    --device <codename> \
    --artifact <ota.zip> \
    --base-url <url-prefix> \
    --release-notes-url <url> \
    --output <feed.json> \
    [--requires-wipe]

Generates the first OpenPhone OTA feed contract for future updater clients.
EOF
}

version=""
channel=""
device=""
artifact=""
base_url=""
release_notes_url=""
output=""
requires_wipe=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      version="${2:-}"
      shift 2
      ;;
    --channel)
      channel="${2:-}"
      shift 2
      ;;
    --device)
      device="${2:-}"
      shift 2
      ;;
    --artifact)
      artifact="${2:-}"
      shift 2
      ;;
    --base-url)
      base_url="${2:-}"
      shift 2
      ;;
    --release-notes-url)
      release_notes_url="${2:-}"
      shift 2
      ;;
    --output)
      output="${2:-}"
      shift 2
      ;;
    --requires-wipe)
      requires_wipe=true
      shift
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

if [[ -z "$version" || -z "$channel" || -z "$device" || -z "$artifact" || -z "$base_url" || -z "$release_notes_url" || -z "$output" ]]; then
  usage >&2
  exit 2
fi

[[ -f "$artifact" ]] || die "missing OTA artifact: $artifact"
case "$artifact" in
  *.zip) ;;
  *) die "OTA artifact must be a .zip: $artifact" ;;
esac

mkdir -p "$(dirname "$output")"
artifact="$(cd "$(dirname "$artifact")" && pwd)/$(basename "$artifact")"
filename="$(basename "$artifact")"
size="$(wc -c < "$artifact" | tr -d ' ')"
sha256="$(file_sha256 "$artifact")"
base_url="${base_url%/}"

python3 - <<'PY' \
  "$version" \
  "$channel" \
  "$device" \
  "$filename" \
  "$base_url/$filename" \
  "$size" \
  "$sha256" \
  "$requires_wipe" \
  "$release_notes_url" \
  "$output"
import datetime
import json
import pathlib
import sys

version, channel, device, filename, url, size, sha256, requires_wipe, release_notes_url, output = sys.argv[1:]
feed = {
    "schema_version": 1,
    "generated_at": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "channel": channel,
    "device": device,
    "updates": [
        {
            "version": version,
            "filename": filename,
            "url": url,
            "size": int(size),
            "sha256": sha256,
            "requires_wipe": requires_wipe == "true",
            "release_notes_url": release_notes_url,
        }
    ],
}
pathlib.Path(output).write_text(json.dumps(feed, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

printf 'OTA feed written to %s\n' "$output"
