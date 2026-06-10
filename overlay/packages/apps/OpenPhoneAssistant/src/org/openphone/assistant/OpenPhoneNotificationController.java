package org.openphone.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.openphone.assistant.commitments.CommitmentRecord;
import org.openphone.assistant.watchers.OpenPhoneWatcherReceiver;
import org.openphone.assistant.watchers.WatcherRecord;
import org.openphone.assistant.watchers.OpenPhoneWatcherScheduler;

public final class OpenPhoneNotificationController {
    static final String CHANNEL_ID = "openphone_agent";
    private static final String COMMITMENT_CHANNEL_ID = "openphone_commitments";
    private static final String WATCHER_CHANNEL_ID = "openphone_watchers";
    static final String ACTION_START = "org.openphone.assistant.action.START";
    static final String ACTION_STOP = "org.openphone.assistant.action.STOP";
    static final String ACTION_OPEN = "org.openphone.assistant.action.OPEN";
    static final int NOTIFICATION_ID = 1001;
    private static final int COMMITMENT_NOTIFICATION_BASE_ID = 5000;
    private static final int WATCHER_NOTIFICATION_BASE_ID = 6000;

    private OpenPhoneNotificationController() {}

    static void showReady(Context context) {
        show(context, false, context.getString(R.string.notification_agent_ready));
    }

    static void showActive(Context context, String taskId) {
        String detail = taskId == null ? context.getString(R.string.notification_agent_active)
                : context.getString(R.string.notification_agent_active) + " " + taskId;
        show(context, true, detail);
    }

    static void cancel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    public static void showCommitmentDue(Context context, CommitmentRecord commitment) {
        if (context == null || commitment == null) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        ensureCommitmentChannel(context, manager);
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_openphone_tile)
                .setContentTitle("Open loop due")
                .setContentText(commitment.title)
                .setStyle(new Notification.BigTextStyle().bigText(commitment.title))
                .setShowWhen(true)
                .setWhen(commitment.dueAtMillis > 0
                        ? commitment.dueAtMillis : System.currentTimeMillis())
                .setContentIntent(pendingBroadcast(context, ACTION_OPEN,
                        requestCode(commitment.id, 10)))
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_openphone_tile,
                        "Complete",
                        commitmentAction(context,
                                OpenPhoneWatcherScheduler.ACTION_COMPLETE_COMMITMENT,
                                commitment.id, 11)).build())
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_openphone_tile,
                        "Snooze",
                        commitmentAction(context,
                                OpenPhoneWatcherScheduler.ACTION_SNOOZE_COMMITMENT,
                                commitment.id, 12)).build())
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_openphone_tile,
                        "Dismiss",
                        commitmentAction(context,
                                OpenPhoneWatcherScheduler.ACTION_DISMISS_COMMITMENT,
                                commitment.id, 13)).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(COMMITMENT_CHANNEL_ID);
        }
        manager.notify(notificationId(commitment.id), builder.build());
    }

    public static void cancelCommitment(Context context, long commitmentId) {
        NotificationManager manager = context == null
                ? null : context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(notificationId(commitmentId));
        }
    }

    public static void showWatcherFired(Context context, WatcherRecord watcher) {
        if (context == null || watcher == null) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        ensureWatcherChannel(manager);
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_openphone_tile)
                .setContentTitle("Watcher fired")
                .setContentText(watcher.title)
                .setStyle(new Notification.BigTextStyle().bigText(watcher.title))
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingBroadcast(context, ACTION_OPEN,
                        requestCode(watcher.id, 20)))
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_openphone_tile,
                        "Open",
                        pendingBroadcast(context, ACTION_OPEN,
                                requestCode(watcher.id, 21))).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(WATCHER_CHANNEL_ID);
        }
        manager.notify(watcherNotificationId(watcher.id), builder.build());
    }

    public static void showWatcherFailed(Context context, WatcherRecord watcher, String reason) {
        if (context == null || watcher == null) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        ensureWatcherChannel(manager);
        String detail = reason == null || reason.trim().isEmpty()
                ? watcher.title : watcher.title + ": " + reason.trim();
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_openphone_tile)
                .setContentTitle("Watcher needs attention")
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingBroadcast(context, ACTION_OPEN,
                        requestCode(watcher.id, 30)))
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_openphone_tile,
                        "Open",
                        pendingBroadcast(context, ACTION_OPEN,
                                requestCode(watcher.id, 31))).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(WATCHER_CHANNEL_ID);
        }
        manager.notify(watcherNotificationId(watcher.id), builder.build());
    }

    private static void show(Context context, boolean active, String text) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_agent),
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_openphone_tile)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setOngoing(active)
                .setShowWhen(false)
                .setContentIntent(pendingBroadcast(context, ACTION_OPEN, 1));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }

        if (active) {
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_openphone_tile,
                    context.getString(R.string.action_stop_task),
                    pendingBroadcast(context, ACTION_STOP, 2)).build());
        } else {
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_openphone_tile,
                    context.getString(R.string.action_start_task),
                    pendingBroadcast(context, ACTION_START, 3)).build());
        }
        builder.addAction(new Notification.Action.Builder(
                R.drawable.ic_openphone_tile,
                context.getString(R.string.action_open_assistant),
                pendingBroadcast(context, ACTION_OPEN, 4)).build());
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static PendingIntent pendingBroadcast(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, OpenPhoneTriggerReceiver.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static PendingIntent commitmentAction(Context context, String action,
            long commitmentId, int actionCode) {
        Intent intent = new Intent(context, OpenPhoneWatcherReceiver.class);
        intent.setAction(action);
        intent.putExtra(OpenPhoneWatcherScheduler.EXTRA_COMMITMENT_ID, commitmentId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode(commitmentId, actionCode),
                intent, flags);
    }

    private static void ensureCommitmentChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    COMMITMENT_CHANNEL_ID,
                    "OpenPhone commitments",
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }

    private static void ensureWatcherChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    WATCHER_CHANNEL_ID,
                    "OpenPhone watchers",
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }

    private static int notificationId(long commitmentId) {
        long bounded = Math.max(0L, Math.min(commitmentId, 999_999L));
        return COMMITMENT_NOTIFICATION_BASE_ID + (int) bounded;
    }

    private static int watcherNotificationId(long watcherId) {
        long bounded = Math.max(0L, Math.min(watcherId, 999_999L));
        return WATCHER_NOTIFICATION_BASE_ID + (int) bounded;
    }

    private static int requestCode(long commitmentId, int actionCode) {
        long bounded = Math.max(0L, Math.min(commitmentId, 999_999L));
        return (int) (COMMITMENT_NOTIFICATION_BASE_ID + bounded * 10L + actionCode);
    }
}
