package org.openphone.assistant.runtime;

public final class RuntimeConfirmationResolution {
    private final RuntimePendingConfirmation mPending;
    private final RuntimeToolResult mResult;

    public RuntimeConfirmationResolution(RuntimePendingConfirmation pending,
            RuntimeToolResult result) {
        mPending = pending;
        mResult = result;
    }

    public RuntimePendingConfirmation pending() {
        return mPending;
    }

    public RuntimeToolRequest request() {
        return mPending == null ? null : mPending.request();
    }

    public RuntimeToolResult result() {
        return mResult;
    }
}
