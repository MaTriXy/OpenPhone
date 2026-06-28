package org.openphone.assistant.runtime;

import org.json.JSONException;
import org.json.JSONObject;

public final class RuntimePendingConfirmation {
    private final String mConfirmationId;
    private final RuntimeToolRequest mRequest;
    private final long mCreatedAtMillis;
    private final String mSummary;

    public RuntimePendingConfirmation(String confirmationId, RuntimeToolRequest request,
            long createdAtMillis, String summary) {
        mConfirmationId = clean(confirmationId);
        mRequest = request;
        mCreatedAtMillis = createdAtMillis;
        mSummary = clean(summary);
    }

    public String confirmationId() {
        return mConfirmationId;
    }

    public RuntimeToolRequest request() {
        return mRequest;
    }

    public long createdAtMillis() {
        return mCreatedAtMillis;
    }

    public String summary() {
        return mSummary;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("confirmation_id", mConfirmationId)
                .put("created_at_ms", mCreatedAtMillis)
                .put("summary", mSummary)
                .put("request", mRequest == null ? new JSONObject() : mRequest.toJson());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
