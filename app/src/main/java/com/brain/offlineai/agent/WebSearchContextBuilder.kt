package com.brain.offlineai.agent

import com.brain.offlineai.data.websearch.WebSearchResult

/**
 * Phase 22 - turns a real, already-returned Tavily response into (a) the
 * real, bounded text block appended to the actual prompt
 * [com.brain.offlineai.engine.BrainEngine.generate] receives, and (b) the
 * real, short chat-visible summary shown before generation runs - same
 * "route/search before acting, never a silent internal decision" standard
 * [com.brain.offlineai.ui.multimodal.AttachmentPromptBuilder] already
 * holds itself to for attachments. Every line here is built only from
 * fields Tavily's own real API response actually returned - nothing is
 * summarized, reworded, or invented on top of it (Rule 1/10).
 */
object WebSearchContextBuilder {

    private const val MAX_CONTENT_CHARS_PER_RESULT = 350
    private const val MAX_RESULTS_IN_PROMPT = 4
    // Tavily's answer is useful context, but it is not bounded by the
    // per-result cap.  An unusually long answer used to inflate the planning
    // prompt and make on-device prefill look like the process had hung.
    private const val MAX_ANSWER_CHARS = 800

    fun buildContextBlock(query: String, results: List<WebSearchResult>, answer: String?): String {
        if (results.isEmpty() && answer == null) return ""
        val sections = buildString {
            if (answer != null) {
                val boundedAnswer = if (answer.length > MAX_ANSWER_CHARS) {
                    answer.take(MAX_ANSWER_CHARS) + "... (truncated)"
                } else {
                    answer
                }
                append("Tavily's summarized answer: ").append(boundedAnswer).append("\n\n")
            }
            results.take(MAX_RESULTS_IN_PROMPT).forEachIndexed { index, result ->
                val content = if (result.content.length > MAX_CONTENT_CHARS_PER_RESULT) {
                    result.content.take(MAX_CONTENT_CHARS_PER_RESULT) + "... (truncated)"
                } else {
                    result.content
                }
                append("${index + 1}. ${result.title} (${result.url})\n$content\n\n")
            }
        }.trim()
        return "\n\n--- Web search results for \"$query\" ---\n$sections\n--- End web search results ---"
    }

    /** Real, short summary posted to the user before the search result content is fed to the model - never a silent internal decision. */
    fun buildSearchingSummary(query: String, resultCount: Int): String =
        "Searched the web for: \"$query\" ($resultCount result${if (resultCount == 1) "" else "s"})"
}
