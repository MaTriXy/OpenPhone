package org.openphone.assistant.model;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openphone.assistant.actions.ToolCatalog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class OpenAiRealtimeVoiceSession {
    private static final String TAG = "OpenPhoneRealtimeVoice";

    public interface Callback {
        void onStatus(String status);
        void onUserTranscript(String transcript);
        void onAssistantTranscript(String transcript);
        void onToolCall(String toolName);
        void onError(String message);
        void onStopped();
    }

    public static final String MODEL = "gpt-realtime-2";

    private static final int SAMPLE_RATE = 24000;
    private static final long CONNECT_TIMEOUT_MS = 20000;
    private static final long EVENT_TIMEOUT_MS = 120000;
    private static final long SELF_ECHO_GUARD_MS = 900;
    private static final long PLAYBACK_DRAIN_GRACE_MS = 350;
    private static final long MAX_PLAYBACK_DRAIN_MS = 6000;
    private static final long PLAYBACK_START_BARGE_GUARD_MS = 350;
    private static final long BARGE_IN_SUSTAIN_MS = 140;
    private static final long BARGE_IN_COOLDOWN_MS = 900;
    private static final double BARGE_IN_MIN_RMS = 1800.0;
    private static final double BARGE_IN_ECHO_RATIO = 1.8;
    private static final double PLAYBACK_ECHO_FLOOR_CAP_RMS = 2600.0;
    private static final String REALTIME_URL =
            "wss://api.openai.com/v1/realtime?model=" + MODEL;

    private final ModelEndpointConfig mEndpointConfig;
    private final Set<String> mCompletedCallIds = new HashSet<>();
    private volatile boolean mCancelled;
    private volatile RealtimeWebSocket mSocket;
    private volatile AudioRecord mRecorder;
    private volatile AudioTrack mPlayer;
    private volatile Thread mAudioThread;
    private volatile AcousticEchoCanceler mEchoCanceler;
    private volatile NoiseSuppressor mNoiseSuppressor;
    private volatile AutomaticGainControl mAutomaticGainControl;
    private volatile double mRecentMicRms;
    private String mPendingAssistantTranscript;
    private volatile boolean mAssistantAudioActive;
    private boolean mPendingToolResponseCreate;
    private volatile long mPlaybackFramesWritten;
    private long mLastAudioWriteUptimeMillis;
    private volatile double mPlaybackMicFloorRms = BARGE_IN_MIN_RMS;
    private volatile long mBargeInCandidateSinceUptimeMillis;
    private volatile long mLastBargeInUptimeMillis;
    private volatile String mCurrentResponseId;
    private volatile String mCurrentAssistantItemId;
    private volatile int mCurrentAssistantContentIndex;
    private volatile long mCurrentAssistantItemStartFrame;

    public OpenAiRealtimeVoiceSession(ModelEndpointConfig endpointConfig) {
        mEndpointConfig = endpointConfig == null
                ? ModelEndpointConfig.directOpenAi("") : endpointConfig;
    }

    public void cancel() {
        mCancelled = true;
        AudioRecord recorder = mRecorder;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
            }
            try {
                recorder.release();
            } catch (RuntimeException ignored) {
            }
        }
        releaseAudioEffects();
        AudioTrack player = mPlayer;
        if (player != null) {
            try {
                player.pause();
                player.flush();
                player.release();
            } catch (RuntimeException ignored) {
            }
        }
        RealtimeWebSocket socket = mSocket;
        if (socket != null) {
            socket.closeQuietly();
        }
        Thread audioThread = mAudioThread;
        if (audioThread != null) {
            audioThread.interrupt();
        }
    }

    public void run(String taskId, ModelAdapter.ToolExecutor executor, Callback callback) {
        if (!mEndpointConfig.isConfigured()) {
            callback.onError(mEndpointConfig.missingCredentialReason());
            return;
        }
        if (mEndpointConfig.isBrokerMode()) {
            callback.onError("Live Realtime 2 voice needs a direct OpenAI API key for now.");
            return;
        }
        if (!ToolCatalog.get().isLoaded()) {
            callback.onError("Action registry is not installed; no model tools available.");
            return;
        }

        RealtimeWebSocket socket = null;
        try {
            Log.i(TAG, "connect model=" + MODEL);
            socket = RealtimeWebSocket.connect(REALTIME_URL, mEndpointConfig.bearerToken());
            mSocket = socket;
            callback.onStatus("Starting live voice");
            socket.send(sessionUpdateEvent());
            startAudioInput(socket, callback);
            Log.i(TAG, "audio streaming started");
            callback.onStatus("Live Realtime 2");
            while (!mCancelled && !executor.isCancelled()) {
                try {
                    JSONObject event = socket.readJson(EVENT_TIMEOUT_MS);
                    handleEvent(socket, taskId, executor, callback, event);
                } catch (SocketTimeoutException ignored) {
                    callback.onStatus("Live Realtime 2");
                }
            }
        } catch (IOException | JSONException e) {
            if (!mCancelled && !executor.isCancelled()) {
                Log.w(TAG, "session failed", e);
                callback.onError(e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage());
            }
        } finally {
            cancel();
            if (mSocket == socket) {
                mSocket = null;
            }
            callback.onStopped();
        }
    }

    private JSONObject sessionUpdateEvent() throws JSONException {
        JSONObject turnDetection = new JSONObject()
                .put("type", "semantic_vad")
                .put("eagerness", "low")
                .put("create_response", true)
                .put("interrupt_response", false);
        JSONObject input = new JSONObject()
                .put("format", new JSONObject()
                        .put("type", "audio/pcm")
                        .put("rate", SAMPLE_RATE))
                .put("transcription", new JSONObject()
                        .put("model", OpenAiSpeechTranscriber.modelName()))
                .put("turn_detection", turnDetection);
        JSONObject output = new JSONObject()
                .put("format", new JSONObject()
                        .put("type", "audio/pcm")
                        .put("rate", SAMPLE_RATE))
                .put("voice", "marin");
        JSONObject session = new JSONObject()
                .put("type", "realtime")
                .put("model", MODEL)
                .put("instructions", liveVoiceInstructions())
                .put("output_modalities", new JSONArray().put("audio"))
                .put("audio", new JSONObject()
                        .put("input", input)
                        .put("output", output))
                .put("reasoning", new JSONObject().put("effort", "low"))
                .put("tool_choice", "auto")
                .put("tools", ToolCatalog.get().realtimeToolDefinitions());
        return new JSONObject()
                .put("type", "session.update")
                .put("session", session);
    }

    private static String liveVoiceInstructions() {
        return "You are OpenPhone, a live OS voice agent running on the user's phone. "
                + "The user is speaking to you through a low-latency voice session. "
                + "Speak briefly and naturally, but take action end to end. Use the phone "
                + "tools whenever they help. If the user asks about the visible screen, call "
                + "get_screen before answering. If the user asks you to control an app, "
                + "inspect the screen and continue with tools until the task is finished, "
                + "blocked by policy, or truly impossible. Do not ask clarification questions "
                + "unless the missing detail is required to avoid doing the wrong real-world "
                + "action. Never claim you cannot use the phone when a relevant tool exists. "
                + "For risky actions, use the provided confirmation and policy flow. Keep "
                + "voice replies short while working, then summarize the outcome.";
    }

    private void startAudioInput(final RealtimeWebSocket socket, final Callback callback)
            throws IOException {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE / 5);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            throw new IOException("microphone_unavailable");
        }
        configureAudioEffects(recorder.getAudioSessionId());
        mRecorder = recorder;
        recorder.startRecording();
        Thread audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[bufferSize];
                while (!mCancelled && !Thread.currentThread().isInterrupted()) {
                    int read;
                    try {
                        read = recorder.read(buffer, 0, buffer.length);
                    } catch (IllegalStateException e) {
                        callback.onError("microphone_read_failed");
                        return;
                    }
                    if (read <= 0) {
                        continue;
                    }
                    double rms = pcm16Rms(buffer, read);
                    mRecentMicRms = rms;
                    try {
                        byte[] chunk = new byte[read];
                        System.arraycopy(buffer, 0, chunk, 0, read);
                        socket.send(new JSONObject()
                                .put("type", "input_audio_buffer.append")
                                .put("audio", Base64.encodeToString(chunk, Base64.NO_WRAP)));
                        maybeBargeInFromLocalMic(socket, callback, rms);
                    } catch (IOException | JSONException e) {
                        if (!mCancelled) {
                            callback.onError(e.getMessage() == null
                                    ? "audio_stream_failed" : e.getMessage());
                        }
                        return;
                    }
                }
            }
        }, "OpenPhoneRealtimeMic");
        mAudioThread = audioThread;
        audioThread.start();
    }

    private void handleEvent(RealtimeWebSocket socket, String taskId,
            ModelAdapter.ToolExecutor executor, Callback callback, JSONObject event)
            throws IOException, JSONException {
        String type = event.optString("type");
        if ("error".equals(type)) {
            JSONObject error = event.optJSONObject("error");
            if (error != null && "conversation_already_has_active_response".equals(
                    error.optString("code"))) {
                Log.w(TAG, "ignoring redundant response.create while response is active");
                return;
            }
            if (isCancelRaceError(error)) {
                Log.i(TAG, "ignoring harmless response.cancel race: "
                        + error.optString("message", error.toString()));
                return;
            }
            throw new IOException(error == null ? event.toString() : error.toString());
        }
        if ("input_audio_buffer.speech_started".equals(type)) {
            if (isPlaybackActiveOrRecentlyActive()) {
                if (isLikelyUserBargeIn(mRecentMicRms)) {
                    requestBargeIn(socket, callback, "server_vad");
                } else {
                    Log.i(TAG, "speech_started ignored during assistant playback"
                            + " micRms=" + Math.round(mRecentMicRms)
                            + " echoFloor=" + Math.round(mPlaybackMicFloorRms));
                }
                return;
            }
            callback.onStatus("Listening");
            return;
        }
        if ("input_audio_buffer.speech_stopped".equals(type)) {
            callback.onStatus("Thinking");
            return;
        }
        if ("conversation.item.input_audio_transcription.done".equals(type)) {
            String transcript = event.optString("transcript", "").trim();
            if (!transcript.isEmpty()) {
                callback.onUserTranscript(transcript);
            }
            return;
        }
        if ("response.output_audio.delta".equals(type)
                || "response.audio.delta".equals(type)) {
            playAudioDelta(event);
            return;
        }
        if ("response.output_audio_transcript.done".equals(type)
                || "response.audio_transcript.done".equals(type)) {
            String transcript = event.optString("transcript", "").trim();
            if (!transcript.isEmpty()) {
                mPendingAssistantTranscript = transcript;
            }
            return;
        }
        if ("response.output_audio.done".equals(type) || "response.audio.done".equals(type)) {
            drainPlayback("audio_done");
            flushAssistantTranscript(callback);
            return;
        }
        RealtimeFunctionCall call = functionCallFromEvent(event);
        if (call != null) {
            if (executeFunctionCall(socket, taskId, executor, callback, call)) {
                mPendingToolResponseCreate = true;
            }
            return;
        }
        if ("response.done".equals(type)) {
            boolean sentToolOutput = false;
            JSONObject response = event.optJSONObject("response");
            if (response != null && "cancelled".equals(response.optString("status"))) {
                mPendingToolResponseCreate = false;
                mPendingAssistantTranscript = null;
                mAssistantAudioActive = false;
                return;
            }
            if (response != null) {
                List<RealtimeFunctionCall> calls = functionCallsFromResponse(response);
                for (RealtimeFunctionCall responseCall : calls) {
                    sentToolOutput |= executeFunctionCall(socket, taskId, executor,
                            callback, responseCall);
                }
            }
            if (sentToolOutput || mPendingToolResponseCreate) {
                mPendingToolResponseCreate = false;
                socket.send(new JSONObject().put("type", "response.create"));
                Log.i(TAG, "response.create sent after tool output");
                return;
            }
            drainPlayback("response_done");
            flushAssistantTranscript(callback);
        }
    }

    private void flushAssistantTranscript(Callback callback) {
        String transcript = mPendingAssistantTranscript;
        if (transcript == null || transcript.trim().isEmpty()) {
            return;
        }
        mPendingAssistantTranscript = null;
        callback.onAssistantTranscript(transcript.trim());
    }

    private boolean executeFunctionCall(RealtimeWebSocket socket, String taskId,
            ModelAdapter.ToolExecutor executor, Callback callback, RealtimeFunctionCall call)
            throws IOException, JSONException {
        if (call.callId.isEmpty() || mCompletedCallIds.contains(call.callId)) {
            return false;
        }
        mCompletedCallIds.add(call.callId);
        callback.onToolCall(call.name);
        JSONObject arguments = parseArguments(call.arguments);
        ensureToolReason(call.name, arguments);
        String output;
        if (!ToolCatalog.get().isAllowedTool(call.name)) {
            output = errorJson("unknown_model_tool:" + call.name);
        } else if (executor.isCancelled()) {
            output = "{\"status\":\"cancelled\",\"reason\":\"user_stopped\"}";
        } else {
            output = executor.callTool(call.name, arguments.toString());
        }
        socket.send(new JSONObject()
                .put("type", "conversation.item.create")
                .put("item", new JSONObject()
                        .put("type", "function_call_output")
                        .put("call_id", call.callId)
                        .put("output", output == null ? "" : output)));
        return true;
    }

    private synchronized void playAudioDelta(JSONObject event) throws IOException {
        String base64Audio = event.optString("delta", "");
        if (base64Audio == null || base64Audio.isEmpty()) {
            return;
        }
        AudioTrack player = ensurePlayer();
        String responseId = event.optString("response_id", "");
        if (!responseId.isEmpty()) {
            mCurrentResponseId = responseId;
        }
        String itemId = event.optString("item_id", "");
        if (!itemId.isEmpty() && !itemId.equals(mCurrentAssistantItemId)) {
            mCurrentAssistantItemId = itemId;
            mCurrentAssistantContentIndex = event.optInt("content_index", 0);
            mCurrentAssistantItemStartFrame = playbackHeadPosition(player);
        }
        byte[] bytes = Base64.decode(base64Audio, Base64.DEFAULT);
        if (bytes.length > 0) {
            int written = player.write(bytes, 0, bytes.length);
            if (written < 0) {
                throw new IOException("speaker_write_failed:" + written);
            }
            mAssistantAudioActive = true;
            mLastAudioWriteUptimeMillis = SystemClock.uptimeMillis();
            mPlaybackFramesWritten += written / 2;
        }
    }

    private AudioTrack ensurePlayer() throws IOException {
        AudioTrack player = mPlayer;
        if (player != null) {
            return player;
        }
        int minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE * 2);
        player = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize, AudioTrack.MODE_STREAM);
        if (player.getState() != AudioTrack.STATE_INITIALIZED) {
            player.release();
            throw new IOException("speaker_unavailable");
        }
        player.play();
        mPlayer = player;
        return player;
    }

    private synchronized void stopPlayback() {
        AudioTrack player = mPlayer;
        if (player == null) {
            return;
        }
        try {
            player.pause();
            player.flush();
            mPlaybackFramesWritten = playbackHeadPosition(player);
            mAssistantAudioActive = false;
            mBargeInCandidateSinceUptimeMillis = 0L;
            player.play();
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isPlaybackActiveOrRecentlyActive() {
        if (mAssistantAudioActive) {
            return true;
        }
        long lastWrite = mLastAudioWriteUptimeMillis;
        return lastWrite > 0
                && SystemClock.uptimeMillis() - lastWrite < SELF_ECHO_GUARD_MS;
    }

    private void drainPlayback(String reason) {
        AudioTrack player = mPlayer;
        if (player == null || !mAssistantAudioActive) {
            return;
        }
        long start = SystemClock.uptimeMillis();
        long remainingFrames = Math.max(0, mPlaybackFramesWritten - playbackHeadPosition(player));
        long drainBudgetMs = Math.min(MAX_PLAYBACK_DRAIN_MS,
                Math.max(PLAYBACK_DRAIN_GRACE_MS,
                        (remainingFrames * 1000L / SAMPLE_RATE) + PLAYBACK_DRAIN_GRACE_MS));
        long deadline = start + drainBudgetMs;
        while (!mCancelled && SystemClock.uptimeMillis() < deadline) {
            remainingFrames = Math.max(0, mPlaybackFramesWritten - playbackHeadPosition(player));
            if (remainingFrames <= SAMPLE_RATE / 20) {
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        mAssistantAudioActive = false;
        mBargeInCandidateSinceUptimeMillis = 0L;
        Log.i(TAG, "playback drain reason=" + reason
                + " remainingFrames=" + Math.max(0,
                        mPlaybackFramesWritten - playbackHeadPosition(player))
                + " waitedMs=" + (SystemClock.uptimeMillis() - start));
    }

    private static long playbackHeadPosition(AudioTrack player) {
        return player.getPlaybackHeadPosition() & 0xffffffffL;
    }

    private void maybeBargeInFromLocalMic(RealtimeWebSocket socket, Callback callback, double rms)
            throws IOException, JSONException {
        if (!mAssistantAudioActive || mCancelled) {
            mBargeInCandidateSinceUptimeMillis = 0L;
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - mLastAudioWriteUptimeMillis < PLAYBACK_START_BARGE_GUARD_MS) {
            return;
        }
        if (!isLikelyUserBargeIn(rms)) {
            updatePlaybackEchoFloor(rms);
            mBargeInCandidateSinceUptimeMillis = 0L;
            return;
        }
        if (mBargeInCandidateSinceUptimeMillis == 0L) {
            mBargeInCandidateSinceUptimeMillis = now;
            return;
        }
        if (now - mBargeInCandidateSinceUptimeMillis < BARGE_IN_SUSTAIN_MS
                || now - mLastBargeInUptimeMillis < BARGE_IN_COOLDOWN_MS) {
            return;
        }
        requestBargeIn(socket, callback, "local_mic");
    }

    private boolean isLikelyUserBargeIn(double rms) {
        return rms >= BARGE_IN_MIN_RMS
                && rms >= Math.max(BARGE_IN_MIN_RMS,
                        mPlaybackMicFloorRms * BARGE_IN_ECHO_RATIO);
    }

    private void updatePlaybackEchoFloor(double rms) {
        if (rms <= 0) {
            return;
        }
        double floor = Math.max(BARGE_IN_MIN_RMS, mPlaybackMicFloorRms);
        mPlaybackMicFloorRms = Math.min(PLAYBACK_ECHO_FLOOR_CAP_RMS,
                (floor * 0.85) + (rms * 0.15));
    }

    private void requestBargeIn(RealtimeWebSocket socket, Callback callback, String reason)
            throws IOException, JSONException {
        long now = SystemClock.uptimeMillis();
        if (now - mLastBargeInUptimeMillis < BARGE_IN_COOLDOWN_MS) {
            return;
        }
        mLastBargeInUptimeMillis = now;
        mBargeInCandidateSinceUptimeMillis = 0L;
        Log.i(TAG, "barge-in accepted reason=" + reason
                + " micRms=" + Math.round(mRecentMicRms)
                + " echoFloor=" + Math.round(mPlaybackMicFloorRms));
        mPendingAssistantTranscript = null;
        sendConversationTruncate(socket);
        stopPlayback();
        JSONObject cancel = new JSONObject().put("type", "response.cancel");
        if (mCurrentResponseId != null && !mCurrentResponseId.isEmpty()) {
            cancel.put("response_id", mCurrentResponseId);
        }
        socket.send(cancel);
        callback.onStatus("Listening");
    }

    private synchronized void sendConversationTruncate(RealtimeWebSocket socket)
            throws IOException, JSONException {
        AudioTrack player = mPlayer;
        String itemId = mCurrentAssistantItemId;
        if (player == null || itemId == null || itemId.isEmpty()) {
            return;
        }
        long playedFrames = Math.max(0,
                playbackHeadPosition(player) - mCurrentAssistantItemStartFrame);
        int audioEndMs = (int) Math.max(0, playedFrames * 1000L / SAMPLE_RATE);
        socket.send(new JSONObject()
                .put("type", "conversation.item.truncate")
                .put("item_id", itemId)
                .put("content_index", mCurrentAssistantContentIndex)
                .put("audio_end_ms", audioEndMs));
        Log.i(TAG, "conversation truncated for barge-in item=" + itemId
                + " audioEndMs=" + audioEndMs);
    }

    private static boolean isCancelRaceError(JSONObject error) {
        if (error == null) {
            return false;
        }
        String message = error.optString("message", "").toLowerCase(Locale.US);
        String code = error.optString("code", "").toLowerCase(Locale.US);
        return code.contains("response_not_found")
                || code.contains("no_active_response")
                || (message.contains("response.cancel")
                        && (message.contains("no response")
                                || message.contains("not found")
                                || message.contains("already")));
    }

    private void configureAudioEffects(int sessionId) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                mEchoCanceler = AcousticEchoCanceler.create(sessionId);
                if (mEchoCanceler != null) {
                    mEchoCanceler.setEnabled(true);
                    Log.i(TAG, "acoustic echo canceller enabled");
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "acoustic echo canceller unavailable", e);
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                mNoiseSuppressor = NoiseSuppressor.create(sessionId);
                if (mNoiseSuppressor != null) {
                    mNoiseSuppressor.setEnabled(true);
                    Log.i(TAG, "noise suppressor enabled");
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "noise suppressor unavailable", e);
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                mAutomaticGainControl = AutomaticGainControl.create(sessionId);
                if (mAutomaticGainControl != null) {
                    mAutomaticGainControl.setEnabled(true);
                    Log.i(TAG, "automatic gain control enabled");
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "automatic gain control unavailable", e);
        }
    }

    private void releaseAudioEffects() {
        AcousticEchoCanceler echoCanceler = mEchoCanceler;
        mEchoCanceler = null;
        if (echoCanceler != null) {
            try {
                echoCanceler.release();
            } catch (RuntimeException ignored) {
            }
        }
        NoiseSuppressor noiseSuppressor = mNoiseSuppressor;
        mNoiseSuppressor = null;
        if (noiseSuppressor != null) {
            try {
                noiseSuppressor.release();
            } catch (RuntimeException ignored) {
            }
        }
        AutomaticGainControl automaticGainControl = mAutomaticGainControl;
        mAutomaticGainControl = null;
        if (automaticGainControl != null) {
            try {
                automaticGainControl.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static double pcm16Rms(byte[] buffer, int byteCount) {
        int samples = byteCount / 2;
        if (samples <= 0) {
            return 0.0;
        }
        double sumSquares = 0.0;
        for (int i = 0; i + 1 < byteCount; i += 2) {
            int sample = (buffer[i] & 0xff) | (buffer[i + 1] << 8);
            sumSquares += (double) sample * (double) sample;
        }
        return Math.sqrt(sumSquares / samples);
    }

    private static RealtimeFunctionCall functionCallFromEvent(JSONObject event) {
        String type = event.optString("type");
        if ("response.function_call_arguments.done".equals(type)) {
            JSONObject item = event.optJSONObject("item");
            if (item != null) {
                return new RealtimeFunctionCall(
                        item.optString("call_id", event.optString("call_id")),
                        item.optString("name", event.optString("name")),
                        item.optString("arguments", event.optString("arguments")));
            }
            return new RealtimeFunctionCall(
                    event.optString("call_id"),
                    event.optString("name"),
                    event.optString("arguments"));
        }
        return null;
    }

    private static List<RealtimeFunctionCall> functionCallsFromResponse(JSONObject response) {
        List<RealtimeFunctionCall> calls = new ArrayList<>();
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return calls;
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"function_call".equals(item.optString("type"))) {
                continue;
            }
            calls.add(new RealtimeFunctionCall(
                    item.optString("call_id"),
                    item.optString("name"),
                    item.optString("arguments")));
        }
        return calls;
    }

    private static JSONObject parseArguments(String arguments) throws JSONException {
        if (arguments == null || arguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(arguments);
    }

    private static void ensureToolReason(String toolName, JSONObject arguments)
            throws JSONException {
        if (arguments == null || !ToolCatalog.get().requiresReason(toolName)
                || arguments.optString("reason", "").trim().length() > 0) {
            return;
        }
        arguments.put("reason", "Live voice task step requested by the user.");
    }

    private static String errorJson(String reason) {
        try {
            return new JSONObject()
                    .put("status", "error")
                    .put("reason", reason == null ? "" : reason)
                    .toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\"}";
        }
    }

    private static final class RealtimeFunctionCall {
        final String callId;
        final String name;
        final String arguments;

        RealtimeFunctionCall(String callId, String name, String arguments) {
            this.callId = callId == null ? "" : callId;
            this.name = name == null ? "" : name;
            this.arguments = arguments == null ? "" : arguments;
        }
    }

    private static final class RealtimeWebSocket {
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final SSLSocket mSocket;
        private final InputStream mInput;
        private final OutputStream mOutput;
        private final SecureRandom mRandom = new SecureRandom();

        private RealtimeWebSocket(SSLSocket socket) throws IOException {
            mSocket = socket;
            mInput = socket.getInputStream();
            mOutput = socket.getOutputStream();
        }

        static RealtimeWebSocket connect(String url, String bearerToken) throws IOException {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 443;
            String path = uri.getRawPath();
            if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
                path += "?" + uri.getRawQuery();
            }
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                    .createSocket(host, port);
            socket.setSoTimeout((int) CONNECT_TIMEOUT_MS);
            socket.startHandshake();

            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Authorization: Bearer " + bearerToken + "\r\n"
                    + "OpenAI-Safety-Identifier: openphone-local-device\r\n"
                    + "\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            String status = reader.readLine();
            if (status == null || !status.contains(" 101 ")) {
                throw new IOException("Realtime WebSocket upgrade failed: " + status);
            }
            String accept = "";
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0 && "sec-websocket-accept".equals(
                        line.substring(0, colon).trim().toLowerCase(Locale.US))) {
                    accept = line.substring(colon + 1).trim();
                }
            }
            String expected = websocketAccept(key);
            if (!expected.equals(accept)) {
                throw new IOException("Realtime WebSocket accept mismatch.");
            }
            socket.setSoTimeout((int) EVENT_TIMEOUT_MS);
            return new RealtimeWebSocket(socket);
        }

        synchronized void send(JSONObject event) throws IOException {
            sendText(event.toString());
        }

        JSONObject readJson(long timeoutMs) throws IOException, JSONException {
            mSocket.setSoTimeout((int) Math.max(1, Math.min(timeoutMs, EVENT_TIMEOUT_MS)));
            String text = readText();
            return new JSONObject(text);
        }

        void closeQuietly() {
            try {
                sendFrame(0x8, new byte[0]);
            } catch (IOException | RuntimeException ignored) {
            }
            try {
                mSocket.close();
            } catch (IOException | RuntimeException ignored) {
            }
        }

        private void sendText(String text) throws IOException {
            sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x80 | (opcode & 0x0f));
            int length = payload.length;
            if (length <= 125) {
                frame.write(0x80 | length);
            } else if (length <= 65535) {
                frame.write(0x80 | 126);
                frame.write((length >>> 8) & 0xff);
                frame.write(length & 0xff);
            } else {
                frame.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) {
                    frame.write((int) ((long) length >>> (8 * i)) & 0xff);
                }
            }
            byte[] mask = new byte[4];
            mRandom.nextBytes(mask);
            frame.write(mask);
            for (int i = 0; i < payload.length; i++) {
                frame.write(payload[i] ^ mask[i % 4]);
            }
            mOutput.write(frame.toByteArray());
            mOutput.flush();
        }

        private String readText() throws IOException {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            while (true) {
                int b0 = readByte();
                int b1 = readByte();
                boolean fin = (b0 & 0x80) != 0;
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
                byte[] mask = null;
                if (masked) {
                    mask = new byte[] {
                            (byte) readByte(), (byte) readByte(),
                            (byte) readByte(), (byte) readByte()
                    };
                }
                if (length > 16L * 1024L * 1024L) {
                    throw new IOException("Realtime frame too large: " + length);
                }
                byte[] payload = readBytes((int) length);
                if (masked && mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                    }
                }
                if (opcode == 0x8) {
                    throw new IOException("Realtime WebSocket closed.");
                }
                if (opcode == 0x9) {
                    sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0xA) {
                    continue;
                }
                if (opcode == 0x1 || opcode == 0x0) {
                    message.write(payload);
                    if (fin) {
                        return message.toString(StandardCharsets.UTF_8.name());
                    }
                }
            }
        }

        private int readByte() throws IOException {
            int value = mInput.read();
            if (value < 0) {
                throw new IOException("Realtime WebSocket EOF.");
            }
            return value;
        }

        private byte[] readBytes(int length) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(length);
            byte[] chunk = new byte[Math.min(8192, Math.max(1, length))];
            while (buffer.position() < length) {
                int count = mInput.read(chunk, 0, Math.min(chunk.length,
                        length - buffer.position()));
                if (count < 0) {
                    throw new IOException("Realtime WebSocket EOF.");
                }
                buffer.put(chunk, 0, count);
            }
            return buffer.array();
        }

        private static String websocketAccept(String key) throws IOException {
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                byte[] digest = sha1.digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII));
                return Base64.encodeToString(digest, Base64.NO_WRAP);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
    }
}
