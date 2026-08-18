package com.brain.offlineai.ui.screens.chat

/** How a bot message should currently render. Mirrors the 4 chat-interface
 *  states shown in the mockup (screens 1-4): plain text, live thinking
 *  checklist, live coding block, and generating-response waveform.
 *  CODE_DONE is the completed counterpart of CODING (Rule 8 Part A -
 *  CODING's natural counterpart was missing: it never had a "finished"
 *  state to hand off to, so it stayed shown forever even after a real
 *  generation had fully completed). */
enum class BotMessageState { TEXT, THINKING, CODING, CODE_DONE, GENERATING, SYSTEM_NOTE }

data class ChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: String,
    val state: BotMessageState = BotMessageState.TEXT,
    val thinkingSteps: List<ThinkingStep> = emptyList(),
    val codeLines: List<String> = emptyList(),
    val generationProgress: Int = 0
)

data class ThinkingStep(val label: String, val done: Boolean)
