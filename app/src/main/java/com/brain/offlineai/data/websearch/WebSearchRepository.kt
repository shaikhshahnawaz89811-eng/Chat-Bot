package com.brain.offlineai.data.websearch

import android.content.Context

/**
 * Phase 22 - the real, offline-first gate every real search request goes
 * through. Same graceful missing-secret fallback Phase 3/4 already
 * document for a missing/invalid API key: no stored Tavily key, or no
 * real device connectivity right now, means [WebSearchOutcome.Unavailable]
 * is returned immediately and silently - [ChatViewModel] posts nothing to
 * the chat and generation proceeds fully offline, exactly as it always
 * has for every user who never configures a key. A real call to
 * [TavilySearchClient] is only ever attempted when both a real key and
 * real connectivity are genuinely present.
 */
class WebSearchRepository(context: Context) {

    private val appContext = context.applicationContext
    private val keyStore = WebSearchKeyStore(appContext)

    suspend fun search(query: String, includeDomains: List<String> = emptyList()): WebSearchOutcome {
        if (!keyStore.isSearchEnabled()) {
            return WebSearchOutcome.Unavailable("Web search is disabled")
        }
        val apiKey = keyStore.getKey()
            ?: return WebSearchOutcome.Unavailable("No Tavily API key configured")
        if (!ConnectivityChecker.hasInternet(appContext)) {
            return WebSearchOutcome.Unavailable("No internet connectivity")
        }
        return TavilySearchClient.search(apiKey, query, includeDomains)
    }

    fun hasStoredKey(): Boolean = keyStore.hasKey()
}
