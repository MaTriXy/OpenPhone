package org.openphone.assistant.external;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class SimpleWebSocketClient {
    interface Listener {
        void onOpen();
        void onMessage(String message);
        void onClosed(String reason);
        void onError(Exception error);
    }

    private static final String TAG = "OpenPhoneExternalWs";
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;
    private static final long DEFAULT_RECONNECT_MIN_MS = 1000L;
    private static final long DEFAULT_RECONNECT_MAX_MS = 30000L;

    private final URI mUri;
    private final Map<String, String> mHeaders;
    private final Listener mListener;
    private final boolean mReconnect;
    private final long mReconnectMinMs;
    private final long mReconnectMaxMs;
    private final Object mWriteLock = new Object();
    private final SecureRandom mRandom = new SecureRandom();
    private final ExecutorService mWriteExecutor =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "OpenPhoneExternalWsWriter");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private volatile boolean mRunning;
    private Socket mSocket;
    private InputStream mInput;
    private OutputStream mOutput;
    private Thread mThread;

    SimpleWebSocketClient(URI uri, Map<String, String> headers, Listener listener) {
        this(uri, headers, listener, false, DEFAULT_RECONNECT_MIN_MS,
                DEFAULT_RECONNECT_MAX_MS);
    }

    SimpleWebSocketClient(URI uri, Map<String, String> headers, Listener listener,
            boolean reconnect, long reconnectMinMs, long reconnectMaxMs) {
        mUri = uri;
        mHeaders = headers == null ? new LinkedHashMap<String, String>() : headers;
        mListener = listener;
        mReconnect = reconnect;
        mReconnectMinMs = reconnectMinMs <= 0 ? DEFAULT_RECONNECT_MIN_MS : reconnectMinMs;
        mReconnectMaxMs = reconnectMaxMs < mReconnectMinMs
                ? mReconnectMinMs : reconnectMaxMs;
    }

    synchronized void start() {
        if (mThread != null) {
            return;
        }
        mRunning = true;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "OpenPhoneExternalWs");
        mThread.start();
    }

    synchronized void close() {
        mRunning = false;
        try {
            submitFrame(0x8, new byte[0], false);
        } catch (RuntimeException ignored) {
        }
        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException ignored) {
        }
        mWriteExecutor.shutdownNow();
    }

    boolean sendText(String message) {
        return submitFrame(0x1, message == null ? new byte[0]
                : message.getBytes(StandardCharsets.UTF_8), true);
    }

    private boolean submitFrame(final int opcode, final byte[] payload,
            final boolean reportErrors) {
        try {
            mWriteExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        sendFrame(opcode, payload);
                    } catch (IOException e) {
                        closeSocketQuietly();
                        if (reportErrors && mRunning && mListener != null) {
                            mListener.onError(e);
                        }
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            closeSocketQuietly();
            if (reportErrors && mRunning && mListener != null) {
                mListener.onError(e);
            }
            return false;
        }
    }

    private void runLoop() {
        int reconnectAttempt = 0;
        try {
            while (mRunning) {
                boolean opened = false;
                try {
                    connectSocket();
                    performHandshake();
                    opened = true;
                    reconnectAttempt = 0;
                    if (mListener != null) {
                        mListener.onOpen();
                    }
                    while (mRunning) {
                        String closeReason = readFrame();
                        if (closeReason != null) {
                            if (mRunning && mListener != null) {
                                mListener.onClosed(closeReason);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (mRunning && mListener != null) {
                        mListener.onError(e);
                    }
                } finally {
                    closeSocketQuietly();
                }
                if (!mRunning || !mReconnect) {
                    break;
                }
                reconnectAttempt = opened ? 1 : reconnectAttempt + 1;
                sleepBeforeReconnect(reconnectAttempt);
            }
        } finally {
            mRunning = false;
            closeSocketQuietly();
            synchronized (this) {
                if (Thread.currentThread() == mThread) {
                    mThread = null;
                }
            }
        }
    }

    private void connectSocket() throws IOException {
        String scheme = mUri.getScheme() == null ? "" : mUri.getScheme().toLowerCase(Locale.US);
        String host = mUri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("missing websocket host");
        }
        int port = mUri.getPort();
        if (port <= 0) {
            port = "wss".equals(scheme) ? 443 : 80;
        }
        if ("wss".equals(scheme)) {
            mSocket = SSLSocketFactory.getDefault().createSocket(host, port);
            ((SSLSocket) mSocket).startHandshake();
            if (!HttpsURLConnection.getDefaultHostnameVerifier()
                    .verify(host, ((SSLSocket) mSocket).getSession())) {
                throw new IOException("websocket hostname verification failed");
            }
        } else if ("ws".equals(scheme)) {
            mSocket = new Socket(host, port);
        } else {
            throw new IOException("unsupported websocket scheme: " + scheme);
        }
        mInput = mSocket.getInputStream();
        mOutput = mSocket.getOutputStream();
    }

    private void performHandshake() throws Exception {
        byte[] nonce = new byte[16];
        mRandom.nextBytes(nonce);
        String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
        String path = mUri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (mUri.getRawQuery() != null && !mUri.getRawQuery().isEmpty()) {
            path += "?" + mUri.getRawQuery();
        }

        StringBuilder request = new StringBuilder();
        request.append("GET ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(mUri.getHost());
        if (mUri.getPort() > 0) {
            request.append(":").append(mUri.getPort());
        }
        request.append("\r\n");
        request.append("Upgrade: websocket\r\n");
        request.append("Connection: Upgrade\r\n");
        request.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        request.append("Sec-WebSocket-Version: 13\r\n");
        for (Map.Entry<String, String> header : mHeaders.entrySet()) {
            if (header.getKey() != null && header.getValue() != null
                    && !header.getKey().trim().isEmpty()) {
                request.append(header.getKey()).append(": ")
                        .append(header.getValue()).append("\r\n");
            }
        }
        request.append("\r\n");
        mOutput.write(request.toString().getBytes(StandardCharsets.US_ASCII));
        mOutput.flush();

        String response = readHttpHeader();
        if (!response.startsWith("HTTP/1.1 101") && !response.startsWith("HTTP/1.0 101")) {
            throw new IOException("websocket upgrade failed");
        }
        String expectedAccept = websocketAccept(key);
        if (!response.toLowerCase(Locale.US).contains(
                ("sec-websocket-accept: " + expectedAccept).toLowerCase(Locale.US))) {
            Log.w(TAG, "websocket accept header missing or unexpected");
        }
    }

    private String readHttpHeader() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int matched = 0;
        byte[] marker = new byte[] {'\r', '\n', '\r', '\n'};
        while (output.size() < 32768) {
            int value = mInput.read();
            if (value < 0) {
                throw new IOException("websocket handshake ended early");
            }
            output.write(value);
            if ((byte) value == marker[matched]) {
                matched++;
                if (matched == marker.length) {
                    return new String(output.toByteArray(), StandardCharsets.US_ASCII);
                }
            } else {
                matched = ((byte) value == marker[0]) ? 1 : 0;
            }
        }
        throw new IOException("websocket handshake too large");
    }

    private static String websocketAccept(String key) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII));
        return Base64.encodeToString(digest, Base64.NO_WRAP);
    }

    private String readFrame() throws IOException {
        int b0 = input().read();
        if (b0 < 0) {
            return "eof";
        }
        int b1 = readByte();
        int opcode = b0 & 0x0f;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7f;
        if (length == 126) {
            length = ((long) readByte() << 8) | readByte();
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
        }
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("websocket frame too large");
        }
        byte[] mask = null;
        if (masked) {
            mask = readExactly(4);
        }
        byte[] payload = readExactly((int) length);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }
        if (opcode == 0x1) {
            if (mListener != null) {
                mListener.onMessage(new String(payload, StandardCharsets.UTF_8));
            }
        } else if (opcode == 0x8) {
            return closeReason(payload);
        } else if (opcode == 0x9) {
            sendFrame(0xA, payload);
        }
        return null;
    }

    private int readByte() throws IOException {
        int value = input().read();
        if (value < 0) {
            throw new IOException("websocket ended early");
        }
        return value & 0xff;
    }

    private byte[] readExactly(int count) throws IOException {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input().read(data, offset, count - offset);
            if (read < 0) {
                throw new IOException("websocket ended early");
            }
            offset += read;
        }
        return data;
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        synchronized (mWriteLock) {
            OutputStream output = mOutput;
            if (output == null) {
                throw new IOException("websocket not connected");
            }
            byte[] body = payload == null ? new byte[0] : payload;
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x80 | (opcode & 0x0f));
            byte[] mask = new byte[4];
            mRandom.nextBytes(mask);
            int length = body.length;
            if (length < 126) {
                frame.write(0x80 | length);
            } else if (length <= 0xffff) {
                frame.write(0x80 | 126);
                frame.write((length >>> 8) & 0xff);
                frame.write(length & 0xff);
            } else {
                frame.write(0x80 | 127);
                long longLength = length;
                for (int i = 7; i >= 0; i--) {
                    frame.write((int) ((longLength >>> (8 * i)) & 0xff));
                }
            }
            frame.write(mask);
            for (int i = 0; i < body.length; i++) {
                frame.write(body[i] ^ mask[i % 4]);
            }
            output.write(frame.toByteArray());
            output.flush();
        }
    }

    private InputStream input() throws IOException {
        InputStream input = mInput;
        if (input == null) {
            throw new IOException("websocket not connected");
        }
        return input;
    }

    private void closeSocketQuietly() {
        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException ignored) {
        }
        mSocket = null;
        mInput = null;
        mOutput = null;
    }

    private void sleepBeforeReconnect(int attempt) {
        long deadline = System.currentTimeMillis() + reconnectDelayMillis(attempt);
        while (mRunning && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.min(250L,
                        Math.max(1L, deadline - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mRunning = false;
                break;
            }
        }
    }

    private long reconnectDelayMillis(int attempt) {
        long delay = mReconnectMinMs;
        int count = Math.max(1, attempt);
        for (int i = 1; i < count && delay < mReconnectMaxMs; i++) {
            if (delay > mReconnectMaxMs / 2L) {
                delay = mReconnectMaxMs;
            } else {
                delay = Math.min(mReconnectMaxMs, delay * 2L);
            }
        }
        return delay;
    }

    private static String closeReason(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return "closed";
        }
        int code = ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
        String reason = "";
        if (payload.length > 2) {
            reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
        }
        return reason.isEmpty() ? String.valueOf(code) : code + " " + reason;
    }
}
