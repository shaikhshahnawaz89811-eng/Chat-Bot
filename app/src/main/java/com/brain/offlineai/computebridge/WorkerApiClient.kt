package com.brain.offlineai.computebridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real client for the Compute Bridge worker HTTP API described in
 * docs/protocol.md (/v1/health, /v1/pair, /v1/worker,
 * /v1/chat/completions). Uses plain HttpURLConnection, the same
 * JDK/Android-provided client this app already uses elsewhere for outbound
 * calls (e.g. Tavily web search) - no new HTTP dependency for this.
 *
 * Confirms what the architecture doc and the worker's own
 * docs/main-app-integration.md both state: "No RAM is physically
 * combined." This class only ever sends a prompt and receives text back
 * over the socket - it has no ability to touch the worker's memory or
 * cores directly, and neither does the worker reach into this phone's.
 */
class WorkerApiClient(private val target: PairedWorker) {

    private fun base() = "http://${target.host}:${target.port}"

    /** Unauthenticated liveness probe - true only on a genuine HTTP 200. */
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("${base()}/v1/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        }.getOrDefault(false)
    }

    /** Real worker metadata (engine state, models, resource budget) - null
     * on any failure (unreachable, wrong/expired token). */
    suspend fun workerInfo(): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("${base()}/v1/worker").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Authorization", "Bearer ${target.token}")
            if (conn.responseCode != 200) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            JSONObject(body)
        }.getOrNull()
    }

    /** Verifies [pairingToken] against the worker at [host]:[port] via a
     * real POST /v1/pair call and returns the access token to store, or
     * null if the worker rejected it / is unreachable. Static so a caller
     * can pair before a [PairedWorker] (which needs an access token) even
     * exists yet. */
    suspend fun pair(host: String, port: Int, pairingToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("http://$host:$port/v1/pair").openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject().put("pairing_token", pairingToken).toString().toByteArray()) }
            if (conn.responseCode != 200) { conn.disconnect(); return@withContext null }
            val body = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            body.optString("access_token").ifBlank { null }
        }.getOrNull()
    }

    /**
     * Streams real generated token pieces from POST /v1/chat/completions
     * (stream=true, Server-Sent Events) as a cold Flow<String> - the exact
     * same shape [com.brain.offlineai.engine.BrainEngine.generate] already
     * returns, so [ComputeManager] can hand either source to the same
     * collector. Follows BrainEngine.generate's own cancellation pattern:
     * the blocking read loop runs in a detached worker coroutine so
     * [awaitClose] can cut the connection immediately even if the loop is
     * blocked waiting on the socket.
     */
    fun chatStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): Flow<String> = callbackFlow {
        val cancelled = AtomicBoolean(false)
        var activeConn: HttpURLConnection? = null

        val job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var conn: HttpURLConnection? = null
            try {
                conn = URL("${base()}/v1/chat/completions").openConnection() as HttpURLConnection
                activeConn = conn
                conn.connectTimeout = 5000
                conn.readTimeout = 45_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer ${target.token}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "text/event-stream")
                val body = JSONObject()
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                    .put("max_tokens", maxTokens)
                    .put("temperature", temperature)
                    .put("top_p", topP)
                    .put("stream", true)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                if (conn.responseCode != 200) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                    if (!cancelled.get()) close(IllegalStateException("Worker returned ${conn.responseCode}: ${err ?: "no body"}"))
                    return@launch
                }

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                while (!cancelled.get()) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    if (chunk.has("error")) {
                        if (!cancelled.get()) {
                            close(IllegalStateException(chunk.getJSONObject("error").optString("message", "Worker generation failed")))
                        }
                        return@launch
                    }
                    val delta = chunk.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                    if (!delta.isNullOrEmpty() && !cancelled.get()) {
                        if (!trySend(delta).isSuccess) break
                    }
                }
                if (!cancelled.get()) close()
            } catch (t: Throwable) {
                if (!cancelled.get()) close(t)
            } finally {
                runCatching { conn?.disconnect() }
            }
        }

        awaitClose {
            cancelled.set(true)
            job.cancel()
            runCatching { activeConn?.disconnect() }
        }
    }.flowOn(Dispatchers.IO)
}
