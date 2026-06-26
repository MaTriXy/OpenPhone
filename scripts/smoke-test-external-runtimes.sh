#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
hermes_root="${HERMES_ROOT:-/Users/adamcohenhillel/Developer/AMBIENT/hermes-agent}"
openclaw_root="${OPENCLAW_ROOT:-/Users/adamcohenhillel/Developer/AMBIENT/openclaw}"

"$root/scripts/check-assistant-java.sh"
bash -n "$root/scripts/smoke-test-openclaw-device-failures.sh"

ed25519_tmp="$(mktemp -d "${TMPDIR:-/tmp}/openphone-openclaw-ed25519.XXXXXX")"
trap 'rm -rf "$ed25519_tmp"' EXIT
mkdir -p "$ed25519_tmp/src/org/openphone/assistant/external" "$ed25519_tmp/classes"
cp "$root/overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenClawEd25519.java" \
  "$ed25519_tmp/src/org/openphone/assistant/external/OpenClawEd25519.java"
cp "$root/overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/ExternalRuntimeTransport.java" \
  "$ed25519_tmp/src/org/openphone/assistant/external/ExternalRuntimeTransport.java"
cat > "$ed25519_tmp/src/org/openphone/assistant/external/OpenClawEd25519VectorTest.java" <<'JAVA'
package org.openphone.assistant.external;

public final class OpenClawEd25519VectorTest {
  public static void main(String[] args) {
    byte[] seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
    OpenClawEd25519.KeyPairData key = OpenClawEd25519.fromSeed(seed);
    assertHex("public", key.publicKey,
        "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
    assertHex("signature", OpenClawEd25519.sign(new byte[0], seed),
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
        + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
  }

  private static void assertHex(String label, byte[] actual, String expected) {
    String got = hex(actual);
    if (!expected.equals(got)) {
      throw new AssertionError(label + " expected " + expected + " got " + got);
    }
  }

  private static byte[] hex(String value) {
    byte[] out = new byte[value.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  private static String hex(byte[] value) {
    char[] table = "0123456789abcdef".toCharArray();
    char[] out = new char[value.length * 2];
    int index = 0;
    for (byte b : value) {
      int v = b & 0xff;
      out[index++] = table[v >>> 4];
      out[index++] = table[v & 0x0f];
    }
    return new String(out);
  }
}
JAVA
cat > "$ed25519_tmp/src/org/openphone/assistant/external/ExternalRuntimeTransportTest.java" <<'JAVA'
package org.openphone.assistant.external;

public final class ExternalRuntimeTransportTest {
  public static void main(String[] args) {
    assertAllowed("wss://gateway.example/ws");
    assertAllowed("https://gateway.example/ws");
    assertAllowed("ws://127.0.0.1:8787");
    assertAllowed("http://localhost:8787");
    assertAllowed("ws://10.0.0.2/ws");
    assertAllowed("ws://172.16.0.2/ws");
    assertAllowed("ws://172.31.255.10/ws");
    assertAllowed("ws://192.168.1.50/ws");
    assertDenied("ws://8.8.8.8/ws");
    assertDenied("ws://172.32.0.2/ws");
    assertDenied("ws://gateway.example/ws");
    assertDenied("ftp://gateway.example/ws");
  }

  private static void assertAllowed(String url) {
    if (!ExternalRuntimeTransport.isAllowedWebSocketUrl(url)) {
      throw new AssertionError("expected allowed: " + url);
    }
  }

  private static void assertDenied(String url) {
    if (ExternalRuntimeTransport.isAllowedWebSocketUrl(url)) {
      throw new AssertionError("expected denied: " + url);
    }
  }
}
JAVA
javac --release 11 -d "$ed25519_tmp/classes" \
  "$ed25519_tmp/src/org/openphone/assistant/external/OpenClawEd25519.java" \
  "$ed25519_tmp/src/org/openphone/assistant/external/OpenClawEd25519VectorTest.java"
java -cp "$ed25519_tmp/classes" org.openphone.assistant.external.OpenClawEd25519VectorTest
javac --release 11 -d "$ed25519_tmp/classes" \
  "$ed25519_tmp/src/org/openphone/assistant/external/ExternalRuntimeTransport.java" \
  "$ed25519_tmp/src/org/openphone/assistant/external/ExternalRuntimeTransportTest.java"
java -cp "$ed25519_tmp/classes" org.openphone.assistant.external.ExternalRuntimeTransportTest

if [[ -x "$openclaw_root/node_modules/.bin/tsx" \
    && -f "$openclaw_root/packages/gateway-protocol/src/index.ts" ]]; then
  (
    cd "$openclaw_root"
    node_modules/.bin/tsx -e '
      import { validateConnectParams, formatValidationErrors } from "./packages/gateway-protocol/src/index.ts";
      const params = {
        minProtocol: 3,
        maxProtocol: 4,
        client: {
          id: "openclaw-android",
          displayName: "OpenPhone",
          version: "0.1",
          platform: "android",
          deviceFamily: "OpenPhone",
          modelIdentifier: "OpenPhone Pixel 9a",
          mode: "node",
          instanceId: "openphone-test-instance",
        },
        role: "node",
        scopes: [],
        caps: ["device", "notifications", "contacts", "calendar", "sms", "callLog", "screen", "apps", "openphone.ui"],
        commands: [
          "device.status",
          "device.info",
          "device.apps",
          "notifications.list",
          "contacts.search",
          "calendar.events",
          "sms.search",
          "callLog.search",
          "openphone.screen.get",
          "openphone.jobs.list",
          "notifications.open",
          "calendar.add",
          "calendar.update",
          "calendar.delete",
          "sms.draft",
          "sms.send",
          "calls.place",
          "openphone.app.open",
          "openphone.url.open",
          "openphone.ui.tap",
          "openphone.ui.tap_element",
          "openphone.ui.long_press",
          "openphone.ui.long_press_element",
          "openphone.ui.swipe",
          "openphone.ui.type_text",
          "openphone.input.press_key",
          "openphone.clipboard.set",
          "openphone.clipboard.paste",
          "openphone.share.text",
          "openphone.jobs.create",
          "openphone.jobs.stop",
        ],
        permissions: { "device.status": true, "openphone.ui.tap": true },
        auth: { token: "test-token" },
        device: {
          id: "a".repeat(64),
          publicKey: "A".repeat(43),
          signature: "B".repeat(86),
          signedAt: 1737264000000,
          nonce: "nonce-1",
        },
        locale: "en-US",
        userAgent: "openphone-assistant/0.1",
      };
      if (!validateConnectParams(params)) {
        console.error(formatValidationErrors(validateConnectParams.errors));
        process.exit(1);
      }
    '
  )
else
  echo "OpenClaw protocol schema smoke skipped (hydrate OPENCLAW_ROOT dependencies to enable)."
fi

python3 - <<'PY' "$root" "$hermes_root"
import ast
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
hermes_root = pathlib.Path(sys.argv[2])

java_required = [
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenPhoneToolBridge.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenClawRuntimeAdapter.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenClawEd25519.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenClawDeviceIdentity.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/ExternalRuntimeTransport.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/HermesRuntimeAdapter.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/ExternalRuntimeManager.java",
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/OpenPhoneSmokeControlReceiver.java",
]
missing = [str(path) for path in java_required if not path.exists()]
if missing:
    raise SystemExit("missing external runtime Java files: " + ", ".join(missing))

assistant_manifest = (
    root / "overlay/packages/apps/OpenPhoneAssistant/AndroidManifest.xml"
).read_text(encoding="utf-8")
for marker in (
    "OpenPhoneSmokeControlReceiver",
    "android.permission.DUMP",
):
    if marker not in assistant_manifest:
        raise SystemExit(f"OpenPhone assistant manifest missing smoke receiver marker: {marker}")

plan = (root / "Hermes_OpenClaw_Plan.md").read_text(encoding="utf-8")
for marker in (
    "Do not push this WIP to GitHub",
    "OpenClaw Ed25519 device identity generation and persistence",
    "shared WebSocket reconnect/backoff",
    "external mutating-tool confirmation queue",
    "OpenClaw receives final approval results",
    "Hermes receives final approval results",
    "Phone-Local Compute Tools",
    "local_screen_understanding",
):
    if marker not in plan:
        raise SystemExit(f"Hermes_OpenClaw_Plan.md missing marker: {marker}")

plugin = hermes_root / "plugins/platforms/openphone/adapter.py"
init = hermes_root / "plugins/platforms/openphone/__init__.py"
yaml = hermes_root / "plugins/platforms/openphone/plugin.yaml"
for path in (plugin, init, yaml):
    if not path.exists():
        raise SystemExit(f"missing Hermes OpenPhone plugin file: {path}")
ast.parse(plugin.read_text(encoding="utf-8"), filename=str(plugin))
ast.parse(init.read_text(encoding="utf-8"), filename=str(init))
plugin_text = plugin.read_text(encoding="utf-8")
for marker in (
    "openphone.presence.online",
    "openphone.confirmation.required",
    "_handle_event",
):
    if marker not in plugin_text:
        raise SystemExit(f"Hermes OpenPhone plugin missing event marker: {marker}")

openclaw_adapter = (
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenClawRuntimeAdapter.java"
).read_text(encoding="utf-8")
for command in (
    "notifications.open",
    "calendar.add",
    "calendar.update",
    "calendar.delete",
    "sms.draft",
    "sms.send",
    "calls.place",
    "openphone.local.screen_understanding",
    "openphone.ui.long_press",
    "openphone.ui.long_press_element",
    "openphone.ui.swipe",
    "openphone.input.press_key",
    "openphone.clipboard.set",
    "openphone.clipboard.paste",
    "openphone.share.text",
):
    if command not in openclaw_adapter:
        raise SystemExit(f"OpenClaw adapter missing planned command mapping: {command}")

bridge = (
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/OpenPhoneToolBridge.java"
).read_text(encoding="utf-8")
for marker in (
    "DEFAULT_READ_ONLY_TOOLS",
    "DEFAULT_MUTATING_TOOLS",
    '"local_screen_understanding"',
    "external_runtime_default_deny",
    "tool_not_granted",
    "mCompletedByIdempotencyKey",
    "MAX_COMPLETED_IDEMPOTENCY_RESULTS",
    "scheduleConfirmationTimeout",
    "onExternalConfirmationTimedOut",
    "ExternalRuntimeResult.timeout",
):
    if marker not in bridge:
        raise SystemExit(f"OpenPhoneToolBridge missing confirmation safety marker: {marker}")
for ungranted in (
    '"browser_fetch_page"',
    '"watch_screen"',
    '"wait"',
    '"finish_task"',
    '"fail_task"',
):
    if ungranted in bridge:
        raise SystemExit(f"OpenPhoneToolBridge grant lists unexpectedly include: {ungranted}")

manager = (
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/ExternalRuntimeManager.java"
).read_text(encoding="utf-8")
hermes_adapter = (
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/HermesRuntimeAdapter.java"
).read_text(encoding="utf-8")
transport = (
    root / "overlay/packages/apps/OpenPhoneAssistant/src/org/openphone/assistant/external/ExternalRuntimeTransport.java"
).read_text(encoding="utf-8")
for marker in (
    "isAllowedWebSocketUrl",
    "isLocalOrPrivateHost",
    '"wss"',
    '"ws"',
):
    if marker not in transport:
        raise SystemExit(f"ExternalRuntimeTransport missing marker: {marker}")
for marker in (
    "requestOpenClawAttention",
    "openphone.confirmation.required",
    "sendEventToRuntime",
    "openphone.presence.offline",
    "aggregateStatusLocked",
    "manager_status",
    "updated_at_ms",
    "insecure_transport_denied",
    "auth_failed",
    "degraded",
):
    if marker not in manager:
        raise SystemExit(f"ExternalRuntimeManager missing event marker: {marker}")
for marker in (
    "requestAttention",
    "chat.subscribe",
    "openphone.attention.requested",
    "handleChatEvent",
    "handleAgentEvent",
    "ExternalRuntimeCallback",
):
    if marker not in openclaw_adapter:
        raise SystemExit(f"OpenClaw adapter missing attention marker: {marker}")
for marker in (
    "openphone.presence.online",
    "presencePayload",
    "local_screen_understanding",
):
    if marker not in openclaw_adapter:
        raise SystemExit(f"OpenClaw adapter missing event marker: {marker}")
    if marker not in hermes_adapter:
        raise SystemExit(f"Hermes adapter missing event marker: {marker}")

secret_patterns = [
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"ASIA[0-9A-Z]{16}"),
    re.compile(r"sk-(?:proj-)?[A-Za-z0-9_-]{20,}"),
    re.compile(r"ghp_[A-Za-z0-9_]{30,}"),
    re.compile(r"github_pat_[A-Za-z0-9_]{20,}"),
    re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"),
    re.compile(r"BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY"),
    re.compile(r"aws_secret_access_key\s*=", re.I),
    re.compile(r"ec2-\d[\w.-]*\.compute[\w.-]*\.amazonaws\.com", re.I),
]
release_scan_files = [
    root / "Hermes_OpenClaw_Plan.md",
    root / "scripts/smoke-test-external-runtimes.sh",
    root / "scripts/smoke-test-openclaw-device-failures.sh",
    *java_required,
    hermes_root / "plugins/platforms/openphone/adapter.py",
    hermes_root / "plugins/platforms/openphone/__init__.py",
    hermes_root / "plugins/platforms/openphone/plugin.yaml",
    hermes_root / "tests/plugins/platforms/openphone/test_openphone_adapter.py",
]
for path in release_scan_files:
    text = path.read_text(encoding="utf-8")
    for pattern in secret_patterns:
        match = pattern.search(text)
        if match:
            raise SystemExit(
                f"possible secret or private deployment artifact in {path}: {match.group(0)[:32]}"
            )
print("external runtime smoke test passed")
PY
