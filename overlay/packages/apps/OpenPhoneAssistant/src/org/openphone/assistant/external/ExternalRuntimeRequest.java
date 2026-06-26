package org.openphone.assistant.external;

import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.session.PhoneSessionStore;

public final class ExternalRuntimeRequest {
    private final String mRequestId;
    private final String mRuntime;
    private final String mRuntimeSessionId;
    private final ExternalRuntimeIdentity mCaller;
    private final String mTool;
    private final JSONObject mParams;
    private final String mReason;
    private final String mAutonomy;
    private final String mIdempotencyKey;
    private final long mTimeoutMs;
    private final String mOpenPhoneTaskId;
    private final String mPhoneSessionId;

    public ExternalRuntimeRequest(String requestId, String runtime, String runtimeSessionId,
            ExternalRuntimeIdentity caller, String tool, JSONObject params, String reason,
            String autonomy, String idempotencyKey, long timeoutMs, String openPhoneTaskId) {
        this(requestId, runtime, runtimeSessionId, "", caller, tool, params, reason, autonomy,
                idempotencyKey, timeoutMs, openPhoneTaskId);
    }

    public ExternalRuntimeRequest(String requestId, String runtime, String runtimeSessionId,
            String phoneSessionId, ExternalRuntimeIdentity caller, String tool, JSONObject params,
            String reason, String autonomy, String idempotencyKey, long timeoutMs,
            String openPhoneTaskId) {
        mRequestId = clean(requestId);
        mRuntime = clean(runtime);
        mRuntimeSessionId = clean(runtimeSessionId);
        String cleanPhoneSessionId = clean(phoneSessionId);
        mPhoneSessionId = cleanPhoneSessionId.isEmpty()
                ? PhoneSessionStore.extractPhoneSessionId(mRuntimeSessionId)
                : cleanPhoneSessionId;
        mCaller = caller == null
                ? new ExternalRuntimeIdentity(mRuntime, "", mRuntime) : caller;
        mTool = clean(tool);
        mParams = params == null ? new JSONObject() : copy(params);
        mReason = clean(reason);
        mAutonomy = clean(autonomy).isEmpty() ? "read_only" : clean(autonomy);
        mIdempotencyKey = clean(idempotencyKey);
        mTimeoutMs = timeoutMs <= 0 ? 30000L : timeoutMs;
        mOpenPhoneTaskId = clean(openPhoneTaskId);
    }

    public String requestId() {
        return mRequestId;
    }

    public String runtime() {
        return mRuntime;
    }

    public String runtimeSessionId() {
        return mRuntimeSessionId;
    }

    public ExternalRuntimeIdentity caller() {
        return mCaller;
    }

    public String tool() {
        return mTool;
    }

    public JSONObject params() {
        return copy(mParams);
    }

    public String reason() {
        return mReason;
    }

    public String autonomy() {
        return mAutonomy;
    }

    public String idempotencyKey() {
        return mIdempotencyKey;
    }

    public long timeoutMs() {
        return mTimeoutMs;
    }

    public String openPhoneTaskId() {
        return mOpenPhoneTaskId;
    }

    public String phoneSessionId() {
        return mPhoneSessionId;
    }

    public String sessionKey() {
        String session = mRuntimeSessionId.isEmpty() ? "global" : mRuntimeSessionId;
        return mRuntime + ":" + session;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("request_id", mRequestId)
                .put("runtime", mRuntime)
                .put("runtime_session_id", mRuntimeSessionId)
                .put("phone_session_id", mPhoneSessionId)
                .put("caller", mCaller.toJson())
                .put("tool", mTool)
                .put("params", copy(mParams))
                .put("reason", mReason)
                .put("autonomy", mAutonomy)
                .put("idempotency_key", mIdempotencyKey)
                .put("timeout_ms", mTimeoutMs)
                .put("openphone_task_id", mOpenPhoneTaskId);
    }

    public static ExternalRuntimeRequest fromJson(JSONObject json) {
        JSONObject params = json == null ? null : json.optJSONObject("params");
        String runtime = json == null ? "" : json.optString("runtime", "");
        return new ExternalRuntimeRequest(
                json == null ? "" : json.optString("request_id", ""),
                runtime,
                json == null ? "" : json.optString("runtime_session_id", ""),
                json == null ? "" : json.optString("phone_session_id", ""),
                ExternalRuntimeIdentity.fromJson(json == null
                        ? null : json.optJSONObject("caller"), runtime),
                json == null ? "" : json.optString("tool", ""),
                params,
                json == null ? "" : json.optString("reason", ""),
                json == null ? "" : json.optString("autonomy", "read_only"),
                json == null ? "" : json.optString("idempotency_key", ""),
                json == null ? 30000L : json.optLong("timeout_ms", 30000L),
                json == null ? "" : json.optString("openphone_task_id", ""));
    }

    static JSONObject copy(JSONObject source) {
        try {
            return new JSONObject(source == null ? "{}" : source.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
