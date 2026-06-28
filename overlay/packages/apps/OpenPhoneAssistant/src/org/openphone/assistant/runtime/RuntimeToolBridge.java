package org.openphone.assistant.runtime;

import android.content.Context;
import android.openphone.OpenPhoneAgentManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.actions.ToolCatalog;
import org.openphone.assistant.agent.FrameworkToolExecutor;
import org.openphone.assistant.session.PhoneExecutionSession;
import org.openphone.assistant.session.PhoneSessionStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class RuntimeToolBridge {
    private static final String TAG = "OpenPhoneRuntime";
    private static final int MAX_COMPLETED_IDEMPOTENCY_RESULTS = 128;

    private static final Set<String> DEFAULT_READ_ONLY_TOOLS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "phone_context",
                    "apps_search",
                    "notifications_list",
                    "notifications_search",
                    "calendar_search",
                    "contacts_search",
                    "messages_search",
                    "calls_search",
                    "memory_search",
                    "commitment_search",
                    "watcher_list",
                    "background_job_list",
                    "get_screen",
                    "local_screen_understanding")));

    private static final Set<String> DEFAULT_MUTATING_TOOLS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "notifications_open",
                    "calendar_create_event",
                    "calendar_update_event",
                    "calendar_delete_event",
                    "messages_draft",
                    "messages_send",
                    "calls_place",
                    "memory_save",
                    "commitment_create",
                    "commitment_update_status",
                    "watcher_create",
                    "watcher_stop",
                    "background_job_create",
                    "background_job_stop",
                    "open_app",
                    "open_url",
                    "tap",
                    "tap_element",
                    "long_press",
                    "long_press_element",
                    "swipe",
                    "type_text",
                    "press_key",
                    "set_clipboard",
                    "paste",
                    "share_text")));

    private final OpenPhoneAgentManager mAgentManager;
    private final FrameworkToolExecutor mToolExecutor;
    private final PhoneSessionStore mSessionStore;
    private final Map<String, String> mTaskIdsByRuntimeSession = new HashMap<>();
    private final Map<String, RuntimePendingConfirmation> mPendingConfirmations =
            new HashMap<>();
    private final Map<String, String> mPendingByIdempotencyKey = new HashMap<>();
    private final Map<String, RuntimeToolResult> mCompletedByIdempotencyKey =
            new LinkedHashMap<String, RuntimeToolResult>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String,
                        RuntimeToolResult> eldest) {
                    return size() > MAX_COMPLETED_IDEMPOTENCY_RESULTS;
                }
            };
    private final ScheduledExecutorService mTimeoutExecutor =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "OpenPhoneRuntimeConfirmTimeouts");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private RuntimeConfirmationCallback mConfirmationCallback;

    public RuntimeToolBridge(Context context, OpenPhoneAgentManager agentManager) {
        this(context, agentManager, new PhoneSessionStore(context));
    }

    public RuntimeToolBridge(Context context, OpenPhoneAgentManager agentManager,
            PhoneSessionStore sessionStore) {
        mAgentManager = agentManager;
        mToolExecutor = new FrameworkToolExecutor(context, agentManager);
        mSessionStore = sessionStore == null ? new PhoneSessionStore(context) : sessionStore;
    }

    public synchronized void setConfirmationCallback(RuntimeConfirmationCallback callback) {
        mConfirmationCallback = callback;
    }

    public synchronized RuntimeToolResult execute(RuntimeToolRequest request) {
        return executeInternal(request, false);
    }

    public synchronized RuntimeConfirmationResolution resolveConfirmation(
            String confirmationId, boolean approved) {
        String normalizedId = confirmationId == null ? "" : confirmationId.trim();
        RuntimePendingConfirmation existing = mPendingConfirmations.get(normalizedId);
        if (existing != null && isTimedOut(existing, System.currentTimeMillis())) {
            return timeoutPendingConfirmationLocked(normalizedId);
        }
        RuntimePendingConfirmation pending = mPendingConfirmations.remove(normalizedId);
        if (pending == null || pending.request() == null) {
            RuntimeToolResult result = RuntimeToolResult.error("",
                    "pending_confirmation_not_found",
                    "Runtime confirmation was not found or already resolved.");
            return new RuntimeConfirmationResolution(null, result);
        }
        if (!pending.request().idempotencyKey().isEmpty()) {
            mPendingByIdempotencyKey.remove(pendingKey(pending.request()));
        }
        RuntimeToolResult result;
        if (approved) {
            result = executeInternal(pending.request(), true);
        } else {
            result = RuntimeToolResult.denied(pending.request().requestId(),
                    "user_denied_confirmation",
                    "User denied the runtime request.",
                    new JSONObject(), auditIdFor(pending.request()));
            rememberCompletedResult(pending.request(), result);
            markSessionForResult(pending.request(), result);
            logResult(pending.request(), result);
        }
        return new RuntimeConfirmationResolution(pending, result);
    }

    private RuntimeToolResult executeInternal(RuntimeToolRequest request,
            boolean approvedMutation) {
        String auditId = auditIdFor(request);
        RuntimeToolResult validation = validate(request, auditId);
        if (validation != null) {
            markSessionForResult(request, validation);
            logResult(request, validation);
            return validation;
        }
        if (!isGrantedTool(request.tool())) {
            JSONObject details = new JSONObject();
            try {
                details.put("tool", request.tool())
                        .put("runtime", request.runtime())
                        .put("policy", "runtime_default_deny");
            } catch (JSONException ignored) {
            }
            RuntimeToolResult result = RuntimeToolResult.denied(
                    request.requestId(),
                    "tool_not_granted",
                    "Runtime requested a tool outside the default grants.",
                    details,
                    auditId);
            markSessionForResult(request, result);
            logResult(request, result);
            return result;
        }
        RuntimeToolResult completed = completedResultFor(request, auditId);
        if (completed != null) {
            markSessionForResult(request, completed);
            logResult(request, completed);
            return completed;
        }
        String autonomy = effectiveAutonomy(request);
        if (isMutatingTool(request.tool()) && isObserveOnly(autonomy)) {
            JSONObject details = new JSONObject();
            try {
                details.put("tool", request.tool())
                        .put("runtime", request.runtime())
                        .put("autonomy", autonomy)
                        .put("policy", "observe_only_blocks_mutation");
            } catch (JSONException ignored) {
            }
            RuntimeToolResult result = RuntimeToolResult.denied(
                    request.requestId(),
                    "observe_only_mutation_denied",
                    "Runtime observe-only mode cannot run mutating phone tools.",
                    details,
                    auditId);
            markSessionForResult(request, result);
            logResult(request, result);
            return result;
        }
        if (isMutatingTool(request.tool()) && !approvedMutation) {
            RuntimeToolResult result = createPendingConfirmation(request, auditId);
            markSessionForResult(request, result);
            logResult(request, result);
            return result;
        }

        String taskId = ensureTask(request);
        if (taskId.isEmpty()) {
            RuntimeToolResult result = RuntimeToolResult.error(
                    request.requestId(), "task_unavailable",
                    "Could not create or resolve an OpenPhone task.");
            markSessionForResult(request, result);
            logResult(request, result);
            return result;
        }

        JSONObject params = request.params();
        try {
            if (params.optString("reason", "").trim().isEmpty()) {
                params.put("reason", request.reason());
            }
            params.put("_runtime", request.runtime());
            params.put("_runtime_request_id", request.requestId());
            if (!request.phoneSessionId().isEmpty()) {
                params.put("_phone_session_id", request.phoneSessionId());
            }
        } catch (JSONException ignored) {
        }

        markSession(request, "running");
        String rawResult = mToolExecutor.execute(taskId, request.tool(), params);
        if (approvedMutation) {
            rawResult = confirmFrameworkActionIfNeeded(rawResult);
        }
        RuntimeToolResult result = normalizeToolResult(request, rawResult, auditId, taskId);
        if (approvedMutation) {
            rememberCompletedResult(request, result);
        }
        markSessionForResult(request, result);
        logResult(request, result);
        return result;
    }

    private RuntimeToolResult createPendingConfirmation(RuntimeToolRequest request,
            String auditId) {
        String existingId = "";
        if (!request.idempotencyKey().isEmpty()) {
            String mappedId = mPendingByIdempotencyKey.get(pendingKey(request));
            existingId = mappedId == null ? "" : mappedId;
        }
        RuntimePendingConfirmation pending = existingId.isEmpty()
                ? null : mPendingConfirmations.get(existingId);
        if (pending != null && isTimedOut(pending, System.currentTimeMillis())) {
            RuntimeConfirmationResolution resolution =
                    timeoutPendingConfirmationLocked(pending.confirmationId());
            return resolution == null ? RuntimeToolResult.timeout(request.requestId(),
                    "Runtime confirmation timed out.", auditId)
                    : resolution.result().forRequest(request.requestId(), auditId);
        }
        if (pending == null) {
            String confirmationId = "runtime-confirm-" + UUID.randomUUID();
            pending = new RuntimePendingConfirmation(confirmationId, request,
                    System.currentTimeMillis(), summaryForRequest(request));
            mPendingConfirmations.put(confirmationId, pending);
            if (!request.idempotencyKey().isEmpty()) {
                mPendingByIdempotencyKey.put(pendingKey(request), confirmationId);
            }
            scheduleConfirmationTimeout(pending);
        }
        JSONObject details = new JSONObject();
        try {
            details.put("confirmation_id", pending.confirmationId())
                    .put("tool", request.tool())
                    .put("runtime", request.runtime())
                    .put("summary", pending.summary())
                    .put("autonomy", effectiveAutonomy(request));
        } catch (JSONException ignored) {
        }
        RuntimeToolResult result = RuntimeToolResult.needsConfirmation(
                request.requestId(),
                "mutating_tool_requires_confirmation",
                "Runtime mutating tools require user confirmation.",
                details,
                auditId,
                "");
        dispatchConfirmationRequested(pending, result);
        return result;
    }

    private void dispatchConfirmationRequested(final RuntimePendingConfirmation pending,
            final RuntimeToolResult result) {
        final RuntimeConfirmationCallback callback = mConfirmationCallback;
        if (callback == null || pending == null || result == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    callback.onRuntimeConfirmationRequested(pending, result);
                } catch (RuntimeException e) {
                    Log.w(TAG, "runtime confirmation callback failed", e);
                }
            }
        }, "OpenPhoneRuntimeConfirmNotify").start();
    }

    private void scheduleConfirmationTimeout(final RuntimePendingConfirmation pending) {
        if (pending == null || pending.request() == null) {
            return;
        }
        mTimeoutExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                RuntimeConfirmationResolution resolution;
                RuntimeConfirmationCallback callback;
                synchronized (RuntimeToolBridge.this) {
                    resolution = timeoutPendingConfirmationLocked(pending.confirmationId());
                    callback = mConfirmationCallback;
                }
                if (resolution == null || resolution.pending() == null
                        || resolution.result() == null || callback == null) {
                    return;
                }
                try {
                    callback.onRuntimeConfirmationTimedOut(
                            resolution.pending(), resolution.result());
                } catch (RuntimeException e) {
                    Log.w(TAG, "runtime confirmation timeout callback failed", e);
                }
            }
        }, pending.request().timeoutMs(), TimeUnit.MILLISECONDS);
    }

    private RuntimeConfirmationResolution timeoutPendingConfirmationLocked(
            String confirmationId) {
        RuntimePendingConfirmation pending = mPendingConfirmations.get(
                confirmationId == null ? "" : confirmationId.trim());
        if (pending == null || pending.request() == null
                || !isTimedOut(pending, System.currentTimeMillis())) {
            return null;
        }
        mPendingConfirmations.remove(pending.confirmationId());
        if (!pending.request().idempotencyKey().isEmpty()) {
            mPendingByIdempotencyKey.remove(pendingKey(pending.request()));
        }
        RuntimeToolResult result = RuntimeToolResult.timeout(
                pending.request().requestId(),
                "Runtime confirmation timed out.",
                auditIdFor(pending.request()));
        rememberCompletedResult(pending.request(), result);
        markSessionForResult(pending.request(), result);
        logResult(pending.request(), result);
        return new RuntimeConfirmationResolution(pending, result);
    }

    private RuntimeToolResult validate(RuntimeToolRequest request, String auditId) {
        if (mAgentManager == null) {
            return RuntimeToolResult.error(request.requestId(), "framework_unavailable",
                    "OpenPhone framework service is not available.");
        }
        if (request.requestId().isEmpty()) {
            return RuntimeToolResult.denied("", "missing_request_id",
                    "Runtime request_id is required.", new JSONObject(), auditId);
        }
        if (request.runtime().isEmpty() || !request.caller().isValid()) {
            return RuntimeToolResult.denied(request.requestId(), "missing_runtime_identity",
                    "Runtime identity is required.", new JSONObject(), auditId);
        }
        if (request.tool().isEmpty()) {
            return RuntimeToolResult.denied(request.requestId(), "missing_tool",
                    "Runtime tool is required.", new JSONObject(), auditId);
        }
        if (request.reason().isEmpty()) {
            return RuntimeToolResult.denied(request.requestId(), "missing_reason",
                    "Runtime tool calls require a reason.", new JSONObject(), auditId);
        }
        ToolCatalog catalog = ToolCatalog.get();
        if (catalog.isLoaded() && !catalog.isAllowedTool(request.tool())) {
            JSONObject details = new JSONObject();
            try {
                details.put("tool", request.tool());
            } catch (JSONException ignored) {
            }
            return RuntimeToolResult.denied(request.requestId(), "unknown_tool",
                    "Runtime requested an unknown OpenPhone tool.", details, auditId);
        }
        return null;
    }

    private String ensureTask(RuntimeToolRequest request) {
        if (!request.openPhoneTaskId().isEmpty()) {
            return request.openPhoneTaskId();
        }
        String existing = mTaskIdsByRuntimeSession.get(request.sessionKey());
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        String taskId = startRuntimeTask(request);
        if (!taskId.isEmpty()) {
            mTaskIdsByRuntimeSession.put(request.sessionKey(), taskId);
        }
        return taskId;
    }

    private String startRuntimeTask(RuntimeToolRequest request) {
        try {
            JSONObject task = new JSONObject()
                    .put("goal", "Runtime " + request.runtime()
                            + " request: " + request.tool())
                    .put("user_visible", false)
                    .put("background_allowed", true)
                    .put("runtime", request.runtime())
                    .put("runtime_session_id", request.runtimeSessionId())
                    .put("phone_session_id", request.phoneSessionId())
                    .put("approved_capabilities", new JSONArray()
                            .put("tasks.observe")
                            .put("screen.read.visible"));
            JSONObject response = parseObject(mAgentManager.startTask(task.toString()));
            return response.optString("task_id", "");
        } catch (JSONException | RuntimeException e) {
            Log.w(TAG, "runtime task start failed runtime=" + request.runtime()
                    + " tool=" + request.tool() + " error=" + e.getClass().getSimpleName());
            return "";
        }
    }

    private String confirmFrameworkActionIfNeeded(String rawResult) {
        JSONObject parsed = parseObject(rawResult);
        String pendingActionId = findStringRecursive(parsed, "pending_action_id");
        if (pendingActionId.isEmpty() || "null".equals(pendingActionId)) {
            return rawResult;
        }
        String status = parsed.optString("status", "");
        String state = parsed.optString("state", "");
        if (!status.contains("confirmation") && !state.contains("confirmation")) {
            return rawResult;
        }
        try {
            return mAgentManager.confirmAction(pendingActionId, true);
        } catch (RuntimeException e) {
            return errorJson("framework_confirmation_failed", e.getClass().getSimpleName());
        }
    }

    private static RuntimeToolResult normalizeToolResult(RuntimeToolRequest request,
            String rawResult, String auditId, String taskId) {
        JSONObject parsed = parseObject(rawResult);
        if (parsed.has("error")) {
            String message = parsed.optString("error", "tool_error");
            return RuntimeToolResult.error(request.requestId(), "tool_error", message)
                    .withOpenPhoneTaskId(taskId);
        }
        String status = parsed.optString("status", "").trim();
        if (status.contains("confirmation")) {
            return RuntimeToolResult.needsConfirmation(request.requestId(),
                    "confirmation_required", "OpenPhone requires confirmation.",
                    parsed, auditId, taskId);
        }
        if ("denied".equals(status) || status.startsWith("denied")) {
            return RuntimeToolResult.denied(request.requestId(), "denied",
                    "OpenPhone denied the request.", parsed, auditId);
        }
        return RuntimeToolResult.ok(request.requestId(), parsed, auditId, taskId);
    }

    private static JSONObject parseObject(String raw) {
        try {
            return new JSONObject(raw == null || raw.trim().isEmpty() ? "{}" : raw);
        } catch (JSONException e) {
            JSONObject json = new JSONObject();
            try {
                json.put("raw", raw == null ? "" : raw);
            } catch (JSONException ignored) {
            }
            return json;
        }
    }

    private static String errorJson(String code, String message) {
        try {
            return new JSONObject()
                    .put("error", code)
                    .put("message", message == null ? "" : message)
                    .toString();
        } catch (JSONException e) {
            return "{\"error\":\"" + code + "\"}";
        }
    }

    private static String findStringRecursive(JSONObject object, String key) {
        if (object == null || key == null || key.isEmpty()) {
            return "";
        }
        String value = object.optString(key, "");
        if (!value.isEmpty()) {
            return value;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String childKey = keys.next();
            JSONObject child = object.optJSONObject(childKey);
            if (child != null) {
                String childValue = findStringRecursive(child, key);
                if (!childValue.isEmpty()) {
                    return childValue;
                }
            }
        }
        return "";
    }

    private static String summaryForRequest(RuntimeToolRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.tool());
        String reason = request.reason();
        if (!reason.isEmpty()) {
            builder.append(": ").append(reason);
        }
        String params = paramsSummary(request.params());
        if (!params.isEmpty()) {
            builder.append(" (").append(params).append(")");
        }
        return truncate(builder.toString(), 220);
    }

    private static String paramsSummary(JSONObject params) {
        if (params == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Iterator<String> keys = params.keys();
        while (keys.hasNext() && builder.length() < 180) {
            String key = keys.next();
            if (key == null || key.startsWith("_") || "reason".equals(key)) {
                continue;
            }
            String value = params.optString(key, "");
            if (value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(key).append('=').append(truncate(value, 48));
        }
        return builder.toString();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= maxChars ? clean : clean.substring(0, maxChars - 3) + "...";
    }

    private static String auditIdFor(RuntimeToolRequest request) {
        return "runtime:" + request.runtime() + ":" + request.requestId();
    }

    private RuntimeToolResult completedResultFor(RuntimeToolRequest request,
            String auditId) {
        if (request.idempotencyKey().isEmpty()) {
            return null;
        }
        RuntimeToolResult result = mCompletedByIdempotencyKey.get(pendingKey(request));
        return result == null ? null : result.forRequest(request.requestId(), auditId);
    }

    private void rememberCompletedResult(RuntimeToolRequest request,
            RuntimeToolResult result) {
        if (request == null || result == null || request.idempotencyKey().isEmpty()
                || RuntimeToolResult.STATUS_NEEDS_CONFIRMATION.equals(result.status())) {
            return;
        }
        mCompletedByIdempotencyKey.put(pendingKey(request), result);
    }

    private void markSession(RuntimeToolRequest request, String status) {
        if (request == null || mSessionStore == null || request.phoneSessionId().isEmpty()) {
            return;
        }
        try {
            mSessionStore.touchStatus(request.phoneSessionId(), status);
        } catch (RuntimeException e) {
            Log.w(TAG, "phone session status update failed", e);
        }
    }

    private void markSessionForResult(RuntimeToolRequest request,
            RuntimeToolResult result) {
        if (result == null) {
            markSession(request, "failed");
            return;
        }
        if (RuntimeToolResult.STATUS_NEEDS_CONFIRMATION.equals(result.status())) {
            markSession(request, "waiting_for_confirmation");
            return;
        }
        markSession(request, result.ok() ? "completed" : "failed");
    }

    private static boolean isGrantedTool(String tool) {
        return DEFAULT_READ_ONLY_TOOLS.contains(tool) || isMutatingTool(tool);
    }

    private static boolean isMutatingTool(String tool) {
        return DEFAULT_MUTATING_TOOLS.contains(tool);
    }

    private static boolean isObserveOnly(String autonomy) {
        String clean = autonomy == null ? "" : autonomy.trim();
        return "observe_only".equals(clean) || "read_only".equals(clean)
                || "dry_run".equals(clean);
    }

    private String effectiveAutonomy(RuntimeToolRequest request) {
        String explicit = request == null ? "" : request.autonomy();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        if (request != null && mSessionStore != null && !request.phoneSessionId().isEmpty()) {
            PhoneExecutionSession session = mSessionStore.find(request.phoneSessionId());
            if (session != null && !session.autonomy.isEmpty()) {
                return session.autonomy;
            }
        }
        return "ask_before_action";
    }

    private static boolean isTimedOut(RuntimePendingConfirmation pending, long nowMillis) {
        if (pending == null || pending.request() == null) {
            return false;
        }
        long elapsed = Math.max(0L, nowMillis - pending.createdAtMillis());
        return elapsed >= pending.request().timeoutMs();
    }

    private static String pendingKey(RuntimeToolRequest request) {
        return request.runtime() + ":" + request.runtimeSessionId()
                + ":" + request.tool()
                + ":" + sha256Hex(request.params().toString())
                + ":" + request.idempotencyKey();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            int offset = 0;
            for (byte b : digest) {
                int v = b & 0xff;
                out[offset++] = hex[v >>> 4];
                out[offset++] = hex[v & 0x0f];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(value == null ? 0 : value.hashCode());
        }
    }

    private static void logResult(RuntimeToolRequest request, RuntimeToolResult result) {
        Log.i(TAG, "audit event=runtime_tool"
                + " runtime=" + request.runtime()
                + " request=" + request.requestId()
                + " tool=" + request.tool()
                + " status=" + result.status()
                + " caller=" + request.caller().principal());
    }
}
