package org.openphone.assistant.runtime.adapters.openclaw;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class OpenClawDeviceIdentity {
    private static final String TAG = "OpenPhoneOpenClawId";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String SEED_KEY_ALIAS = "openphone.runtime.openclaw.identity.seed";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private final File mIdentityFile;
    private final File mLegacyIdentityFile;
    private DeviceRecord mCached;

    OpenClawDeviceIdentity(Context context) {
        mIdentityFile = new File(context.getFilesDir(),
                "openphone/runtime/openclaw_device_identity.json");
        mLegacyIdentityFile = new File(context.getFilesDir(),
                "openphone/external/openclaw_device_identity.json");
    }

    synchronized DeviceRecord loadOrCreate() {
        if (mCached != null) {
            return mCached;
        }
        DeviceRecord existing = readIdentity(mIdentityFile);
        if (existing != null && writeIdentity(existing)) {
            deleteLegacyIdentity();
        }
        if (existing == null) {
            existing = readIdentity(mLegacyIdentityFile);
            if (existing != null) {
                if (writeIdentity(existing)) {
                    deleteLegacyIdentity();
                }
            }
        }
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

    private DeviceRecord readIdentity(File file) {
        try {
            if (file == null || !file.exists()) {
                return null;
            }
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
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
            DeviceRecord record = recordFromStorageJson(object);
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

    private boolean writeIdentity(DeviceRecord record) {
        try {
            File parent = mIdentityFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
                restrictOwnerOnly(parent, true);
            }
            byte[] bytes = storageJson(record).toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(mIdentityFile, false)) {
                out.write(bytes);
            }
            restrictOwnerOnly(mIdentityFile, false);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not persist OpenClaw identity");
            return false;
        }
    }

    private void deleteLegacyIdentity() {
        try {
            if (mLegacyIdentityFile != null && mLegacyIdentityFile.exists()
                    && !mLegacyIdentityFile.delete()) {
                Log.w(TAG, "Could not remove legacy OpenClaw identity");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Could not remove legacy OpenClaw identity");
        }
    }

    private static void restrictOwnerOnly(File file, boolean directory) {
        if (file == null) {
            return;
        }
        try {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
            if (directory) {
                file.setExecutable(true, true);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Could not restrict OpenClaw identity file mode");
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

    private JSONObject storageJson(DeviceRecord record) throws Exception {
        EncryptedValue encryptedSeed = encryptString(record.privateKeySeedBase64);
        return new JSONObject()
                .put("deviceId", record.deviceId)
                .put("publicKeyRawBase64", record.publicKeyRawBase64)
                .put("privateKeySeedEncryptedBase64", encryptedSeed.ciphertextBase64)
                .put("privateKeySeedIvBase64", encryptedSeed.ivBase64)
                .put("createdAtMs", record.createdAtMs)
                .put("deviceToken", record.deviceToken)
                .put("deviceTokenScopes", toJsonArray(record.deviceTokenScopes));
    }

    private DeviceRecord recordFromStorageJson(JSONObject object) {
        return new DeviceRecord(
                object.optString("deviceId", ""),
                object.optString("publicKeyRawBase64", ""),
                seedFromStorageJson(object),
                object.optLong("createdAtMs", System.currentTimeMillis()),
                object.optString("deviceToken", ""),
                copyStringArray(object.optJSONArray("deviceTokenScopes")));
    }

    private String seedFromStorageJson(JSONObject object) {
        String ciphertext = object.optString("privateKeySeedEncryptedBase64", "");
        String iv = object.optString("privateKeySeedIvBase64", "");
        if (ciphertext.isEmpty() || iv.isEmpty()) {
            return object.optString("privateKeySeedBase64", "");
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, storageKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.DEFAULT)));
            byte[] plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.DEFAULT));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "Could not decrypt OpenClaw identity seed");
            return "";
        }
    }

    private EncryptedValue encryptString(String value) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, storageKey());
        byte[] ciphertext = cipher.doFinal(safe(value).getBytes(StandardCharsets.UTF_8));
        return new EncryptedValue(
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
    }

    private SecretKey storageKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        Key existing = keyStore.getKey(SEED_KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(SEED_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
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

    }

    private static final class EncryptedValue {
        final String ciphertextBase64;
        final String ivBase64;

        EncryptedValue(String ciphertextBase64, String ivBase64) {
            this.ciphertextBase64 = safe(ciphertextBase64);
            this.ivBase64 = safe(ivBase64);
        }
    }
}
