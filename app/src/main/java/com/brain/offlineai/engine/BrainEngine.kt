package com.brain.offlineai.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** Real state of the local llama.cpp engine - no state here is decorative. */
sealed class EngineState {
    data object Unloaded : EngineState()
    data class Loading(val modelName: String) : EngineState()
    data class Loaded(val modelName: String, val contextSize: Int) : EngineState()
    data class Error(val message: String) : EngineState()
    /**
     * Phase 23 (Appendix - Mobile Thermal Management) - a real, distinct
     * state from plain [Unloaded]: the model was genuinely unloaded by
     * [BrainEngine.pauseForThermal] because the real device thermal
     * status reached SEVERE+ (see
     * [com.brain.offlineai.engine.thermal.ThermalPolicy]), not because
     * the user ever chose to unload it or never loaded one in the first
     * place. Kept separate so the AI Engine Status card (and
     * [com.brain.offlineai.ui.screens.chat.ChatViewModel]'s own resume
     * check) can tell "genuinely nothing to reload" apart from "there is
     * a real model to reload, and a real paused task waiting on it, the
     * moment the device cools down".
     */
    data class ThermalPaused(val modelName: String, val contextSize: Int) : EngineState()
}

/**
 * Single process-wide owner of the native llama.cpp context. There is only
 * ever one model loaded at a time (matches the Phase-1 "AI Engine Status"
 * card, which shows exactly one model).
 */
object BrainEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val state: StateFlow<EngineState> = _state
    private val generationActive = AtomicBoolean(false)
    /** Serializes every operation that touches the single native model/context.
     * Generation, load, unload and thermal pause can never enter llama.cpp
     * concurrently, so a slow/cancelled generation cannot race a settings
     * reload or thermal teardown. */
    private val nativeOperationMutex = Mutex()

    private var backendReady = false

    /**
     * Loads [modelPath] (already copied into app-private storage by
     * ModelFileManager). Runs on Dispatchers.Default since model loading
     * (mmap + metadata parse) is CPU/IO-bound and can take several seconds
     * for a multi-hundred-MB GGUF file - never on the main thread.
     */
    suspend fun loadModel(modelPath: String, modelDisplayName: String, nCtx: Int = 4096, nThreads: Int = 4) {
        nativeOperationMutex.withLock {
            _state.value = EngineState.Loading(modelDisplayName)
            withContext(Dispatchers.Default) {
                if (!backendReady) {
                    backendReady = BrainNative.nativeBackendInit()
                }
                val ok = BrainNative.nativeLoadModel(modelPath, nCtx, nThreads)
                _state.value = if (ok) {
                    EngineState.Loaded(modelDisplayName, BrainNative.nativeGetContextSize())
                } else {
                    EngineState.Error("Model failed to load - check the file is a valid GGUF and fits in device RAM.")
                }
            }
        }
    }

    suspend fun unloadModel() {
        nativeOperationMutex.withLock {
            withContext(Dispatchers.Default) {
                BrainNative.nativeUnloadModel()
            }
            _state.value = EngineState.Unloaded
        }
    }

    /**
     * Phase 23 (Appendix - Mobile Thermal Management) - the real, same
     * native unload [unloadModel] already does (Rule 4 - one real
     * implementation, not a second reimplementation of the native
     * teardown call), but landing on [EngineState.ThermalPaused] instead
     * of plain [EngineState.Unloaded] so the rest of the app can tell
     * this genuinely wasn't the user's own choice - see that state's own
     * doc. No-ops (returns without touching native state) if nothing was
     * actually loaded, since there's nothing real to pause.
     */
    suspend fun pauseForThermal() {
        nativeOperationMutex.withLock {
            val loaded = _state.value as? EngineState.Loaded ?: return
            withContext(Dispatchers.Default) {
                BrainNative.nativeUnloadModel()
            }
            _state.value = EngineState.ThermalPaused(loaded.modelName, loaded.contextSize)
        }
    }

    val isLoaded: Boolean
        get() = _state.value is EngineState.Loaded

    /**
     * A timed-out collector can return before the detached native worker has
     * released llama.cpp's process-wide mutex. Callers must wait for this
     * endpoint before starting a fallback generation.
     */
    suspend fun awaitGenerationIdle(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (generationActive.get() && System.currentTimeMillis() < deadline) {
            delay(25L)
        }
        return !generationActive.get()
    }

    /**
     * Streams real generated text pieces from the model as a cold Flow.
     * Each emission is one decoded token piece straight from
     * llama_token_to_piece - nothing here is scripted or pre-written.
     * Cancelling collection of this flow cancels generation inside the
     * native loop via the TokenCallback returning false.
     *
     * Bug fix (user request - "bada task nahin kar pa raha") - [onStopReason]
     * is additive (default no-op, so every existing call site, e.g.
     * [com.brain.offlineai.server.LocalApiServer], is unaffected) and hands
     * back the real, literal stop reason [BrainNative.nativeGenerate]
     * already returns ("end_of_generation", "max_tokens", "context_full",
     * "cancelled") right before the flow closes normally. Before this,
     * that string was read only to check for an "error:" prefix and then
     * thrown away - so nothing upstream could ever tell a genuinely
     * finished reply ("end_of_generation") apart from one that was simply
     * cut off by [maxTokens] mid-answer ("max_tokens"). ChatViewModel uses
     * this to know when it's real to keep going with a real follow-up
     * `generate()` call instead of silently presenting a truncated answer
     * as if it were complete.
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        onStopReason: (String) -> Unit = {},
        /**
         * Bug fix (user report - "phone slow hai", planning and the
         * single-response fallback both timing out on a genuinely slow
         * device even while the model is still honestly working). Called
         * on every real native callback - both a real decoded token AND a
         * real prefill heartbeat (see the empty-token branch below) - so
         * a caller can tell "still genuinely making progress, just slow"
         * apart from "actually stuck". Never called after cancellation.
         * Default no-op so every existing call site is unaffected.
         */
        onProgress: () -> Unit = {}
    ): Flow<String> =
        callbackFlow {
            if (!isLoaded) {
                close(IllegalStateException("No model loaded"))
                return@callbackFlow
            }
            /*
             * Do not call the blocking JNI function directly from this
             * callbackFlow producer.  A coroutine timeout/cancellation cannot
             * regain control while nativeGenerate is doing prompt prefill, so
             * the old implementation could leave the UI on "Working" forever
             * and keep the model running in an orphaned coroutine.
             *
             * The native call still owns the process-wide native mutex, so a
             * later request cannot corrupt the model while a timed-out call is
             * finishing.  The detached worker is deliberately cancelled from
             * the flow, while the callback's atomic flag makes nativeGenerate
             * stop at its next token boundary.
             */
            val cancelled = AtomicBoolean(false)
            val callback = BrainNative.TokenCallback { token ->
                if (cancelled.get()) {
                    false
                } else if (token.isEmpty()) {
                    // Real prefill heartbeat (see llama_bridge.cpp's own
                    // doc on the chunked-prefill fix) - native calls this
                    // between prompt chunks purely so a real Kotlin-side
                    // cancel/timeout can interrupt prefill itself, not
                    // only the token-by-token decode loop after it. Never
                    // a real generated token: not sent into the flow, and
                    // never counted toward the reply. Still real forward
                    // motion, so it counts toward [onProgress] the same as
                    // a decoded token does.
                    onProgress()
                    true
                } else {
                    onProgress()
                    trySend(token).isSuccess && !cancelled.get()
                }
            }

            // This scope is independent of the collector.  That is
            // intentional: cancelling the collector must return immediately
            // even if JNI is currently inside a long, non-cancellable prefill.
            // A timed-out caller can leave the detached native worker alive
            // until the next prefill heartbeat. Queue the next request here
            // as well as exposing awaitGenerationIdle(), so every caller is
            // protected even when it is not the planner fallback path.
            // A previous call can still be unwinding inside the real native
            // llama.cpp mutex after a Kotlin-side watchdog stopped waiting.
            // Do not turn that normal native teardown race into a generic
            // generation error for the user's next message. Wait for the real
            // engine to become idle, while emitting genuine liveness heartbeats
            // so the UI watchdog knows this coroutine is actively waiting.
            val idle = withTimeoutOrNull(90_000L) {
                while (generationActive.get()) {
                    // Waiting for a previous real native call is not model
                    // generation progress. Do not manufacture heartbeats or
                    // the caller's stall watchdog can never detect a real stall.
                    delay(250L)
                }
            }
            if (idle == null) {
                close(IllegalStateException("Previous generation did not become idle within 90 seconds"))
                return@callbackFlow
            }
            val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                nativeOperationMutex.withLock {
                    generationActive.set(true)
                    try {
                        val stopReason = BrainNative.nativeGenerate(prompt, maxTokens, temperature, topP, callback)
                        if (!cancelled.get()) {
                            if (stopReason.startsWith("error:")) {
                                close(IllegalStateException(stopReason))
                            } else {
                                onStopReason(stopReason)
                                close()
                            }
                        }
                    } finally {
                        generationActive.set(false)
                    }
                }
            }
            awaitClose {
                cancelled.set(true)
                worker.cancel()
            }
        }
}
