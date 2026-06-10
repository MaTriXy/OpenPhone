package org.openphone.assistant;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.context.ContextIndexStore;
import org.openphone.assistant.watchers.OpenPhoneWatcherScheduler;

public final class OpenPhoneNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "OpenPhoneNotifIndex";
    private static final int MAX_FIELD_LENGTH = 512;
    private static volatile OpenPhoneNotificationListenerService sInstance;

    @Override
    public void onListenerConnected() {
        sInstance = this;
        try {
            StatusBarNotification[] notifications = getActiveNotifications();
            if (notifications == null) {
                return;
            }
            ContextIndexStore store = new ContextIndexStore(this);
            for (StatusBarNotification sbn : notifications) {
                record(store, "notification.active", sbn);
            }
            Log.i(TAG, "Indexed active notifications: " + notifications.length);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to index active notifications", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sInstance == this) {
            sInstance = null;
        }
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            NotificationEvent event = record(new ContextIndexStore(this),
                    "notification.posted", sbn);
            if (event != null) {
                OpenPhoneWatcherScheduler.onNotificationPosted(this, event.packageName,
                        event.title, event.text, event.notificationKey,
                        event.observedAtMillis);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to index notification post", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            record(new ContextIndexStore(this), "notification.removed", sbn);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to index notification removal", e);
        }
    }

    public static void ensureEnabled(Context context) {
        if (context == null) {
            return;
        }
        ComponentName component = new ComponentName(context,
                OpenPhoneNotificationListenerService.class);
        String flattened = component.flattenToString();
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.setNotificationListenerAccessGranted(component, true, false);
                return;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "NotificationManager listener grant failed; falling back", e);
        }
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    "enabled_notification_listeners");
            if (enabled == null || enabled.isEmpty()) {
                enabled = flattened;
            } else if (!containsService(enabled, flattened)) {
                enabled = enabled + ":" + flattened;
            }
            Settings.Secure.putString(context.getContentResolver(),
                    "enabled_notification_listeners", enabled);
        } catch (SecurityException e) {
            Log.w(TAG, "Notification listener enable denied", e);
        }
    }

    public static String openMatchingNotification(String notificationKey, String packageName,
            String query) {
        OpenPhoneNotificationListenerService service = sInstance;
        if (service == null) {
            return "notification_listener_unavailable";
        }
        try {
            StatusBarNotification[] notifications = service.getActiveNotifications();
            if (notifications == null || notifications.length == 0) {
                return "no_active_notifications";
            }
            StatusBarNotification best = null;
            for (StatusBarNotification sbn : notifications) {
                if (!matches(sbn, notificationKey, packageName, query)) {
                    continue;
                }
                best = sbn;
                break;
            }
            if (best == null) {
                return "notification_not_found";
            }
            Notification notification = best.getNotification();
            PendingIntent intent = notification == null ? null : notification.contentIntent;
            if (intent == null) {
                return "notification_has_no_content_intent";
            }
            intent.send();
            return "notification.opened";
        } catch (PendingIntent.CanceledException e) {
            return "notification_intent_cancelled";
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to open matching notification", e);
            return "notification_open_failed";
        }
    }

    private static NotificationEvent record(ContextIndexStore store, String sourceType,
            StatusBarNotification sbn) {
        if (store == null || sbn == null) {
            return null;
        }
        Notification notification = sbn.getNotification();
        boolean secret = notification != null
                && notification.visibility == Notification.VISIBILITY_SECRET;
        Bundle extras = notification == null ? null : notification.extras;
        String title = secret ? "<redacted notification>"
                : truncate(extractText(extras, Notification.EXTRA_TITLE));
        String text = secret ? "<redacted notification>"
                : truncate(firstNonEmpty(
                        extractText(extras, Notification.EXTRA_TEXT),
                        extractText(extras, Notification.EXTRA_BIG_TEXT),
                        extractText(extras, Notification.EXTRA_SUB_TEXT)));
        JSONObject payload = new JSONObject();
        try {
            payload.put("key", sbn.getKey())
                    .put("id", sbn.getId())
                    .put("tag", sbn.getTag() == null ? "" : sbn.getTag())
                    .put("post_time", sbn.getPostTime())
                    .put("clearable", sbn.isClearable())
                    .put("ongoing", sbn.isOngoing())
                    .put("group_key", sbn.getGroupKey() == null ? "" : sbn.getGroupKey())
                    .put("redacted", secret);
            if (notification != null) {
                payload.put("category", notification.category == null
                                ? "" : notification.category)
                        .put("visibility", notification.visibility);
            }
        } catch (JSONException ignored) {
        }
        store.recordNotificationEvent(sourceType, sbn.getPackageName(), sbn.getKey(),
                title, text, sbn.getPostTime(), payload.toString());
        return new NotificationEvent(sbn.getPackageName(), sbn.getKey(), title, text,
                sbn.getPostTime());
    }

    private static String extractText(Bundle extras, String key) {
        if (extras == null || key == null) {
            return "";
        }
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String truncate(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() <= MAX_FIELD_LENGTH) {
            return clean;
        }
        return clean.substring(0, MAX_FIELD_LENGTH);
    }

    private static boolean containsService(String enabled, String flattened) {
        if (enabled == null || flattened == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (flattened.equals(splitter.next())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(StatusBarNotification sbn, String notificationKey,
            String packageName, String query) {
        if (sbn == null) {
            return false;
        }
        String cleanKey = notificationKey == null ? "" : notificationKey.trim();
        if (!cleanKey.isEmpty() && !cleanKey.equals(sbn.getKey())) {
            return false;
        }
        String cleanPackage = packageName == null ? "" : packageName.trim().toLowerCase();
        if (!cleanPackage.isEmpty()
                && !sbn.getPackageName().toLowerCase().contains(cleanPackage)) {
            return false;
        }
        String cleanQuery = query == null ? "" : query.trim().toLowerCase();
        if (cleanQuery.isEmpty()) {
            return !cleanKey.isEmpty() || !cleanPackage.isEmpty();
        }
        Notification notification = sbn.getNotification();
        Bundle extras = notification == null ? null : notification.extras;
        String title = extractText(extras, Notification.EXTRA_TITLE);
        String text = firstNonEmpty(
                extractText(extras, Notification.EXTRA_TEXT),
                extractText(extras, Notification.EXTRA_BIG_TEXT),
                extractText(extras, Notification.EXTRA_SUB_TEXT));
        String haystack = (sbn.getPackageName() + " " + title + " " + text).toLowerCase();
        return haystack.contains(cleanQuery);
    }

    private static final class NotificationEvent {
        final String packageName;
        final String notificationKey;
        final String title;
        final String text;
        final long observedAtMillis;

        NotificationEvent(String packageName, String notificationKey, String title, String text,
                long observedAtMillis) {
            this.packageName = packageName == null ? "" : packageName;
            this.notificationKey = notificationKey == null ? "" : notificationKey;
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
            this.observedAtMillis = observedAtMillis;
        }
    }
}
