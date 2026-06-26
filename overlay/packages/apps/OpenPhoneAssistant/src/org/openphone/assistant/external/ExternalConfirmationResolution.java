package org.openphone.assistant.external;

public final class ExternalConfirmationResolution {
    private final ExternalPendingConfirmation mPending;
    private final ExternalRuntimeResult mResult;

    public ExternalConfirmationResolution(ExternalPendingConfirmation pending,
            ExternalRuntimeResult result) {
        mPending = pending;
        mResult = result;
    }

    public ExternalPendingConfirmation pending() {
        return mPending;
    }

    public ExternalRuntimeRequest request() {
        return mPending == null ? null : mPending.request();
    }

    public ExternalRuntimeResult result() {
        return mResult;
    }
}
