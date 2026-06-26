package org.openphone.assistant.external;

import org.json.JSONObject;

public final class RuntimeEvent {
    private final String mEvent;
    private final JSONObject mPayload;

    public RuntimeEvent(String event, JSONObject payload) {
        mEvent = event == null ? "" : event.trim();
        mPayload = payload == null ? new JSONObject() : ExternalRuntimeRequest.copy(payload);
    }

    public String event() {
        return mEvent;
    }

    public JSONObject payload() {
        return ExternalRuntimeRequest.copy(mPayload);
    }
}
