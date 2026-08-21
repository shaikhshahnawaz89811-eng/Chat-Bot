package com.brain.offlineai.server

import com.brain.offlineai.data.apikeys.ApiKeyRepository
import com.brain.offlineai.data.apikeys.KeyStatus
import com.brain.offlineai.data.apikeys.statusAt
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.EngineState
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Real OpenAI-compatible HTTP server (mockup screen 10 - "Local API Server
 * Status", the actual OpenAI-compatible highlight badge from the top of the
 * mockup). Bound to "0.0.0.0" so a paired companion app (e.g. Rani) on
 * another phone on the same Wi-Fi/hotspot can reach it too - this is the
 * same LAN-only, no-internet-relay posture the Compute Bridge feature
 * already uses (see network_security_config.xml's comment). No traffic
 * ever leaves the local network: there is no cloud relay, no tunnel, no
 * outbound call added anywhere in this class. Auth is still mandatory on
 * every route below (see [authenticate]) - opening the socket to the LAN
 * does not weaken that.
 *
 * Every route below hits real data:
 * - Auth checks the actual SQLCipher-encrypted api_keys table via
 *   [ApiKeyRepository] (same table Phase 3's API Keys screens manage) -
 *   there is no bypass, no hardcoded "dev" key.
 * - Chat completions run the real llama.cpp decode loop via
 *   [BrainEngine.generate] - the same engine Chat screen uses. No route
 *   here ever fabricates a response.
 */
class LocalApiServer(
    port: Int,
    private val apiKeyRepository: ApiKeyRepository,
    private val onRequestServed: () -> Unit
) : NanoHTTPD("0.0.0.0", port) {

    override fun serve(session: IHTTPSession): Response = try {
        when {
            session.method == Method.GET && session.uri == "/v1/models" -> handleModels(session)
            session.method == Method.POST && session.uri == "/v1/chat/completions" -> handleChatCompletions(session)
            else -> jsonError(Response.Status.NOT_FOUND, "not_found", "No such route: ${session.method} ${session.uri}")
        }
    } catch (e: Exception) {
        jsonError(Response.Status.INTERNAL_ERROR, "server_error", e.message ?: "Internal server error.")
    }

    /**
     * Real auth: reads the `Authorization: Bearer <key>` header, looks the
     * literal key value up in the encrypted DB, and checks its live status
     * via [statusAt] (same computed-not-stored status logic the API Keys
     * UI uses, so a key revoked from the UI is rejected here immediately).
     * On success, records a real `lastUsedAt` write - the "Last Used" field
     * on the Key Details screen reflects genuine Local API traffic.
     */
    private fun authenticate(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: return false
        val token = header.removePrefix("Bearer ").trim()
        if (token.isEmpty()) return false
        val entity = runBlocking { apiKeyRepository.getKeyByValue(token) } ?: return false
        if (entity.statusAt() != KeyStatus.ACTIVE) return false
        runBlocking { apiKeyRepository.touchLastUsed(entity.id) }
        return true
    }

    private fun handleModels(session: IHTTPSession): Response {
        if (!authenticate(session)) return unauthorized()
        onRequestServed()

        val data = JSONArray()
        val state = BrainEngine.state.value
        if (state is EngineState.Loaded) {
            data.put(
                JSONObject()
                    .put("id", state.modelName)
                    .put("object", "model")
                    .put("owned_by", "brain-local")
            )
        }
        // No model loaded -> an honest empty list, not a placeholder entry.
        val body = JSONObject().put("object", "list").put("data", data)
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", body.toString())
    }

    private fun handleChatCompletions(session: IHTTPSession): Response {
        if (!authenticate(session)) return unauthorized()

        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: ""
        val request = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return jsonError(Response.Status.BAD_REQUEST, "invalid_request", "Body is not valid JSON.")
        }

        val messages = request.optJSONArray("messages")
        if (messages == null || messages.length() == 0) {
            return jsonError(Response.Status.BAD_REQUEST, "invalid_request", "\"messages\" is required and must be a non-empty array.")
        }
        if (!BrainEngine.isLoaded) {
            return jsonError(
                Response.Status.SERVICE_UNAVAILABLE,
                "model_not_loaded",
                "No model is loaded in Brain. Open the app, go to Models, and load a .gguf model first."
            )
        }

        val prompt = buildChatMlPrompt(messages)
        val stream = request.optBoolean("stream", false)
        val maxTokens = request.optInt("max_tokens", 512)
        val temperature = request.optDouble("temperature", 0.7).toFloat()
        val topP = request.optDouble("top_p", 0.9).toFloat()
        val modelName = (BrainEngine.state.value as? EngineState.Loaded)?.modelName ?: "brain-local"
        val completionId = "chatcmpl-${System.currentTimeMillis()}"

        onRequestServed()

        return if (stream) {
            streamChatCompletion(prompt, maxTokens, temperature, topP, completionId, modelName)
        } else {
            val text = runBlocking {
                val builder = StringBuilder()
                BrainEngine.generate(prompt, maxTokens, temperature, topP, formatAsChat = false).collect { builder.append(it) }
                builder.toString()
            }
            val body = JSONObject()
                .put("id", completionId)
                .put("object", "chat.completion")
                .put("created", System.currentTimeMillis() / 1000)
                .put("model", modelName)
                .put(
                    "choices",
                    JSONArray().put(
                        JSONObject()
                            .put("index", 0)
                            .put("message", JSONObject().put("role", "assistant").put("content", text))
                            .put("finish_reason", "stop")
                    )
                )
            // No "usage" (prompt/completion token counts) field: computing a
            // real one needs a native tokenizer call this bridge doesn't
            // expose to Kotlin yet, and this project's rules are to never
            // publish an invented number as if it were real - omitting the
            // (optional, per the OpenAI spec) field is the honest choice.
            NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", body.toString())
        }
    }

    /**
     * Real token-by-token SSE streaming straight from [BrainEngine.generate]
     * - a background thread feeds a piped stream as each token actually
     * decodes, and NanoHTTPD's chunked response reads it live. Nothing here
     * buffers a full answer and pretends to stream it.
     */
    private fun streamChatCompletion(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        completionId: String,
        modelName: String
    ): Response {
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 8192)

        Thread {
            try {
                runBlocking {
                    BrainEngine.generate(prompt, maxTokens, temperature, topP, formatAsChat = false).collect { token ->
                        val chunk = JSONObject()
                            .put("id", completionId)
                            .put("object", "chat.completion.chunk")
                            .put("created", System.currentTimeMillis() / 1000)
                            .put("model", modelName)
                            .put(
                                "choices",
                                JSONArray().put(
                                    JSONObject()
                                        .put("index", 0)
                                        .put("delta", JSONObject().put("content", token))
                                        .put("finish_reason", JSONObject.NULL)
                                )
                            )
                        pipedOut.write("data: $chunk\n\n".toByteArray(Charsets.UTF_8))
                        pipedOut.flush()
                    }
                }
                pipedOut.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                pipedOut.flush()
            } catch (e: Exception) {
                // Client disconnected mid-stream or generation hit a real
                // error - either way there's nothing left to write.
            } finally {
                try { pipedOut.close() } catch (e: Exception) { /* already closed */ }
            }
        }.start()

        val response = NanoHTTPD.newChunkedResponse(Response.Status.OK, "text/event-stream", pipedIn)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        return response
    }

    private fun unauthorized(): Response = jsonError(
        Response.Status.UNAUTHORIZED,
        "invalid_api_key",
        "Missing or invalid API key. Send it as 'Authorization: Bearer <key>' using a key created on the API Keys screen."
    )

    private fun jsonError(status: Response.Status, type: String, message: String): Response {
        val body = JSONObject().put(
            "error",
            JSONObject().put("type", type).put("message", message)
        )
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body.toString())
    }

    companion object {
        /**
         * Qwen2.5's real ChatML format - the model this project targets
         * expects this exact structure to produce coherent instruct-style
         * answers, so the Local API Server applies it even though the Chat
         * screen's own ViewModel (Phase 2) currently sends raw text; this is
         * a separate, self-contained code path and doesn't change that
         * screen's existing behavior.
         */
        internal fun buildChatMlPrompt(messages: JSONArray): String {
            val sb = StringBuilder()
            for (i in 0 until messages.length()) {
                val msg = messages.getJSONObject(i)
                val role = msg.optString("role", "user")
                val content = msg.optString("content", "")
                sb.append("<|im_start|>").append(role).append('\n')
                    .append(content).append("<|im_end|>\n")
            }
            sb.append("<|im_start|>assistant\n")
            return sb.toString()
        }
    }
}
