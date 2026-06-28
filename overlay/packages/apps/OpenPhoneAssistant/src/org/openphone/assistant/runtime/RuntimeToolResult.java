package org.openphone.assistant.runtime;

import org.json.JSONException;
import org.json.JSONObject;

public final class RuntimeToolResult {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_NEEDS_CONFIRMATION = "needs_confirmation";
    public static final String STATUS_DENIED = "denied";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_CANCELLED = "cancelled";

    private final String mRequestId;
    private final String mStatus;
    private final JSONObject mResult;
    private final String mErrorCode;
    private final String mErrorMessage;
    private final String mAuditId;
    private final String mOpenPhoneTaskId;

    private RuntimeToolResult(String requestId, String status, JSONObject result,
            String errorCode, String errorMessage, String auditId, String openPhoneTaskId) {
        mRequestId = clean(requestId);
        mStatus = clean(status).isEmpty() ? STATUS_ERROR : clean(status);
        mResult = result == null ? new JSONObject() : RuntimeToolRequest.copy(result);
        mErrorCode = clean(errorCode);
        mErrorMessage = clean(errorMessage);
        mAuditId = clean(auditId);
        mOpenPhoneTaskId = clean(openPhoneTaskId);
    }

    public String requestId() {
        return mRequestId;
    }

    public String status() {
        return mStatus;
    }

    public boolean ok() {
        return STATUS_OK.equals(mStatus);
    }

    public JSONObject result() {
        return RuntimeToolRequest.copy(mResult);
    }

    public String errorCode() {
        return mErrorCode;
    }

    public String errorMessage() {
        return mErrorMessage;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject()
                .put("request_id", mRequestId)
                .put("status", mStatus)
                .put("result", RuntimeToolRequest.copy(mResult))
                .put("audit_id", mAuditId)
                .put("requires_confirmation", STATUS_NEEDS_CONFIRMATION.equals(mStatus));
        if (!mOpenPhoneTaskId.isEmpty()) {
            json.put("openphone_task_id", mOpenPhoneTaskId);
        }
        if (!mErrorCode.isEmpty() || !mErrorMessage.isEmpty()) {
            json.put("error", new JSONObject()
                    .put("code", mErrorCode)
                    .put("message", mErrorMessage));
        }
        return json;
    }

    public String toWireString() {
        try {
            return toJson().toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\",\"error\":{\"code\":\"json_error\"}}";
        }
    }

    public RuntimeToolResult withOpenPhoneTaskId(String taskId) {
        return new RuntimeToolResult(mRequestId, mStatus, mResult, mErrorCode,
                mErrorMessage, mAuditId, taskId);
    }

    public RuntimeToolResult forRequest(String requestId, String auditId) {
        return new RuntimeToolResult(requestId, mStatus, mResult, mErrorCode,
                mErrorMessage, auditId, mOpenPhoneTaskId);
    }

    public static RuntimeToolResult ok(String requestId, JSONObject result,
            String auditId, String taskId) {
        return new RuntimeToolResult(requestId, STATUS_OK, result, "", "",
                auditId, taskId);
    }

    public static RuntimeToolResult needsConfirmation(String requestId, String code,
            String message, JSONObject details, String auditId, String taskId) {
        return new RuntimeToolResult(requestId, STATUS_NEEDS_CONFIRMATION, details,
                code, message, auditId, taskId);
    }

    public static RuntimeToolResult denied(String requestId, String code,
            String message, JSONObject details, String auditId) {
        return new RuntimeToolResult(requestId, STATUS_DENIED, details,
                code, message, auditId, "");
    }

    public static RuntimeToolResult error(String requestId, String code,
            String message) {
        return new RuntimeToolResult(requestId, STATUS_ERROR, new JSONObject(),
                code, message, "", "");
    }

    public static RuntimeToolResult timeout(String requestId, String message) {
        return timeout(requestId, message, "");
    }

    public static RuntimeToolResult timeout(String requestId, String message,
            String auditId) {
        return new RuntimeToolResult(requestId, STATUS_TIMEOUT, new JSONObject(),
                "timeout", message, auditId, "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
