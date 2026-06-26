#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "$root/scripts/common.sh"

usage() {
  cat <<'EOF'
Usage: scripts/smoke-test-openclaw-device-failures.sh

Runs OpenClaw/OpenPhone external-runtime failure-mode checks against the
USB-connected Pixel. This script modifies OpenPhone external-runtime
Settings.Secure keys, starts local WebSocket shims, uses adb reverse, and
restores the original settings on exit.

Required:
  - adb device connected and authorized
  - OpenPhoneAssistant APK containing the current external-runtime code
  - Python websockets package available to python3

Covered:
  - gateway offline status
  - bad OpenClaw token/auth_failed status
  - OpenClaw attention request emits chat.subscribe and openphone.attention.requested
  - OpenClaw final chat fanout reaches the phone runtime callback
  - unknown OpenClaw command denial
  - user-approved confirmation result completes the original OpenClaw invoke
  - user-denied confirmation result
  - repeated idempotency key returns cached final result
  - confirmation timeout result
  - public plaintext ws:// transport is denied
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
need_cmd python3

python3 - <<'PY'
try:
    import websockets  # noqa: F401
except Exception as exc:
    raise SystemExit(f"python3 websockets package is required: {exc}")
PY

adb_cmd=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb_cmd+=( -s "$ANDROID_SERIAL" )
fi

"${adb_cmd[@]}" wait-for-device >/dev/null
state="$("${adb_cmd[@]}" get-state 2>/dev/null || true)"
[[ "$state" == "device" ]] || die "ADB state is '$state', expected 'device'"

python3 - <<'PY'
from __future__ import annotations

import asyncio
import json
import os
import shlex
import socket
import subprocess
import sys
import time
from contextlib import suppress
from typing import Any

import websockets

ADB = ["adb"]
if os.environ.get("ANDROID_SERIAL"):
    ADB += ["-s", os.environ["ANDROID_SERIAL"]]

PKG = "org.openphone.assistant"
SMOKE_RECEIVER = f"{PKG}/.OpenPhoneSmokeControlReceiver"
ACTION_RELOAD = "org.openphone.assistant.action.RELOAD_EXTERNAL_RUNTIMES"
ACTION_LOG_STATUS = "org.openphone.assistant.action.LOG_EXTERNAL_RUNTIME_STATUS"
ACTION_REQUEST_OPENCLAW_ATTENTION = "org.openphone.assistant.action.REQUEST_OPENCLAW_ATTENTION"
ACTION_APPROVE = "org.openphone.assistant.action.EXTERNAL_APPROVE"
ACTION_DENY = "org.openphone.assistant.action.EXTERNAL_DENY"
EXTRA_OPENCLAW_ATTENTION_TEXT = "org.openphone.assistant.extra.OPENCLAW_ATTENTION_TEXT"
EXTRA_OPENCLAW_ATTENTION_SOURCE = "org.openphone.assistant.extra.OPENCLAW_ATTENTION_SOURCE"
EXTRA_OPENCLAW_ATTENTION_AUTONOMY = "org.openphone.assistant.extra.OPENCLAW_ATTENTION_AUTONOMY"
EXTRA_OPENCLAW_ATTENTION_INCLUDE_SCREEN = "org.openphone.assistant.extra.OPENCLAW_ATTENTION_INCLUDE_SCREEN"
EXTRA_CONFIRMATION_ID = "org.openphone.assistant.extra.EXTERNAL_CONFIRMATION_ID"
RUN_ID = str(int(time.time() * 1000))

SETTINGS_KEYS = [
    "openphone_external_runtimes_enabled",
    "openphone_external_openclaw_enabled",
    "openphone_external_openclaw_url",
    "openphone_external_openclaw_token",
    "openphone_external_openclaw_device_id",
    "openphone_external_openclaw_label",
    "openphone_external_hermes_enabled",
    "openphone_external_hermes_url",
    "openphone_external_hermes_token",
    "openphone_external_hermes_device_id",
    "openphone_external_hermes_label",
]


def adb(*args: str, check: bool = True) -> str:
    proc = subprocess.run(
        [*ADB, *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if check and proc.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed:\n{proc.stdout}")
    return proc.stdout.strip()


def shell(*args: str, check: bool = True) -> str:
    command = " ".join(shlex.quote(arg) for arg in args)
    return adb("shell", command, check=check)


def setting_get(key: str) -> str:
    return shell("settings", "get", "secure", key, check=False).strip()


def setting_put(key: str, value: str) -> None:
    shell("settings", "put", "secure", key, value)


def setting_delete(key: str) -> None:
    shell("settings", "delete", "secure", key, check=False)


def restore_settings(original: dict[str, str]) -> None:
    for key, value in original.items():
        if value in {"", "null"}:
            setting_delete(key)
        else:
            setting_put(key, value)


def configure_openclaw(url: str, token: str = "openphone-device-failure-token") -> None:
    setting_put("openphone_external_runtimes_enabled", "1")
    setting_put("openphone_external_openclaw_enabled", "1")
    setting_put("openphone_external_openclaw_url", url)
    setting_put("openphone_external_openclaw_token", token)
    setting_put("openphone_external_openclaw_device_id", "openphone-device-failure-smoke")
    setting_put("openphone_external_openclaw_label", "OpenPhone Failure Smoke")
    setting_put("openphone_external_hermes_enabled", "0")


def reload_external() -> None:
    shell("am", "broadcast", "--receiver-foreground",
          "-n", SMOKE_RECEIVER, "-a", ACTION_RELOAD, check=False)


def log_external_status() -> str:
    shell("am", "broadcast", "--receiver-foreground",
          "-n", SMOKE_RECEIVER, "-a", ACTION_LOG_STATUS, check=False)
    time.sleep(0.4)
    return shell(
        "logcat",
        "-d",
        "-t",
        "300",
        "-s",
        "OpenPhoneAssistant:I",
        "OpenPhoneExternal:I",
        "OpenPhoneOpenClaw:I",
        "OpenPhoneOpenClaw:W",
        check=False,
    )


def assert_status_contains(expected: str, label: str) -> None:
    deadline = time.time() + 8
    last = ""
    while time.time() < deadline:
        last = log_external_status()
        if expected in last:
            print(f"[ok] {label}: observed {expected}")
            return
        time.sleep(0.7)
    raise AssertionError(f"{label}: did not observe {expected}. Recent logs:\n{last}")


def assert_log_contains(expected: str, label: str, timeout: float = 8) -> None:
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        last = log_external_status()
        if expected in last:
            print(f"[ok] {label}: observed {expected}")
            return
        time.sleep(0.5)
    raise AssertionError(f"{label}: did not observe {expected}. Recent logs:\n{last}")


def request_openclaw_attention(text: str, source: str = "chat") -> None:
    shell(
        "am",
        "broadcast",
        "--receiver-foreground",
        "-n",
        SMOKE_RECEIVER,
        "-a",
        ACTION_REQUEST_OPENCLAW_ATTENTION,
        "--es",
        EXTRA_OPENCLAW_ATTENTION_TEXT,
        text,
        "--es",
        EXTRA_OPENCLAW_ATTENTION_SOURCE,
        source,
        "--es",
        EXTRA_OPENCLAW_ATTENTION_AUTONOMY,
        "observe_only",
        "--ez",
        EXTRA_OPENCLAW_ATTENTION_INCLUDE_SCREEN,
        "false",
        check=False,
    )


def deny_confirmation(confirmation_id: str) -> None:
    shell(
        "am",
        "broadcast",
        "--receiver-foreground",
        "-n",
        SMOKE_RECEIVER,
        "-a",
        ACTION_DENY,
        "--es",
        EXTRA_CONFIRMATION_ID,
        confirmation_id,
        check=False,
    )


def approve_confirmation(confirmation_id: str) -> None:
    shell(
        "am",
        "broadcast",
        "--receiver-foreground",
        "-n",
        SMOKE_RECEIVER,
        "-a",
        ACTION_APPROVE,
        "--es",
        EXTRA_CONFIRMATION_ID,
        confirmation_id,
        check=False,
    )


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


class OpenClawFailureShim:
    def __init__(self, reject_connect: bool = False) -> None:
        self.reject_connect = reject_connect
        self.frames: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        self.connected = asyncio.Event()
        self.server: Any = None
        self.websocket: Any = None
        self.port = free_port()

    async def __aenter__(self) -> "OpenClawFailureShim":
        self.backlog: list[dict[str, Any]] = []
        self.server = await websockets.serve(self._handle, "127.0.0.1", self.port)
        adb("reverse", f"tcp:{self.port}", f"tcp:{self.port}")
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        adb("reverse", "--remove", f"tcp:{self.port}", check=False)
        if self.websocket is not None:
            await self.websocket.close()
        if self.server is not None:
            self.server.close()
            await self.server.wait_closed()

    async def _handle(self, websocket) -> None:
        self.websocket = websocket
        await websocket.send(json.dumps({
            "type": "event",
            "event": "connect.challenge",
            "payload": {"nonce": "openphone-device-failure-smoke"},
        }, separators=(",", ":")))
        with suppress(websockets.exceptions.ConnectionClosed):
            async for raw in websocket:
                frame = json.loads(raw)
                await self.frames.put(frame)
                if frame.get("type") == "req" and frame.get("method") == "connect":
                    if self.reject_connect:
                        await websocket.send(json.dumps({
                            "type": "res",
                            "id": frame.get("id"),
                            "ok": False,
                            "error": {
                                "code": "unauthorized",
                                "message": "device failure smoke rejected token",
                                "details": {"code": "invalid_token"},
                            },
                        }, separators=(",", ":")))
                    else:
                        await websocket.send(json.dumps({
                            "type": "res",
                            "id": frame.get("id"),
                            "ok": True,
                            "payload": {
                                "auth": {
                                    "deviceToken": "device-failure-smoke-node-token",
                                    "scopes": [],
                                },
                            },
                        }, separators=(",", ":")))
                        self.connected.set()

    async def wait_connected(self) -> None:
        await asyncio.wait_for(self.connected.wait(), timeout=20)

    async def wait_rpc(self, method: str, *, invoke_id: str | None = None,
                       timeout: float = 12) -> dict[str, Any]:
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            for index, frame in enumerate(list(self.backlog)):
                if self._matches_rpc(frame, method, invoke_id):
                    return self.backlog.pop(index)
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                raise TimeoutError(f"timed out waiting for {method} {invoke_id or ''}")
            frame = await asyncio.wait_for(self.frames.get(), timeout=remaining)
            if not self._matches_rpc(frame, method, invoke_id):
                self.backlog.append(frame)
                if len(self.backlog) > 128:
                    self.backlog = self.backlog[-128:]
                continue
            return frame

    @staticmethod
    def _matches_rpc(frame: dict[str, Any], method: str, invoke_id: str | None) -> bool:
        if frame.get("type") != "req" or frame.get("method") != method:
            return False
        params = frame.get("params") or {}
        if invoke_id is not None and str(params.get("id") or "") != invoke_id:
            return False
        return True

    async def wait_node_event(self, event_name: str, *, request_id: str | None = None,
                              timeout: float = 12) -> dict[str, Any]:
        deadline = asyncio.get_running_loop().time() + timeout
        while True:
            frame = await self.wait_rpc("node.event", timeout=max(0.1, deadline - asyncio.get_running_loop().time()))
            params = frame.get("params") or {}
            if params.get("event") != event_name:
                continue
            payload = json.loads(str(params.get("payloadJSON") or "{}"))
            if request_id is not None and payload.get("request_id") != request_id:
                continue
            return payload

    async def send_event(self, event_name: str, payload: dict[str, Any]) -> None:
        assert self.websocket is not None
        await self.websocket.send(json.dumps({
            "type": "event",
            "event": event_name,
            "payload": payload,
        }, separators=(",", ":")))

    async def invoke(self, invoke_id: str, command: str, params: dict[str, Any],
                     *, idempotency_key: str | None = None,
                     timeout_ms: int = 15000) -> dict[str, Any]:
        await self.send_invoke_request(
            invoke_id,
            command,
            params,
            idempotency_key=idempotency_key,
            timeout_ms=timeout_ms,
        )
        return await self.wait_invoke_result(invoke_id)

    async def send_invoke_request(self, invoke_id: str, command: str,
                                  params: dict[str, Any],
                                  *, idempotency_key: str | None = None,
                                  timeout_ms: int = 15000) -> None:
        assert self.websocket is not None
        payload: dict[str, Any] = {
            "id": invoke_id,
            "nodeId": "openphone-device-failure-node",
            "command": command,
            "params": params,
            "timeoutMs": timeout_ms,
        }
        if idempotency_key:
            payload["idempotencyKey"] = idempotency_key
        await self.websocket.send(json.dumps({
            "type": "event",
            "event": "node.invoke.request",
            "payload": payload,
        }, separators=(",", ":")))

    async def wait_invoke_result(self, invoke_id: str, timeout: float = 20) -> dict[str, Any]:
        frame = await self.wait_rpc(
            "node.invoke.result",
            invoke_id=invoke_id,
            timeout=timeout,
        )
        return frame.get("params") or {}


def payload_result(params: dict[str, Any]) -> dict[str, Any]:
    payload = params.get("payload") if isinstance(params.get("payload"), dict) else {}
    return payload


async def run_live_failure_modes() -> None:
    async with OpenClawFailureShim() as shim:
        configure_openclaw(f"ws://127.0.0.1:{shim.port}")
        reload_external()
        await shim.wait_connected()
        print("[ok] OpenClaw failure shim connected")

        unknown = await shim.invoke(
            f"unknown-command-{RUN_ID}",
            "openphone.unknown",
            {"reason": "OpenPhone device failure smoke unknown command"},
        )
        deny_request_id = f"deny-{RUN_ID}-1"
        deny_retry_request_id = f"deny-{RUN_ID}-2"
        deny_key = f"openphone-device-failure-deny-key-{RUN_ID}"
        deny_session_key = f"openphone:device-failure-smoke:deny-{RUN_ID}"
        approve_request_id = f"approve-{RUN_ID}-1"
        approve_key = f"openphone-device-failure-approve-key-{RUN_ID}"
        approve_session_key = f"openphone:device-failure-smoke:approve-{RUN_ID}"
        timeout_request_id = f"timeout-{RUN_ID}-1"
        timeout_key = f"openphone-device-failure-timeout-key-{RUN_ID}"
        timeout_session_key = f"openphone:device-failure-smoke:timeout-{RUN_ID}"
        if unknown.get("ok") is not False:
            raise AssertionError(f"unknown command was not rejected: {unknown}")
        error = unknown.get("error") if isinstance(unknown.get("error"), dict) else {}
        if error.get("code") != "unknown_command":
            raise AssertionError(f"unknown command error mismatch: {unknown}")
        print("[ok] unknown command rejected")

        await shim.send_invoke_request(
            deny_request_id,
            "openphone.url.open",
            {
                "url": "https://example.invalid/openphone-denied-smoke",
                "reason": "OpenPhone device failure smoke deny",
                "sessionKey": deny_session_key,
            },
            idempotency_key=deny_key,
        )
        denied_required = await shim.wait_node_event(
            "openphone.confirmation.required",
            request_id=deny_request_id,
            timeout=15,
        )
        confirmation_id = denied_required.get("confirmation_id")
        if not confirmation_id:
            raise AssertionError(f"confirmation id missing: {denied_required}")
        deny_confirmation(str(confirmation_id))
        denied_initial = await shim.wait_invoke_result(deny_request_id, timeout=20)
        denied_payload = payload_result(denied_initial)
        if denied_payload.get("status") != "denied":
            raise AssertionError(f"denied invoke did not complete with denial: {denied_initial}")
        denied_event = await shim.wait_node_event(
            "openphone.confirmation.resolved",
            request_id=deny_request_id,
            timeout=15,
        )
        if denied_event.get("runtime_session_id") != deny_session_key:
            raise AssertionError(f"deny runtime session mismatch: {denied_event}")
        denied_result = denied_event.get("result") if isinstance(denied_event.get("result"), dict) else {}
        if denied_result.get("status") != "denied":
            raise AssertionError(f"deny result mismatch: {denied_event}")
        print("[ok] denied confirmation completed original invoke")

        cached = await shim.invoke(
            deny_retry_request_id,
            "openphone.url.open",
            {
                "url": "https://example.invalid/openphone-denied-smoke",
                "reason": "OpenPhone device failure smoke idempotency retry",
                "sessionKey": deny_session_key,
            },
            idempotency_key=deny_key,
        )
        cached_payload = payload_result(cached)
        if cached_payload.get("status") != "denied":
            raise AssertionError(f"idempotency retry did not return cached denial: {cached}")
        print("[ok] idempotency retry returned cached final result")

        await shim.send_invoke_request(
            approve_request_id,
            "openphone.url.open",
            {
                "url": "https://example.invalid/openphone-approved-smoke",
                "reason": "OpenPhone device failure smoke approve",
                "sessionKey": approve_session_key,
            },
            idempotency_key=approve_key,
        )
        approved_required = await shim.wait_node_event(
            "openphone.confirmation.required",
            request_id=approve_request_id,
            timeout=15,
        )
        approved_confirmation_id = approved_required.get("confirmation_id")
        if not approved_confirmation_id:
            raise AssertionError(f"approval confirmation id missing: {approved_required}")
        approve_confirmation(str(approved_confirmation_id))
        approved_result = await shim.wait_invoke_result(approve_request_id, timeout=20)
        approved_payload = payload_result(approved_result)
        if approved_payload.get("status") != "ok":
            raise AssertionError(f"approved invoke did not complete with ok: {approved_result}")
        approved_event = await shim.wait_node_event(
            "openphone.confirmation.resolved",
            request_id=approve_request_id,
            timeout=15,
        )
        if approved_event.get("runtime_session_id") != approve_session_key:
            raise AssertionError(f"approve runtime session mismatch: {approved_event}")
        approved_event_result = (
            approved_event.get("result") if isinstance(approved_event.get("result"), dict) else {}
        )
        if approved_event_result.get("status") != "ok":
            raise AssertionError(f"approve result mismatch: {approved_event}")
        print("[ok] approved confirmation completed original invoke")

        await shim.send_invoke_request(
            timeout_request_id,
            "openphone.url.open",
            {
                "url": "https://example.invalid/openphone-timeout-smoke",
                "reason": "OpenPhone device failure smoke timeout",
                "sessionKey": timeout_session_key,
            },
            idempotency_key=timeout_key,
            timeout_ms=1200,
        )
        timeout_required = await shim.wait_node_event(
            "openphone.confirmation.required",
            request_id=timeout_request_id,
            timeout=15,
        )
        if not timeout_required.get("confirmation_id"):
            raise AssertionError(f"timeout confirmation id missing: {timeout_required}")
        timeout_initial = await shim.wait_invoke_result(timeout_request_id, timeout=12)
        timeout_payload = payload_result(timeout_initial)
        if timeout_payload.get("status") != "timeout":
            raise AssertionError(f"timeout invoke did not complete with timeout: {timeout_initial}")
        timeout_event = await shim.wait_node_event(
            "openphone.confirmation.resolved",
            request_id=timeout_request_id,
            timeout=12,
        )
        if timeout_event.get("runtime_session_id") != timeout_session_key:
            raise AssertionError(f"timeout runtime session mismatch: {timeout_event}")
        timeout_result = timeout_event.get("result") if isinstance(timeout_event.get("result"), dict) else {}
        if timeout_result.get("status") != "timeout":
            raise AssertionError(f"timeout result mismatch: {timeout_event}")
        print("[ok] confirmation timeout returned timeout result")


async def run_attention_path() -> None:
    async with OpenClawFailureShim() as shim:
        configure_openclaw(f"ws://127.0.0.1:{shim.port}")
        reload_external()
        await shim.wait_connected()
        print("[ok] OpenClaw attention shim connected")

        prompt = f"OpenClaw attention smoke {RUN_ID}"
        request_openclaw_attention(prompt, source="chat")
        subscribe = await shim.wait_node_event("chat.subscribe", timeout=15)
        attention = await shim.wait_node_event("openphone.attention.requested", timeout=15)
        session_key = str(attention.get("sessionKey") or "")
        if not session_key:
            raise AssertionError(f"attention missing sessionKey: {attention}")
        if subscribe.get("sessionKey") != session_key:
            raise AssertionError(
                f"subscribe session mismatch subscribe={subscribe} attention={attention}"
            )
        if attention.get("text") != prompt:
            raise AssertionError(f"attention text mismatch: {attention}")
        if attention.get("source") != "chat":
            raise AssertionError(f"attention source mismatch: {attention}")
        if attention.get("include_screen") is not False:
            raise AssertionError(f"attention include_screen mismatch: {attention}")
        print("[ok] attention emitted chat.subscribe and openphone.attention.requested")

        await shim.send_event("agent", {
            "sessionKey": session_key,
            "stream": "lifecycle",
            "data": {"phase": "start"},
        })
        await shim.send_event("chat", {
            "sessionKey": session_key,
            "runId": f"attention-smoke-{RUN_ID}",
            "state": "final",
            "message": {
                "role": "assistant",
                "content": [{
                    "type": "text",
                    "text": "OpenClaw attention smoke final response",
                }],
            },
        })
        assert_log_contains(
            f"external runtime message runtime=openclaw terminal=true session={session_key}",
            "attention final fanout",
            timeout=10,
        )


async def run_bad_token_mode() -> None:
    async with OpenClawFailureShim(reject_connect=True) as shim:
        configure_openclaw(f"ws://127.0.0.1:{shim.port}", token="bad-openclaw-device-token")
        reload_external()
        await asyncio.sleep(2)
        assert_status_contains("auth_failed", "bad token")


async def main() -> None:
    original = {key: setting_get(key) for key in SETTINGS_KEYS}
    try:
        shell("logcat", "-c", check=False)

        offline_port = free_port()
        configure_openclaw(f"ws://127.0.0.1:{offline_port}")
        reload_external()
        await asyncio.sleep(2)
        assert_status_contains("offline", "gateway offline")

        configure_openclaw("ws://8.8.8.8/openphone")
        reload_external()
        await asyncio.sleep(1)
        assert_status_contains("insecure_transport_denied", "public plaintext transport")

        await run_bad_token_mode()
        await run_attention_path()
        await run_live_failure_modes()
    finally:
        restore_settings(original)
        reload_external()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as exc:
        print(f"smoke-test-openclaw-device-failures: FAIL: {exc}", file=sys.stderr)
        raise
PY

printf '\nOpenClaw device failure-mode smoke test passed.\n'
