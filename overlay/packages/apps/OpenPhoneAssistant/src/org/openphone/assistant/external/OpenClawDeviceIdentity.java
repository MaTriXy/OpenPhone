package org.openphone.assistant.external;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

final class OpenClawDeviceIdentity {
    private static final String TAG = "OpenPhoneOpenClawId";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final File mIdentityFile;
    private DeviceRecord mCached;

    OpenClawDeviceIdentity(Context context) {
        mIdentityFile = new File(context.getFilesDir(),
                "openphone/external/openclaw_device_identity.json");
    }

    synchronized DeviceRecord loadOrCreate() {
        if (mCached != null) {
            return mCached;
        }
        DeviceRecord existing = readIdentity();
        if (existing != null) {
            String derived = deriveDeviceId(existing.publicKeyRawBase64);
            if (!derived.isEmpty() && !derived.equals(existing.deviceId)) {
                existing = new DeviceRecord(derived, existing.publicKeyRawBase64,
                        existing.privateKeySeedBase64, existing.createdAtMs,
                        existing.deviceToken, existing.deviceTokenScopes);
                writeIdentity(existing);
            }
            mCached = existing;
            return existing;
        }
        DeviceRecord fresh = generate();
        writeIdentity(fresh);
        mCached = fresh;
        return fresh;
    }

    synchronized void storeDeviceToken(String deviceToken, JSONArray scopes) {
        if (deviceToken == null || deviceToken.trim().isEmpty()) {
            return;
        }
        DeviceRecord current = loadOrCreate();
        DeviceRecord updated = new DeviceRecord(current.deviceId, current.publicKeyRawBase64,
                current.privateKeySeedBase64, current.createdAtMs, deviceToken.trim(),
                copyStringArray(scopes));
        writeIdentity(updated);
        mCached = updated;
    }

    synchronized String storedDeviceToken() {
        return loadOrCreate().deviceToken;
    }

    synchronized JSONArray storedDeviceTokenScopes() {
        return toJsonArray(loadOrCreate().deviceTokenScopes);
    }

    synchronized JSONObject signedDevice(String clientId, String clientMode, String role,
            JSONArray scopes, String token, String nonce, String platform, String deviceFamily)
            throws JSONException {
        if (nonce == null || nonce.trim().isEmpty()) {
            throw new JSONException("OpenClaw connect nonce is required");
        }
        DeviceRecord identity = loadOrCreate();
        long signedAtMs = System.currentTimeMillis();
        String payload = buildV3Payload(identity.deviceId, clientId, clientMode, role,
                scopes, signedAtMs, token, nonce, platform, deviceFamily);
        String signature = signPayload(payload, identity);
        String publicKey = publicKeyBase64Url(identity);
        if (signature.isEmpty() || publicKey.isEmpty()) {
            throw new JSONException("Could not sign OpenClaw device auth payload");
        }
        return new JSONObject()
                .put("id", identity.deviceId)
                .put("publicKey", publicKey)
                .put("signature", signature)
                .put("signedAt", signedAtMs)
                .put("nonce", nonce);
    }

    private DeviceRecord readIdentity() {
        try {
            if (!mIdentityFile.exists()) {
                return null;
            }
            byte[] bytes = new byte[(int) mIdentityFile.length()];
            try (FileInputStream in = new FileInputStream(mIdentityFile)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = in.read(bytes, offset, bytes.length - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
            }
            JSONObject object = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            DeviceRecord record = DeviceRecord.fromJson(object);
            if (record.deviceId.isEmpty()
                    || record.publicKeyRawBase64.isEmpty()
                    || !hasValidSeed(record.privateKeySeedBase64)) {
                return null;
            }
            return record;
        } catch (Exception e) {
            Log.w(TAG, "Ignoring unreadable OpenClaw identity");
            return null;
        }
    }

    private void writeIdentity(DeviceRecord record) {
        try {
            File parent = mIdentityFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            byte[] bytes = record.toJson().toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(mIdentityFile, false)) {
                out.write(bytes);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not persist OpenClaw identity");
        }
    }

    private DeviceRecord generate() {
        try {
            OpenClawEd25519.KeyPairData keyPair = OpenClawEd25519.generate(new SecureRandom());
            String publicKeyRawBase64 = Base64.encodeToString(keyPair.publicKey, Base64.NO_WRAP);
            String privateKeySeedBase64 = Base64.encodeToString(keyPair.seed, Base64.NO_WRAP);
            return new DeviceRecord(sha256Hex(keyPair.publicKey), publicKeyRawBase64,
                    privateKeySeedBase64, System.currentTimeMillis(), "",
                    new String[0]);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate OpenClaw device identity", e);
        }
    }

    private String signPayload(String payload, DeviceRecord identity) {
        try {
            byte[] seed = Base64.decode(identity.privateKeySeedBase64, Base64.DEFAULT);
            return base64UrlEncode(OpenClawEd25519.sign(
                    payload.getBytes(StandardCharsets.UTF_8), seed));
        } catch (Exception e) {
            Log.w(TAG, "OpenClaw device signing failed " + e.getClass().getSimpleName());
            return "";
        }
    }

    private String publicKeyBase64Url(DeviceRecord identity) {
        try {
            return base64UrlEncode(Base64.decode(identity.publicKeyRawBase64, Base64.DEFAULT));
        } catch (Exception e) {
            return "";
        }
    }

    private static String buildV3Payload(String deviceId, String clientId, String clientMode,
            String role, JSONArray scopes, long signedAtMs, String token, String nonce,
            String platform, String deviceFamily) {
        return join("|",
                "v3",
                safe(deviceId),
                safe(clientId),
                safe(clientMode),
                safe(role),
                joinScopes(scopes),
                String.valueOf(signedAtMs),
                safe(token),
                safe(nonce),
                normalizeMetadataField(platform),
                normalizeMetadataField(deviceFamily));
    }

    private static String normalizeMetadataField(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                out.append((char) (ch + 32));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String joinScopes(JSONArray scopes) {
        if (scopes == null || scopes.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < scopes.length(); i++) {
            String value = scopes.optString(i, "");
            if (value.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(value);
        }
        return out.toString();
    }

    private static String deriveDeviceId(String publicKeyRawBase64) {
        try {
            return sha256Hex(Base64.decode(publicKeyRawBase64, Base64.DEFAULT));
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean hasValidSeed(String privateKeySeedBase64) {
        try {
            return OpenClawEd25519.isValidSeed(
                    Base64.decode(privateKeySeedBase64, Base64.DEFAULT));
        } catch (Exception e) {
            return false;
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        char[] out = new char[digest.length * 2];
        int index = 0;
        for (byte b : digest) {
            int v = b & 0xff;
            out[index++] = HEX[v >>> 4];
            out[index++] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.encodeToString(data,
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String join(String delimiter, String... parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append(delimiter);
            }
            out.append(parts[i]);
        }
        return out.toString();
    }

    private static String[] copyStringArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new String[0];
        }
        String[] out = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            out[i] = array.optString(i, "");
        }
        return out;
    }

    private static JSONArray toJsonArray(String[] values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                array.put(value);
            }
        }
        return array;
    }

    static final class DeviceRecord {
        final String deviceId;
        final String publicKeyRawBase64;
        final String privateKeySeedBase64;
        final long createdAtMs;
        final String deviceToken;
        final String[] deviceTokenScopes;

        DeviceRecord(String deviceId, String publicKeyRawBase64, String privateKeySeedBase64,
                long createdAtMs, String deviceToken, String[] deviceTokenScopes) {
            this.deviceId = safe(deviceId);
            this.publicKeyRawBase64 = safe(publicKeyRawBase64);
            this.privateKeySeedBase64 = safe(privateKeySeedBase64);
            this.createdAtMs = createdAtMs;
            this.deviceToken = safe(deviceToken);
            this.deviceTokenScopes = deviceTokenScopes == null ? new String[0]
                    : Arrays.copyOf(deviceTokenScopes, deviceTokenScopes.length);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("deviceId", deviceId)
                    .put("publicKeyRawBase64", publicKeyRawBase64)
                    .put("privateKeySeedBase64", privateKeySeedBase64)
                    .put("createdAtMs", createdAtMs)
                    .put("deviceToken", deviceToken)
                    .put("deviceTokenScopes", toJsonArray(deviceTokenScopes));
        }

        static DeviceRecord fromJson(JSONObject object) {
            return new DeviceRecord(
                    object.optString("deviceId", ""),
                    object.optString("publicKeyRawBase64", ""),
                    object.optString("privateKeySeedBase64", ""),
                    object.optLong("createdAtMs", System.currentTimeMillis()),
                    object.optString("deviceToken", ""),
                    copyStringArray(object.optJSONArray("deviceTokenScopes")));
        }
    }
}
