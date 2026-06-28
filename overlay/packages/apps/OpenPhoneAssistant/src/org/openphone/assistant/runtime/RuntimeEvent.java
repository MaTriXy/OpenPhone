package org.openphone.assistant.runtime;

import org.json.JSONObject;

public final class RuntimeEvent {
    private final String mEvent;
    private final JSONObject mPayload;

    public RuntimeEvent(String event, JSONObject payload) {
        mEvent = event == null ? "" : event.trim();
        mPayload = payload == null ? new JSONObject() : RuntimeToolRequest.copy(payload);
    }

    public String event() {
        return mEvent;
    }

    public JSONObject payload() {
        return RuntimeToolRequest.copy(mPayload);
    }
}
