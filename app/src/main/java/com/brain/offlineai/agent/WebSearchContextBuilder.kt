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

    private const val MAX_CONTENT_CHARS_PER_RESULT = 300
    private const val MAX_RESULTS_IN_PROMPT = 3

    fun buildContextBlock(query: String, results: List<WebSearchResult>, answer: String?): String {
        if (results.isEmpty() && answer == null) return ""
        val ranked = rankResults(query, results)
        val sections = buildString {
            ranked.take(MAX_RESULTS_IN_PROMPT).forEachIndexed { index, result ->
                val content = if (result.content.length > MAX_CONTENT_CHARS_PER_RESULT) {
                    result.content.take(MAX_CONTENT_CHARS_PER_RESULT) + "... (truncated)"
                } else {
                    result.content
                }
                append("${index + 1}. ${result.title}\n$content\n\n")
            }
        }.trim()
        return "\n\n--- Web search results for \"$query\" ---\nThese are external reference results only. They are not the user's requested output and must not replace the user's task. Use them only when they contain a directly useful factual detail.\n$sections\n--- End web search results ---"
    }

    /** Deterministic relevance pass: keep real results, but prefer pages whose title/content actually contains the user's important query terms. */
    fun rankResults(query: String, results: List<WebSearchResult>): List<WebSearchResult> {
        val terms = Regex("[A-Za-z0-9]{3,}").findAll(query.lowercase()).map { it.value }.filterNot {
            it in setOf(
                "official", "documentation", "current", "implementation", "guidance", "development",
                "create", "build", "make", "website", "web", "app", "application", "project",
                "search", "online", "latest", "version", "help", "how", "to"
            )
        }.toSet()
        val scoredResults = results.filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .mapIndexed { index, result ->
                val haystack = (result.title + " " + result.content + " " + result.url).lowercase()
                val termHits = terms.count { haystack.contains(it) }
                val score = termHits * 10 + if (termHits > 0 && result.title.isNotBlank()) 2 else 0
                Triple(result, score, index)
            }
            .sortedWith(compareByDescending<Triple<WebSearchResult, Int, Int>> { it.second }.thenBy { it.third })
        val relevant = scoredResults.filter { it.second > 0 }.map { it.first }
        return if (relevant.isNotEmpty()) relevant else scoredResults.take(1).map { it.first }
    }

    /** Real, short label available to UI callers that need a human-readable search event. */
    fun buildSearchingSummary(query: String, resultCount: Int): String =
        "Searched the web for: \"$query\" ($resultCount result${if (resultCount == 1) "" else "s"})"
}
