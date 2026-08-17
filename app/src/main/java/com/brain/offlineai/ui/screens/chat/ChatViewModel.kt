package com.brain.offlineai.ui.screens.chat

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brain.offlineai.data.analytics.AnalyticsStore
import com.brain.offlineai.data.history.ChatHistoryRepository
import com.brain.offlineai.data.settings.ModelSettingsRepository
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.EngineState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drives the Chat screen state - Phase 2 rewrite, Phase 5 wiring added,
 * Phase 7 real persistence added.
 *
 * The Phase 1 version of this file played a scripted, hardcoded
 * thinking-checklist and a canned demo `is_prime` code snippet before
 * ending on a fixed "not wired yet" note - explicitly documented there as
 * a placeholder for this exact replacement. All of that is gone now.
 *
 * What happens today: sendMessage() calls BrainEngine.generate(prompt),
 * which runs a real llama.cpp decode loop and streams back real token
 * pieces one at a time. There is no fallback path that invents an answer
 * when no model is loaded - the UI honestly asks the user to import one
 * (see ModelsScreen) instead.
 *
 * Phase 5: temperature/top-p now come from [ModelSettingsRepository] (the
 * new Model Settings screen) instead of BrainEngine.generate()'s hardcoded
 * defaults, and every real message sent / real tokens generated feed
 * [AnalyticsStore] so the new Analytics screen has genuine numbers to show.
 *
 * Phase 7: this is also the fix for the Rule 1/16 audit gap on the
 * "History" bottom-nav destination - every session is now really persisted
 * via [ChatHistoryRepository] as messages are sent/streamed (not only held
 * in the in-memory `messages` state below), and [openSessionId] lets this
 * same ViewModel reopen a past session from the new History screen instead
 * of always starting empty. `sessionId` starts null (a fresh, not-yet-
 * persisted conversation, same default behavior as every earlier phase)
 * and is created for real in the DB the moment the first message is sent -
 * so opening the Chat tab and never typing anything still creates zero rows,
 * same honest "no data until something real happens" rule the rest of the
 * app already follows (e.g. Analytics' real zeros on a fresh install).
 */
class ChatViewModel(
    application: Application,
    private val openSessionId: String? = null
) : AndroidViewModel(application) {

    private val settingsRepository = ModelSettingsRepository(application)
    private val analyticsStore = AnalyticsStore(application)
    private val historyRepository = ChatHistoryRepository(application)

    var messages = mutableStateOf(listOf<ChatMessage>())
        private set

    var inputText = mutableStateOf("")
        private set

    /** Null until the first message of a fresh conversation is actually sent - see class doc above. */
    private var sessionId: String? = openSessionId

    private var nextId = 1L

    init {
        if (openSessionId != null) {
            viewModelScope.launch { loadExistingSession(openSessionId) }
        }
    }

    private suspend fun loadExistingSession(id: String) {
        val stored = historyRepository.getMessages(id)
        if (stored.isEmpty()) return
        messages.value = stored.map { row ->
            ChatMessage(
                id = row.messageId,
                text = row.text,
                isUser = row.isUser,
                timestamp = formatTime(row.timestampMillis),
                state = BotMessageState.TEXT
            )
        }
        // Real continuation, not a fresh id space colliding with restored rows.
        nextId = (stored.maxOf { it.messageId } + 1)
    }

    fun onInputChange(newText: String) {
        inputText.value = newText
    }

    fun sendMessage() {
        val text = inputText.value.trim()
        if (text.isEmpty()) return
        inputText.value = ""

        val userMessage = ChatMessage(
            id = nextId++,
            text = text,
            isUser = true,
            timestamp = timeNow()
        )
        messages.value = messages.value + userMessage
        analyticsStore.incrementMessagesSent()

        viewModelScope.launch {
            val activeSessionId = ensureSession(text)
            persistMessage(activeSessionId, userMessage)

            if (BrainEngine.state.value !is EngineState.Loaded) {
                postSystemNote(
                    activeSessionId,
                    "No model is loaded yet. Go to Models and import + load a " +
                        ".gguf file (e.g. Qwen2.5-1.5B-Instruct) before chatting - " +
                        "this build never fabricates an answer without a real model."
                )
                return@launch
            }

            streamRealResponse(activeSessionId, text)
        }
    }

    /** Real session creation on first send only - see class doc above for why this stays lazy. */
    private suspend fun ensureSession(firstMessageText: String): String {
        val existing = sessionId
        if (existing != null) return existing
        val created = historyRepository.createSession(firstMessageText)
        sessionId = created
        return created
    }

    private suspend fun persistMessage(activeSessionId: String, message: ChatMessage) {
        historyRepository.saveMessage(
            sessionId = activeSessionId,
            messageId = message.id,
            text = message.text,
            isUser = message.isUser,
            timestampMillis = System.currentTimeMillis(),
            totalMessageCount = messages.value.size
        )
    }

    private suspend fun streamRealResponse(activeSessionId: String, prompt: String) {
        val botId = nextId++
        val builder = StringBuilder()

        upsertBotMessage(
            botId,
            ChatMessage(id = botId, text = "", isUser = false, timestamp = timeNow(), state = BotMessageState.GENERATING, generationProgress = 0)
        )

        var tokenCount = 0
        val settings = settingsRepository.getSettings()
        BrainEngine.generate(prompt, temperature = settings.temperature, topP = settings.topP)
            .onCompletion { cause ->
                if (cause == null) {
                    val finalText = builder.toString().ifBlank { "(model returned no output)" }
                    val finalMessage = renderMessage(botId, finalText, BotMessageState.TEXT)
                    upsertBotMessage(botId, finalMessage)
                    persistMessage(activeSessionId, finalMessage)
                    // Real final count, written once per completed generation
                    // (not per streamed token) - Rule 20 minimal-necessary-payload.
                    analyticsStore.addTokensGenerated(tokenCount.toLong())
                }
            }
            .catch { e ->
                val errorMessage = ChatMessage(
                    id = botId,
                    text = "Generation error: ${e.message}",
                    isUser = false,
                    timestamp = timeNow(),
                    state = BotMessageState.SYSTEM_NOTE
                )
                upsertBotMessage(botId, errorMessage)
                persistMessage(activeSessionId, errorMessage)
            }
            .collect { piece ->
                builder.append(piece)
                tokenCount++
                upsertBotMessage(botId, renderMessage(botId, builder.toString(), BotMessageState.GENERATING, tokenCount))
            }
    }

    /** Real streamed text is rendered as a code block whenever it actually contains a fenced code block. */
    private fun renderMessage(id: Long, text: String, state: BotMessageState, tokenCount: Int = 0): ChatMessage {
        val hasFence = text.contains("```")
        return if (hasFence) {
            val afterFence = text.substringAfter("```")
            val firstLine = afterFence.substringBefore('\n')
            val body = if (firstLine.isNotBlank() && !afterFence.startsWith("\n")) {
                afterFence.substringAfter('\n', "")
            } else afterFence
            val codeBody = body.substringBefore("```").lines()
            ChatMessage(
                id = id, text = text, isUser = false, timestamp = timeNow(),
                state = BotMessageState.CODING, codeLines = codeBody,
                generationProgress = tokenCount
            )
        } else {
            ChatMessage(
                id = id, text = text, isUser = false, timestamp = timeNow(),
                state = state, generationProgress = tokenCount
            )
        }
    }

    private suspend fun postSystemNote(activeSessionId: String, text: String) {
        val botId = nextId++
        val note = ChatMessage(id = botId, text = text, isUser = false, timestamp = timeNow(), state = BotMessageState.SYSTEM_NOTE)
        upsertBotMessage(botId, note)
        persistMessage(activeSessionId, note)
    }

    private fun upsertBotMessage(id: Long, message: ChatMessage) {
        val current = messages.value
        messages.value = if (current.any { it.id == id }) {
            current.map { if (it.id == id) message else it }
        } else {
            current + message
        }
    }

    private fun timeNow(): String = formatTime(System.currentTimeMillis())

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    /**
     * Real factory so `viewModel(factory = ...)` in ChatScreen can pass
     * [openSessionId] through - the plain `viewModel()` default factory
     * Phase 2-6 relied on only knows how to construct an `AndroidViewModel`
     * with just an Application, so it can't carry the extra session-id
     * argument History needs to reopen a past conversation.
     */
    class Factory(
        private val application: Application,
        private val openSessionId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application, openSessionId) as T
        }
    }
}
