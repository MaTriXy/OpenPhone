package org.openphone.assistant.external;

import java.net.URI;
import java.util.Locale;

final class ExternalRuntimeTransport {
    private ExternalRuntimeTransport() {
    }

    static String normalizeWsUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.startsWith("https://")) {
            value = "wss://" + value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = "ws://" + value.substring("http://".length());
        }
        return value;
    }

    static boolean isAllowedWebSocketUrl(String url) {
        try {
            URI uri = new URI(normalizeWsUrl(url));
            String scheme = uri.getScheme() == null
                    ? "" : uri.getScheme().toLowerCase(Locale.US);
            if ("wss".equals(scheme)) {
                return true;
            }
            if (!"ws".equals(scheme)) {
                return false;
            }
            return isLocalOrPrivateHost(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLocalOrPrivateHost(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.US);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if ("localhost".equals(value) || "::1".equals(value)
                || "0:0:0:0:0:0:0:1".equals(value)
                || value.startsWith("127.")) {
            return true;
        }
        if (value.startsWith("10.") || value.startsWith("192.168.")
                || value.startsWith("169.254.")) {
            return true;
        }
        if (!value.startsWith("172.")) {
            return false;
        }
        String[] parts = value.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
