package org.openphone.assistant

class MainActivity : AssistantActivityBackend() {
    companion object {
        const val EXTRA_START_VOICE = "org.openphone.assistant.extra.START_VOICE"
        const val EXTRA_STOP_AGENT = "org.openphone.assistant.extra.STOP_AGENT"
    }
}
