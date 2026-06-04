#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/generate-app-policy-override.sh \
    --package <package> \
    --capability <capability> [--capability <capability> ...] \
    --decision <allow|confirm|explicit_confirm|deny> \
    --reason <reason> \
    [--match <exact|prefix>] \
    [--output <file>] \
    [--install-adb]

Generates a versioned OpenPhone app-policy override JSON payload suitable for
Settings.Secure key `openphone_app_policy_overrides`. With --install-adb, the
payload is written to a connected development device with:

  adb shell settings put secure openphone_app_policy_overrides '<json>'
EOF
}

package_name=""
match="exact"
decision=""
reason=""
output=""
install_adb=false
capabilities=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package)
      package_name="${2:-}"
      shift 2
      ;;
    --match)
      match="${2:-}"
      shift 2
      ;;
    --capability)
      capabilities+=("${2:-}")
      shift 2
      ;;
    --decision)
      decision="${2:-}"
      shift 2
      ;;
    --reason)
      reason="${2:-}"
      shift 2
      ;;
    --output)
      output="${2:-}"
      shift 2
      ;;
    --install-adb)
      install_adb=true
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

if [[ -z "$package_name" || ${#capabilities[@]} -eq 0 || -z "$decision" || -z "$reason" ]]; then
  usage >&2
  exit 2
fi

case "$match" in
  exact|prefix) ;;
  *) printf 'invalid match: %s\n' "$match" >&2; exit 2 ;;
esac

case "$decision" in
  allow|confirm|explicit_confirm|deny) ;;
  *) printf 'invalid decision: %s\n' "$decision" >&2; exit 2 ;;
esac

payload="$(
  python3 - <<'PY' "$package_name" "$match" "$decision" "$reason" "${capabilities[@]}"
import json
import sys

package_name, match, decision, reason, *capabilities = sys.argv[1:]
payload = {
    "version": 1,
    "default_decision": "inherit",
    "package_overrides": [
        {
            "package": package_name,
            "match": match,
            "capabilities": capabilities,
            "decision": decision,
            "reason": reason,
        }
    ],
}
print(json.dumps(payload, separators=(",", ":")))
PY
)"

if [[ -n "$output" ]]; then
  mkdir -p "$(dirname "$output")"
  printf '%s\n' "$payload" >"$output"
else
  printf '%s\n' "$payload"
fi

if [[ "$install_adb" == true ]]; then
  command -v adb >/dev/null 2>&1 || {
    printf 'adb is required for --install-adb\n' >&2
    exit 1
  }
  adb shell settings put secure openphone_app_policy_overrides "$payload"
fi
