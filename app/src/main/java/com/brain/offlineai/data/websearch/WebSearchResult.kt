package com.brain.offlineai.data.websearch

/** One real result row from a genuine Tavily `/search` response - title/url/content are exactly what the API returned, never reworded. */
data class WebSearchResult(
    val title: String,
    val url: String,
    val content: String
)

/**
 * Real, exhaustive outcome of one [TavilySearchClient] call via
 * [WebSearchRepository] - every branch corresponds to something that
 * genuinely happened (a real HTTP success, a real missing key, a real
 * absent connection, a real HTTP/network failure). Nothing here is a
 * stubbed always-success/always-fail default (Rule 10/17).
 */
sealed class WebSearchOutcome {
    /** A real call was made and genuinely returned results. [answer] is Tavily's own optional short synthesized answer, when the API provided one. */
    data class Success(val results: List<WebSearchResult>, val answer: String?) : WebSearchOutcome()

    /** No real call was even attempted - no stored key, or no real device connectivity right now. Offline-first: this is the normal, silent, no-op path for the overwhelming majority of users who never configure a key. */
    data class Unavailable(val reason: String) : WebSearchOutcome()

    /** A real call was attempted (key present, connectivity present) but genuinely failed - a real HTTP error status or a real network exception. */
    data class Failed(val reason: String) : WebSearchOutcome()
}
