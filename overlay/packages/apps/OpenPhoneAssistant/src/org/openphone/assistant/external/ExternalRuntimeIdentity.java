package org.openphone.assistant.external;

import org.json.JSONException;
import org.json.JSONObject;

public final class ExternalRuntimeIdentity {
    private final String mRuntime;
    private final String mGateway;
    private final String mPrincipal;

    public ExternalRuntimeIdentity(String runtime, String gateway, String principal) {
        mRuntime = clean(runtime);
        mGateway = clean(gateway);
        mPrincipal = clean(principal);
    }

    public String runtime() {
        return mRuntime;
    }

    public String gateway() {
        return mGateway;
    }

    public String principal() {
        return mPrincipal;
    }

    public boolean isValid() {
        return !mRuntime.isEmpty() && !mPrincipal.isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("runtime", mRuntime)
                .put("gateway", mGateway)
                .put("principal", mPrincipal);
    }

    public static ExternalRuntimeIdentity fromJson(JSONObject json, String fallbackRuntime) {
        if (json == null) {
            return new ExternalRuntimeIdentity(fallbackRuntime, "", fallbackRuntime);
        }
        return new ExternalRuntimeIdentity(
                json.optString("runtime", fallbackRuntime),
                json.optString("gateway", ""),
                json.optString("principal", fallbackRuntime));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
