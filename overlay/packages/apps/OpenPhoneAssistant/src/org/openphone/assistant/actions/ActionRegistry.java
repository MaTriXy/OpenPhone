package org.openphone.assistant.actions;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ActionRegistry {
    private static final String TAG = "OpenPhoneActionRegistry";
    private static final String SYSTEM_EXT_REGISTRY =
            "/system_ext/etc/openphone/action_registry.json";

    private final boolean mLoaded;
    private final Map<String, ActionMetadata> mByTool;
    private final List<ActionMetadata> mOrdered;

    private ActionRegistry(boolean loaded, Map<String, ActionMetadata> byTool,
            List<ActionMetadata> ordered) {
        mLoaded = loaded;
        mByTool = byTool;
        mOrdered = Collections.unmodifiableList(ordered);
    }

    public static ActionRegistry load() {
        File file = new File(SYSTEM_EXT_REGISTRY);
        if (!file.exists()) {
            Log.w(TAG, "Action registry not installed: " + SYSTEM_EXT_REGISTRY);
            return new ActionRegistry(false, new HashMap<String, ActionMetadata>(),
                    new ArrayList<ActionMetadata>());
        }
        try {
            String raw = readUtf8(file);
            JSONObject root = new JSONObject(raw);
            JSONArray actions = root.optJSONArray("actions");
            Map<String, ActionMetadata> byTool = new HashMap<>();
            List<ActionMetadata> ordered = new ArrayList<>();
            if (actions != null) {
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject action = actions.optJSONObject(i);
                    if (action == null) {
                        continue;
                    }
                    ActionMetadata metadata = ActionMetadata.fromJson(action);
                    if (!metadata.modelTool.isEmpty()) {
                        byTool.put(metadata.modelTool, metadata);
                        ordered.add(metadata);
                    }
                }
            }
            return new ActionRegistry(true, byTool, ordered);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Failed to load action registry", e);
            return new ActionRegistry(false, new HashMap<String, ActionMetadata>(),
                    new ArrayList<ActionMetadata>());
        }
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public boolean hasTool(String modelTool) {
        return mByTool.containsKey(modelTool == null ? "" : modelTool);
    }

    public ActionMetadata forTool(String modelTool) {
        return mByTool.get(modelTool == null ? "" : modelTool);
    }

    public List<ActionMetadata> tools() {
        return mOrdered;
    }

    private static String readUtf8(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static final class ActionMetadata {
        public final String name;
        public final String modelTool;
        public final String description;
        public final String kind;
        public final String riskClass;
        public final String authorizationPolicy;
        public final String executorService;
        public final String auditEventType;
        public final String primaryCapability;
        public final JSONObject inputSchema;
        public final List<String> requiredInputs;

        private ActionMetadata(String name, String modelTool, String description, String kind,
                String riskClass, String authorizationPolicy, String executorService,
                String auditEventType, String primaryCapability, JSONObject inputSchema,
                List<String> requiredInputs) {
            this.name = name;
            this.modelTool = modelTool;
            this.description = description;
            this.kind = kind;
            this.riskClass = riskClass;
            this.authorizationPolicy = authorizationPolicy;
            this.executorService = executorService;
            this.auditEventType = auditEventType;
            this.primaryCapability = primaryCapability;
            this.inputSchema = inputSchema;
            this.requiredInputs = Collections.unmodifiableList(requiredInputs);
        }

        public boolean requiresReason() {
            return requiredInputs.contains("reason");
        }

        static ActionMetadata fromJson(JSONObject json) {
            JSONObject inputSchema = json.optJSONObject("input_schema_json");
            if (inputSchema == null) {
                inputSchema = new JSONObject();
            }
            List<String> requiredInputs = new ArrayList<>();
            JSONArray required = inputSchema.optJSONArray("required");
            if (required != null) {
                for (int i = 0; i < required.length(); i++) {
                    String value = required.optString(i, "");
                    if (!value.isEmpty()) {
                        requiredInputs.add(value);
                    }
                }
            }
            JSONArray capabilities = json.optJSONArray("required_capabilities");
            String primaryCapability = capabilities == null
                    ? "" : capabilities.optString(0, "");
            return new ActionMetadata(
                    json.optString("name", ""),
                    json.optString("model_tool", ""),
                    json.optString("description", ""),
                    json.optString("kind", ""),
                    json.optString("risk_class", ""),
                    json.optString("authorization_policy", ""),
                    json.optString("executor_service", ""),
                    json.optString("audit_event_type", ""),
                    primaryCapability,
                    inputSchema,
                    requiredInputs);
        }
    }
}
