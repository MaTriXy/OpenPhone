package org.openphone.assistant.runtime;

public interface RuntimeCallback {
    void onRuntimeMessage(String runtime, String sessionKey, String message, boolean terminal);
}
