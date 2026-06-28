package org.openphone.assistant.runtime;

import java.util.Locale;

public final class RuntimeRegistry {
    public static final String AUTO = "auto";
    public static final String BUILTIN = "builtin";
    public static final String OPENCLAW = "openclaw";
    public static final String HERMES = "hermes";

    private RuntimeRegistry() {
    }

    public static String normalize(String runtime) {
        String clean = runtime == null ? "" : runtime.trim().toLowerCase(Locale.US);
        if (clean.isEmpty()) {
            return "";
        }
        if ("phone".equals(clean) || "local".equals(clean)
                || "local_phone_runtime".equals(clean)) {
            return BUILTIN;
        }
        if (!isValidIdentifier(clean)) {
            return "";
        }
        return clean;
    }

    public static String cleanSelection(String runtime) {
        String clean = normalize(runtime);
        return clean.isEmpty() ? AUTO : clean;
    }

    public static boolean isBuiltin(String runtime) {
        return BUILTIN.equals(normalize(runtime));
    }

    public static boolean isKnownRuntime(String runtime) {
        String clean = normalize(runtime);
        return BUILTIN.equals(clean) || OPENCLAW.equals(clean) || HERMES.equals(clean);
    }

    public static boolean isKnownRemoteRuntime(String runtime) {
        String clean = normalize(runtime);
        return OPENCLAW.equals(clean) || HERMES.equals(clean);
    }

    public static boolean isRemoteRuntime(String runtime) {
        String clean = normalize(runtime);
        return !clean.isEmpty() && !BUILTIN.equals(clean) && !AUTO.equals(clean);
    }

    public static String label(String runtime) {
        String clean = cleanSelection(runtime);
        if (BUILTIN.equals(clean)) {
            return "Phone";
        }
        if (OPENCLAW.equals(clean)) {
            return "OpenClaw";
        }
        if (HERMES.equals(clean)) {
            return "Hermes";
        }
        if (AUTO.equals(clean)) {
            return "Auto";
        }
        return titleCaseIdentifier(clean);
    }

    public static int sortRank(String runtime) {
        String clean = normalize(runtime);
        if (BUILTIN.equals(clean)) {
            return 0;
        }
        if (OPENCLAW.equals(clean)) {
            return 10;
        }
        if (HERMES.equals(clean)) {
            return 20;
        }
        return 100;
    }

    private static boolean isValidIdentifier(String value) {
        if (value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9'
                    || c == '_' || c == '-' || c == '.';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static String titleCaseIdentifier(String runtime) {
        String clean = runtime == null ? "" : runtime.trim();
        if (clean.isEmpty()) {
            return "Runtime";
        }
        StringBuilder out = new StringBuilder(clean.length());
        boolean startWord = true;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                out.append(' ');
                startWord = true;
                continue;
            }
            if (startWord) {
                out.append(Character.toUpperCase(c));
                startWord = false;
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }
}
