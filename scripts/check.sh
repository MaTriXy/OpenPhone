#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

required=(
  README.md
  SPEC.md
  COMMERCIAL.md
  CONTRIBUTING.md
  LICENSE
  LICENSE.noncommercial
  NOTICE
  THIRD_PARTY_NOTICES.md
  docs/ARCHITECTURE.md
  docs/BUILD.md
  docs/BRINGUP_LOG.md
  docs/CAPABILITIES.md
  docs/DEVICE_SUPPORT.md
  docs/FRAMEWORK_PLAN.md
  docs/IMPLEMENTATION_STATUS.md
  docs/LICENSING.md
  docs/V1_AI_PHONE_PLAN.md
  docs/contracts/action-request.schema.json
  docs/contracts/agent-task.schema.json
  docs/contracts/audit-event.schema.json
  docs/contracts/screen-context.schema.json
  devices/MATRIX.md
  devices/tegu.md
  manifests/openphone.xml
  overlay/vendor/openphone/AndroidProducts.mk
  overlay/vendor/openphone/products/openphone_common.mk
  overlay/vendor/openphone/products/openphone_arm64.mk
  overlay/vendor/openphone/products/openphone_tegu.mk
  overlay/packages/apps/OpenPhoneAssistant/Android.bp
  overlay/packages/apps/OpenPhoneAssistant/AndroidManifest.xml
  overlay/packages/apps/OpenPhoneAssistant/LICENSE
  overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/IOpenPhoneAssistant.aidl
  patches/system_sepolicy/0001-OpenPhone-label-agent-manager-service.patch
)

for file in "${required[@]}"; do
  [[ -f "$root/$file" ]] || {
    printf 'missing required file: %s\n' "$file" >&2
    exit 1
  }
done

for script in "$root"/scripts/*.sh; do
  bash -n "$script"
done

if command -v xmllint >/dev/null 2>&1; then
  xmllint --noout "$root/manifests/openphone.xml"
  xmllint --noout "$root/overlay/packages/apps/OpenPhoneAssistant/AndroidManifest.xml"
  xmllint --noout "$root/overlay/vendor/openphone/config/privapp-permissions-openphone.xml"
  xmllint --noout "$root/overlay/vendor/openphone/config/sysconfig-openphone.xml"
fi

if command -v python3 >/dev/null 2>&1; then
  python3 - <<'PY' "$root"
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
scan_roots = [
    root / "docs",
    root / "overlay",
]
for scan_root in scan_roots:
    for path in sorted(scan_root.rglob("*.json")):
        with path.open("r", encoding="utf-8") as handle:
            json.load(handle)
PY
fi

if grep -R "SPDX-license-identifier-Apache-2.0" \
    "$root/overlay/vendor/openphone" \
    "$root/overlay/packages/apps/OpenPhoneAssistant" >/dev/null 2>&1; then
  printf 'OpenPhone-owned overlay modules must not be marked Apache-2.0\n' >&2
  exit 1
fi

printf 'OpenPhone repo checks passed.\n'
