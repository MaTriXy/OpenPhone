package org.openphone.assistant.runtime;

import org.json.JSONObject;

public interface RuntimeAdapter {
    String name();
    void start();
    void stop();
    String status();
    String requestAttention(String phoneSessionId, String attentionId, String source,
            String text, String autonomy, boolean includeScreen, JSONObject context);
    void sendEvent(RuntimeEvent event);
    void sendToolResult(RuntimeToolRequest request, RuntimeToolResult result);
}
