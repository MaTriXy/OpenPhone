package org.openphone.assistant.ota;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class OtaUpdateClient {
    public interface ProgressCallback {
        void onProgress(String status);
    }

    public static final class Update {
        public final String version;
        public final String filename;
        public final String url;
        public final long size;
        public final String sha256;
        public final boolean requiresWipe;
        public final String releaseNotesUrl;

        private Update(JSONObject object) {
            version = object.optString("version");
            filename = object.optString("filename");
            url = object.optString("url");
            size = object.optLong("size", -1);
            sha256 = object.optString("sha256");
            requiresWipe = object.optBoolean("requires_wipe");
            releaseNotesUrl = object.optString("release_notes_url");
        }

        public String summary() {
            return "Version: " + version
                    + "\nFile: " + filename
                    + "\nSize: " + size + " bytes"
                    + "\nSHA-256: " + sha256
                    + "\nRequires wipe: " + (requiresWipe ? "yes" : "no")
                    + "\nRelease notes: " + releaseNotesUrl;
        }
    }

    private OtaUpdateClient() {
    }

    public static Update fetchLatest(String feedUrl, String expectedDevice)
            throws IOException, JSONException {
        JSONObject feed = new JSONObject(readUrl(feedUrl, 1024 * 1024));
        if (feed.optInt("schema_version") != 1) {
            throw new IOException("Unsupported OTA feed schema_version");
        }
        String device = feed.optString("device");
        if (expectedDevice != null && !expectedDevice.isEmpty()
                && !expectedDevice.equals(device)) {
            throw new IOException("OTA feed is for " + device + ", not " + expectedDevice);
        }
        JSONArray updates = feed.optJSONArray("updates");
        if (updates == null || updates.length() == 0) {
            throw new IOException("OTA feed contains no updates");
        }
        Update update = new Update(updates.getJSONObject(0));
        validateUpdate(update);
        return update;
    }

    public static String downloadToDownloads(Context context, Update update,
            ProgressCallback callback) throws IOException {
        validateUpdate(update);
        notify(callback, "Downloading " + update.filename + "...");

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, update.filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/OpenPhone");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore did not return an OTA URI");
        }

        long total = 0;
        MessageDigest digest = sha256Digest();
        try (InputStream input = new BufferedInputStream(openStream(update.url));
                OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Could not open OTA output URI");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            long nextProgress = 8 * 1024 * 1024;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                total += read;
                if (total >= nextProgress) {
                    notify(callback, "Downloaded " + total + " of " + update.size + " bytes...");
                    nextProgress += 8 * 1024 * 1024;
                }
            }
        } catch (IOException e) {
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }

        if (total != update.size) {
            context.getContentResolver().delete(uri, null, null);
            throw new IOException("OTA size mismatch: expected " + update.size
                    + " bytes, downloaded " + total);
        }

        String actualSha256 = hex(digest.digest());
        if (!update.sha256.equals(actualSha256)) {
            context.getContentResolver().delete(uri, null, null);
            throw new IOException("OTA SHA-256 mismatch: expected " + update.sha256
                    + ", downloaded " + actualSha256);
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, done, null, null);
        return "Downloaded and verified " + update.filename
                + " in Downloads/OpenPhone.\n\n"
                + "Install is still manual for this preview: reboot to recovery and sideload "
                + "the verified ZIP, or use the documented host flashing flow.";
    }

    private static void validateUpdate(Update update) throws IOException {
        if (update == null) {
            throw new IOException("No OTA update selected");
        }
        if (update.version.isEmpty() || update.filename.isEmpty()
                || update.url.isEmpty() || update.sha256.isEmpty()) {
            throw new IOException("OTA update entry is missing required fields");
        }
        if (update.filename.contains("/")) {
            throw new IOException("OTA filename must be a basename");
        }
        if (update.size <= 0) {
            throw new IOException("OTA size must be positive");
        }
        if (!update.sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("OTA SHA-256 is invalid");
        }
        validateHttpUrl(update.url);
    }

    private static String readUrl(String url, int maxBytes) throws IOException {
        validateHttpUrl(url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " while reading OTA feed");
        }
        StringBuilder builder = new StringBuilder();
        int total = 0;
        char[] buffer = new char[8192];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("OTA feed is too large");
                }
                builder.append(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        return builder.toString();
    }

    private static InputStream openStream(String url) throws IOException {
        validateHttpUrl(url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " while downloading OTA");
        }
        return connection.getInputStream();
    }

    private static void validateHttpUrl(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Missing URL");
        }
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new IOException("Only http and https OTA URLs are supported");
        }
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static void notify(ProgressCallback callback, String status) {
        if (callback != null) {
            callback.onProgress(status);
        }
    }
}
