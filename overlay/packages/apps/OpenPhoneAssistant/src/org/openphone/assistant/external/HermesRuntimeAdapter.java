package org.openphone.assistant.external;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.OpenPhoneNotificationController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.UUID;

public final class HermesRuntimeAdapter implements RuntimeAdapter {
    private static final String TAG = "OpenPhoneHermes";

    private final Context mContext;
    private final ExternalRuntimeConfig.RuntimeSettings mSettings;
    private final OpenPhoneToolBridge mToolBridge;
    private volatile String mStatus = "idle";
    private SimpleWebSocketClient mClient;

    public HermesRuntimeAdapter(Context context, ExternalRuntimeConfig.RuntimeSettings settings,
            OpenPhoneToolBridge toolBridge) {
        mContext = context;
        mSettings = settings;
        mToolBridge = toolBridge;
    }

    @Override
    public String name() {
        return "hermes";
    }

    @Override
    public synchronized void start() {
        if (!mSettings.configured()) {
            mStatus = "not_configured";
            return;
        }
        try {
            String wsUrl = ExternalRuntimeTransport.normalizeWsUrl(mSettings.url);
            if (!ExternalRuntimeTransport.isAllowedWebSocketUrl(wsUrl)) {
                mStatus = "insecure_transport_denied";
                Log.w(TAG, "Hermes adapter rejected insecure non-local websocket URL");
                return;
            }
            mClient = new SimpleWebSocketClient(new URI(wsUrl),
                    new LinkedHashMap<String, String>(), new SimpleWebSocketClient.Listener() {
                        @Override
                        public void onOpen() {
                            mStatus = "handshaking";
                            sendHello();
                        }

                        @Override
                        public void onMessage(String message) {
                            handleMessage(message);
                        }

                        @Override
                        public void onClosed(String reason) {
                            if (isAuthClose(reason)) {
                                mStatus = "auth_failed";
                                if (mClient != null) {
                                    mClient.close();
                                }
                            } else if (!"auth_failed".equals(mStatus)) {
                                mStatus = "closed";
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            if (!"auth_failed".equals(mStatus)) {
                                mStatus = "error:" + error.getClass().getSimpleName();
                            }
                            Log.w(TAG, "Hermes adapter error "
                                    + error.getClass().getSimpleName(), error);
                        }
                    }, true, 1000L, 30000L);
            mClient.start();
            mStatus = "connecting";
        } catch (Exception e) {
            mStatus = "error:" + e.getClass().getSimpleName();
            Log.w(TAG, "Hermes adapter start failed", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (mClient != null) {
            mClient.close();
            mClient = null;
        }
        mStatus = "stopped";
    }

    @Override
    public String status() {
        return mStatus;
    }

    @Override
    public void sendEvent(RuntimeEvent event) {
        if (mClient == null || !"connected".equals(mStatus)) {
            return;
        }
        try {
            mClient.sendText(new JSONObject()
                    .put("type", "event")
                    .put("event", event.event())
                    .put("payload", event.payload())
                    .toString());
        } catch (JSONException ignored) {
        }
    }

    @Override
    public void sendToolResult(ExternalRuntimeRequest request, ExternalRuntimeResult result) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("request_id", request == null ? "" : request.requestId())
                    .put("session_id", request == null ? "" : request.runtimeSessionId())
                    .put("tool", request == null ? "" : request.tool())
                    .put("result", result == null ? new JSONObject() : result.toJson());
        } catch (JSONException ignored) {
        }
        sendFrame("invoke_result", payload);
    }

    public JSONObject capabilityDescriptor() {
        JSONObject descriptor = new JSONObject();
        try {
            descriptor.put("contract_version", "openphone-runtime-v1")
                    .put("platform", "openphone")
                    .put("label", mSettings.label)
                    .put("supports_threads", true)
                    .put("supports_draft_streaming", false)
                    .put("supports_edit", false)
                    .put("pii_safe", false)
                    .put("commands", new JSONArray()
                            .put("phone_context")
                            .put("notifications_list")
                            .put("contacts_search")
                            .put("calendar_search")
                            .put("messages_search")
                            .put("calls_search")
                            .put("get_screen")
                            .put("local_screen_understanding"));
        } catch (JSONException ignored) {
        }
        return descriptor;
    }

    private void handleMessage(String raw) {
        JSONObject frame;
        try {
            frame = new JSONObject(raw);
        } catch (JSONException e) {
            Log.w(TAG, "Ignoring malformed Hermes frame");
            return;
        }
        String type = frame.optString("type", "");
        if ("hello_ok".equals(type)) {
            mStatus = "connected";
            sendEvent(new RuntimeEvent("openphone.presence.online", presencePayload("connect")));
            return;
        }
        if ("invoke".equals(type)) {
            ExternalRuntimeResult result = executeHermesToolCall(frame);
            sendInvokeResult(frame.optString("request_id", ""), result);
            return;
        }
        if ("ping".equals(type)) {
            sendFrame("pong", new JSONObject());
            return;
        }
        if ("message".equals(type)) {
            Log.i(TAG, "Hermes message received chat=" + frame.optString("chat_id", "")
                    + " message_id=" + frame.optString("message_id", ""));
            OpenPhoneNotificationController.showExternalRuntimeMessage(
                    mContext,
                    "Hermes",
                    frame.optString("title", "Hermes"),
                    frame.optString("text", ""),
                    frame.optString("message_id", frame.optString("request_id", "")));
        }
    }

    private ExternalRuntimeResult executeHermesToolCall(JSONObject toolCall) {
        JSONObject params = toolCall == null ? new JSONObject()
                : toolCall.optJSONObject("params");
        if (params == null) {
            params = new JSONObject();
        }
        String requestId = toolCall == null ? "" : toolCall.optString("request_id", "");
        String idempotencyKey = toolCall == null ? ""
                : toolCall.optString("idempotency_key", requestId);
        return mToolBridge.execute(new ExternalRuntimeRequest(
                requestId,
                "hermes",
                toolCall == null ? "" : toolCall.optString("session_id", "hermes"),
                new ExternalRuntimeIdentity("hermes", mSettings.label, principal()),
                toolCall == null ? "" : toolCall.optString("tool", ""),
                params,
                toolCall == null ? "" : toolCall.optString("reason", ""),
                toolCall == null ? "read_only" : toolCall.optString("autonomy", "read_only"),
                idempotencyKey,
                toolCall == null ? 30000L : toolCall.optLong("timeout_ms", 30000L),
                ""));
    }

    private void sendHello() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("type", "hello")
                    .put("device_id", principal())
                    .put("label", mSettings.label)
                    .put("token", mSettings.token)
                    .put("descriptor", capabilityDescriptor());
        } catch (JSONException ignored) {
        }
        if (mClient != null) {
            mClient.sendText(payload.toString());
        }
    }

    private void sendInvokeResult(String requestId, ExternalRuntimeResult result) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("request_id", requestId)
                    .put("result", result.toJson());
        } catch (JSONException ignored) {
        }
        sendFrame("invoke_result", payload);
    }

    private void sendFrame(String type, JSONObject payload) {
        if (mClient == null) {
            return;
        }
        try {
            JSONObject frame = payload == null ? new JSONObject() : payload;
            frame.put("type", type);
            if (!frame.has("request_id")) {
                frame.put("request_id", "openphone-hermes-" + UUID.randomUUID());
            }
            mClient.sendText(frame.toString());
        } catch (JSONException ignored) {
        }
    }

    private String principal() {
        return mSettings.deviceId == null || mSettings.deviceId.isEmpty()
                ? "openphone-hermes" : mSettings.deviceId;
    }

    private JSONObject presencePayload(String trigger) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("trigger", trigger == null ? "" : trigger)
                    .put("sentAtMs", System.currentTimeMillis())
                    .put("displayName", mSettings.label)
                    .put("version", "0.1")
                    .put("platform", "Android")
                    .put("deviceFamily", "OpenPhone");
        } catch (JSONException ignored) {
        }
        return payload;
    }

    private static boolean isAuthClose(String reason) {
        String value = reason == null ? "" : reason.toLowerCase();
        return value.contains("4401") || value.contains("unauthorized")
                || value.contains("auth");
    }
}
