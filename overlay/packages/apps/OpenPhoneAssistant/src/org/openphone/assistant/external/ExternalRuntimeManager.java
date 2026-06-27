package org.openphone.assistant.external;

import android.content.Context;
import android.openphone.OpenPhoneAgentManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.OpenPhoneNotificationController;
import org.openphone.assistant.session.PhoneExecutionSession;
import org.openphone.assistant.session.PhoneSessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ExternalRuntimeManager implements ExternalConfirmationCallback {
    private static final String TAG = "OpenPhoneExternal";

    private final Context mContext;
    private final OpenPhoneAgentManager mAgentManager;
    private final OpenPhoneToolBridge mToolBridge;
    private final PhoneSessionStore mSessionStore;
    private final List<RuntimeAdapter> mAdapters = new ArrayList<>();
    private ExternalRuntimeCallback mRuntimeCallback;
    private String mStatus = "disabled";

    public ExternalRuntimeManager(Context context, OpenPhoneAgentManager agentManager) {
        mContext = context;
        mAgentManager = agentManager;
        mSessionStore = new PhoneSessionStore(context);
        mToolBridge = new OpenPhoneToolBridge(context, agentManager, mSessionStore);
        mToolBridge.setConfirmationCallback(this);
    }

    public synchronized void start() {
        stopLocked();
        ExternalRuntimeConfig config = ExternalRuntimeConfig.load(mContext);
        if (mAgentManager == null) {
            mStatus = "framework_unavailable";
            return;
        }
        if (!config.anyEnabled()) {
            mStatus = "disabled";
            return;
        }
        if (config.openClaw.configured()) {
            mAdapters.add(new OpenClawRuntimeAdapter(mContext, config.openClaw, mToolBridge,
                    new ExternalRuntimeCallback() {
                        @Override
                        public void onRuntimeMessage(String runtime, String sessionKey,
                                String message, boolean terminal) {
                            dispatchRuntimeMessage(runtime, sessionKey, message, terminal);
                        }
                    }));
        }
        if (mAdapters.isEmpty()) {
            mStatus = "not_configured";
            return;
        }
        for (RuntimeAdapter adapter : mAdapters) {
            adapter.start();
        }
        mStatus = "connecting";
        Log.i(TAG, "external runtime manager started adapters=" + mAdapters.size());
    }

    public synchronized void stop() {
        stopLocked();
        mStatus = "stopped";
    }

    public synchronized String statusJson() {
        JSONArray adapters = new JSONArray();
        for (RuntimeAdapter adapter : mAdapters) {
            try {
                adapters.put(new JSONObject()
                        .put("name", adapter.name())
                        .put("status", adapter.status()));
            } catch (JSONException ignored) {
            }
        }
        String aggregateStatus = aggregateStatusLocked();
        if (!"connecting".equals(aggregateStatus) || "connecting".equals(mStatus)) {
            mStatus = aggregateStatus;
        }
        try {
            return new JSONObject()
                    .put("status", aggregateStatus)
                    .put("manager_status", mStatus)
                    .put("updated_at_ms", System.currentTimeMillis())
                    .put("adapters", adapters)
                    .toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\"}";
        }
    }

    public synchronized void sendEvent(RuntimeEvent event) {
        for (RuntimeAdapter adapter : mAdapters) {
            adapter.sendEvent(event);
        }
    }

    public synchronized void setRuntimeCallback(ExternalRuntimeCallback callback) {
        mRuntimeCallback = callback;
    }

    public synchronized String requestOpenClawAttention(String text, String source,
            String autonomy, boolean includeScreen, JSONObject context) {
        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) {
            return resultJson(false, "missing_text", "OpenClaw attention text is required.");
        }
        for (RuntimeAdapter adapter : mAdapters) {
            if (adapter instanceof OpenClawRuntimeAdapter) {
                OpenClawRuntimeAdapter openClaw = (OpenClawRuntimeAdapter) adapter;
                if (!"connected".equals(openClaw.status())) {
                    return resultJson(false, "openclaw_not_connected",
                            "OpenClaw runtime is not connected.");
                }
                String attentionId = "attn-" + UUID.randomUUID();
                PhoneExecutionSession session = mSessionStore.create("openclaw", "",
                        attentionId, source, autonomy, message);
                String sessionKey = openClaw.requestAttention(session.phoneSessionId,
                        attentionId, source, message, autonomy, includeScreen, context);
                if (!sessionKey.isEmpty()) {
                    mSessionStore.upsert(session.phoneSessionId, "openclaw", sessionKey,
                            attentionId, source, autonomy, "waiting_for_runtime", message);
                    return resultJson(true, "sent", "OpenClaw attention request sent.",
                            session.phoneSessionId, sessionKey);
                }
                mSessionStore.touchStatus(session.phoneSessionId, "failed");
                return resultJson(false, "send_failed",
                        "Could not send OpenClaw attention request.",
                        session.phoneSessionId, "");
            }
        }
        return resultJson(false, "openclaw_not_configured",
                "OpenClaw runtime is not configured.", "", "");
    }

    public synchronized String resolveExternalConfirmation(String confirmationId,
            boolean approved) {
        ExternalConfirmationResolution resolution =
                mToolBridge.resolveConfirmation(confirmationId, approved);
        ExternalRuntimeRequest request = resolution.request();
        ExternalRuntimeResult result = resolution.result();
        if (resolution.pending() != null) {
            OpenPhoneNotificationController.cancelExternalRuntimeConfirmation(
                    mContext, resolution.pending().confirmationId());
        }
        sendResolutionToRuntime(request, result);
        return result == null ? "{\"status\":\"error\"}" : result.toWireString();
    }

    @Override
    public synchronized void onExternalConfirmationRequested(ExternalPendingConfirmation pending,
            ExternalRuntimeResult initialResult) {
        if (pending == null || pending.request() == null) {
            return;
        }
        OpenPhoneNotificationController.showExternalRuntimeConfirmation(
                mContext,
                pending.confirmationId(),
                pending.request().runtime(),
                pending.request().tool(),
                pending.summary());
        sendEventToRuntime(pending.request().runtime(),
                new RuntimeEvent("openphone.confirmation.required",
                        confirmationPayload(pending, initialResult)));
    }

    @Override
    public synchronized void onExternalConfirmationTimedOut(ExternalPendingConfirmation pending,
            ExternalRuntimeResult timeoutResult) {
        if (pending == null || pending.request() == null || timeoutResult == null) {
            return;
        }
        OpenPhoneNotificationController.cancelExternalRuntimeConfirmation(
                mContext, pending.confirmationId());
        sendResolutionToRuntime(pending.request(), timeoutResult);
    }

    private void sendResolutionToRuntime(ExternalRuntimeRequest request,
            ExternalRuntimeResult result) {
        if (request == null || result == null) {
            return;
        }
        sendResultToRuntime(request.runtime(), request, result);
    }

    private void dispatchRuntimeMessage(String runtime, String sessionKey, String message,
            boolean terminal) {
        ExternalRuntimeCallback callback = mRuntimeCallback;
        if (callback != null) {
            callback.onRuntimeMessage(runtime, sessionKey, message, terminal);
        }
    }

    private void sendResultToRuntime(String runtime, ExternalRuntimeRequest request,
            ExternalRuntimeResult result) {
        for (RuntimeAdapter adapter : mAdapters) {
            if (adapter.name().equals(runtime)) {
                adapter.sendToolResult(request, result);
            }
        }
    }

    private void sendEventToRuntime(String runtime, RuntimeEvent event) {
        if (runtime == null || runtime.isEmpty() || event == null) {
            return;
        }
        for (RuntimeAdapter adapter : mAdapters) {
            if (adapter.name().equals(runtime)) {
                adapter.sendEvent(event);
            }
        }
    }

    private void stopLocked() {
        sendEvent(new RuntimeEvent("openphone.presence.offline", presencePayload("stop")));
        for (RuntimeAdapter adapter : mAdapters) {
            adapter.stop();
        }
        mAdapters.clear();
    }

    private String aggregateStatusLocked() {
        if (mAdapters.isEmpty()) {
            return mStatus;
        }
        boolean anyConnected = false;
        boolean anyConnecting = false;
        boolean anyAuthFailed = false;
        boolean anyInsecureTransport = false;
        boolean anyOffline = false;
        for (RuntimeAdapter adapter : mAdapters) {
            String status = adapter.status();
            if ("connected".equals(status)) {
                anyConnected = true;
            } else if ("connecting".equals(status) || "waiting_for_challenge".equals(status)
                    || "handshaking".equals(status)) {
                anyConnecting = true;
            } else if (status != null && status.startsWith("auth_failed")) {
                anyAuthFailed = true;
            } else if ("insecure_transport_denied".equals(status)) {
                anyInsecureTransport = true;
            } else {
                anyOffline = true;
            }
        }
        if (anyInsecureTransport) {
            return anyConnected ? "degraded" : "insecure_transport_denied";
        }
        if (anyAuthFailed) {
            return anyConnected ? "degraded" : "auth_failed";
        }
        if (anyConnected) {
            return anyOffline || anyConnecting ? "degraded" : "connected";
        }
        if (anyConnecting) {
            return "connecting";
        }
        return "offline";
    }

    private JSONObject confirmationPayload(ExternalPendingConfirmation pending,
            ExternalRuntimeResult initialResult) {
        JSONObject payload = new JSONObject();
        ExternalRuntimeRequest request = pending == null ? null : pending.request();
        try {
            payload.put("confirmation_id", pending == null ? "" : pending.confirmationId())
                    .put("request_id", request == null ? "" : request.requestId())
                    .put("runtime", request == null ? "" : request.runtime())
                    .put("runtime_session_id", request == null ? "" : request.runtimeSessionId())
                    .put("tool", request == null ? "" : request.tool())
                    .put("summary", pending == null ? "" : pending.summary())
                    .put("reason", request == null ? "" : request.reason())
                    .put("autonomy", request == null ? "" : request.autonomy())
                    .put("timeout_ms", request == null ? 0L : request.timeoutMs())
                    .put("result", initialResult == null
                            ? new JSONObject() : initialResult.toJson());
        } catch (JSONException ignored) {
        }
        return payload;
    }

    private JSONObject presencePayload(String trigger) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("trigger", trigger == null ? "" : trigger)
                    .put("sentAtMs", System.currentTimeMillis())
                    .put("runtime_manager_status", mStatus);
        } catch (JSONException ignored) {
        }
        return payload;
    }

    private static String resultJson(boolean ok, String status, String message) {
        return resultJson(ok, status, message, "", "");
    }

    private static String resultJson(boolean ok, String status, String message,
            String phoneSessionId, String runtimeSessionId) {
        try {
            return new JSONObject()
                    .put("ok", ok)
                    .put("status", status == null ? "" : status)
                    .put("message", message == null ? "" : message)
                    .put("phone_session_id", phoneSessionId == null ? "" : phoneSessionId)
                    .put("runtime_session_id", runtimeSessionId == null ? "" : runtimeSessionId)
                    .toString();
        } catch (JSONException e) {
            return ok ? "{\"ok\":true}" : "{\"ok\":false}";
        }
    }
}
