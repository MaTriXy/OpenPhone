package org.openphone.assistant.model;

public final class ModelEndpointConfig {
    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String OPENAI_TRANSCRIPTIONS_URL =
            "https://api.openai.com/v1/audio/transcriptions";

    private final boolean mBrokerMode;
    private final String mResponsesUrl;
    private final String mTranscriptionsUrl;
    private final String mBearerToken;

    private ModelEndpointConfig(boolean brokerMode, String responsesUrl,
            String transcriptionsUrl, String bearerToken) {
        mBrokerMode = brokerMode;
        mResponsesUrl = responsesUrl == null ? "" : responsesUrl.trim();
        mTranscriptionsUrl = transcriptionsUrl == null ? "" : transcriptionsUrl.trim();
        mBearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    public static ModelEndpointConfig directOpenAi(String apiKey) {
        return new ModelEndpointConfig(false, OPENAI_RESPONSES_URL, OPENAI_TRANSCRIPTIONS_URL,
                apiKey);
    }

    public static ModelEndpointConfig broker(String baseUrl, String token) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.isEmpty()) {
            return new ModelEndpointConfig(true, "", "", token);
        }
        return new ModelEndpointConfig(true, normalized + "/v1/responses",
                normalized + "/v1/audio/transcriptions", token);
    }

    public boolean isBrokerMode() {
        return mBrokerMode;
    }

    public boolean isConfigured() {
        return !mResponsesUrl.isEmpty() && !mBearerToken.isEmpty();
    }

    public String responsesUrl() {
        return mResponsesUrl;
    }

    public String transcriptionsUrl() {
        return mTranscriptionsUrl;
    }

    public String bearerToken() {
        return mBearerToken;
    }

    public String providerName() {
        return mBrokerMode ? "openphone-model-broker" : "openai-responses-vision-dev";
    }

    public String providerDisplayName() {
        return mBrokerMode ? "OpenPhone model broker" : "OpenAI Responses vision";
    }

    public String privacyDisclosure() {
        if (mBrokerMode) {
            return "Cloud task mode sends the goal, task-scoped screenshots, and UI metadata "
                    + "to the configured OpenPhone model broker while the task is active. "
                    + "Provider API keys stay server-side; the phone only holds a broker token "
                    + "for this session.";
        }
        return "Cloud task mode sends the goal, task-scoped screenshots, and UI metadata "
                + "to OpenAI while the task is active. The development API key stays in "
                + "memory and is not written to the trajectory.";
    }

    public String missingCredentialReason() {
        if (mBrokerMode) {
            return mResponsesUrl.isEmpty() ? "missing_broker_url" : "missing_broker_token";
        }
        return "missing_dev_api_key";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }
        return value;
    }
}
