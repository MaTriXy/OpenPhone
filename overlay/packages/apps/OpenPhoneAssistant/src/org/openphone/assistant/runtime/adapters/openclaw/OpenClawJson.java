package org.openphone.assistant.runtime.adapters.openclaw;

import org.json.JSONException;
import org.json.JSONObject;

final class OpenClawJson {
    private OpenClawJson() {
    }

    static JSONObject parseObject(String raw) {
        try {
            return new JSONObject(raw == null || raw.trim().isEmpty() ? "{}" : raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String clean = value == null ? "" : value.trim();
            if (!clean.isEmpty()) {
                return clean;
            }
        }
        return "";
    }

    static String firstString(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String clean = source.optString(key, "").trim();
            if (!clean.isEmpty()) {
                return clean;
            }
        }
        return "";
    }

    static String firstString(JSONObject first, JSONObject second, String... keys) {
        return firstStringFromSources(new JSONObject[] { first, second }, keys);
    }

    static String firstStringFromSources(JSONObject[] sources, String... keys) {
        if (sources == null || keys == null) {
            return "";
        }
        for (JSONObject source : sources) {
            if (source == null) {
                continue;
            }
            String clean = firstString(source, keys);
            if (!clean.isEmpty()) {
                return clean;
            }
        }
        return "";
    }
}
