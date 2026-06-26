package org.openphone.assistant.external;

public interface ExternalRuntimeCallback {
    void onRuntimeMessage(String runtime, String sessionKey, String message, boolean terminal);
}
