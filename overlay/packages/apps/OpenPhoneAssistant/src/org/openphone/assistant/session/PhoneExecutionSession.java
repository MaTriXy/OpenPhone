package org.openphone.assistant.session;

import org.json.JSONException;
import org.json.JSONObject;

public final class PhoneExecutionSession {
    public final String phoneSessionId;
    public final String runtimeKind;
    public final String runtimeSessionId;
    public final String attentionId;
    public final String source;
    public final String autonomy;
    public final String status;
    public final long createdAtMillis;
    public final long updatedAtMillis;
    public final String lastScreenObservationId;
    public final String lastRuntimeMessageId;
    public final String auditLogId;
    public final String summary;

    public PhoneExecutionSession(String phoneSessionId, String runtimeKind,
            String runtimeSessionId, String attentionId, String source, String autonomy,
            String status, long createdAtMillis, long updatedAtMillis,
            String lastScreenObservationId, String lastRuntimeMessageId,
            String auditLogId, String summary) {
        this.phoneSessionId = clean(phoneSessionId);
        this.runtimeKind = clean(runtimeKind);
        this.runtimeSessionId = clean(runtimeSessionId);
        this.attentionId = clean(attentionId);
        this.source = clean(source);
        this.autonomy = clean(autonomy);
        this.status = clean(status).isEmpty() ? "created" : clean(status);
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.lastScreenObservationId = clean(lastScreenObservationId);
        this.lastRuntimeMessageId = clean(lastRuntimeMessageId);
        this.auditLogId = clean(auditLogId);
        this.summary = clean(summary);
    }

    public JSONObject toJson() {
        JSONObject out = new JSONObject();
        try {
            out.put("phone_session_id", phoneSessionId)
                    .put("runtime_kind", runtimeKind)
                    .put("runtime_session_id", runtimeSessionId)
                    .put("attention_id", attentionId)
                    .put("source", source)
                    .put("autonomy", autonomy)
                    .put("status", status)
                    .put("created_at_ms", createdAtMillis)
                    .put("updated_at_ms", updatedAtMillis)
                    .put("last_screen_observation_id", lastScreenObservationId)
                    .put("last_runtime_message_id", lastRuntimeMessageId)
                    .put("audit_log_id", auditLogId)
                    .put("summary", summary);
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static PhoneExecutionSession fromJson(JSONObject json) {
        if (json == null) {
            return null;
        }
        return new PhoneExecutionSession(
                json.optString("phone_session_id", ""),
                json.optString("runtime_kind", ""),
                json.optString("runtime_session_id", ""),
                json.optString("attention_id", ""),
                json.optString("source", ""),
                json.optString("autonomy", ""),
                json.optString("status", "created"),
                json.optLong("created_at_ms", 0L),
                json.optLong("updated_at_ms", 0L),
                json.optString("last_screen_observation_id", ""),
                json.optString("last_runtime_message_id", ""),
                json.optString("audit_log_id", ""),
                json.optString("summary", ""));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
