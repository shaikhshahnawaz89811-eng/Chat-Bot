package com.brain.offlineai.computebridge

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.brain.offlineai.engine.BrainEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The "Compute Manager" module from
 * Distributed_Compute_Bridge_Architecture.pdf: decides, per generation
 * request, whether it runs on this phone's own [BrainEngine] or is handed
 * to a paired Compute Bridge worker phone.
 *
 * [ComputeMode.LOCAL] is the default for every install, so with nothing
 * paired and nothing changed in Compute Bridge settings, this behaves
 * exactly like a direct [BrainEngine.generate] call - existing chat
 * behavior is unchanged unless the user opens the Compute Bridge screen
 * and turns Remote/Auto on.
 *
 * [PairedWorkerStore.list] is a real list - REMOTE/AUTO mode walks every
 * enabled worker in priority order before ever falling back to the local
 * engine, so this is a worker pool, not one hardcoded device. Nothing here
 * merges RAM or CPU across devices: a remote attempt is one HTTP request
 * to one worker's own process, which runs the model on its own memory and
 * streams text back - see [WorkerApiClient]'s own doc.
 */
class ComputeManager(private val application: Application) {

    private val store = PairedWorkerStore(application)

    val workerStore: PairedWorkerStore get() = store
    val mode: ComputeMode get() = store.mode
    fun setMode(newMode: ComputeMode) { store.mode = newMode }
    fun pairedWorkers(): List<PairedWorker> = store.list()

    /**
     * Same signature as [BrainEngine.generate] - a drop-in replacement for
     * that one call site in ChatViewModel's continuation loop. Every
     * other piece of that loop (thermal checks, context-budget chunking,
     * stall watchdog) keeps working unmodified, since it only ever
     * depends on getting back a Flow<String> plus the two callbacks.
     *
     * [forceMode] is an optional per-call override of [store.mode] -
     * default null so every existing caller (which never passes it) sees
     * zero behavior change. It exists so a caller (see ChatViewModel's
     * multi-file build) can pin one specific call to LOCAL or REMOTE on
     * purpose - e.g. to run this phone's own engine and a paired worker
     * on two different files at the same time without both accidentally
     * racing for the same target (llama.cpp only allows one generation
     * at a time per engine - local or remote - so genuine simultaneous
     * work across two devices means each call must be pinned to a
     * different, explicit target, never left to AUTO's own heuristic for
     * both calls).
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        onStopReason: (String) -> Unit = {},
        onProgress: () -> Unit = {},
        forceMode: ComputeMode? = null
    ): Flow<String> = when (forceMode ?: store.mode) {
        ComputeMode.LOCAL ->
            BrainEngine.generate(prompt, maxTokens, temperature, topP, onStopReason, onProgress)

        ComputeMode.REMOTE ->
            remoteFirst(prompt, maxTokens, temperature, topP, onStopReason, onProgress)

        ComputeMode.AUTO ->
            if (preferRemoteNow()) {
                remoteFirst(prompt, maxTokens, temperature, topP, onStopReason, onProgress)
            } else {
                localFirst(prompt, maxTokens, temperature, topP, onStopReason, onProgress)
            }
    }

    /** Auto mode's real resource check - the architecture doc's "Low-
     * resource condition mein remote worker prefer kiya ja sakta hai": no
     * local model loaded at all, or this phone's own RAM is already over
     * 80% used, both prefer a paired worker over starting/continuing a
     * local generation. */
    private fun preferRemoteNow(): Boolean {
        if (!BrainEngine.isLoaded) return true
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val fraction = if (info.totalMem > 0) {
            (info.totalMem - info.availMem).toFloat() / info.totalMem.toFloat()
        } else 0f
        return fraction > 0.80f
    }

    private fun candidateWorkers(): List<PairedWorker> =
        store.list().filter { it.enabled }.sortedByDescending { it.priority }

    /** Tries every enabled paired worker in priority order; falls back to
     * the local engine only if none of them produced a single token. If a
     * worker fails after already streaming real partial output, that
     * failure is surfaced honestly instead of silently splicing in a
     * second, different source mid-answer. */
    private fun remoteFirst(
        prompt: String, maxTokens: Int, temperature: Float, topP: Float,
        onStopReason: (String) -> Unit, onProgress: () -> Unit
    ): Flow<String> = flow {
        val candidates = candidateWorkers()
        var lastError: Throwable? = null

        for (candidate in candidates) {
            var sawToken = false
            var count = 0
            try {
                WorkerApiClient(candidate).chatStream(prompt, maxTokens, temperature, topP).collect { piece ->
                    sawToken = true
                    count++
                    onProgress()
                    emit(piece)
                }
                if (count == 0) {
                    lastError = IllegalStateException("Remote worker returned no output")
                    continue
                }
                onStopReason(if (count >= maxTokens) "max_tokens" else "end_of_generation")
                return@flow
            } catch (t: Throwable) {
                if (sawToken) throw t
                lastError = t
            }
        }

        if (!BrainEngine.isLoaded) {
            throw lastError ?: IllegalStateException("No Compute Bridge worker reachable and no local model loaded")
        }
        emitAll(BrainEngine.generate(prompt, maxTokens, temperature, topP, onStopReason, onProgress))
    }

    /** Tries the local engine first; falls back to [remoteFirst] only if
     * local produced zero tokens before failing (e.g. no model loaded). */
    private fun localFirst(
        prompt: String, maxTokens: Int, temperature: Float, topP: Float,
        onStopReason: (String) -> Unit, onProgress: () -> Unit
    ): Flow<String> = flow {
        var sawToken = false
        try {
            BrainEngine.generate(prompt, maxTokens, temperature, topP, onStopReason, onProgress).collect {
                sawToken = true
                emit(it)
            }
            if (!sawToken) throw IllegalStateException("Local model returned no output")
            return@flow
        } catch (t: Throwable) {
            if (sawToken) throw t
        }
        emitAll(remoteFirst(prompt, maxTokens, temperature, topP, onStopReason, onProgress))
    }
}
