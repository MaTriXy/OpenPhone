package org.openphone.assistant;

public final class AgentControlActivity extends AssistantActivityBackend {
    static final String EXTRA_START_VOICE =
            "org.openphone.assistant.extra.START_VOICE";
    static final String EXTRA_STOP_AGENT =
            "org.openphone.assistant.extra.STOP_AGENT";
    static final String EXTRA_TOGGLE_AGENT =
            "org.openphone.assistant.extra.TOGGLE_AGENT";
    static final String EXTRA_HOLD_TO_RECORD =
            "org.openphone.assistant.extra.HOLD_TO_RECORD";
    static final String EXTRA_FINISH_VOICE_CAPTURE =
            "org.openphone.assistant.extra.FINISH_VOICE_CAPTURE";

    @Override
    protected boolean isControlSurface() {
        return true;
    }
}
