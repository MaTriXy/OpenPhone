package org.openphone.assistant.agent;

import android.content.Context;
import android.openphone.OpenPhoneAgentManager;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openphone.assistant.OpenPhoneAccessibilityService;

public final class FrameworkToolExecutor {
    private final Context mContext;
    private final OpenPhoneAgentManager mAgentManager;

    public FrameworkToolExecutor(Context context, OpenPhoneAgentManager agentManager) {
        mContext = context;
        mAgentManager = agentManager;
    }

    public String execute(String taskId, String toolName, JSONObject arguments) {
        if (mAgentManager == null) {
            return error("framework_unavailable");
        }
        if (taskId == null || taskId.isEmpty()) {
            return error("no_active_task");
        }
        if (requiresModelReason(toolName) && arguments.optString("reason", "").trim().isEmpty()) {
            return error("missing_reason:" + toolName);
        }
        try {
            switch (toolName) {
                case "get_screen":
                    return getScreen(taskId, arguments);
                case "watch_screen":
                    return watchScreen(taskId, arguments);
                case "open_app":
                    String requestedPackage = packageName(arguments.optString("package",
                            arguments.optString("package_or_label")));
                    return mAgentManager.executeAction(taskId, action("open_app")
                            .put("package", requestedPackage)
                            .put("label", arguments.optString("label",
                                    arguments.optString("package_or_label")))
                            .put("reason", arguments.optString("reason")).toString());
                case "open_url":
                    return mAgentManager.executeAction(taskId, action("open_url")
                            .put("url", normalizedUrl(arguments.optString("url")))
                            .put("reason", arguments.optString("reason")).toString());
                case "tap":
                    return mAgentManager.executeAction(taskId, action("tap")
                            .put("target", point(arguments.optDouble("x"), arguments.optDouble("y")))
                            .put("reason", arguments.optString("reason")).toString());
                case "tap_element":
                    return mAgentManager.executeAction(taskId, action("tap")
                            .put("target", elementCenter(arguments))
                            .put("reason", arguments.optString("reason")).toString());
                case "long_press":
                    return mAgentManager.executeAction(taskId, action("long_press")
                            .put("target", point(arguments.optDouble("x"), arguments.optDouble("y")))
                            .put("duration_ms", arguments.optLong("duration_ms", 650))
                            .put("reason", arguments.optString("reason")).toString());
                case "long_press_element":
                    return mAgentManager.executeAction(taskId, action("long_press")
                            .put("target", elementCenter(arguments))
                            .put("duration_ms", arguments.optLong("duration_ms", 650))
                            .put("reason", arguments.optString("reason")).toString());
                case "swipe":
                    return mAgentManager.executeAction(taskId, action("scroll")
                            .put("target", new JSONObject()
                                    .put("start_x", arguments.optDouble("start_x"))
                                    .put("start_y", arguments.optDouble("start_y"))
                                    .put("end_x", arguments.optDouble("end_x"))
                                    .put("end_y", arguments.optDouble("end_y")))
                            .put("reason", arguments.optString("reason")).toString());
                case "type_text":
                    return mAgentManager.executeAction(taskId, action("type_text")
                            .put("text", arguments.optString("text"))
                            .put("reason", arguments.optString("reason")).toString());
                case "press_key":
                    return pressKey(taskId, arguments);
                case "set_clipboard":
                    return mAgentManager.executeAction(taskId, action("copy")
                            .put("text", arguments.optString("text"))
                            .put("reason", arguments.optString("reason")).toString());
                case "paste":
                    return mAgentManager.executeAction(taskId, action("paste")
                            .put("reason", arguments.optString("reason")).toString());
                case "share_text":
                    return mAgentManager.executeAction(taskId, action("share")
                            .put("text", arguments.optString("text"))
                            .put("chooser_title", arguments.optString("chooser_title"))
                            .put("reason", arguments.optString("reason")).toString());
                case "wait":
                    return waitFor(arguments.optLong("duration_ms", 1000),
                            arguments.optString("reason"));
                case "ask_user_confirmation":
                    return new JSONObject()
                            .put("status", "confirmation_requested")
                            .put("summary", arguments.optString("summary"))
                            .put("risk", arguments.optString("risk"))
                            .put("reason", arguments.optString("reason"))
                            .put("action", arguments.optJSONObject("action_json") == null
                                    ? new JSONObject() : arguments.optJSONObject("action_json"))
                            .toString();
                case "finish_task":
                    return status("task.finished", arguments.optString("summary"));
                case "fail_task":
                    return status("task.failed", arguments.optString("reason"));
                default:
                    return error("unknown_tool:" + toolName);
            }
        } catch (JSONException e) {
            return error("json_error:" + e.getMessage());
        }
    }

    private static boolean requiresModelReason(String toolName) {
        switch (toolName) {
            case "get_screen":
            case "watch_screen":
            case "open_app":
            case "open_url":
            case "tap":
            case "tap_element":
            case "long_press":
            case "long_press_element":
            case "swipe":
            case "type_text":
            case "press_key":
            case "set_clipboard":
            case "paste":
            case "share_text":
            case "wait":
            case "ask_user_confirmation":
                return true;
            default:
                return false;
        }
    }

    private String getScreen(String taskId, JSONObject arguments) throws JSONException {
        JSONObject uiTree = accessibilitySnapshot();
        if (shouldBlockScreenshot(arguments, uiTree)) {
            return blockedScreenResult("sensitive_screen_screenshot_blocked", uiTree);
        }
        String screen = mAgentManager.getScreen(taskId, arguments.toString());
        return enrichScreenResult(screen, arguments, uiTree);
    }

    private String watchScreen(String taskId, JSONObject arguments) throws JSONException {
        long durationMs = Math.max(500, Math.min(arguments.optLong("duration_ms", 1500), 5000));
        int fps = Math.max(1, Math.min(arguments.optInt("fps", 1), 5));
        JSONObject boundedArguments = new JSONObject(arguments.toString())
                .put("duration_ms", durationMs)
                .put("fps", fps);
        JSONObject uiTree = accessibilitySnapshot();
        if (shouldBlockScreenshot(boundedArguments, uiTree)) {
            return blockedScreenResult("sensitive_screen_watch_blocked", uiTree);
        }
        String screen = mAgentManager.watchScreen(taskId, boundedArguments.toString());
        return enrichScreenResult(screen, boundedArguments, uiTree);
    }

    private String enrichScreenResult(String screen, JSONObject arguments, JSONObject uiTree)
            throws JSONException {
        if (!arguments.optBoolean("include_ui_tree", false)) {
            return screen;
        }
        JSONObject screenJson = new JSONObject(screen);
        screenJson.put("ui_tree", uiTree);
        JSONArray visibleText = uiTree.optJSONArray("visible_text");
        if (visibleText != null) {
            screenJson.put("visible_text", visibleText);
        }
        JSONArray elements = uiTree.optJSONArray("interactive_elements");
        if (elements != null) {
            screenJson.put("interactive_elements", elements);
        }
        return screenJson.toString();
    }

    private static JSONObject accessibilitySnapshot() throws JSONException {
        return new JSONObject(OpenPhoneAccessibilityService.snapshotJson());
    }

    private static boolean shouldBlockScreenshot(JSONObject arguments, JSONObject uiTree) {
        return arguments.optBoolean("include_screenshot", false)
                && hasSensitiveScreenFlag(uiTree);
    }

    private static boolean hasSensitiveScreenFlag(JSONObject uiTree) {
        JSONArray flags = uiTree.optJSONArray("risk_flags");
        if (flags == null) {
            return false;
        }
        for (int i = 0; i < flags.length(); i++) {
            String flag = flags.optString(i);
            if ("sensitive_input_visible".equals(flag)
                    || "account_or_payment_hint_visible".equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String blockedScreenResult(String reason, JSONObject uiTree) throws JSONException {
        return new JSONObject()
                .put("status", "screen.blocked")
                .put("reason", reason)
                .put("screen_capture_included", false)
                .put("ui_tree", uiTree)
                .put("risk_flags", uiTree.optJSONArray("risk_flags") == null
                        ? new JSONArray() : uiTree.optJSONArray("risk_flags"))
                .toString();
    }

    private static String waitFor(long durationMs, String reason) throws JSONException {
        long boundedDurationMs = Math.max(250, Math.min(durationMs, 5000));
        try {
            Thread.sleep(boundedDurationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("interrupted");
        }
        return new JSONObject()
                .put("status", "waited")
                .put("duration_ms", boundedDurationMs)
                .put("reason", reason == null ? "" : reason)
                .toString();
    }

    private static String normalizedUrl(String url) throws JSONException {
        if (url == null || url.trim().isEmpty()) {
            throw new JSONException("missing_url");
        }

        String normalizedUrl = url.trim();
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "https://" + normalizedUrl;
        }
        return normalizedUrl;
    }

    private String pressKey(String taskId, JSONObject arguments) throws JSONException {
        String key = arguments.optString("key", "back").trim().toLowerCase();
        String reason = arguments.optString("reason");
        if ("enter".equals(key) || "search".equals(key) || "go".equals(key)
                || "done".equals(key)) {
            return mAgentManager.executeAction(taskId, action("tap")
                    .put("target", point(930, 2360))
                    .put("reason", reason).toString());
        }
        return mAgentManager.executeAction(taskId, action(key).put("reason", reason).toString());
    }

    private static JSONObject action(String type) throws JSONException {
        return new JSONObject().put("type", type);
    }

    private static JSONObject point(double x, double y) throws JSONException {
        return new JSONObject().put("x", x).put("y", y);
    }

    private static JSONObject elementCenter(JSONObject arguments) throws JSONException {
        String elementId = arguments.optString("element_id", "").trim();
        if (elementId.isEmpty()) {
            throw new JSONException("missing_element_id");
        }
        JSONObject snapshot = accessibilitySnapshot();
        JSONArray elements = snapshot.optJSONArray("interactive_elements");
        if (elements == null) {
            throw new JSONException("interactive_elements_unavailable");
        }
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.optJSONObject(i);
            if (element == null || !elementId.equals(element.optString("id"))) {
                continue;
            }
            if (!element.optBoolean("enabled", true)) {
                throw new JSONException("element_disabled:" + elementId);
            }
            JSONArray bounds = element.optJSONArray("bounds");
            if (bounds == null || bounds.length() < 4) {
                throw new JSONException("element_bounds_unavailable:" + elementId);
            }
            double left = bounds.optDouble(0);
            double top = bounds.optDouble(1);
            double right = bounds.optDouble(2);
            double bottom = bounds.optDouble(3);
            if (right <= left || bottom <= top) {
                throw new JSONException("element_bounds_invalid:" + elementId);
            }
            return point((left + right) / 2.0, (top + bottom) / 2.0);
        }
        throw new JSONException("element_not_found:" + elementId);
    }

    private static String packageName(String packageOrLabel) {
        if (packageOrLabel == null) {
            return "";
        }
        String value = packageOrLabel.trim();
        if (value.indexOf('.') >= 0) {
            return value;
        }
        if ("settings".equalsIgnoreCase(value)) {
            return "com.android.settings";
        }
        if ("browser".equalsIgnoreCase(value) || "web".equalsIgnoreCase(value)
                || "jelly".equalsIgnoreCase(value)) {
            return "org.lineageos.jelly";
        }
        if ("play store".equalsIgnoreCase(value) || "google play".equalsIgnoreCase(value)
                || "google play store".equalsIgnoreCase(value)) {
            return "com.android.vending";
        }
        if ("spotify".equalsIgnoreCase(value)) {
            return "com.spotify.music";
        }
        if ("twitter".equalsIgnoreCase(value) || "x".equalsIgnoreCase(value)) {
            return "com.twitter.android";
        }
        if ("assistant".equalsIgnoreCase(value) || "openphone".equalsIgnoreCase(value)) {
            return "org.openphone.assistant";
        }
        return value;
    }

    private static String error(String reason) {
        try {
            return new JSONObject().put("status", "error").put("reason", reason).toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\"}";
        }
    }

    private static String status(String status, String detail) {
        try {
            return new JSONObject().put("status", status).put("detail", detail).toString();
        } catch (JSONException e) {
            return "{\"status\":\"" + status + "\"}";
        }
    }
}
