// Compile-check stub for android.app.NotificationManager.
//
// The assistant builds with platform_apis: true and uses the hidden
// @SystemApi setNotificationListenerAccessGranted(ComponentName, boolean,
// boolean) overload, which is absent from the public android.jar. This
// source stub shadows the SDK class during the javac gate and must declare
// every NotificationManager method the assistant sources call.
package android.app;

import android.content.ComponentName;

public class NotificationManager {
    public static final int IMPORTANCE_NONE = 0;
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_DEFAULT = 3;
    public static final int IMPORTANCE_HIGH = 4;

    public void notify(int id, Notification notification) {
        throw new UnsupportedOperationException("compile-check stub");
    }

    public void cancel(int id) {
        throw new UnsupportedOperationException("compile-check stub");
    }

    public void createNotificationChannel(NotificationChannel channel) {
        throw new UnsupportedOperationException("compile-check stub");
    }

    public void setNotificationListenerAccessGranted(ComponentName listener,
            boolean granted, boolean userSet) {
        throw new UnsupportedOperationException("compile-check stub");
    }
}
