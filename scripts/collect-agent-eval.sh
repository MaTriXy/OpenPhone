#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage:
  scripts/collect-agent-eval.sh \
    --eval-id <id> \
    --goal <goal> \
    --status <pass|fail|blocked|needs_review> \
    --summary <summary> \
    --provider <provider> \
    --model <model> \
    --transport <local|direct_provider_dev|openphone_broker> \
    [--failure-reason <reason>] \
    [--notes <notes>] \
    [--output-dir <dir>]

Pulls the latest exported OpenPhone trajectory zip and audit JSON from the
connected device's Downloads/OpenPhone directory, creates an agent eval report,
and validates the report plus evidence bundle.

Run Advanced -> Export Trace and Advanced -> Export Audit in the assistant
before running this script.
EOF
}

need_cmd adb

eval_id=""
goal=""
status=""
summary=""
provider=""
model=""
transport=""
failure_reason=""
notes=""
output_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --eval-id)
      eval_id="${2:-}"
      shift 2
      ;;
    --goal)
      goal="${2:-}"
      shift 2
      ;;
    --status)
      status="${2:-}"
      shift 2
      ;;
    --summary)
      summary="${2:-}"
      shift 2
      ;;
    --provider)
      provider="${2:-}"
      shift 2
      ;;
    --model)
      model="${2:-}"
      shift 2
      ;;
    --transport)
      transport="${2:-}"
      shift 2
      ;;
    --failure-reason)
      failure_reason="${2:-}"
      shift 2
      ;;
    --notes)
      notes="${2:-}"
      shift 2
      ;;
    --output-dir)
      output_dir="${2:-}"
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

if [[ -z "$eval_id" || -z "$goal" || -z "$status" || -z "$summary" \
    || -z "$provider" || -z "$model" || -z "$transport" ]]; then
  usage >&2
  exit 2
fi

case "$status" in
  pass|fail|blocked|needs_review) ;;
  *) die "invalid status: $status" ;;
esac

case "$transport" in
  local|direct_provider_dev|openphone_broker) ;;
  *) die "invalid transport: $transport" ;;
esac

if [[ "$status" == "fail" || "$status" == "blocked" ]]; then
  [[ -n "$failure_reason" ]] || die "--failure-reason is required for fail/blocked"
fi

adb_cmd() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" "$@"
  else
    adb "$@"
  fi
}

manifest_value() {
  local attr="$1"
  sed -n "s/.*android:${attr}=\"\\([^\"]*\\)\".*/\\1/p" \
    "$root/overlay/packages/apps/OpenPhoneAssistant/AndroidManifest.xml" |
    head -1
}

latest_remote_file() {
  local pattern="$1"
  adb_cmd shell "ls -1t /sdcard/Download/OpenPhone/$pattern 2>/dev/null | head -1" |
    tr -d '\r'
}

state="$(adb_cmd get-state 2>/dev/null || true)"
[[ "$state" == "device" ]] || die "ADB state is '$state', expected 'device'"

if ! shell_probe="$(adb_cmd shell 'echo shell-ok' 2>&1)"; then
  printf '%s\n' "$shell_probe" >&2
  die "ADB shell is not usable. Finish onboarding, enable USB debugging, and accept the host prompt."
fi
[[ "$shell_probe" == *"shell-ok"* ]] || die "ADB shell probe returned unexpected output: $shell_probe"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ -z "$output_dir" ]]; then
  output_dir="$root/.worktree/evals/$eval_id-$timestamp"
fi
mkdir -p "$output_dir"

trajectory_remote="$(latest_remote_file 'openphone-trajectory*.zip')"
audit_remote="$(latest_remote_file 'openphone-audit*.json')"
[[ -n "$trajectory_remote" ]] || die "no exported trajectory zip found in /sdcard/Download/OpenPhone"
[[ -n "$audit_remote" ]] || die "no exported audit JSON found in /sdcard/Download/OpenPhone"

trajectory_file="$(basename "$trajectory_remote")"
audit_file="$(basename "$audit_remote")"
adb_cmd pull "$trajectory_remote" "$output_dir/$trajectory_file" >/dev/null
adb_cmd pull "$audit_remote" "$output_dir/$audit_file" >/dev/null

codename="$(adb_cmd shell 'getprop ro.product.device' | tr -d '\r')"
sku="$(adb_cmd shell 'getprop ro.boot.hardware.sku' | tr -d '\r')"
slot="$(adb_cmd shell 'getprop ro.boot.slot_suffix' | tr -d '\r')"
openphone_version="$(adb_cmd shell 'getprop ro.openphone.version' | tr -d '\r')"
assistant_version_code="$(manifest_value versionCode)"
assistant_version_name="$(manifest_value versionName)"
git_commit="$(git -C "$root" rev-parse --short HEAD 2>/dev/null || true)"
cloud=false
if [[ "$transport" == "direct_provider_dev" || "$transport" == "openphone_broker" ]]; then
  cloud=true
fi

python3 - <<'PY' \
  "$output_dir/agent-eval.json" \
  "$eval_id" \
  "$goal" \
  "$codename" \
  "$sku" \
  "$slot" \
  "$openphone_version" \
  "$assistant_version_code" \
  "$assistant_version_name" \
  "$git_commit" \
  "$provider" \
  "$model" \
  "$transport" \
  "$cloud" \
  "$status" \
  "$summary" \
  "$failure_reason" \
  "$trajectory_file" \
  "$audit_file" \
  "$notes"
import json
import pathlib
import sys

(
    output,
    eval_id,
    goal,
    codename,
    sku,
    slot,
    openphone_version,
    assistant_version_code,
    assistant_version_name,
    git_commit,
    provider,
    model,
    transport,
    cloud,
    status,
    summary,
    failure_reason,
    trajectory,
    audit,
    notes,
) = sys.argv[1:]

result = {
    "status": status,
    "summary": summary,
}
if failure_reason:
    result["failure_reason"] = failure_reason

payload = {
    "schema": "openphone.agent_eval_report.v1",
    "eval_id": eval_id,
    "goal": goal,
    "device": {
        "codename": codename,
        "sku": sku,
        "serial_redacted": True,
        "slot": slot,
    },
    "build": {
        "openphone_version": openphone_version or "unknown",
        "assistant_version_code": int(assistant_version_code),
        "assistant_version_name": assistant_version_name,
    },
    "model": {
        "provider": provider,
        "name": model,
        "transport": transport,
        "cloud": cloud == "true",
    },
    "result": result,
    "evidence": {
        "trajectory": trajectory,
        "audit": audit,
        "notes": notes,
    },
}
if git_commit:
    payload["build"]["git_commit"] = git_commit

pathlib.Path(output).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY

"$root/scripts/validate-agent-eval-report.sh" \
  "$output_dir/agent-eval.json" \
  "$output_dir"

printf 'Agent eval evidence collected in %s\n' "$output_dir"
