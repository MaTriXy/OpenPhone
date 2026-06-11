package org.openphone.assistant.context;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.openphone.OpenPhoneContextManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Client of the OS-owned OpenPhone context index (system_server
 * openphone_context service). The assistant no longer owns the durable
 * context_event store; a one-time migration moves rows from the legacy
 * app-local SQLite database into the OS store.
 */
public final class ContextIndexStore {
    private static final String TAG = "OpenPhoneContextIndex";
    private static final String LEGACY_DB_NAME = "openphone_context_index.db";
    private static final String SOURCE_APP = "org.openphone.assistant";
    private static final String CHAT_PREFS_NAME = "openphone_chat_history";
    private static final String KEY_CURRENT_MESSAGES = "current_messages";
    private static final String KEY_HISTORY = "history";
    private static final String BACKFILL_PREFS_NAME = "openphone_context_index";
    private static final String KEY_CHAT_BACKFILLED_V1 = "chat_backfilled_v1";
    private static final String KEY_OS_STORE_MIGRATED_V1 = "os_store_migrated_v1";
    private static final int MIGRATION_CHUNK = 100;

    private static final Object sMigrationLock = new Object();

    private final Context mContext;
    private final OpenPhoneContextManager mManager;

    public ContextIndexStore(Context context) {
        mContext = context.getApplicationContext();
        OpenPhoneContextManager manager = null;
        try {
            manager = mContext.getSystemService(OpenPhoneContextManager.class);
        } catch (RuntimeException e) {
            Log.w(TAG, "OpenPhone context service unavailable", e);
        }
        mManager = manager;
    }

    public long recordConversationMessage(String speaker, String message) {
        String cleanMessage = message == null ? "" : message.trim();
        if (cleanMessage.isEmpty()) {
            return -1L;
        }
        boolean user = "You".equals(speaker);
        JSONObject payload = new JSONObject();
        try {
            payload.put("speaker", speaker == null ? "" : speaker)
                    .put("is_user", user);
        } catch (JSONException ignored) {
        }
        return insert("assistant.conversation.message", user ? "User message" : "Assistant reply",
                cleanMessage, payload.toString(), "");
    }

    public void backfillChatHistoryIfNeeded() {
        migrateLegacyStoreIfNeeded();
        SharedPreferences backfillPrefs = mContext.getSharedPreferences(BACKFILL_PREFS_NAME,
                Context.MODE_PRIVATE);
        if (backfillPrefs.getBoolean(KEY_CHAT_BACKFILLED_V1, false)) {
            return;
        }
        SharedPreferences chatPrefs = mContext.getSharedPreferences(CHAT_PREFS_NAME,
                Context.MODE_PRIVATE);
        int count = 0;
        count += backfillMessages(chatPrefs.getString(KEY_CURRENT_MESSAGES, null),
                "current");
        String history = chatPrefs.getString(KEY_HISTORY, null);
        if (history != null && !history.isEmpty()) {
            try {
                JSONArray sessions = new JSONArray(history);
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject session = sessions.optJSONObject(i);
                    if (session == null) {
                        continue;
                    }
                    count += backfillMessages(session.optJSONArray("messages"),
                            session.optString("id", "history"));
                }
            } catch (JSONException ignored) {
            }
        }
        backfillPrefs.edit()
                .putBoolean(KEY_CHAT_BACKFILLED_V1, true)
                .putInt("chat_backfilled_v1_count", count)
                .apply();
    }

    /**
     * One-time move of the legacy assistant-local context database into the
     * OS-owned store. Rows are copied in chunks (binder transactions are
     * size-limited) and the legacy file is kept on disk until the copy has
     * fully succeeded, so a crash mid-migration retries on next start.
     */
    public void migrateLegacyStoreIfNeeded() {
        if (mManager == null) {
            return;
        }
        synchronized (sMigrationLock) {
            SharedPreferences prefs = mContext.getSharedPreferences(BACKFILL_PREFS_NAME,
                    Context.MODE_PRIVATE);
            if (prefs.getBoolean(KEY_OS_STORE_MIGRATED_V1, false)) {
                return;
            }
            File legacyDb = mContext.getDatabasePath(LEGACY_DB_NAME);
            if (!legacyDb.exists()) {
                prefs.edit().putBoolean(KEY_OS_STORE_MIGRATED_V1, true).apply();
                return;
            }
            int migrated = 0;
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(legacyDb.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READONLY)) {
                long lastId = 0;
                while (true) {
                    JSONArray chunk = new JSONArray();
                    try (Cursor cursor = db.rawQuery("SELECT id, source_type, source_app, "
                            + "source_record_id, observed_at, title, text, payload_json "
                            + "FROM context_event WHERE deleted_at IS NULL AND id > ? "
                            + "ORDER BY id ASC LIMIT ?",
                            new String[]{Long.toString(lastId),
                                    Integer.toString(MIGRATION_CHUNK)})) {
                        while (cursor.moveToNext()) {
                            lastId = cursor.getLong(0);
                            JSONObject event = new JSONObject();
                            try {
                                event.put("source_type", cursor.getString(1))
                                        .put("source_app", cursor.getString(2))
                                        .put("source_record_id", cursor.getString(3))
                                        .put("observed_at", cursor.getLong(4))
                                        .put("title", cursor.getString(5))
                                        .put("text", cursor.getString(6))
                                        .put("payload_json", cursor.getString(7));
                            } catch (JSONException e) {
                                continue;
                            }
                            chunk.put(event);
                        }
                    }
                    if (chunk.length() == 0) {
                        break;
                    }
                    JSONObject result = parseOrEmpty(
                            mManager.insertEvents(chunk.toString()));
                    if (result.has("error")) {
                        Log.w(TAG, "Legacy context migration failed: "
                                + result.optString("error"));
                        return;
                    }
                    migrated += result.optInt("inserted", 0);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Legacy context migration failed", e);
                return;
            }
            prefs.edit()
                    .putBoolean(KEY_OS_STORE_MIGRATED_V1, true)
                    .putInt("os_store_migrated_v1_count", migrated)
                    .apply();
            Log.i(TAG, "Migrated " + migrated + " legacy context events into the OS store");
        }
    }

    public long recordAgentEvent(String eventType, String title, String text, String taskId,
            String payloadJson) {
        JSONObject payload = parseOrEmpty(payloadJson);
        try {
            payload.put("task_id", taskId == null ? "" : taskId);
        } catch (JSONException ignored) {
        }
        return insert(eventType == null || eventType.isEmpty() ? "assistant.agent.event" : eventType,
                title, text, payload.toString(), taskId);
    }

    public long recordNotificationEvent(String eventType, String packageName,
            String notificationKey, String title, String text, long observedAtMillis,
            String payloadJson) {
        String sourceType = eventType == null || eventType.isEmpty()
                ? "notification.posted" : eventType;
        String cleanPackage = packageName == null ? "" : packageName;
        String cleanKey = notificationKey == null ? "" : notificationKey;
        JSONObject payload = parseOrEmpty(payloadJson);
        try {
            payload.put("package", cleanPackage)
                    .put("notification_key", cleanKey);
        } catch (JSONException ignored) {
        }
        return insert(sourceType, cleanPackage, cleanKey, observedAtMillis, title, text,
                payload.toString());
    }

    public List<ContextEvent> search(String query, int limit) {
        return queryEvents(query, "", "", limit);
    }

    public List<ContextEvent> latest(int limit) {
        return queryEvents("", "", "", limit);
    }

    public List<ContextEvent> notifications(String query, int limit) {
        return queryEvents(query, "", "notification.", limit);
    }

    public List<ContextEvent> latestNotifications(int limit) {
        return queryEvents("", "", "notification.", limit);
    }

    /** Recent chat messages as a JSON array of {speaker, text}, oldest first. */
    public String recentConversationJson(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        List<ContextEvent> events = queryEvents("",
                "assistant.conversation.message", "", boundedLimit);
        JSONArray conversation = new JSONArray();
        for (int i = events.size() - 1; i >= 0; i--) {
            ContextEvent event = events.get(i);
            JSONObject payload = parseOrEmpty(event.payloadJson);
            try {
                conversation.put(new JSONObject()
                        .put("speaker", payload.optString("speaker",
                                payload.optBoolean("is_user", false) ? "You" : "OpenPhone"))
                        .put("text", event.text == null ? "" : event.text));
            } catch (JSONException ignored) {
            }
        }
        return conversation.toString();
    }

    public String searchJson(String query, int limit) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"events\":[");
        List<ContextEvent> events = search(query, limit);
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(eventJson(events.get(i)));
        }
        builder.append("]}");
        return builder.toString();
    }

    public String notificationsJson(String query, int limit) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"notifications\":[");
        List<ContextEvent> events = notifications(query, limit);
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(eventJson(events.get(i)));
        }
        builder.append("]}");
        return builder.toString();
    }

    private long insert(String sourceType, String title, String text, String payloadJson,
            String sourceRecordId) {
        return insert(sourceType, SOURCE_APP, sourceRecordId, System.currentTimeMillis(),
                title, text, payloadJson);
    }

    private long insert(String sourceType, String sourceApp, String sourceRecordId,
            long observedAtMillis, String title, String text, String payloadJson) {
        if (mManager == null) {
            return -1L;
        }
        JSONObject event = new JSONObject();
        try {
            event.put("source_type", sourceType == null ? "" : sourceType)
                    .put("source_app", sourceApp == null ? "" : sourceApp)
                    .put("source_record_id", sourceRecordId == null ? "" : sourceRecordId)
                    .put("observed_at", observedAtMillis)
                    .put("title", title == null ? "" : title)
                    .put("text", text == null ? "" : text)
                    .put("payload_json", payloadJson == null ? "{}" : payloadJson);
        } catch (JSONException e) {
            return -1L;
        }
        try {
            JSONObject result = parseOrEmpty(mManager.insertEvent(event.toString()));
            return result.optLong("id", -1L);
        } catch (RuntimeException e) {
            Log.w(TAG, "Context insert failed", e);
            return -1L;
        }
    }

    private List<ContextEvent> queryEvents(String query, String sourceType,
            String sourceTypePrefix, int limit) {
        List<ContextEvent> events = new ArrayList<>();
        if (mManager == null) {
            return events;
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        JSONObject request = new JSONObject();
        try {
            request.put("query", query == null ? "" : query)
                    .put("source_type", sourceType == null ? "" : sourceType)
                    .put("source_type_prefix", sourceTypePrefix == null ? "" : sourceTypePrefix)
                    .put("limit", boundedLimit);
        } catch (JSONException e) {
            return events;
        }
        JSONObject response;
        try {
            response = parseOrEmpty(mManager.queryEvents(request.toString()));
        } catch (RuntimeException e) {
            Log.w(TAG, "Context query failed", e);
            return events;
        }
        JSONArray array = response.optJSONArray("events");
        if (array == null) {
            return events;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject event = array.optJSONObject(i);
            if (event == null) {
                continue;
            }
            events.add(new ContextEvent(
                    event.optLong("id"),
                    event.optString("source_type", ""),
                    event.optString("source_app", ""),
                    event.optString("source_record_id", ""),
                    event.optLong("observed_at"),
                    event.optString("title", ""),
                    event.optString("text", ""),
                    event.optString("payload_json", "{}")));
        }
        return events;
    }

    private int backfillMessages(String rawMessages, String sessionId) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return 0;
        }
        try {
            return backfillMessages(new JSONArray(rawMessages), sessionId);
        } catch (JSONException e) {
            return 0;
        }
    }

    private int backfillMessages(JSONArray messages, String sessionId) {
        if (messages == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String body = message.optString("body", "").trim();
            if (body.isEmpty()) {
                continue;
            }
            String speaker = message.optString("speaker",
                    message.optBoolean("is_user", false) ? "You" : "OpenPhone");
            JSONObject payload = new JSONObject();
            try {
                payload.put("speaker", speaker)
                        .put("is_user", message.optBoolean("is_user", false))
                        .put("backfilled", true)
                        .put("chat_session_id", sessionId == null ? "" : sessionId);
            } catch (JSONException ignored) {
            }
            insert("assistant.conversation.message",
                    message.optBoolean("is_user", false) ? "User message" : "Assistant reply",
                    body, payload.toString(), sessionId == null ? "" : sessionId);
            count++;
        }
        return count;
    }

    private static JSONObject parseOrEmpty(String payloadJson) {
        try {
            return new JSONObject(payloadJson == null || payloadJson.isEmpty()
                    ? "{}" : payloadJson);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static String eventJson(ContextEvent event) {
        try {
            return new JSONObject()
                    .put("id", event.id)
                    .put("source_type", event.sourceType)
                    .put("source_app", event.sourceApp)
                    .put("source_record_id", event.sourceRecordId)
                    .put("observed_at", event.observedAtMillis)
                    .put("title", event.title)
                    .put("text", event.text)
                    .put("payload", parseOrEmpty(event.payloadJson))
                    .toString();
        } catch (JSONException e) {
            return "{}";
        }
    }
}
