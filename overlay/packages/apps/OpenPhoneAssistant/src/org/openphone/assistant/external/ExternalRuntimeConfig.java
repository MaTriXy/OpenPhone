package org.openphone.assistant.external;

import android.content.Context;
import android.provider.Settings;

public final class ExternalRuntimeConfig {
    private static final String KEY_GLOBAL_ENABLED = "openphone_external_runtimes_enabled";

    private static final String KEY_OPENCLAW_ENABLED = "openphone_external_openclaw_enabled";
    private static final String KEY_OPENCLAW_URL = "openphone_external_openclaw_url";
    private static final String KEY_OPENCLAW_TOKEN = "openphone_external_openclaw_token";
    private static final String KEY_OPENCLAW_DEVICE_ID = "openphone_external_openclaw_device_id";
    private static final String KEY_OPENCLAW_LABEL = "openphone_external_openclaw_label";

    public final boolean globallyEnabled;
    public final RuntimeSettings openClaw;

    private ExternalRuntimeConfig(boolean globallyEnabled, RuntimeSettings openClaw) {
        this.globallyEnabled = globallyEnabled;
        this.openClaw = openClaw;
    }

    public boolean anyEnabled() {
        return globallyEnabled && openClaw.enabled;
    }

    public static ExternalRuntimeConfig load(Context context) {
        boolean global = secureBool(context, KEY_GLOBAL_ENABLED, false);
        return new ExternalRuntimeConfig(global,
                new RuntimeSettings("openclaw",
                        secureBool(context, KEY_OPENCLAW_ENABLED, false),
                        secureString(context, KEY_OPENCLAW_URL),
                        secureString(context, KEY_OPENCLAW_TOKEN),
                        secureString(context, KEY_OPENCLAW_DEVICE_ID),
                        secureString(context, KEY_OPENCLAW_LABEL)));
    }

    private static boolean secureBool(Context context, String key, boolean fallback) {
        String value = Settings.Secure.getString(context.getContentResolver(), key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim())
                || "yes".equalsIgnoreCase(value.trim());
    }

    private static String secureString(Context context, String key) {
        String value = Settings.Secure.getString(context.getContentResolver(), key);
        return value == null ? "" : value.trim();
    }

    public static final class RuntimeSettings {
        public final String runtime;
        public final boolean enabled;
        public final String url;
        public final String token;
        public final String deviceId;
        public final String label;

        RuntimeSettings(String runtime, boolean enabled, String url, String token,
                String deviceId, String label) {
            this.runtime = runtime;
            this.enabled = enabled;
            this.url = url;
            this.token = token;
            this.deviceId = deviceId;
            this.label = label == null || label.isEmpty() ? runtime : label;
        }

        public boolean configured() {
            return enabled && !url.isEmpty();
        }
    }
}
