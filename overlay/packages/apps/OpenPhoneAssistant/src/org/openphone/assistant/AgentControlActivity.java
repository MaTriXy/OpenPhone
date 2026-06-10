package org.openphone.assistant;

public final class AgentControlActivity extends AssistantActivityBackend {
    static final String EXTRA_START_VOICE =
            "org.openphone.assistant.extra.START_VOICE";
    static final String EXTRA_STOP_AGENT =
            "org.openphone.assistant.extra.STOP_AGENT";
    static final String EXTRA_TOGGLE_AGENT =
            "org.openphone.assistant.extra.TOGGLE_AGENT";

    @Override
    protected boolean isControlSurface() {
        return true;
    }
}
