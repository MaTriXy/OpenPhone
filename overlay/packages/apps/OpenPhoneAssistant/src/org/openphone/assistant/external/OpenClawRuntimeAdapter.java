package org.openphone.assistant.external;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.session.PhoneSessionStore;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class OpenClawRuntimeAdapter implements RuntimeAdapter {
    private static final String TAG = "OpenPhoneOpenClaw";
    private static final int MAX_SEEN_RUNTIME_MESSAGES = 64;

    private final ExternalRuntimeConfig.RuntimeSettings mSettings;
    private final OpenPhoneToolBridge mToolBridge;
    private final OpenClawDeviceIdentity mDeviceIdentity;
    private final ExternalRuntimeCallback mCallback;
    private final Map<String, String> mCommandToTool = new LinkedHashMap<>();
    private final Map<String, String> mPendingInvokeNodeIds =
            new LinkedHashMap<String, String>(16, 0.75f, true);
    private final Map<String, String> mSeenRuntimeMessages =
            new LinkedHashMap<String, String>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SEEN_RUNTIME_MESSAGES;
                }
            };
    private final Map<String, String> mLatestAgentTextBySession =
            new LinkedHashMap<String, String>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SEEN_RUNTIME_MESSAGES;
                }
            };
    private volatile String mStatus = "idle";
    private volatile String mConnectNonce = "";
    private volatile boolean mConnectSent;
    private SimpleWebSocketClient mClient;

    public OpenClawRuntimeAdapter(Context context,
            ExternalRuntimeConfig.RuntimeSettings settings, OpenPhoneToolBridge toolBridge) {
        this(context, settings, toolBridge, null);
    }

    public OpenClawRuntimeAdapter(Context context,
            ExternalRuntimeConfig.RuntimeSettings settings, OpenPhoneToolBridge toolBridge,
            ExternalRuntimeCallback callback) {
        mSettings = settings;
        mToolBridge = toolBridge;
        mCallback = callback;
        mDeviceIdentity = new OpenClawDeviceIdentity(context);
        registerCommands();
    }

    @Override
    public String name() {
        return "openclaw";
    }

    @Override
    public synchronized void start() {
        if (!mSettings.configured()) {
            mStatus = "not_configured";
            return;
        }
        try {
            mConnectSent = false;
            mConnectNonce = "";
            String wsUrl = ExternalRuntimeTransport.normalizeWsUrl(mSettings.url);
            if (!ExternalRuntimeTransport.isAllowedWebSocketUrl(wsUrl)) {
                mStatus = "insecure_transport_denied";
                Log.w(TAG, "OpenClaw adapter rejected insecure non-local websocket URL");
                return;
            }
            mClient = new SimpleWebSocketClient(new URI(wsUrl),
                    new LinkedHashMap<String, String>(), new SimpleWebSocketClient.Listener() {
                        @Override
                        public void onOpen() {
                            mStatus = "waiting_for_challenge";
                        }

                        @Override
                        public void onMessage(String message) {
                            handleMessage(message);
                        }

                        @Override
                        public void onClosed(String reason) {
                            if (!mStatus.startsWith("auth_failed")) {
                                mStatus = "closed";
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            if (!mStatus.startsWith("auth_failed")) {
                                mStatus = "error:" + error.getClass().getSimpleName();
                            }
                            Log.w(TAG, "OpenClaw adapter error "
                                    + error.getClass().getSimpleName(), error);
                        }
                    }, true, 1000L, 30000L);
            mClient.start();
            mStatus = "connecting";
        } catch (Exception e) {
            mStatus = "error:" + e.getClass().getSimpleName();
            Log.w(TAG, "OpenClaw adapter start failed", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (mClient != null) {
            mClient.close();
            mClient = null;
        }
        mConnectSent = false;
        mConnectNonce = "";
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
        sendRpc("node.event", buildNodeEventParams(event.event(), event.payload()));
    }

    @Override
    public synchronized void sendToolResult(ExternalRuntimeRequest request,
            ExternalRuntimeResult result) {
        if (request != null && result != null) {
            String nodeId = takePendingInvokeNodeId(request.requestId());
            if (!nodeId.isEmpty()) {
                sendInvokeResult(request.requestId(), nodeId, result);
            }
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("request_id", request == null ? "" : request.requestId())
                    .put("runtime_session_id", request == null ? "" : request.runtimeSessionId())
                    .put("tool", request == null ? "" : request.tool())
                    .put("result", result == null ? new JSONObject() : result.toJson());
        } catch (JSONException ignored) {
        }
        sendEvent(new RuntimeEvent("openphone.confirmation.resolved", payload));
    }

    public synchronized String requestAttention(String phoneSessionId, String attentionId,
            String source, String text, String autonomy, boolean includeScreen,
            JSONObject context) {
        if (mClient == null || !"connected".equals(mStatus)) {
            return "";
        }
        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) {
            return "";
        }
        String cleanPhoneSessionId = phoneSessionId == null ? "" : phoneSessionId.trim();
        if (cleanPhoneSessionId.isEmpty()) {
            cleanPhoneSessionId = "phone-sess-" + UUID.randomUUID();
        }
        String cleanAttentionId = attentionId == null ? "" : attentionId.trim();
        if (cleanAttentionId.isEmpty()) {
            cleanAttentionId = "attn-" + UUID.randomUUID();
        }
        String sessionKey = "openphone:" + principal() + ":" + cleanPhoneSessionId;
        JSONObject subscribe = new JSONObject();
        JSONObject payload = new JSONObject();
        try {
            subscribe.put("sessionKey", sessionKey);
            JSONObject eventContext = context == null
                    ? new JSONObject() : ExternalRuntimeRequest.copy(context);
            eventContext.put("displayName", mSettings.label)
                    .put("platform", "Android")
                    .put("deviceFamily", "OpenPhone");
            payload.put("message", buildAgentRequestMessage(cleanPhoneSessionId,
                            cleanAttentionId, cleanSource(source), message,
                            cleanAutonomy(autonomy), includeScreen, eventContext))
                    .put("sessionKey", sessionKey)
                    .put("thinking", "low")
                    .put("deliver", false)
                    .put("key", cleanAttentionId);
            JSONArray attachments = agentRequestAttachments(cleanAttentionId, eventContext);
            if (attachments.length() > 0) {
                payload.put("attachments", attachments);
            }
        } catch (JSONException ignored) {
            return "";
        }
        sendRpc("node.event", buildNodeEventParams("chat.subscribe", subscribe));
        sendRpc("node.event", buildNodeEventParams("agent.request", payload));
        return sessionKey;
    }

    private String buildAgentRequestMessage(String phoneSessionId, String attentionId,
            String source, String text, String autonomy, boolean includeScreen,
            JSONObject context) throws JSONException {
        String nodeId = principal();
        JSONObject requestContext = new JSONObject()
                .put("nodeId", nodeId)
                .put("phoneSessionId", phoneSessionId)
                .put("attentionId", attentionId)
                .put("source", source)
                .put("autonomy", autonomy)
                .put("includeScreen", includeScreen)
                .put("displayName", mSettings.label)
                .put("platform", "Android")
                .put("deviceFamily", "OpenPhone");
        String contextJson = compactJson(redactedOpenPhoneContext(context), 4000);
        if (!contextJson.isEmpty()) {
            requestContext.put("contextJson", contextJson);
        }
        String screenPreflight = compactJson(redactedScreenPreflight(context), 6000);
        StringBuilder out = new StringBuilder();
        out.append(text.trim())
                .append("\n\nOpenPhone request context:\n")
                .append(requestContext.toString());
        if (!screenPreflight.isEmpty()) {
            out.append("\n\nOpenPhone screen preflight observation:\n")
                    .append(screenPreflight);
        }
        out.append("\n\nOpenPhone device-control instructions:\n")
                .append("- The user is asking from a live OpenPhone Android node. ")
                .append("Target node: ").append(nodeId).append(".\n")
                .append("- If an OpenPhone screen preflight observation is present, ")
                .append("use it as the current phone screen context before using ")
                .append("workspace/bootstrap context.\n")
                .append("- If an OpenPhone screenshot is attached, treat it as the ")
                .append("rendered phone screen and use accessibility metadata as ")
                .append("supporting context.\n")
                .append("- Use the nodes tool to inspect or control this phone. ")
                .append("Do not say you cannot see the phone when a relevant node ")
                .append("tool is available.\n")
                .append("- To read the current screen, invoke node ")
                .append(nodeId)
                .append(" with command openphone.screen.get and params ")
                .append("{\"include_screenshot\":true,\"include_activity\":true,")
                .append("\"include_ui_tree\":true,\"reason\":")
                .append("\"answer the OpenPhone user from the current phone screen\"}.\n")
                .append("- For phone actions, invoke the matching OpenPhone command, ")
                .append("for example openphone.url.open, openphone.app.open, ")
                .append("openphone.ui.tap, openphone.ui.type_text, or notifications.open.\n")
                .append("- Mutating phone actions may require local confirmation on ")
                .append("the Android device; wait for the tool result and report ")
                .append("whether it was approved, denied, or completed.");
        if (includeScreen) {
            out.append("\n- This request asked to include the screen. Before answering ")
                    .append("screen/app/UI questions, read the phone screen with ")
                    .append("openphone.screen.get if the attached/preflight context is ")
                    .append("missing or stale.");
        }
        return truncate(out.toString(), 18000);
    }

    private static JSONArray agentRequestAttachments(String attentionId, JSONObject context)
            throws JSONException {
        JSONArray attachments = new JSONArray();
        JSONObject screenshot = screenshotFromContext(context);
        if (screenshot == null) {
            return attachments;
        }
        String data = screenshot.optString("data", "");
        String encoding = screenshot.optString("encoding", "base64");
        if (data.isEmpty() || !"base64".equalsIgnoreCase(encoding)) {
            return attachments;
        }
        String mimeType = screenshot.optString("mime_type", "image/jpeg");
        if (!mimeType.toLowerCase(Locale.US).startsWith("image/")) {
            return attachments;
        }
        String extension = mimeType.toLowerCase(Locale.US).contains("png") ? "png" : "jpg";
        String cleanAttentionId = attentionId == null || attentionId.trim().isEmpty()
                ? "screen" : attentionId.trim().replaceAll("[^A-Za-z0-9_.-]", "-");
        attachments.put(new JSONObject()
                .put("type", "image")
                .put("mimeType", mimeType)
                .put("fileName", "openphone-" + cleanAttentionId + "." + extension)
                .put("content", data));
        return attachments;
    }

    private static JSONObject screenshotFromContext(JSONObject context) {
        if (context == null) {
            return null;
        }
        JSONObject preflight = context.optJSONObject("screen_preflight");
        return preflight == null ? null : preflight.optJSONObject("screenshot");
    }

    private static JSONObject redactedOpenPhoneContext(JSONObject context) {
        if (context == null) {
            return new JSONObject();
        }
        try {
            JSONObject copy = new JSONObject(context.toString());
            redactScreenshotBytes(copy.optJSONObject("screen_preflight"));
            return copy;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONObject redactedScreenPreflight(JSONObject context) {
        if (context == null) {
            return new JSONObject();
        }
        JSONObject preflight = context.optJSONObject("screen_preflight");
        if (preflight == null) {
            return new JSONObject();
        }
        try {
            JSONObject copy = new JSONObject(preflight.toString());
            redactScreenshotBytes(copy);
            return copy;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void redactScreenshotBytes(JSONObject preflight) throws JSONException {
        if (preflight == null) {
            return;
        }
        JSONObject screenshot = preflight.optJSONObject("screenshot");
        if (screenshot == null) {
            return;
        }
        String data = screenshot.optString("data", "");
        if (!data.isEmpty()) {
            screenshot.put("data", "[base64 chars=" + data.length() + "]");
        }
    }

    private static String compactJson(JSONObject object, int maxChars) {
        if (object == null || object.length() == 0) {
            return "";
        }
        return truncate(object.toString(), maxChars);
    }

    private static String truncate(String value, int maxChars) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() <= maxChars) {
            return clean;
        }
        return clean.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private void handleMessage(String raw) {
        JSONObject frame;
        try {
            frame = new JSONObject(raw);
        } catch (JSONException e) {
            Log.w(TAG, "Ignoring malformed OpenClaw frame");
            return;
        }
        String type = frame.optString("type", "");
        if ("event".equals(type)) {
            String event = frame.optString("event", "");
            JSONObject payload = frame.optJSONObject("payload");
            if (payload == null) {
                payload = parseObject(frame.optString("payloadJSON", "{}"));
            }
            if ("connect.challenge".equals(event)) {
                mConnectNonce = payload.optString("nonce", "");
                if (!mConnectNonce.isEmpty()) {
                    sendConnectOnce();
                }
                return;
            }
            if ("chat".equals(event)) {
                handleChatEvent(payload);
                return;
            }
            if ("agent".equals(event)) {
                handleAgentEvent(payload);
                return;
            }
            if ("node.invoke.request".equals(event)) {
                handleInvoke(payload);
            }
            return;
        }
        if ("res".equals(type) && frame.optString("id", "").startsWith("openphone-connect-")) {
            if (frame.optBoolean("ok", false)) {
                persistDeviceTokens(connectPayload(frame));
                mStatus = "connected";
                sendPresence("connect");
            } else {
                mStatus = connectFailureStatus(frame);
                if (mClient != null) {
                    mClient.close();
                }
            }
        }
    }

    private void handleChatEvent(JSONObject payload) {
        if (payload == null) {
            return;
        }
        String state = payload.optString("state", "");
        boolean terminal = "final".equals(state) || "error".equals(state)
                || "done".equals(state) || "complete".equals(state)
                || payload.optBoolean("terminal", false)
                || payload.optBoolean("final", false)
                || payload.optBoolean("isFinal", false);
        if (!terminal) {
            return;
        }
        String text = extractMessageText(payload);
        if ("error".equals(state)) {
            String errorText = firstNonEmpty(payload.optString("errorMessage", ""),
                    payload.optString("error", ""), text);
            text = errorText.isEmpty() ? "OpenClaw returned an error."
                    : openClawErrorForUser(errorText);
        }
        if (text.isEmpty()) {
            return;
        }
        String sessionKey = sessionKeyFromPayload(payload);
        String runId = payload.optString("runId", sessionKey);
        String dedupeKey = "chat:" + sessionKey + ":" + runId + ":"
                + (state.isEmpty() ? "terminal" : state);
        String previous = mSeenRuntimeMessages.get(dedupeKey);
        if (text.equals(previous)) {
            return;
        }
        mSeenRuntimeMessages.put(dedupeKey, text);
        emitRuntimeMessage(sessionKey, text, true);
    }

    private void handleAgentEvent(JSONObject payload) {
        if (payload == null) {
            return;
        }
        String stream = payload.optString("stream", "");
        String sessionKey = sessionKeyFromPayload(payload);
        if ("assistant".equals(stream)) {
            String text = agentText(payload, payload.optJSONObject("data"));
            if (!text.isEmpty()) {
                mLatestAgentTextBySession.put(sessionKey, text);
            }
            return;
        }
        if (!"lifecycle".equals(stream)) {
            return;
        }
        JSONObject data = payload.optJSONObject("data");
        String phase = data == null ? payload.optString("phase", "")
                : data.optString("phase", payload.optString("phase", ""));
        if ("start".equals(phase)) {
            emitRuntimeMessageOnce("agent:" + sessionKey + ":start", sessionKey,
                    "OpenClaw is working on it.", false);
            return;
        }
        String error = lifecycleError(payload, data);
        if (error.isEmpty()) {
            if ("finishing".equals(phase) || "finish".equals(phase)
                    || "end".equals(phase) || "done".equals(phase)
                    || "complete".equals(phase)) {
                String text = mLatestAgentTextBySession.remove(sessionKey);
                if (text != null && !text.trim().isEmpty()) {
                    emitRuntimeMessageOnce("agent:" + sessionKey + ":final:" + text,
                            sessionKey, text, true);
                }
            }
            return;
        }
        if ("error".equals(phase) || "failed".equals(phase)
                || "finishing".equals(phase) || "finish".equals(phase)) {
            String displayError = openClawErrorForUser(error);
            emitRuntimeMessageOnce("agent:" + sessionKey + ":terminal:" + displayError,
                    sessionKey, displayError, true);
        }
    }

    private static String agentText(JSONObject payload, JSONObject data) {
        String text = firstNonEmpty(
                data == null ? "" : data.optString("text", ""),
                data == null ? "" : data.optString("message", ""),
                data == null ? "" : data.optString("deltaText", ""),
                payload == null ? "" : payload.optString("text", ""),
                payload == null ? "" : payload.optString("message", ""),
                payload == null ? "" : payload.optString("deltaText", ""));
        return text.trim();
    }

    private void emitRuntimeMessageOnce(String dedupeKey, String sessionKey, String message,
            boolean terminal) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty()) {
            return;
        }
        String key = dedupeKey == null ? "" : dedupeKey.trim();
        if (key.isEmpty()) {
            emitRuntimeMessage(sessionKey, clean, terminal);
            return;
        }
        String previous = mSeenRuntimeMessages.get(key);
        if (clean.equals(previous)) {
            return;
        }
        mSeenRuntimeMessages.put(key, clean);
        emitRuntimeMessage(sessionKey, clean, terminal);
    }

    private void emitRuntimeMessage(String sessionKey, String message, boolean terminal) {
        String clean = message == null ? "" : message.trim();
        if (mCallback == null || clean.isEmpty()) {
            return;
        }
        String dedupeKey = "runtime-message:" + (sessionKey == null ? "" : sessionKey)
                + ":" + terminal + ":" + clean;
        String previous = mSeenRuntimeMessages.get(dedupeKey);
        if (clean.equals(previous)) {
            return;
        }
        mSeenRuntimeMessages.put(dedupeKey, clean);
        try {
            mCallback.onRuntimeMessage(name(), sessionKey == null ? "" : sessionKey,
                    clean, terminal);
        } catch (RuntimeException e) {
            Log.w(TAG, "runtime callback failed", e);
        }
    }

    private static String extractMessageText(JSONObject payload) {
        JSONObject message = payload.optJSONObject("message");
        if (message == null) {
            String direct = firstNonEmpty(
                    payload.optString("text", ""),
                    payload.optString("deltaText", ""),
                    payload.optString("errorMessage", ""),
                    payload.optString("error", ""));
            return direct.trim();
        }
        String direct = message.optString("text", "").trim();
        if (!direct.isEmpty()) {
            return direct;
        }
        JSONArray content = message.optJSONArray("content");
        if (content == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.optJSONObject(i);
            if (part == null) {
                continue;
            }
            String text = part.optString("text", "").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(text);
        }
        return out.toString().trim();
    }

    private static String sessionKeyFromPayload(JSONObject payload) {
        if (payload == null) {
            return "";
        }
        String sessionKey = firstNonEmpty(payload.optString("sessionKey", ""),
                payload.optString("session_key", ""));
        if (!sessionKey.isEmpty()) {
            return sessionKey;
        }
        JSONObject session = payload.optJSONObject("session");
        if (session != null) {
            return firstNonEmpty(session.optString("sessionKey", ""),
                    session.optString("key", ""));
        }
        return "";
    }

    private static String lifecycleError(JSONObject payload, JSONObject data) {
        return firstNonEmpty(
                data == null ? "" : data.optString("error", ""),
                data == null ? "" : data.optString("errorMessage", ""),
                data == null ? "" : data.optString("message", ""),
                payload == null ? "" : payload.optString("error", ""),
                payload == null ? "" : payload.optString("errorMessage", ""));
    }

    private static String openClawErrorForUser(String error) {
        String clean = error == null ? "" : error.trim();
        String lower = clean.toLowerCase(Locale.US);
        if (lower.contains("401") || lower.contains("unauthorized")
                || lower.contains("missing bearer")
                || lower.contains("authentication")) {
            return "OpenClaw needs model authentication before it can answer from the phone.";
        }
        if (clean.isEmpty()) {
            return "OpenClaw could not complete the request.";
        }
        if (clean.length() > 320) {
            clean = clean.substring(0, 317).trim() + "...";
        }
        return "OpenClaw could not complete the request: " + clean;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String clean = value == null ? "" : value.trim();
            if (!clean.isEmpty()) {
                return clean;
            }
        }
        return "";
    }

    private void handleInvoke(JSONObject payload) {
        String invokeId = payload.optString("id", "");
        String nodeId = payload.optString("nodeId", "");
        String command = payload.optString("command", "");
        JSONObject params = payload.optJSONObject("params");
        if (params == null) {
            params = parseObject(payload.optString("paramsJSON", "{}"));
        }
        String tool = mCommandToTool.get(command);
        ExternalRuntimeResult result;
        if (tool == null || tool.isEmpty()) {
            result = ExternalRuntimeResult.denied(invokeId, "unknown_command",
                    "OpenPhone does not advertise this OpenClaw command.",
                    new JSONObject(), "external:openclaw:" + invokeId);
        } else {
            rememberPendingInvokeNodeId(invokeId, nodeId);
            String idempotencyKey = payload.optString("idempotencyKey", invokeId);
            String runtimeSessionId = runtimeSessionId(payload, params);
            String phoneSessionId = phoneSessionId(payload, params, runtimeSessionId);
            result = mToolBridge.execute(new ExternalRuntimeRequest(
                    invokeId,
                    "openclaw",
                    runtimeSessionId,
                    phoneSessionId,
                    new ExternalRuntimeIdentity("openclaw", mSettings.label, principal()),
                    tool,
                    mapParams(command, tool, params),
                    params.optString("reason", "OpenClaw command " + command),
                    params.optString("autonomy", "read_only"),
                    idempotencyKey.isEmpty() ? invokeId : idempotencyKey,
                    payload.optLong("timeoutMs", 30000L),
                    ""));
        }
        if (ExternalRuntimeResult.STATUS_NEEDS_CONFIRMATION.equals(result.status())) {
            return;
        }
        takePendingInvokeNodeId(invokeId);
        if ("canvas.snapshot".equals(command)) {
            sendCanvasSnapshotResult(invokeId, nodeId, result);
        } else {
            sendInvokeResult(invokeId, nodeId, result);
        }
    }

    private synchronized void rememberPendingInvokeNodeId(String invokeId, String nodeId) {
        String cleanInvokeId = invokeId == null ? "" : invokeId.trim();
        if (cleanInvokeId.isEmpty()) {
            return;
        }
        mPendingInvokeNodeIds.put(cleanInvokeId, nodeId == null ? "" : nodeId.trim());
        while (mPendingInvokeNodeIds.size() > 64) {
            String oldest = mPendingInvokeNodeIds.keySet().iterator().next();
            mPendingInvokeNodeIds.remove(oldest);
        }
    }

    private synchronized String takePendingInvokeNodeId(String invokeId) {
        String cleanInvokeId = invokeId == null ? "" : invokeId.trim();
        if (cleanInvokeId.isEmpty()) {
            return "";
        }
        String nodeId = mPendingInvokeNodeIds.remove(cleanInvokeId);
        return nodeId == null ? "" : nodeId;
    }

    private String runtimeSessionId(JSONObject payload, JSONObject params) {
        String sessionKey = payload == null ? "" : payload.optString("sessionKey", "");
        if (sessionKey.isEmpty() && params != null) {
            sessionKey = params.optString("sessionKey", "");
        }
        if (sessionKey.isEmpty() && params != null) {
            sessionKey = params.optString("session_key", "");
        }
        if (!sessionKey.isEmpty()) {
            return sessionKey;
        }
        String phoneSessionId = payload == null ? "" : payload.optString("phone_session_id", "");
        if (phoneSessionId.isEmpty() && params != null) {
            phoneSessionId = params.optString("phone_session_id", "");
        }
        if (phoneSessionId.isEmpty() && params != null) {
            phoneSessionId = params.optString("phoneSessionId", "");
        }
        if (!phoneSessionId.isEmpty()) {
            return "openphone:" + principal() + ":" + phoneSessionId;
        }
        String runId = payload == null ? "" : payload.optString("runId", "");
        if (runId.isEmpty() && params != null) {
            runId = params.optString("runId", "");
        }
        if (!runId.isEmpty()) {
            return "openclaw-run:" + runId;
        }
        String nodeId = payload == null ? "" : payload.optString("nodeId", "");
        return nodeId.isEmpty() ? "node" : nodeId;
    }

    private String phoneSessionId(JSONObject payload, JSONObject params,
            String runtimeSessionId) {
        String phoneSessionId = payload == null ? "" : payload.optString("phone_session_id", "");
        if (phoneSessionId.isEmpty() && params != null) {
            phoneSessionId = params.optString("phone_session_id", "");
        }
        if (phoneSessionId.isEmpty() && params != null) {
            phoneSessionId = params.optString("phoneSessionId", "");
        }
        if (!phoneSessionId.isEmpty()) {
            return phoneSessionId;
        }
        return PhoneSessionStore.extractPhoneSessionId(runtimeSessionId);
    }

    private JSONObject mapParams(String command, String tool, JSONObject params) {
        JSONObject mapped = ExternalRuntimeRequest.copy(params);
        try {
            if ("device.apps".equals(command) && !mapped.has("query")) {
                mapped.put("query", params.optString("query", ""));
            }
            if ("openphone.screen.get".equals(command)) {
                mapped.put("include_screenshot", params.optBoolean("include_screenshot", false));
                mapped.put("include_activity", params.optBoolean("include_activity", true));
                mapped.put("include_ui_tree", params.optBoolean("include_ui_tree", true));
            }
            if ("canvas.snapshot".equals(command)) {
                mapped.put("include_screenshot", true);
                mapped.put("include_activity", params.optBoolean("include_activity", true));
                mapped.put("include_ui_tree", params.optBoolean("include_ui_tree", false));
            }
            mapped.put("_openclaw_command", command);
            mapped.put("_openphone_tool", tool);
        } catch (JSONException ignored) {
        }
        return mapped;
    }

    private void sendCanvasSnapshotResult(String invokeId, String nodeId,
            ExternalRuntimeResult result) {
        JSONObject params = new JSONObject();
        try {
            params.put("id", invokeId).put("nodeId", nodeId);
            if (!result.ok()) {
                params.put("ok", false)
                        .put("error", new JSONObject()
                                .put("code", result.errorCode().isEmpty()
                                        ? result.status() : result.errorCode())
                                .put("message", result.errorMessage().isEmpty()
                                        ? result.status() : result.errorMessage()))
                        .put("payload", result.toJson());
                sendRpc("node.invoke.result", params);
                return;
            }

            JSONObject screenshot = result.result().optJSONObject("screenshot");
            String data = screenshot == null ? "" : screenshot.optString("data", "");
            String encoding = screenshot == null ? "" : screenshot.optString("encoding", "base64");
            if (data.isEmpty() || !"base64".equalsIgnoreCase(encoding)) {
                params.put("ok", false)
                        .put("error", new JSONObject()
                                .put("code", "snapshot_unavailable")
                                .put("message", "OpenPhone screen result did not include a base64 screenshot."))
                        .put("payload", result.toJson());
                sendRpc("node.invoke.result", params);
                return;
            }

            String mimeType = screenshot.optString("mime_type", "image/jpeg")
                    .toLowerCase(Locale.US);
            String format = mimeType.contains("png") ? "png" : "jpeg";
            params.put("ok", true)
                    .put("payload", new JSONObject()
                            .put("format", format)
                            .put("base64", data));
        } catch (JSONException ignored) {
        }
        sendRpc("node.invoke.result", params);
    }

    private void sendInvokeResult(String invokeId, String nodeId, ExternalRuntimeResult result) {
        JSONObject params = new JSONObject();
        try {
            boolean accepted = result.ok()
                    || ExternalRuntimeResult.STATUS_NEEDS_CONFIRMATION.equals(result.status());
            params.put("id", invokeId)
                    .put("nodeId", nodeId)
                    .put("ok", accepted);
            if (accepted) {
                params.put("payload", result.toJson());
            } else {
                params.put("error", new JSONObject()
                        .put("code", result.errorCode().isEmpty()
                                ? result.status() : result.errorCode())
                        .put("message", result.errorMessage().isEmpty()
                                ? result.status() : result.errorMessage()));
                params.put("payload", result.toJson());
            }
        } catch (JSONException ignored) {
        }
        sendRpc("node.invoke.result", params);
    }

    private synchronized void sendConnectOnce() {
        if (mConnectSent) {
            return;
        }
        if (mConnectNonce == null || mConnectNonce.isEmpty()) {
            mStatus = "waiting_for_challenge";
            return;
        }
        mConnectSent = true;
        sendConnect();
    }

    private void sendConnect() {
        JSONObject params = new JSONObject();
        try {
            String clientId = "openclaw-android";
            String clientMode = "node";
            String role = "node";
            JSONArray scopes = connectScopes();
            String authToken = connectAuthToken();
            params.put("minProtocol", 3)
                    .put("maxProtocol", 4)
                    .put("client", clientInfo(clientId, clientMode))
                    .put("role", role)
                    .put("scopes", scopes)
                    .put("caps", capabilities())
                    .put("commands", commands())
                    .put("permissions", permissions())
                    .put("locale", Locale.getDefault().toLanguageTag())
                    .put("userAgent", "openphone-assistant/0.1")
                    .put("device", mDeviceIdentity.signedDevice(clientId, clientMode, role,
                            scopes, authToken, mConnectNonce, "android", "OpenPhone"));
            if (!authToken.isEmpty()) {
                params.put("auth", new JSONObject().put("token", authToken));
            }
        } catch (RuntimeException | JSONException e) {
            mStatus = "device_auth_error";
            Log.w(TAG, "Could not build signed OpenClaw connect frame");
            return;
        }
        sendFrame(new JSONObject(), "openphone-connect-" + System.currentTimeMillis(),
                "connect", params);
    }

    private JSONObject clientInfo(String clientId, String clientMode) throws JSONException {
        JSONObject client = new JSONObject()
                .put("id", clientId)
                .put("displayName", mSettings.label)
                .put("version", "0.1")
                .put("platform", "android")
                .put("deviceFamily", "OpenPhone")
                .put("mode", clientMode)
                .put("instanceId", instanceId());
        if (Build.MODEL != null && !Build.MODEL.trim().isEmpty()) {
            client.put("modelIdentifier", Build.MODEL.trim());
        }
        return client;
    }

    private void sendPresence(String trigger) {
        JSONObject payload = presencePayload(trigger);
        sendRpc("node.event", buildNodeEventParams("node.presence.alive", payload));
        sendEvent(new RuntimeEvent("openphone.presence.online", payload));
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

    private JSONObject buildNodeEventParams(String event, JSONObject payload) {
        JSONObject params = new JSONObject();
        try {
            params.put("event", event)
                    .put("payloadJSON", payload == null ? "{}" : payload.toString());
        } catch (JSONException ignored) {
        }
        return params;
    }

    private void sendRpc(String method, JSONObject params) {
        sendFrame(new JSONObject(), "openphone-rpc-" + UUID.randomUUID(), method, params);
    }

    private void sendFrame(JSONObject ignored, String id, String method, JSONObject params) {
        if (mClient == null) {
            return;
        }
        try {
            JSONObject frame = new JSONObject()
                    .put("type", "req")
                    .put("id", id)
                    .put("method", method)
                    .put("params", params == null ? new JSONObject() : params);
            mClient.sendText(frame.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Could not encode OpenClaw frame");
        }
    }

    private JSONArray capabilities() {
        return new JSONArray()
                .put("device")
                .put("notifications")
                .put("contacts")
                .put("calendar")
                .put("sms")
                .put("callLog")
                .put("screen")
                .put("apps")
                .put("openphone.ui");
    }

    private JSONArray commands() {
        JSONArray commands = new JSONArray();
        for (String command : mCommandToTool.keySet()) {
            commands.put(command);
        }
        return commands;
    }

    private JSONObject permissions() {
        JSONObject permissions = new JSONObject();
        for (String command : mCommandToTool.keySet()) {
            try {
                permissions.put(command, true);
            } catch (JSONException ignored) {
            }
        }
        return permissions;
    }

    private JSONArray connectScopes() {
        if (mSettings.token == null || mSettings.token.isEmpty()) {
            return mDeviceIdentity.storedDeviceTokenScopes();
        }
        return new JSONArray();
    }

    private String connectAuthToken() {
        if (mSettings.token != null && !mSettings.token.isEmpty()) {
            return mSettings.token;
        }
        return mDeviceIdentity.storedDeviceToken();
    }

    private String instanceId() {
        if (mSettings.deviceId != null && !mSettings.deviceId.isEmpty()) {
            return mSettings.deviceId;
        }
        return principal();
    }

    private String principal() {
        try {
            return mDeviceIdentity.loadOrCreate().deviceId;
        } catch (RuntimeException e) {
            return "openphone-android";
        }
    }

    private JSONObject connectPayload(JSONObject frame) {
        JSONObject payload = frame.optJSONObject("payload");
        if (payload != null) {
            return payload;
        }
        return parseObject(frame.optString("payloadJSON", "{}"));
    }

    private void persistDeviceTokens(JSONObject payload) {
        JSONObject auth = payload.optJSONObject("auth");
        if (auth == null) {
            return;
        }
        String deviceToken = auth.optString("deviceToken", "");
        if (!deviceToken.isEmpty()) {
            mDeviceIdentity.storeDeviceToken(deviceToken, auth.optJSONArray("scopes"));
        }
        JSONArray deviceTokens = auth.optJSONArray("deviceTokens");
        if (deviceTokens == null) {
            return;
        }
        for (int i = 0; i < deviceTokens.length(); i++) {
            JSONObject entry = deviceTokens.optJSONObject(i);
            if (entry == null || !"node".equals(entry.optString("role", ""))) {
                continue;
            }
            String nodeToken = entry.optString("deviceToken", "");
            if (!nodeToken.isEmpty()) {
                mDeviceIdentity.storeDeviceToken(nodeToken, entry.optJSONArray("scopes"));
            }
        }
    }

    private String connectFailureStatus(JSONObject frame) {
        JSONObject error = frame.optJSONObject("error");
        if (error == null) {
            return "auth_failed";
        }
        JSONObject details = error.optJSONObject("details");
        String code = details == null ? "" : details.optString("code", "");
        if (!code.isEmpty()) {
            return "auth_failed:" + code;
        }
        String message = error.optString("message", "");
        return message.isEmpty() ? "auth_failed" : "auth_failed:" + message;
    }

    private void registerCommands() {
        mCommandToTool.put("device.status", "phone_context");
        mCommandToTool.put("device.info", "phone_context");
        mCommandToTool.put("device.apps", "apps_search");
        mCommandToTool.put("notifications.list", "notifications_list");
        mCommandToTool.put("contacts.search", "contacts_search");
        mCommandToTool.put("calendar.events", "calendar_search");
        mCommandToTool.put("sms.search", "messages_search");
        mCommandToTool.put("callLog.search", "calls_search");
        mCommandToTool.put("openphone.screen.get", "get_screen");
        mCommandToTool.put("canvas.snapshot", "get_screen");
        mCommandToTool.put("openphone.local.screen_understanding",
                "local_screen_understanding");
        mCommandToTool.put("openphone.jobs.list", "background_job_list");
        mCommandToTool.put("notifications.open", "notifications_open");
        mCommandToTool.put("calendar.add", "calendar_create_event");
        mCommandToTool.put("calendar.update", "calendar_update_event");
        mCommandToTool.put("calendar.delete", "calendar_delete_event");
        mCommandToTool.put("sms.draft", "messages_draft");
        mCommandToTool.put("sms.send", "messages_send");
        mCommandToTool.put("calls.place", "calls_place");
        mCommandToTool.put("openphone.app.open", "open_app");
        mCommandToTool.put("openphone.url.open", "open_url");
        mCommandToTool.put("openphone.ui.tap", "tap");
        mCommandToTool.put("openphone.ui.tap_element", "tap_element");
        mCommandToTool.put("openphone.ui.long_press", "long_press");
        mCommandToTool.put("openphone.ui.long_press_element", "long_press_element");
        mCommandToTool.put("openphone.ui.swipe", "swipe");
        mCommandToTool.put("openphone.ui.type_text", "type_text");
        mCommandToTool.put("openphone.input.press_key", "press_key");
        mCommandToTool.put("openphone.clipboard.set", "set_clipboard");
        mCommandToTool.put("openphone.clipboard.paste", "paste");
        mCommandToTool.put("openphone.share.text", "share_text");
        mCommandToTool.put("openphone.jobs.create", "background_job_create");
        mCommandToTool.put("openphone.jobs.stop", "background_job_stop");
    }

    private static String cleanSource(String source) {
        String clean = source == null ? "" : source.trim().toLowerCase(Locale.US);
        if ("classic_voice".equals(clean) || "voice".equals(clean)) {
            return "classic_voice";
        }
        if ("dynamic_island".equals(clean) || "watcher".equals(clean)
                || "job".equals(clean) || "notification".equals(clean)) {
            return clean;
        }
        return "chat";
    }

    private static String cleanAutonomy(String autonomy) {
        String clean = autonomy == null ? "" : autonomy.trim().toLowerCase(Locale.US);
        if ("yolo".equals(clean) || "trusted_actions".equals(clean)) {
            return "trusted_actions";
        }
        if ("dry_run".equals(clean) || "observe_only".equals(clean)) {
            return "observe_only";
        }
        return "ask_before_action";
    }

    private static JSONObject parseObject(String raw) {
        try {
            return new JSONObject(raw == null || raw.trim().isEmpty() ? "{}" : raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

}
