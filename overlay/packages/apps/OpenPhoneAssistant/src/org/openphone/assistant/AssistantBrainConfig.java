package org.openphone.assistant;

import android.content.Context;
import android.provider.Settings;

import org.openphone.assistant.external.ExternalRuntimeConfig;

import java.util.Locale;

public final class AssistantBrainConfig {
    public static final String KEY_ASSISTANT_BRAIN = "openphone_assistant_brain";
    public static final String BUILTIN = "builtin";
    public static final String OPENCLAW = "openclaw";
    public static final String HERMES = "hermes";
    public static final String AUTO = "auto";

    private AssistantBrainConfig() {
    }

    public static String loadMode(Context context) {
        String raw = Settings.Secure.getString(context.getContentResolver(),
                KEY_ASSISTANT_BRAIN);
        return cleanMode(raw);
    }

    public static void persistMode(Context context, String mode) {
        Settings.Secure.putString(context.getContentResolver(), KEY_ASSISTANT_BRAIN,
                cleanMode(mode));
    }

    public static String cleanMode(String mode) {
        String clean = mode == null ? "" : mode.trim().toLowerCase(Locale.US);
        if (BUILTIN.equals(clean) || OPENCLAW.equals(clean) || HERMES.equals(clean)) {
            return clean;
        }
        return AUTO;
    }

    public static String label(String mode) {
        String clean = cleanMode(mode);
        if (BUILTIN.equals(clean)) {
            return "Built-in";
        }
        if (OPENCLAW.equals(clean)) {
            return "OpenClaw";
        }
        if (HERMES.equals(clean)) {
            return "Hermes";
        }
        return "Auto";
    }

    public static String routeRuntime(Context context, ExternalRuntimeConfig config) {
        String mode = loadMode(context);
        if (BUILTIN.equals(mode)) {
            return BUILTIN;
        }
        if (OPENCLAW.equals(mode)) {
            return OPENCLAW;
        }
        if (HERMES.equals(mode)) {
            return HERMES;
        }
        if (config != null && config.globallyEnabled && config.openClaw.configured()) {
            return OPENCLAW;
        }
        if (config != null && config.globallyEnabled && config.hermes.configured()) {
            return HERMES;
        }
        return BUILTIN;
    }
}
