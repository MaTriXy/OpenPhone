package org.openphone.assistant.runtime;

public interface RuntimeConfirmationCallback {
    void onRuntimeConfirmationRequested(RuntimePendingConfirmation pending,
            RuntimeToolResult initialResult);

    void onRuntimeConfirmationTimedOut(RuntimePendingConfirmation pending,
            RuntimeToolResult timeoutResult);
}
