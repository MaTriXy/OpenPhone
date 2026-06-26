package org.openphone.assistant.external;

public interface RuntimeAdapter {
    String name();
    void start();
    void stop();
    String status();
    void sendEvent(RuntimeEvent event);
    void sendToolResult(ExternalRuntimeRequest request, ExternalRuntimeResult result);
}
