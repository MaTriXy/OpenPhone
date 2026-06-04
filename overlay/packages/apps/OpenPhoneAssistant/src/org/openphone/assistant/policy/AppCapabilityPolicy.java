package org.openphone.assistant.policy;

import android.content.Context;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AppCapabilityPolicy {
    private static final String POLICY_PATH = "/system_ext/etc/openphone/app_policy.json";
    private static final String SECURE_OVERRIDES = "openphone_app_policy_overrides";

    private static JSONObject sPolicy;

    private AppCapabilityPolicy() {
    }

    public static Decision evaluate(Context context, String packageName, String capability) {
        if (packageName == null || packageName.isEmpty()
                || capability == null || capability.isEmpty()) {
            return Decision.inherit();
        }
        try {
            Decision userOverride = evaluatePolicy(
                    secureOverrides(context), packageName, capability, "settings_secure");
            if (!userOverride.isInherit()) {
                return userOverride;
            }
            Decision seedDecision = evaluatePolicy(
                    policy(), packageName, capability, "app_policy");
            if (!seedDecision.isInherit()) {
                return seedDecision;
            }
        } catch (JSONException e) {
            return Decision.inherit();
        }
        return Decision.inherit();
    }

    private static Decision evaluatePolicy(JSONObject policy, String packageName, String capability,
            String fallbackReason) {
        if (policy == null) {
            return Decision.inherit();
        }
        JSONArray overrides = policy.optJSONArray("package_overrides");
        if (overrides == null) {
            return Decision.inherit();
        }
        for (int i = 0; i < overrides.length(); i++) {
            JSONObject override = overrides.optJSONObject(i);
            if (override == null || !matchesPackage(override, packageName)
                    || !containsCapability(override, capability)) {
                continue;
            }
            return new Decision(
                    override.optString("decision", "inherit"),
                    override.optString("reason", fallbackReason));
        }
        return Decision.inherit();
    }

    private static JSONObject secureOverrides(Context context) throws JSONException {
        if (context == null) {
            return null;
        }
        String raw = Settings.Secure.getString(context.getContentResolver(), SECURE_OVERRIDES);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        JSONObject policy = new JSONObject(raw);
        if (policy.optInt("version", 0) != 1) {
            return null;
        }
        return policy;
    }

    private static synchronized JSONObject policy() throws JSONException {
        if (sPolicy != null) {
            return sPolicy;
        }
        File policyFile = new File(POLICY_PATH);
        if (policyFile.isFile()) {
            try {
                sPolicy = new JSONObject(readFile(policyFile));
                return sPolicy;
            } catch (IOException ignored) {
                // Fall through to the built-in conservative seed. The product
                // image should normally install the JSON policy under system_ext.
            }
        }
        sPolicy = builtInPolicy();
        return sPolicy;
    }

    private static String readFile(File file) throws IOException {
        long length = file.length();
        if (length <= 0 || length > 1024 * 1024) {
            throw new IOException("invalid policy file size");
        }
        byte[] data = new byte[(int) length];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            if (offset != data.length) {
                throw new IOException("short policy read");
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static boolean matchesPackage(JSONObject override, String packageName) {
        String configuredPackage = override.optString("package", "");
        String match = override.optString("match", "exact");
        if ("prefix".equals(match)) {
            return packageName.startsWith(configuredPackage);
        }
        return packageName.equals(configuredPackage);
    }

    private static boolean containsCapability(JSONObject override, String capability) {
        JSONArray capabilities = override.optJSONArray("capabilities");
        if (capabilities == null) {
            return false;
        }
        for (int i = 0; i < capabilities.length(); i++) {
            if (capability.equals(capabilities.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject builtInPolicy() throws JSONException {
        return new JSONObject()
                .put("version", 1)
                .put("default_decision", "inherit")
                .put("package_overrides", new JSONArray()
                        .put(override("com.android.permissioncontroller", "exact",
                                new String[]{"input.perform"}, "explicit_confirm",
                                "permission grants can expose private device data or accounts"))
                        .put(override("com.android.settings", "exact",
                                new String[]{"input.perform", "clipboard.read",
                                        "clipboard.write"}, "confirm",
                                "settings screens can change device, account, security, "
                                        + "or network state"))
                        .put(override("com.google.android.vending", "exact",
                                new String[]{"input.perform", "share.content"},
                                "explicit_confirm",
                                "app marketplace flows can install apps or initiate purchases"))
                        .put(override("com.google.android.gms", "prefix",
                                new String[]{"input.perform", "clipboard.read",
                                        "clipboard.write"}, "explicit_confirm",
                                "Google account and payment surfaces require explicit "
                                        + "user approval"))
                        .put(override("com.android.settings.password", "prefix",
                                new String[]{"input.perform", "screen.capture",
                                        "clipboard.read", "clipboard.write"}, "deny",
                                "lock credential and password surfaces are blocked for v1")));
    }

    private static JSONObject override(String packageName, String match, String[] capabilities,
            String decision, String reason) throws JSONException {
        JSONArray capabilityArray = new JSONArray();
        for (String capability : capabilities) {
            capabilityArray.put(capability);
        }
        return new JSONObject()
                .put("package", packageName)
                .put("match", match)
                .put("capabilities", capabilityArray)
                .put("decision", decision)
                .put("reason", reason);
    }

    public static final class Decision {
        public final String action;
        public final String reason;

        private Decision(String action, String reason) {
            this.action = action == null ? "inherit" : action;
            this.reason = reason == null ? "" : reason;
        }

        public static Decision inherit() {
            return new Decision("inherit", "");
        }

        public boolean isInherit() {
            return "inherit".equals(action);
        }

        public boolean requiresIntervention() {
            return "confirm".equals(action) || "explicit_confirm".equals(action)
                    || "deny".equals(action);
        }
    }
}
