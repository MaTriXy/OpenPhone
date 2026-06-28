package org.openphone.assistant.state

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import org.openphone.assistant.runtime.RuntimeRegistry

class AssistantViewModel(
    private val chatHistoryStore: ChatHistoryStore? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AssistantUiState(
            chat = ChatUiState(
                messages = chatHistoryStore?.loadCurrentMessages().orEmpty(),
                history = chatHistoryStore?.loadHistory().orEmpty(),
            ),
        ),
    )
    val state: StateFlow<AssistantUiState> = mutableState

    fun showChat() {
        mutableState.update { it.copy(route = AssistantRoute.Chat) }
    }

    fun showAdvanced() {
        mutableState.update { it.copy(route = AssistantRoute.Advanced) }
    }

    fun showRuntimes() {
        mutableState.update { it.copy(route = AssistantRoute.Runtimes) }
    }

    fun setComposerText(text: String) {
        mutableState.update { it.copy(chat = it.chat.copy(composerText = text)) }
    }

    fun chooseSuggestion(text: String) {
        setComposerText(text)
    }

    fun appendUserMessage(text: String) {
        if (text.isBlank()) return
        mutableState.update {
            val message = ChatMessage("You", text.trim(), isUser = true)
            val messages = it.chat.messages + message
            chatHistoryStore?.saveCurrentMessages(messages)
            it.copy(chat = it.chat.copy(messages = messages, composerText = ""))
        }
    }

    fun appendAssistantMessage(text: String) {
        if (text.isBlank()) return
        mutableState.update {
            val message = ChatMessage("OpenPhone", text.trim(), isUser = false)
            val messages = it.chat.messages + message
            chatHistoryStore?.saveCurrentMessages(messages)
            it.copy(chat = it.chat.copy(messages = messages))
        }
    }

    fun newChat() {
        mutableState.update {
            val history = chatHistoryStore?.archiveAndStartNew(it.chat.messages).orEmpty()
            it.copy(
                route = AssistantRoute.Chat,
                pending = null,
                chat = it.chat.copy(
                    messages = emptyList(),
                    history = history,
                    composerText = "",
                ),
            )
        }
    }

    fun openChatSession(sessionId: String) {
        val messages = chatHistoryStore?.loadSessionMessages(sessionId).orEmpty()
        if (messages.isEmpty()) return
        chatHistoryStore?.saveCurrentMessages(messages)
        mutableState.update {
            it.copy(
                route = AssistantRoute.Chat,
                pending = null,
                chat = it.chat.copy(messages = messages, composerText = ""),
            )
        }
    }

    fun setRuntimeStatus(text: String, activeTaskId: String?, isRunning: Boolean, isListening: Boolean) {
        mutableState.update {
            it.copy(
                chat = it.chat.copy(
                    statusText = text.ifBlank { "OpenPhone is ready" },
                    activeTaskId = activeTaskId,
                    isAgentRunning = isRunning,
                    isListening = isListening,
                ),
            )
        }
    }

    fun setPendingConfirmation(pending: PendingConfirmation?) {
        mutableState.update { it.copy(pending = pending) }
    }

    fun setModelConfig(model: ModelConfig) {
        mutableState.update { it.copy(advanced = it.advanced.copy(model = model)) }
    }

    fun updateModelConfig(transform: (ModelConfig) -> ModelConfig) {
        mutableState.update {
            it.copy(advanced = it.advanced.copy(model = transform(it.advanced.model)))
        }
    }

    fun setOtaState(ota: OtaState) {
        mutableState.update { it.copy(advanced = it.advanced.copy(ota = ota)) }
    }

    fun updateOtaState(transform: (OtaState) -> OtaState) {
        mutableState.update {
            it.copy(advanced = it.advanced.copy(ota = transform(it.advanced.ota)))
        }
    }

    fun setTaskGrants(grants: TaskGrants) {
        mutableState.update { it.copy(advanced = it.advanced.copy(grants = grants)) }
    }

    fun setAutonomyMode(mode: String) {
        val cleanMode = when (mode) {
            "yolo" -> "yolo"
            "dry_run" -> "dry_run"
            else -> "reviewed"
        }
        mutableState.update { it.copy(advanced = it.advanced.copy(autonomyMode = cleanMode)) }
    }

    fun updateTaskGrants(transform: (TaskGrants) -> TaskGrants) {
        mutableState.update {
            it.copy(advanced = it.advanced.copy(grants = transform(it.advanced.grants)))
        }
    }

    fun setDeveloperState(developer: DeveloperState) {
        mutableState.update { it.copy(advanced = it.advanced.copy(developer = developer)) }
    }

    fun updateDeveloperState(transform: (DeveloperState) -> DeveloperState) {
        mutableState.update {
            it.copy(advanced = it.advanced.copy(developer = transform(it.advanced.developer)))
        }
    }

    fun setRuntimesFromJson(rawJson: String, lastAction: String = "") {
        mutableState.update {
            it.copy(runtimes = parseRuntimes(rawJson, lastAction.ifBlank { it.runtimes.lastAction }))
        }
    }

    private fun parseRuntimes(rawJson: String, lastAction: String): RuntimesUiState {
        val root = runCatching { JSONObject(rawJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        val configuredByName = linkedMapOf<String, RuntimeAdapterUiState>()
        val configured = root.optJSONArray("configured")
        if (configured != null) {
            for (index in 0 until configured.length()) {
                val item = configured.optJSONObject(index) ?: continue
                val name = item.optString("name").ifBlank { item.optString("runtime") }
                if (name.isBlank()) continue
                configuredByName[name] = RuntimeAdapterUiState(
                    name = name,
                    label = item.optString("label", name),
                    enabled = item.optBoolean("enabled", false),
                    configured = item.optBoolean("configured", false),
                    url = item.optString("url"),
                    deviceId = item.optString("device_id"),
                )
            }
        }

        val adapters = mutableListOf<RuntimeAdapterUiState>()
        val statusAdapters = root.optJSONArray("adapters")
        val runtimeNamesWithStatus = mutableSetOf<String>()
        if (statusAdapters != null) {
            for (index in 0 until statusAdapters.length()) {
                val item = statusAdapters.optJSONObject(index) ?: continue
                val name = item.optString("name")
                if (name.isBlank()) continue
                runtimeNamesWithStatus += name
                val configuredAdapter = configuredByName.remove(name)
                adapters += (configuredAdapter ?: RuntimeAdapterUiState(name = name, label = name)).copy(
                    status = item.optString("status", configuredAdapter?.status ?: "unknown"),
                )
            }
        }
        adapters += configuredByName.values.filter {
            RuntimeRegistry.isKnownRemoteRuntime(it.name) ||
                it.enabled ||
                it.configured ||
                runtimeNamesWithStatus.contains(it.name)
        }

        return RuntimesUiState(
            status = root.optString("status", "unknown"),
            managerStatus = root.optString("manager_status", "unknown"),
            chatRuntime = root.optString("chat_runtime", "auto"),
            effectiveChatRuntime = root.optString("effective_chat_runtime", "builtin"),
            volumeRuntime = root.optString("volume_runtime", "builtin"),
            backgroundRuntime = root.optString("background_runtime", "builtin"),
            updatedAtMillis = root.optLong("updated_at_ms", 0L),
            lastAction = lastAction,
            adapters = adapters.sortedWith(
                compareBy<RuntimeAdapterUiState> { RuntimeRegistry.sortRank(it.name) }
                    .thenBy { it.name },
            ),
        )
    }

    companion object {
        fun previewState() = AssistantUiState(
            chat = ChatUiState(
                messages = listOf(
                    ChatMessage("You", "Summarize what is on screen", true),
                    ChatMessage("OpenPhone", "I can read the current screen and suggest the next action.", false),
                ),
                activeTaskId = "task_preview",
                statusText = "Running screen task",
            ),
            pending = PendingConfirmation(
                actionId = "pending_preview",
                toolName = "tap",
                summary = "Tap the primary button in the current foreground app.",
            ),
            advanced = AdvancedUiState(
                model = ModelConfig(
                    useRealtimeVision = true,
                    useRealtime2 = true,
                    useLiveRealtimeVoice = true,
                    useBroker = false,
                    brokerUrl = "https://broker.openphone.invalid/v1",
                    disclosure = "Live Realtime 2 with the classic OpenPhone agent available.",
                ),
                ota = OtaState(
                    feedUrl = "https://updates.openphone.invalid/ota.json",
                    status = "OpenPhone 0.1.22-dev is available.",
                    canDownload = true,
                ),
                developer = DeveloperState(
                    goal = "Open Settings and show battery usage",
                    rawActionJson = """{"type":"back"}""",
                    screenContext = "package: com.android.settings\nactivity: .Settings",
                    auditLog = "task_started task_preview\nscreen_context_read",
                ),
            ),
            runtimes = RuntimesUiState(
                status = "connected",
                managerStatus = "connected",
                chatRuntime = "openclaw",
                effectiveChatRuntime = "openclaw",
                volumeRuntime = "builtin",
                backgroundRuntime = "builtin",
                updatedAtMillis = System.currentTimeMillis(),
                lastAction = "Chat runtime set to OpenClaw",
                adapters = listOf(
                    RuntimeAdapterUiState(
                        name = "openclaw",
                        label = "OpenClaw",
                        status = "connected",
                        enabled = true,
                        configured = true,
                        url = "ws://127.0.0.1:18789",
                        deviceId = "openphone-preview-openclaw",
                    ),
                ),
            ),
        )
    }
}

class ChatHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadCurrentMessages(): List<ChatMessage> =
        parseMessages(prefs.getString(KEY_CURRENT_MESSAGES, null))

    fun loadHistory(): List<ChatSessionSummary> =
        parseSessions().map { it.summary }

    fun saveCurrentMessages(messages: List<ChatMessage>) {
        prefs.edit().putString(KEY_CURRENT_MESSAGES, messagesToJson(messages).toString()).apply()
    }

    fun archiveAndStartNew(messages: List<ChatMessage>): List<ChatSessionSummary> {
        val editor = prefs.edit().putString(KEY_CURRENT_MESSAGES, JSONArray().toString())
        if (messages.isNotEmpty()) {
            val sessions = parseSessions().toMutableList()
            val now = System.currentTimeMillis()
            sessions.add(
                0,
                StoredSession(
                    summary = ChatSessionSummary(
                        id = now.toString(),
                        title = titleFor(messages),
                        updatedAtMillis = now,
                    ),
                    messages = messages,
                ),
            )
            editor.putString(KEY_HISTORY, sessions.take(MAX_SESSIONS).let(::sessionsToJson).toString())
        }
        editor.apply()
        return loadHistory()
    }

    fun loadSessionMessages(sessionId: String): List<ChatMessage> =
        parseSessions().firstOrNull { it.summary.id == sessionId }?.messages.orEmpty()

    private fun parseSessions(): List<StoredSession> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val session = array.optJSONObject(index) ?: continue
                    val id = session.optString("id")
                    if (id.isBlank()) continue
                    val messages = parseMessages(session.optJSONArray("messages"))
                    add(
                        StoredSession(
                            summary = ChatSessionSummary(
                                id = id,
                                title = session.optString("title", "New chat"),
                                updatedAtMillis = session.optLong("updated_at", 0L),
                            ),
                            messages = messages,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun sessionsToJson(sessions: List<StoredSession>): JSONArray {
        val array = JSONArray()
        sessions.forEach { session ->
            array.put(
                JSONObject()
                    .put("id", session.summary.id)
                    .put("title", session.summary.title)
                    .put("updated_at", session.summary.updatedAtMillis)
                    .put("messages", messagesToJson(session.messages)),
            )
        }
        return array
    }

    private fun messagesToJson(messages: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("speaker", message.speaker)
                    .put("body", message.body)
                    .put("is_user", message.isUser),
            )
        }
        return array
    }

    private fun parseMessages(raw: String?): List<ChatMessage> =
        raw?.let { runCatching { JSONArray(it) }.getOrNull() }?.let(::parseMessages).orEmpty()

    private fun parseMessages(array: JSONArray?): List<ChatMessage> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val body = item.optString("body").trim()
                if (body.isBlank()) continue
                val isUser = item.optBoolean("is_user", false)
                add(
                    ChatMessage(
                        speaker = item.optString("speaker", if (isUser) "You" else "OpenPhone"),
                        body = body,
                        isUser = isUser,
                    ),
                )
            }
        }
    }

    private fun titleFor(messages: List<ChatMessage>): String {
        val source = messages.firstOrNull { it.isUser }?.body ?: messages.firstOrNull()?.body ?: "New chat"
        return source.replace(Regex("\\s+"), " ").take(MAX_TITLE_LENGTH).ifBlank { "New chat" }
    }

    private data class StoredSession(
        val summary: ChatSessionSummary,
        val messages: List<ChatMessage>,
    )

    companion object {
        private const val PREFS_NAME = "openphone_chat_history"
        private const val KEY_CURRENT_MESSAGES = "current_messages"
        private const val KEY_HISTORY = "history"
        private const val MAX_SESSIONS = 30
        private const val MAX_TITLE_LENGTH = 48
    }
}
