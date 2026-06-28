package org.openphone.assistant.runtime.adapters.openclaw;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.runtime.RuntimeToolRequest;

import java.util.LinkedHashMap;
import java.util.Map;

final class OpenClawCommandRegistry {
    private static final String CANVAS_SNAPSHOT = "canvas.snapshot";

    private final Map<String, String> mCommandToTool;

    private OpenClawCommandRegistry(Map<String, String> commandToTool) {
        mCommandToTool = commandToTool;
    }

    static OpenClawCommandRegistry createDefault() {
        Map<String, String> commandToTool = new LinkedHashMap<>();
        commandToTool.put("openphone.device.status", "phone_context");
        commandToTool.put("device.status", "phone_context");
        commandToTool.put("device.info", "phone_context");
        commandToTool.put("openphone.apps.search", "apps_search");
        commandToTool.put("device.apps", "apps_search");
        commandToTool.put("openphone.notifications.list", "notifications_list");
        commandToTool.put("notifications.list", "notifications_list");
        commandToTool.put("openphone.notifications.search", "notifications_search");
        commandToTool.put("notifications.search", "notifications_search");
        commandToTool.put("openphone.contacts.search", "contacts_search");
        commandToTool.put("contacts.search", "contacts_search");
        commandToTool.put("openphone.calendar.search", "calendar_search");
        commandToTool.put("calendar.events", "calendar_search");
        commandToTool.put("openphone.messages.search", "messages_search");
        commandToTool.put("sms.search", "messages_search");
        commandToTool.put("openphone.calls.search", "calls_search");
        commandToTool.put("callLog.search", "calls_search");
        commandToTool.put("openphone.screen.get", "get_screen");
        commandToTool.put("canvas.snapshot", "get_screen");
        commandToTool.put("openphone.screen.understand_local",
                "local_screen_understanding");
        commandToTool.put("openphone.local.screen_understanding",
                "local_screen_understanding");
        commandToTool.put("openphone.jobs.list", "background_job_list");
        commandToTool.put("openphone.notifications.open", "notifications_open");
        commandToTool.put("notifications.open", "notifications_open");
        commandToTool.put("openphone.calendar.add", "calendar_create_event");
        commandToTool.put("calendar.add", "calendar_create_event");
        commandToTool.put("openphone.calendar.update", "calendar_update_event");
        commandToTool.put("calendar.update", "calendar_update_event");
        commandToTool.put("openphone.calendar.delete", "calendar_delete_event");
        commandToTool.put("calendar.delete", "calendar_delete_event");
        commandToTool.put("openphone.messages.draft", "messages_draft");
        commandToTool.put("sms.draft", "messages_draft");
        commandToTool.put("openphone.messages.send", "messages_send");
        commandToTool.put("sms.send", "messages_send");
        commandToTool.put("openphone.calls.place", "calls_place");
        commandToTool.put("calls.place", "calls_place");
        commandToTool.put("openphone.memory.search", "memory_search");
        commandToTool.put("openphone.memory.save", "memory_save");
        commandToTool.put("openphone.watchers.list", "watcher_list");
        commandToTool.put("openphone.watchers.create", "watcher_create");
        commandToTool.put("openphone.watchers.stop", "watcher_stop");
        commandToTool.put("openphone.app.open", "open_app");
        commandToTool.put("openphone.url.open", "open_url");
        commandToTool.put("openphone.ui.tap", "tap");
        commandToTool.put("openphone.ui.tap_element", "tap_element");
        commandToTool.put("openphone.ui.long_press", "long_press");
        commandToTool.put("openphone.ui.long_press_element", "long_press_element");
        commandToTool.put("openphone.ui.swipe", "swipe");
        commandToTool.put("openphone.ui.type_text", "type_text");
        commandToTool.put("openphone.input.press_key", "press_key");
        commandToTool.put("openphone.clipboard.set", "set_clipboard");
        commandToTool.put("openphone.clipboard.paste", "paste");
        commandToTool.put("openphone.share.text", "share_text");
        commandToTool.put("openphone.jobs.create", "background_job_create");
        commandToTool.put("openphone.jobs.stop", "background_job_stop");
        return new OpenClawCommandRegistry(commandToTool);
    }

    String toolFor(String command) {
        return mCommandToTool.get(command);
    }

    boolean isCanvasSnapshot(String command) {
        return CANVAS_SNAPSHOT.equals(command);
    }

    JSONArray commands() {
        JSONArray commands = new JSONArray();
        for (String command : mCommandToTool.keySet()) {
            commands.put(command);
        }
        return commands;
    }

    JSONObject permissions() {
        JSONObject permissions = new JSONObject();
        for (String command : mCommandToTool.keySet()) {
            try {
                permissions.put(command, true);
            } catch (JSONException ignored) {
            }
        }
        return permissions;
    }

    JSONObject mapParams(String command, String tool, JSONObject params) {
        JSONObject mapped = RuntimeToolRequest.copy(params);
        try {
            if ("device.apps".equals(command) && !mapped.has("query")) {
                mapped.put("query", params.optString("query", ""));
            }
            if ("openphone.screen.get".equals(command)) {
                mapped.put("include_screenshot", params.has("include_screenshot")
                        ? params.optBoolean("include_screenshot", true) : true);
                mapped.put("include_activity", params.optBoolean("include_activity", true));
                mapped.put("include_ui_tree", params.optBoolean("include_ui_tree", true));
            }
            if (CANVAS_SNAPSHOT.equals(command)) {
                mapped.put("include_screenshot", true);
                mapped.put("include_activity", params.optBoolean("include_activity", true));
                mapped.put("include_ui_tree", params.optBoolean("include_ui_tree", false));
            }
            mapped.put("_openclaw_command", command);
            mapped.put("_openphone_tool", tool);
        } catch (JSONException ignored) {
        }
        return mapped;
    }
}
