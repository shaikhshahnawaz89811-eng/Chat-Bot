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
    /** True only while the native KV context contains a resumable generation. */
    private val continuationReady = AtomicBoolean(false)
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
                continuationReady.set(false)
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
            continuationReady.set(false)
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
            continuationReady.set(false)
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
        onProgress: () -> Unit = {}
    ): Flow<String> = generateInternal(prompt, maxTokens, temperature, topP, true, onStopReason, onProgress)

    /**
     * Continues the exact native llama.cpp KV context left by a previous
     * generation that ended at max_tokens. No conversation/history/prompt is
     * re-read or re-prefilled. This is intentionally separate from generate()
     * so a brand-new user message always gets a clean context.
     */
    fun continueGenerate(
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        onStopReason: (String) -> Unit = {},
        onProgress: () -> Unit = {}
    ): Flow<String> = generateInternal("", maxTokens, temperature, topP, false, onStopReason, onProgress)

    private fun generateInternal(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        resetContext: Boolean,
        onStopReason: (String) -> Unit,
        onProgress: () -> Unit
    ): Flow<String> = callbackFlow {
        if (!isLoaded) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }

        val idle = withTimeoutOrNull(90_000L) {
            while (generationActive.get()) {
                // This is engine teardown/waiting, not model token progress.
                // Keep the caller informed that resume is waiting for the
                // previous native operation to become genuinely idle, while
                // deliberately never incrementing the token counter.
                onProgress()
                delay(250L)
            }
        }
        if (idle == null) {
            close(IllegalStateException("Previous generation did not become idle within 90 seconds"))
            return@callbackFlow
        }
        if (!resetContext && !continuationReady.get()) {
            close(IllegalStateException("No resumable native generation context"))
            return@callbackFlow
        }

        val cancelled = AtomicBoolean(false)
        val callback = BrainNative.TokenCallback { token ->
            if (cancelled.get()) {
                false
            } else if (token.isEmpty()) {
                // Empty callback is a native prefill heartbeat only. It is
                // deliberately NOT reported as generation progress: the
                // ChatViewModel stall watchdog must measure time to the next
                // real generated token, otherwise a slow prefill can reset
                // the 90-second watchdog forever while the UI remains at
                // "0 tokens" for minutes. Cancellation still returns true
                // here so the native prefill loop remains interruptible.
                true
            } else {
                onProgress()
                trySend(token).isSuccess && !cancelled.get()
            }
        }

        val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            nativeOperationMutex.withLock {
                generationActive.set(true)
                try {
                    val stopReason = if (resetContext) {
                        BrainNative.nativeGenerate(prompt, maxTokens, temperature, topP, callback)
                    } else {
                        BrainNative.nativeContinueGenerate(maxTokens, temperature, topP, callback)
                    }
                    if (!cancelled.get()) {
                        if (stopReason.startsWith("error:")) {
                            continuationReady.set(false)
                            close(IllegalStateException(stopReason))
                        } else {
                            onStopReason(stopReason)
                            continuationReady.set(stopReason == "max_tokens")
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
            // Coroutine cancellation cannot interrupt a blocking JNI call by
            // itself. Explicitly signal the native inference loop before
            // cancelling the worker so a watchdog/Stop can actually release
            // the llama.cpp call instead of leaving a hidden native worker
            // running behind the UI.
            if (generationActive.get()) {
                BrainNative.nativeCancelGeneration()
            }
            worker.cancel()
            // A cancellation/timeout must never advertise the native KV cache
            // as a safe continuation point: the native worker may have stopped
            // at a prefill boundary rather than after a complete generated token.
            if (cancelled.get()) continuationReady.set(false)
        }
    }

}
