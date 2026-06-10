package org.openphone.assistant.watchers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class OpenPhoneWatcherReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (OpenPhoneWatcherScheduler.ACTION_CHECK.equals(action)) {
            OpenPhoneWatcherScheduler.checkNow(context);
            return;
        }
        long commitmentId = intent == null ? -1L
                : intent.getLongExtra(OpenPhoneWatcherScheduler.EXTRA_COMMITMENT_ID, -1L);
        if (OpenPhoneWatcherScheduler.ACTION_COMPLETE_COMMITMENT.equals(action)) {
            OpenPhoneWatcherScheduler.completeCommitment(context, commitmentId);
            return;
        }
        if (OpenPhoneWatcherScheduler.ACTION_SNOOZE_COMMITMENT.equals(action)) {
            OpenPhoneWatcherScheduler.snoozeCommitment(context, commitmentId);
            return;
        }
        if (OpenPhoneWatcherScheduler.ACTION_DISMISS_COMMITMENT.equals(action)) {
            OpenPhoneWatcherScheduler.dismissCommitment(context, commitmentId);
        }
    }
}
