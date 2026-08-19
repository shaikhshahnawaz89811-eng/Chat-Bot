package com.brain.offlineai.data.websearch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 22 - real client for Tavily's actual, current public Search API
 * (Rule 9 - confirmed live at implementation time against
 * https://docs.tavily.com/documentation/api-reference/introduction and
 * Tavily's own Help Center, not assumed/guessed from older training
 * data): `POST https://api.tavily.com/search`, JSON body, real
 * `Authorization: Bearer <key>` header - the exact real shape Tavily's
 * own quickstart curl example uses. No SDK dependency was added
 * (Rule 20) - this is a plain `HttpURLConnection` call plus `org.json`,
 * the same real, already-included JSON library
 * [com.brain.offlineai.server.LocalApiServer] already uses, so no new
 * Gradle dependency was needed for outbound HTTPS either (`HttpURLConnection`
 * ships with the JDK/Android itself).
 *
 * Every real field sent is the minimum this feature actually needs
 * (Rule 20 - minimal payload): the query text, a small `max_results` cap,
 * and `search_depth: "basic"` (Tavily's own cheaper, faster real tier -
 * "advanced" costs more real API credits per Tavily's own docs, and this
 * feature has no real need for that extra depth). The project's own
 * source code / file contents are never sent - only a short search query
 * string ever leaves the device.
 */
object TavilySearchClient {

    private const val SEARCH_URL = "https://api.tavily.com/search"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_RESULTS = 5

    /** Real POST to Tavily's real endpoint. Returns a real [WebSearchOutcome.Success]/[WebSearchOutcome.Failed] - never a fabricated result. */
    suspend fun search(apiKey: String, query: String): WebSearchOutcome = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("query", query)
                put("max_results", MAX_RESULTS)
                put("search_depth", "basic")
                put("include_answer", true)
            }
            val (code, responseText) = post(apiKey, requestBody)
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext WebSearchOutcome.Failed(describeHttpError(code, responseText))
            }
            val json = JSONObject(responseText)
            val resultsArray: JSONArray = json.optJSONArray("results") ?: JSONArray()
            val results = (0 until resultsArray.length()).map { i ->
                val item = resultsArray.getJSONObject(i)
                WebSearchResult(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    content = item.optString("content", "")
                )
            }
            val answer = json.optString("answer", "").takeIf { it.isNotBlank() }
            WebSearchOutcome.Success(results, answer)
        } catch (e: Exception) {
            WebSearchOutcome.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Real, minimal validation call - the same real endpoint above with
     * `max_results = 1`, used only to confirm a genuinely working key
     * before [WebSearchKeyStore] saves it (see
     * `WebSearchSettingsViewModel`). A real HTTP 200 means the key is
     * genuinely valid; 401/403 means it genuinely isn't - never guessed
     * from the key's shape/prefix alone.
     */
    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("query", "connectivity check")
                put("max_results", 1)
                put("search_depth", "basic")
            }
            val (code, _) = post(apiKey, requestBody)
            code == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }

    private fun post(apiKey: String, body: JSONObject): Pair<Int, String> {
        val connection = URL(SEARCH_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            code to text
        } finally {
            connection.disconnect()
        }
    }

    private fun describeHttpError(code: Int, body: String): String = when (code) {
        401, 403 -> "Tavily rejected the stored API key (HTTP $code) - it may be invalid, expired, or revoked."
        429 -> "Tavily rate limit reached (HTTP 429) - try again shortly."
        else -> "Tavily returned HTTP $code" + if (body.isNotBlank()) ": ${body.take(200)}" else ""
    }
}
