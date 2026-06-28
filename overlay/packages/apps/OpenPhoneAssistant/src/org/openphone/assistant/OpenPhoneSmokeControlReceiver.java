package org.openphone.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public final class OpenPhoneSmokeControlReceiver extends BroadcastReceiver {
    private static final String TAG = "OpenPhoneAssistant";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isDebugBuild() || context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!isAllowedAction(action)) {
            Log.w(TAG, "Ignoring unsupported smoke control action " + action);
            return;
        }
        Intent service = new Intent(context, OpenPhoneAssistantService.class);
        service.setAction(action);
        if (intent.getExtras() != null) {
            service.putExtras(intent.getExtras());
        }
        context.startService(service);
    }

    private static boolean isAllowedAction(String action) {
        return OpenPhoneAssistantService.ACTION_LOG_RUNTIME_STATUS.equals(action)
                || OpenPhoneAssistantService.ACTION_RELOAD_RUNTIMES.equals(action)
                || OpenPhoneAssistantService.ACTION_REQUEST_RUNTIME_ATTENTION.equals(action)
                || OpenPhoneNotificationController.ACTION_EXTERNAL_APPROVE.equals(action)
                || OpenPhoneNotificationController.ACTION_EXTERNAL_DENY.equals(action);
    }

    private static boolean isDebugBuild() {
        return "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE);
    }
}
