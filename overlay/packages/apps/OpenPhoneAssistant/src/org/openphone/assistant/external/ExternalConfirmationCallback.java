package org.openphone.assistant.external;

public interface ExternalConfirmationCallback {
    void onExternalConfirmationRequested(ExternalPendingConfirmation pending,
            ExternalRuntimeResult initialResult);

    void onExternalConfirmationTimedOut(ExternalPendingConfirmation pending,
            ExternalRuntimeResult timeoutResult);
}
