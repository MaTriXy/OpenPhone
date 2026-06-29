#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/smoke-test-openclaw-runtime.sh

Runs a live OpenClaw/OpenPhone runtime smoke against an ADB-connected
OpenPhone device or emulator and an already-running OpenClaw gateway.

Required environment:
  OPENPHONE_OPENCLAW_TOKEN      Gateway token. OPENCLAW_GATEWAY_TOKEN is also accepted.

Optional environment:
  OPENPHONE_OPENCLAW_URL        Gateway URL from the host. Default: ws://127.0.0.1:18791
  OPENPHONE_OPENCLAW_DEVICE_ID  Phone runtime device id. Default: openphone-live-smoke
  OPENPHONE_OPENCLAW_LABEL      Runtime label written to Android settings. Default: OpenClaw
  OPENPHONE_OPENCLAW_CONFIGURE_PHONE
                                Set to 0 to skip Android Settings.Secure writes. Default: 1
  OPENPHONE_OPENCLAW_ADB_REVERSE
                                Set to 0 to skip adb reverse. Default: auto for loopback URLs
  OPENPHONE_OPENCLAW_AUTO_APPROVE_NODE
                                Approve pending OpenClaw node command surface. Default: 1
  OPENPHONE_OPENCLAW_VOICE      Set to 1 to also smoke volume voice -> OpenClaw -> Android TTS.
  OPENPHONE_OPENCLAW_VOICE_TEXT Text for the voice smoke. Default is underscore-safe text.
  ANDROID_SERIAL                adb serial, if multiple devices are attached
                                (for example, emulator-5584).

What this proves:
  - Phone runtime connects to OpenClaw as an approved node.
  - OpenClaw exposes approved OpenPhone commands through node.list.
  - OpenClaw can invoke openphone.screen.get and receive screenshot/accessibility data.
  - Optional: volume_voice attention reaches OpenClaw and produces Android service TTS.

This script does not start OpenClaw. Start the gateway first, install the
OpenPhone OpenClaw plugin, then run this script.
EOF
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
  "")
    ;;
  *)
    usage >&2
    die "unknown argument: $1"
    ;;
esac

need_cmd adb
need_cmd node

gateway_url="${OPENPHONE_OPENCLAW_URL:-ws://127.0.0.1:18791}"
gateway_token="${OPENPHONE_OPENCLAW_TOKEN:-${OPENCLAW_GATEWAY_TOKEN:-}}"
device_id="${OPENPHONE_OPENCLAW_DEVICE_ID:-openphone-live-smoke}"
runtime_label="${OPENPHONE_OPENCLAW_LABEL:-OpenClaw}"
configure_phone="${OPENPHONE_OPENCLAW_CONFIGURE_PHONE:-1}"
auto_approve_node="${OPENPHONE_OPENCLAW_AUTO_APPROVE_NODE:-1}"
voice_smoke="${OPENPHONE_OPENCLAW_VOICE:-0}"
voice_text="${OPENPHONE_OPENCLAW_VOICE_TEXT:-Please_reply_with_hello_voice_smoke}"

[[ -n "$gateway_token" ]] || die "set OPENPHONE_OPENCLAW_TOKEN or OPENCLAW_GATEWAY_TOKEN"

adb_cmd=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb_cmd+=(-s "$ANDROID_SERIAL")
fi

extract_url_port() {
  local url="$1"
  node -e '
    const url = process.argv[1];
    try {
      const parsed = new URL(url);
      const port = parsed.port || (parsed.protocol === "wss:" ? "443" : "80");
      process.stdout.write(port);
    } catch {
      process.exit(1);
    }
  ' "$url"
}

is_loopback_url() {
  local url="$1"
  node -e '
    const url = process.argv[1];
    try {
      const host = new URL(url).hostname;
      process.exit(host === "127.0.0.1" || host === "localhost" || host === "::1" ? 0 : 1);
    } catch {
      process.exit(1);
    }
  ' "$url"
}

adb_wait_booted() {
  "${adb_cmd[@]}" wait-for-device >/dev/null
  while [[ "$("${adb_cmd[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    sleep 2
  done
}

adb_wait_booted
"${adb_cmd[@]}" root >/dev/null 2>&1 || true
adb_wait_booted

port="$(extract_url_port "$gateway_url")" || die "could not parse OPENPHONE_OPENCLAW_URL=$gateway_url"
adb_reverse="${OPENPHONE_OPENCLAW_ADB_REVERSE:-auto}"
if [[ "$adb_reverse" == "auto" ]]; then
  if is_loopback_url "$gateway_url"; then
    adb_reverse="1"
  else
    adb_reverse="0"
  fi
fi

if [[ "$adb_reverse" == "1" ]]; then
  info "adb reverse tcp:$port tcp:$port"
  "${adb_cmd[@]}" reverse "tcp:$port" "tcp:$port" >/dev/null
fi

if [[ "$configure_phone" == "1" ]]; then
  info "configuring OpenClaw runtime settings on phone"
  "${adb_cmd[@]}" shell settings put secure openphone_runtimes_enabled 1
  "${adb_cmd[@]}" shell settings put secure openphone_runtime_openclaw_enabled 1
  "${adb_cmd[@]}" shell settings put secure openphone_runtime_openclaw_url "$gateway_url"
  "${adb_cmd[@]}" shell settings put secure openphone_runtime_openclaw_token "$gateway_token"
  "${adb_cmd[@]}" shell settings put secure openphone_runtime_openclaw_device_id "$device_id"
  "${adb_cmd[@]}" shell settings put secure openphone_runtime_openclaw_label "$runtime_label"
fi

info "reloading OpenPhone runtimes"
"${adb_cmd[@]}" shell am startservice \
  -n org.openphone.assistant/.OpenPhoneAssistantService \
  -a org.openphone.assistant.action.RELOAD_RUNTIMES >/dev/null

OPENPHONE_OPENCLAW_URL="$gateway_url" \
OPENPHONE_OPENCLAW_TOKEN="$gateway_token" \
OPENPHONE_OPENCLAW_AUTO_APPROVE_NODE="$auto_approve_node" \
node --input-type=module - <<'NODE'
const gatewayUrl = process.env.OPENPHONE_OPENCLAW_URL;
const gatewayToken = process.env.OPENPHONE_OPENCLAW_TOKEN;
const autoApproveNode = process.env.OPENPHONE_OPENCLAW_AUTO_APPROVE_NODE !== "0";

if (typeof WebSocket !== "function") {
  throw new Error("this Node runtime does not expose global WebSocket");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function withTimeout(promise, label, timeoutMs) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label} timed out`)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

const ws = new WebSocket(gatewayUrl);
const pending = new Map();

ws.addEventListener("message", (event) => {
  let frame;
  try {
    frame = JSON.parse(event.data);
  } catch {
    return;
  }
  if (frame.type === "res" && pending.has(frame.id)) {
    pending.get(frame.id)(frame);
    pending.delete(frame.id);
  }
});

await withTimeout(
  new Promise((resolve, reject) => {
    ws.addEventListener("open", resolve, { once: true });
    ws.addEventListener("error", () => reject(new Error("websocket error")), { once: true });
  }),
  "websocket open",
  10_000,
);

function request(method, params = {}, timeoutMs = 20_000) {
  const id = `${method}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const response = new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error(`${method} timed out`));
    }, timeoutMs);
    pending.set(id, (frame) => {
      clearTimeout(timer);
      resolve(frame);
    });
  });
  ws.send(JSON.stringify({ type: "req", id, method, params }));
  return response;
}

const connect = await request("connect", {
  minProtocol: 4,
  maxProtocol: 4,
  client: {
    id: "gateway-client",
    version: "0.1.0",
    platform: process.platform,
    mode: "backend",
    displayName: "OpenPhone live runtime smoke",
  },
  caps: [],
  commands: [],
  role: "operator",
  scopes: ["operator.read", "operator.write", "operator.pairing"],
  auth: { token: gatewayToken },
});
if (!connect.ok) {
  throw new Error(`connect failed: ${JSON.stringify(connect.error || connect)}`);
}

async function listNodes() {
  const result = await request("node.list", {}, 10_000);
  if (!result.ok) {
    throw new Error(`node.list failed: ${JSON.stringify(result.error || result)}`);
  }
  return result.payload?.nodes || [];
}

async function listPendingPairRequests() {
  const result = await request("node.pair.list", {}, 10_000);
  if (!result.ok) {
    throw new Error(`node.pair.list failed: ${JSON.stringify(result.error || result)}`);
  }
  return result.payload?.pending || [];
}

async function findOpenPhoneNode() {
  const deadline = Date.now() + 30_000;
  let lastNodes = [];
  while (Date.now() < deadline) {
    lastNodes = await listNodes();
    const node = lastNodes.find((entry) => {
      if (entry?.connected !== true) {
        return false;
      }
      if (entry.clientId === "openclaw-android" && entry.platform === "android") {
        return true;
      }
      return entry.deviceFamily === "OpenPhone" ||
        (Array.isArray(entry.commands) && entry.commands.includes("openphone.screen.get"));
    });
    if (node) {
      return node;
    }
    await sleep(1_000);
  }
  throw new Error(`no connected OpenPhone node found: ${JSON.stringify(lastNodes)}`);
}

function hasOpenPhoneCommandSurface(node) {
  return Array.isArray(node?.commands) && node.commands.includes("openphone.screen.get");
}

function isApprovedOpenPhoneNode(node) {
  return node?.approvalState === "approved" || hasOpenPhoneCommandSurface(node);
}

async function waitForCommandSurface(nodeId) {
  const deadline = Date.now() + 15_000;
  let latest = null;
  while (Date.now() < deadline) {
    const nodes = await listNodes();
    latest = nodes.find((entry) => entry?.nodeId === nodeId) || null;
    if (isApprovedOpenPhoneNode(latest) && hasOpenPhoneCommandSurface(latest)) {
      return latest;
    }
    await sleep(1_000);
  }
  throw new Error(`OpenPhone node command surface is not ready: ${JSON.stringify({
    nodeId,
    approvalState: latest?.approvalState,
    commands: latest?.commands,
    pendingRequestId: latest?.pendingRequestId,
  })}`);
}

async function findPendingRequestId(nodeId) {
  const deadline = Date.now() + 15_000;
  let latestNode = null;
  let latestPending = [];
  while (Date.now() < deadline) {
    const nodes = await listNodes();
    latestNode = nodes.find((entry) => entry?.nodeId === nodeId) || null;
    if (latestNode?.pendingRequestId) {
      return latestNode.pendingRequestId;
    }

    latestPending = await listPendingPairRequests();
    const pending = latestPending.find((entry) => entry?.nodeId === nodeId);
    if (pending?.requestId) {
      return pending.requestId;
    }

    await sleep(1_000);
  }
  throw new Error(`OpenPhone node is connected but has no pending approval request: ${JSON.stringify({
    nodeId,
    approvalState: latestNode?.approvalState,
    pendingCount: latestPending.length,
  })}`);
}

let node = await findOpenPhoneNode();
if (!isApprovedOpenPhoneNode(node)) {
  if (autoApproveNode) {
    const requestId = node.pendingRequestId || await findPendingRequestId(node.nodeId);
    const approved = await request("node.pair.approve", { requestId });
    if (!approved.ok) {
      throw new Error(`node.pair.approve failed: ${JSON.stringify(approved.error || approved)}`);
    }
    await sleep(1_000);
    node = await findOpenPhoneNode();
  }
  if (!isApprovedOpenPhoneNode(node)) {
    throw new Error(
      `OpenPhone node is connected but not approved: ${JSON.stringify({
        nodeId: node.nodeId,
        approvalState: node.approvalState,
        pendingRequestId: node.pendingRequestId,
      })}`,
    );
  }
}
node = await waitForCommandSurface(node.nodeId);

const invoke = await request("node.invoke", {
  nodeId: node.nodeId,
  command: "openphone.screen.get",
  params: {
    include_screenshot: true,
    include_accessibility: true,
  },
  timeoutMs: 20_000,
  idempotencyKey: `openphone-live-runtime-smoke-${Date.now()}`,
}, 30_000);
if (!invoke.ok) {
  throw new Error(`node.invoke failed: ${JSON.stringify(invoke.error || invoke)}`);
}

const result = invoke.payload?.payload?.result;
const screenshot = result?.screenshot?.data;
const summary = {
  nodeId: node.nodeId,
  approvalState: node.approvalState,
  state: result?.state,
  captureMode: result?.capture_mode,
  visibleTextCount: Array.isArray(result?.visible_text) ? result.visible_text.length : 0,
  interactiveElementCount: Array.isArray(result?.interactive_elements)
    ? result.interactive_elements.length
    : 0,
  screenshotChars: typeof screenshot === "string" ? screenshot.length : 0,
};

if (
  summary.state !== "screen.captured.screenshot_jpeg_base64" ||
  summary.screenshotChars < 1000
) {
  throw new Error(`screen proof did not include screenshot data: ${JSON.stringify(summary)}`);
}

console.log(`[ok] OpenClaw node approved: ${summary.nodeId}`);
console.log(`[ok] openphone.screen.get screenshot chars: ${summary.screenshotChars}`);
console.log(`[ok] visible text: ${summary.visibleTextCount}, interactive elements: ${summary.interactiveElementCount}`);
ws.close();
NODE

if [[ "$voice_smoke" == "1" ]]; then
  info "running volume_voice attention smoke"
  "${adb_cmd[@]}" shell input keyevent HOME >/dev/null || true
  "${adb_cmd[@]}" logcat -c
  "${adb_cmd[@]}" shell am startservice \
    -n org.openphone.assistant/.OpenPhoneAssistantService \
    -a org.openphone.assistant.action.REQUEST_RUNTIME_ATTENTION \
    --es org.openphone.assistant.extra.RUNTIME_ATTENTION_RUNTIME openclaw \
    --es org.openphone.assistant.extra.RUNTIME_ATTENTION_TEXT "$voice_text" \
    --es org.openphone.assistant.extra.RUNTIME_ATTENTION_SOURCE volume_voice \
    --es org.openphone.assistant.extra.RUNTIME_ATTENTION_AUTONOMY observe_only \
    --ez org.openphone.assistant.extra.RUNTIME_ATTENTION_INCLUDE_SCREEN false >/dev/null
  sleep "${OPENPHONE_OPENCLAW_VOICE_WAIT_SECONDS:-25}"
  logs="$("${adb_cmd[@]}" logcat -d -s OpenPhoneAssistant:I OpenPhoneRuntime:I OpenPhoneOpenClaw:I OpenPhoneOpenClaw:W TextToSpeech:I TextToSpeech:W)"
  printf '%s\n' "$logs" | grep -F "runtime attention sent runtime=openclaw source=volume_voice" >/dev/null \
    || die "voice smoke did not send volume_voice attention"
  printf '%s\n' "$logs" | grep -F "runtime message runtime=openclaw terminal=true" >/dev/null \
    || die "voice smoke did not receive terminal OpenClaw message"
  printf '%s\n' "$logs" | grep -F "runtime service TTS speaking" >/dev/null \
    || die "voice smoke did not observe Android service TTS"
  printf '%s\n' "$logs" | grep -E "runtime attention sent|runtime message runtime=openclaw terminal=true|runtime service TTS speaking" || true
  info "volume_voice OpenClaw TTS smoke passed"
fi

info "OpenClaw runtime smoke passed"
