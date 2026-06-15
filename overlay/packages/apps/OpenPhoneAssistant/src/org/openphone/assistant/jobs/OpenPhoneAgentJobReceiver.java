package org.openphone.assistant.jobs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class OpenPhoneAgentJobReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !OpenPhoneAgentJobScheduler.ACTION_CHECK.equals(intent.getAction())) {
            return;
        }
        OpenPhoneAgentJobScheduler.checkNow(context);
    }
}
