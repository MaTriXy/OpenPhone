package org.openphone.assistant.watchers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.OpenPhoneNotificationController;
import org.openphone.assistant.PointerOverlayController;
import org.openphone.assistant.commitments.CommitmentRecord;
import org.openphone.assistant.commitments.CommitmentStore;
import org.openphone.assistant.model.ModelEndpointConfig;
import org.openphone.assistant.model.OpenAiResponsesAgentAdapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.List;

public final class OpenPhoneWatcherScheduler {
    private static final String TAG = "OpenPhoneWatcher";
    public static final String ACTION_CHECK =
            "org.openphone.assistant.action.CHECK_WATCHERS";
    public static final String ACTION_COMPLETE_COMMITMENT =
            "org.openphone.assistant.action.COMPLETE_COMMITMENT";
    public static final String ACTION_SNOOZE_COMMITMENT =
            "org.openphone.assistant.action.SNOOZE_COMMITMENT";
    public static final String ACTION_DISMISS_COMMITMENT =
            "org.openphone.assistant.action.DISMISS_COMMITMENT";
    public static final String EXTRA_COMMITMENT_ID =
            "org.openphone.assistant.extra.COMMITMENT_ID";
    private static final long MIN_DELAY_MILLIS = 15_000L;
    private static final long SNOOZE_MILLIS = 60L * 60L * 1000L;
    private static final long STUCK_TIMEOUT_MILLIS = 10L * 60L * 1000L;
    private static final long DEFAULT_WEB_INTERVAL_MILLIS = 15L * 60L * 1000L;
    private static final long DEFAULT_MESSAGE_INTERVAL_MILLIS = 5L * 60L * 1000L;
    private static final int MAX_WEB_BYTES = 512 * 1024;
    private static final int MAX_JUDGMENT_CHARS = 24_000;
    private static final int MAX_DUE_PER_CHECK = 8;

    private OpenPhoneWatcherScheduler() {}

    public static void checkNow(Context context) {
        if (context == null) {
            return;
        }
        CommitmentStore store = new CommitmentStore(context);
        WatcherStore watcherStore = new WatcherStore(context);
        long now = System.currentTimeMillis();
        int repaired = watcherStore.repairStuck(now - STUCK_TIMEOUT_MILLIS, now);
        if (repaired > 0) {
            Log.w(TAG, "Repaired stuck watchers: " + repaired);
        }
        List<CommitmentRecord> due = store.due(now, MAX_DUE_PER_CHECK);
        for (CommitmentRecord commitment : due) {
            if (store.updateStatus(commitment.id, "fired")) {
                OpenPhoneNotificationController.showCommitmentDue(context, commitment);
            }
        }
        for (WatcherRecord watcher : watcherStore.due(now, MAX_DUE_PER_CHECK)) {
            fireWatcher(context, watcherStore, watcher, now);
        }
        scheduleNext(context, store, watcherStore);
    }

    public static void scheduleNext(Context context) {
        if (context == null) {
            return;
        }
        scheduleNext(context, new CommitmentStore(context), new WatcherStore(context));
    }

    public static void completeCommitment(Context context, long id) {
        updateAndReschedule(context, id, "completed");
    }

    public static void dismissCommitment(Context context, long id) {
        if (context == null || id <= 0) {
            return;
        }
        CommitmentStore store = new CommitmentStore(context);
        store.dismiss(id);
        OpenPhoneNotificationController.cancelCommitment(context, id);
        scheduleNext(context, store, new WatcherStore(context));
    }

    public static void snoozeCommitment(Context context, long id) {
        if (context == null || id <= 0) {
            return;
        }
        CommitmentStore store = new CommitmentStore(context);
        store.snooze(id, System.currentTimeMillis() + SNOOZE_MILLIS);
        OpenPhoneNotificationController.cancelCommitment(context, id);
        scheduleNext(context, store, new WatcherStore(context));
    }

    private static void updateAndReschedule(Context context, long id, String status) {
        if (context == null || id <= 0) {
            return;
        }
        CommitmentStore store = new CommitmentStore(context);
        store.updateStatus(id, status);
        OpenPhoneNotificationController.cancelCommitment(context, id);
        scheduleNext(context, store, new WatcherStore(context));
    }

    public static void stopWatcher(Context context, long id) {
        if (context == null || id <= 0) {
            return;
        }
        WatcherStore watcherStore = new WatcherStore(context);
        watcherStore.stop(id);
        scheduleNext(context, new CommitmentStore(context), watcherStore);
    }

    public static void onNotificationPosted(Context context, String packageName, String title,
            String text, String notificationKey, long observedAtMillis) {
        if (context == null) {
            return;
        }
        WatcherStore watcherStore = new WatcherStore(context);
        long now = System.currentTimeMillis();
        for (WatcherRecord watcher : watcherStore.activeByType("notification", MAX_DUE_PER_CHECK)) {
            if (!notificationMatches(watcher, packageName, title, text)) {
                continue;
            }
            if (!watcherStore.markRunning(watcher.id, now)) {
                continue;
            }
            String resultHash = "notification:"
                    + safeHashPart(packageName) + ":"
                    + safeHashPart(notificationKey) + ":"
                    + observedAtMillis;
            watcherStore.markFired(watcher.id, resultHash, now);
            OpenPhoneNotificationController.showWatcherFired(context, watcher);
        }
        scheduleNext(context, new CommitmentStore(context), watcherStore);
    }

    private static void fireWatcher(Context context, WatcherStore store,
            WatcherRecord watcher, long now) {
        if (!store.markRunning(watcher.id, now)) {
            return;
        }
        if ("web_change".equals(watcher.type)) {
            runWebChangeWatcher(context.getApplicationContext(), watcher);
            return;
        }
        if ("message_reply".equals(watcher.type)) {
            runMessageReplyWatcher(context, store, watcher, now);
            return;
        }
        if ("call_back".equals(watcher.type)) {
            runCallBackWatcher(context, store, watcher, now);
            return;
        }
        if (!"time".equals(watcher.type)) {
            failWatcher(context, store, watcher, now, "unsupported_watcher_type:" + watcher.type);
            return;
        }
        store.markFired(watcher.id, watcher.type + ":" + watcher.title, now);
        OpenPhoneNotificationController.showWatcherFired(context, watcher);
    }

    private static void runMessageReplyWatcher(Context context, WatcherStore store,
            WatcherRecord watcher, long now) {
        JSONObject condition = parseOrEmpty(watcher.conditionJson);
        JSONObject schedule = parseOrEmpty(watcher.scheduleJson);
        String address = firstNonEmpty(condition.optString("address", ""),
                condition.optString("phone", ""),
                condition.optString("phone_number", ""),
                condition.optString("sender", ""),
                condition.optString("from", ""));
        long threadId = condition.optLong("thread_id", 0L);
        if (address.isEmpty() && threadId <= 0) {
            failWatcher(context, store, watcher, now, "missing_message_watcher_target");
            return;
        }
        long baseline = condition.optLong("baseline_ms", 0L);
        if (baseline <= 0) {
            baseline = watcher.createdAtMillis;
        }
        long deadline = condition.optLong("deadline_at", 0L);
        String notifyOn = firstNonEmpty(condition.optString("notify_on", ""),
                deadline > 0 ? "no_reply" : "reply");
        InboundMessage reply;
        try {
            reply = findInboundMessage(context, address, threadId, baseline);
        } catch (SecurityException e) {
            failWatcher(context, store, watcher, now, "messages_permission_denied:read");
            return;
        } catch (RuntimeException e) {
            failWatcher(context, store, watcher, now, "messages_query_failed:"
                    + safeHashPart(e.getClass().getSimpleName()));
            return;
        }
        if (reply != null) {
            String resultHash = "message_reply:" + reply.messageId + ":" + reply.dateMillis;
            store.markFired(watcher.id, resultHash, now);
            if ("reply".equals(notifyOn)) {
                OpenPhoneNotificationController.showWatcherFired(context, watcher);
            }
            // notify_on=no_reply: the reply arrived, so the reminder is moot —
            // resolve the watcher silently.
            return;
        }
        if (deadline > 0 && now >= deadline) {
            if ("no_reply".equals(notifyOn)) {
                store.markFired(watcher.id, "message_reply:no_reply_by_deadline:" + deadline,
                        now);
                OpenPhoneNotificationController.showWatcherFired(context, watcher);
            } else {
                store.stop(watcher.id);
            }
            return;
        }
        long interval = messageWatcherIntervalMillis(condition, schedule);
        long nextRunAt = now + interval;
        if (deadline > now) {
            nextRunAt = Math.min(nextRunAt, Math.max(deadline, now + MIN_DELAY_MILLIS));
        }
        store.markNoop(watcher.id, nextRunAt, "message_reply:no_reply_yet:" + baseline, now);
    }

    private static void runCallBackWatcher(Context context, WatcherStore store,
            WatcherRecord watcher, long now) {
        JSONObject condition = parseOrEmpty(watcher.conditionJson);
        JSONObject schedule = parseOrEmpty(watcher.scheduleJson);
        String number = firstNonEmpty(condition.optString("number", ""),
                condition.optString("address", ""),
                condition.optString("phone", ""),
                condition.optString("phone_number", ""));
        if (number.isEmpty()) {
            failWatcher(context, store, watcher, now, "missing_call_watcher_number");
            return;
        }
        long baseline = condition.optLong("baseline_ms", 0L);
        if (baseline <= 0) {
            baseline = watcher.createdAtMillis;
        }
        long deadline = condition.optLong("deadline_at", 0L);
        String notifyOn = firstNonEmpty(condition.optString("notify_on", ""),
                deadline > 0 ? "no_call" : "call");
        // direction filter: any (default), incoming, outgoing
        String direction = firstNonEmpty(condition.optString("direction", ""), "any")
                .toLowerCase(Locale.US);
        ObservedCall call;
        try {
            call = findCall(context, number, baseline, direction);
        } catch (SecurityException e) {
            failWatcher(context, store, watcher, now, "calls_permission_denied:read");
            return;
        } catch (RuntimeException e) {
            failWatcher(context, store, watcher, now, "calls_query_failed:"
                    + safeHashPart(e.getClass().getSimpleName()));
            return;
        }
        if (call != null) {
            String resultHash = "call_back:" + call.callId + ":" + call.dateMillis;
            store.markFired(watcher.id, resultHash, now);
            if ("call".equals(notifyOn)) {
                OpenPhoneNotificationController.showWatcherFired(context, watcher);
            }
            // notify_on=no_call: the call happened, so the reminder is moot —
            // resolve the watcher silently.
            return;
        }
        if (deadline > 0 && now >= deadline) {
            if ("no_call".equals(notifyOn)) {
                store.markFired(watcher.id, "call_back:no_call_by_deadline:" + deadline, now);
                OpenPhoneNotificationController.showWatcherFired(context, watcher);
            } else {
                store.stop(watcher.id);
            }
            return;
        }
        long interval = messageWatcherIntervalMillis(condition, schedule);
        long nextRunAt = now + interval;
        if (deadline > now) {
            nextRunAt = Math.min(nextRunAt, Math.max(deadline, now + MIN_DELAY_MILLIS));
        }
        store.markNoop(watcher.id, nextRunAt, "call_back:no_call_yet:" + baseline, now);
    }

    private static ObservedCall findCall(Context context, String number, long baselineMillis,
            String direction) {
        String[] projection = new String[] {
                android.provider.CallLog.Calls._ID,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.DATE,
                android.provider.CallLog.Calls.TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI, projection,
                android.provider.CallLog.Calls.DATE + " > ?",
                new String[] { Long.toString(baselineMillis) },
                android.provider.CallLog.Calls.DATE + " DESC")) {
            if (cursor == null) {
                throw new IllegalStateException("calls_query_null_cursor");
            }
            int scanned = 0;
            while (cursor.moveToNext() && scanned < 100) {
                scanned++;
                int type = cursor.getInt(3);
                if ("incoming".equals(direction)
                        && type != android.provider.CallLog.Calls.INCOMING_TYPE
                        && type != android.provider.CallLog.Calls.MISSED_TYPE) {
                    continue;
                }
                if ("outgoing".equals(direction)
                        && type != android.provider.CallLog.Calls.OUTGOING_TYPE) {
                    continue;
                }
                String rowNumber = cursor.getString(1);
                if (!addressMatches(rowNumber, number)) {
                    continue;
                }
                return new ObservedCall(cursor.getLong(0), cursor.getLong(2));
            }
        }
        return null;
    }

    private static final class ObservedCall {
        final long callId;
        final long dateMillis;

        ObservedCall(long callId, long dateMillis) {
            this.callId = callId;
            this.dateMillis = dateMillis;
        }
    }

    private static InboundMessage findInboundMessage(Context context, String address,
            long threadId, long baselineMillis) {
        StringBuilder selection = new StringBuilder(
                Telephony.Sms.TYPE + " = " + Telephony.Sms.MESSAGE_TYPE_INBOX
                        + " AND " + Telephony.Sms.DATE + " > ?");
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        args.add(Long.toString(baselineMillis));
        if (threadId > 0) {
            selection.append(" AND ").append(Telephony.Sms.THREAD_ID).append(" = ?");
            args.add(Long.toString(threadId));
        }
        String[] projection = new String[] {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE
        };
        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI, projection,
                selection.toString(), args.toArray(new String[0]),
                Telephony.Sms.DATE + " DESC")) {
            if (cursor == null) {
                throw new IllegalStateException("messages_query_null_cursor");
            }
            int scanned = 0;
            while (cursor.moveToNext() && scanned < 100) {
                scanned++;
                String rowAddress = cursor.getString(1);
                if (!address.isEmpty() && !addressMatches(rowAddress, address)) {
                    continue;
                }
                return new InboundMessage(cursor.getLong(0), cursor.getLong(2));
            }
        }
        return null;
    }

    private static boolean addressMatches(String rowAddress, String watchedAddress) {
        if (containsNormalized(rowAddress, watchedAddress)) {
            return true;
        }
        return PhoneNumberUtils.compare(safe(rowAddress), safe(watchedAddress));
    }

    private static long messageWatcherIntervalMillis(JSONObject condition, JSONObject schedule) {
        long interval = firstPositive(
                schedule.optLong("interval_ms", 0L),
                schedule.optLong("interval_millis", 0L),
                condition.optLong("interval_ms", 0L),
                condition.optLong("interval_millis", 0L));
        if (interval <= 0) {
            interval = DEFAULT_MESSAGE_INTERVAL_MILLIS;
        }
        return Math.max(MIN_DELAY_MILLIS, Math.min(interval, 24L * 60L * 60L * 1000L));
    }

    private static final class InboundMessage {
        final long messageId;
        final long dateMillis;

        InboundMessage(long messageId, long dateMillis) {
            this.messageId = messageId;
            this.dateMillis = dateMillis;
        }
    }

    private static void runWebChangeWatcher(Context context, WatcherRecord watcher) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                WatcherStore store = new WatcherStore(context);
                long now = System.currentTimeMillis();
                JSONObject condition = parseOrEmpty(watcher.conditionJson);
                JSONObject schedule = parseOrEmpty(watcher.scheduleJson);
                String url = firstNonEmpty(condition.optString("url", ""),
                        condition.optString("uri", ""),
                        condition.optString("query", ""));
                if (!isHttpUrl(url)) {
                    failWatcher(context, store, watcher, now, "bad_web_watcher_url");
                    scheduleNext(context);
                    return;
                }
                String previousHash = safe(watcher.lastResultHash);
                try {
                    byte[] content = fetchContent(url);
                    String contentHash = "web:" + sha256Hex(content);
                    String evaluator = normalizeEvaluator(firstNonEmpty(
                            condition.optString("evaluator", ""),
                            condition.optString("operator", ""),
                            condition.optString("mode", "")));
                    long nextRunAt = now + watcherIntervalMillis(condition, schedule);
                    if ("text_contains".equals(evaluator)
                            || "semantic_match".equals(evaluator)) {
                        String query = firstNonEmpty(condition.optString("query", ""),
                                condition.optString("condition_text", ""),
                                condition.optString("text", ""),
                                condition.optString("contains", ""),
                                condition.optString("needle", ""));
                        if (query.isEmpty()) {
                            failWatcher(context, store, watcher, now,
                                    "missing_web_watcher_query");
                            scheduleNext(context);
                            return;
                        }
                        boolean matched;
                        String firedPrefix;
                        if ("semantic_match".equals(evaluator)) {
                            String noMatchHash = "web_no_match:" + contentHash;
                            if (noMatchHash.equals(previousHash)) {
                                // Content unchanged since the last "no" verdict; skip the
                                // model call instead of re-judging identical bytes.
                                store.markNoop(watcher.id, nextRunAt, noMatchHash, now);
                                scheduleNext(context);
                                return;
                            }
                            matched = judgeSemanticMatch(context, query,
                                    webTextForJudgment(content));
                            firedPrefix = "web_semantic_match:";
                        } else {
                            String text = new String(content,
                                    java.nio.charset.StandardCharsets.UTF_8);
                            matched = containsNormalized(text, query);
                            firedPrefix = "web_match:";
                        }
                        if (matched) {
                            String matchHash = firedPrefix + sha256Hex(
                                    (url + "\n" + query + "\n" + contentHash)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            store.markFired(watcher.id, matchHash, now);
                            OpenPhoneNotificationController.showWatcherFired(context, watcher);
                        } else {
                            store.markNoop(watcher.id, nextRunAt,
                                    "web_no_match:" + contentHash, now);
                        }
                        scheduleNext(context);
                        return;
                    }
                    if (previousHash.isEmpty()
                            || previousHash.startsWith("unsupported_watcher_type:")
                            || previousHash.startsWith("stuck_running_repaired")
                            || previousHash.startsWith("web_error:")) {
                        store.markNoop(watcher.id, nextRunAt, contentHash, now);
                    } else if (previousHash.equals(contentHash)) {
                        store.markNoop(watcher.id, nextRunAt, contentHash, now);
                    } else {
                        store.markFired(watcher.id, contentHash, now);
                        OpenPhoneNotificationController.showWatcherFired(context, watcher);
                    }
                } catch (IOException | RuntimeException e) {
                    failWatcher(context, store, watcher, now, "web_error:"
                            + safeHashPart(e.getClass().getSimpleName() + ":" + e.getMessage()));
                }
                scheduleNext(context);
            }
        }, "OpenPhoneWebWatcher").start();
    }

    private static String fetchContentHash(String urlString) throws IOException {
        return sha256Hex(fetchContent(urlString));
    }

    private static boolean judgeSemanticMatch(Context context, String conditionText,
            String pageText) throws IOException {
        ModelEndpointConfig endpointConfig = watcherModelEndpointConfig(context);
        if (!endpointConfig.isConfigured()) {
            throw new IOException("semantic_model_unconfigured");
        }
        return new OpenAiResponsesAgentAdapter(endpointConfig)
                .judgeWatcherCondition(conditionText, pageText);
    }

    private static ModelEndpointConfig watcherModelEndpointConfig(Context context) {
        // Watchers run from a BroadcastReceiver with no activity, so the in-memory dev
        // key held by the assistant UI is unavailable; the Secure setting is the only
        // credential source for background evaluation (dev builds only).
        if (!"userdebug".equals(Build.TYPE) && !"eng".equals(Build.TYPE)) {
            return ModelEndpointConfig.directOpenAi("");
        }
        String apiKey = Settings.Secure.getString(context.getContentResolver(),
                "openphone_dev_openai_api_key");
        return ModelEndpointConfig.directOpenAi(apiKey == null ? "" : apiKey);
    }

    private static String webTextForJudgment(byte[] content) {
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        text = text.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]{0,500}>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= MAX_JUDGMENT_CHARS
                ? text : text.substring(0, MAX_JUDGMENT_CHARS);
    }

    private static byte[] fetchContent(String urlString) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent", "OpenPhoneWatcher/1");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("http_status_" + status);
            }
            try (InputStream input = connection.getInputStream()) {
                return readBounded(input, MAX_WEB_BYTES);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        while (true) {
            int read = input.read(buffer, 0, Math.min(buffer.length, maxBytes - total));
            if (read < 0) {
                break;
            }
            output.write(buffer, 0, read);
            total += read;
            if (total >= maxBytes) {
                break;
            }
        }
        return output.toByteArray();
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data == null ? new byte[0] : data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long watcherIntervalMillis(JSONObject condition, JSONObject schedule) {
        long interval = firstPositive(
                schedule.optLong("interval_ms", 0L),
                schedule.optLong("interval_millis", 0L),
                condition.optLong("interval_ms", 0L),
                condition.optLong("interval_millis", 0L));
        if (interval <= 0) {
            return DEFAULT_WEB_INTERVAL_MILLIS;
        }
        return Math.max(MIN_DELAY_MILLIS, Math.min(interval, 24L * 60L * 60L * 1000L));
    }

    private static long firstPositive(long... values) {
        if (values == null) {
            return 0L;
        }
        for (long value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0L;
    }

    private static boolean isHttpUrl(String value) {
        String clean = safe(value).toLowerCase(Locale.US);
        return clean.startsWith("https://") || clean.startsWith("http://");
    }

    private static boolean notificationMatches(WatcherRecord watcher, String packageName,
            String title, String text) {
        JSONObject condition = parseOrEmpty(watcher.conditionJson);
        String packageFilter = firstNonEmpty(condition.optString("package", ""),
                condition.optString("package_name", ""),
                condition.optString("source_app", ""));
        String titleNeedle = firstNonEmpty(condition.optString("title_contains", ""),
                condition.optString("title", ""));
        String textNeedle = firstNonEmpty(condition.optString("text_contains", ""),
                condition.optString("body_contains", ""));
        String query = firstNonEmpty(condition.optString("query", ""),
                condition.optString("contains", ""),
                condition.optString("text", ""),
                condition.optString("needle", ""));
        if (packageFilter.isEmpty() && titleNeedle.isEmpty()
                && textNeedle.isEmpty() && query.isEmpty()) {
            return false;
        }
        if (!packageFilter.isEmpty()
                && !containsNormalized(packageName, packageFilter)) {
            return false;
        }
        String haystack = (safe(packageName) + " " + safe(title) + " " + safe(text))
                .toLowerCase(Locale.US);
        String titleHaystack = safe(title).toLowerCase(Locale.US);
        String textHaystack = safe(text).toLowerCase(Locale.US);
        if (!titleNeedle.isEmpty()
                && !titleHaystack.contains(titleNeedle.toLowerCase(Locale.US))) {
            return false;
        }
        if (!textNeedle.isEmpty()
                && !textHaystack.contains(textNeedle.toLowerCase(Locale.US))) {
            return false;
        }
        return query.isEmpty() || haystack.contains(query.toLowerCase(Locale.US));
    }

    private static void failWatcher(Context context, WatcherStore store, WatcherRecord watcher,
            long now, String reason) {
        int failures = watcher.failureCount + 1;
        long nextRunAt = now + WatcherStore.backoffMillis(failures);
        long failureAlertAt = failures >= 3 ? now : watcher.failureAlertAtMillis;
        store.markFailed(watcher.id, reason, nextRunAt, failures, failureAlertAt, now);
        Log.w(TAG, "Watcher failed: " + watcher.id + " " + reason);
        if (failures >= 3) {
            OpenPhoneNotificationController.showWatcherFailed(context, watcher, reason);
        }
    }

    private static void scheduleNext(Context context, CommitmentStore store,
            WatcherStore watcherStore) {
        try {
            PointerOverlayController.publishWatchingCount(watcherStore.active(50).size());
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to publish watcher count", e);
        }
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            return;
        }
        PendingIntent pending = checkPendingIntent(context);
        long now = System.currentTimeMillis();
        long nextCommitmentDueAt = store.nextDueAt(now);
        long nextWatcherRunAt = watcherStore.nextRunAt(now);
        long nextDueAt = earliestPositive(nextCommitmentDueAt, nextWatcherRunAt);
        if (nextDueAt <= 0) {
            alarms.cancel(pending);
            return;
        }
        long triggerAt = Math.max(nextDueAt, now + MIN_DELAY_MILLIS);
        try {
            alarms.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            Log.i(TAG, "Scheduled watcher check at " + triggerAt);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to schedule watcher check", e);
        }
    }

    private static PendingIntent checkPendingIntent(Context context) {
        Intent intent = new Intent(context, OpenPhoneWatcherReceiver.class);
        intent.setAction(ACTION_CHECK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 7001, intent, flags);
    }

    private static long earliestPositive(long first, long second) {
        if (first <= 0) {
            return second;
        }
        if (second <= 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static JSONObject parseOrEmpty(String json) {
        try {
            return new JSONObject(json == null || json.isEmpty() ? "{}" : json);
        } catch (JSONException e) {
            return new JSONObject();
        }
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

    private static String normalizeEvaluator(String evaluator) {
        String clean = safe(evaluator).toLowerCase(Locale.US);
        if ("contains".equals(clean) || "text".equals(clean) || "text_match".equals(clean)) {
            return "text_contains";
        }
        if ("semantic".equals(clean) || "semantic_contains".equals(clean)) {
            return "semantic_match";
        }
        if (clean.isEmpty() || "change".equals(clean) || "hash".equals(clean)) {
            return "hash_change";
        }
        return clean;
    }

    private static boolean containsNormalized(String value, String needle) {
        return safe(value).toLowerCase(Locale.US)
                .contains(safe(needle).toLowerCase(Locale.US));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeHashPart(String value) {
        String clean = safe(value);
        return clean.length() <= 80 ? clean : clean.substring(0, 80);
    }
}
