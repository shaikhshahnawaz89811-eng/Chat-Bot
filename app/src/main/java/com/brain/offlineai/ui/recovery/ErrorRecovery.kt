package com.brain.offlineai.ui.recovery

/**
 * Phase 15 (Error & Recovery Flow, spec section 9) - real, deterministic
 * classification of a genuine generation failure (a [Throwable] actually
 * thrown out of [com.brain.offlineai.engine.BrainEngine.generate]'s real
 * Flow, caught by [com.brain.offlineai.ui.screens.chat.ChatViewModel]'s
 * `streamRealResponse`). This never asks the model itself to explain its
 * own failure - the same "no ungrounded model guess" posture Phase 12's
 * `TaskSplitter` and Phase 13's `InputNormalizer` already established for
 * text, applied here to errors: a genuinely broken engine cannot be
 * trusted to reliably diagnose itself, so classification is plain Kotlin
 * type/string matching over the real exception this app's own code threw.
 *
 * [retryable] is the real, honest signal for whether a single automatic
 * retry is worth attempting - it is `true` only for categories that are
 * plausibly transient (a one-off native decode hiccup, or a genuinely
 * unrecognized error where a retry is a safe, cheap thing to try once).
 * It is `false` for categories where a retry cannot possibly help (no
 * model loaded, a real out-of-memory condition, or a real cancellation) -
 * retrying those would just waste a second real decode attempt on a
 * cause that hasn't changed.
 */
enum class ErrorCategory(
    val rootCauseLabel: String,
    val userSuggestion: String,
    val retryable: Boolean
) {
    NO_MODEL_LOADED(
        rootCauseLabel = "No model is loaded",
        userSuggestion = "Import and load a model from the Models screen, then send your message again.",
        retryable = false
    ),
    OUT_OF_MEMORY(
        rootCauseLabel = "The device ran out of memory during generation",
        userSuggestion = "Try a shorter context length in Model Settings, or close other apps to free RAM.",
        retryable = false
    ),
    CANCELLED(
        rootCauseLabel = "Generation was cancelled before it finished",
        userSuggestion = "No action needed - send a new message when you're ready.",
        retryable = false
    ),
    GENERATION_STALLED(
        rootCauseLabel = "Generation stalled without native progress",
        userSuggestion = "The task was paused safely. Continue from the saved point, or shorten the context if it happens again.",
        retryable = false
    ),
    DECODE_ERROR(
        rootCauseLabel = "The native decode step returned a real decode error",
        userSuggestion = "This is a real native decode error; one bounded retry may recover a transient failure.",
        retryable = true
    ),
    UNKNOWN(
        rootCauseLabel = "An unexpected error occurred",
        userSuggestion = "This can be transient - a single automatic retry was attempted.",
        retryable = true
    )
}

/**
 * Real, bounded string/type matching over [error]'s actual class and
 * message - never a guess, never delegated to the model. Order matters:
 * more specific real signals (the app's own "No model loaded"
 * [IllegalStateException] message from `BrainEngine.generate`, a real
 * [OutOfMemoryError]) are checked before the generic fallback.
 */
fun classifyGenerationError(error: Throwable): ErrorCategory {
    val message = error.message?.lowercase().orEmpty()
    return when {
        error is IllegalStateException && message.contains("no model loaded") -> ErrorCategory.NO_MODEL_LOADED
        error is OutOfMemoryError -> ErrorCategory.OUT_OF_MEMORY
        message.contains("out of memory") || message.contains("oom") -> ErrorCategory.OUT_OF_MEMORY
        message.contains("timeout") || message.contains("timed out") || message.contains("stalled") || message.contains("did not become idle") -> ErrorCategory.GENERATION_STALLED
        message.contains("cancel") -> ErrorCategory.CANCELLED
        message.contains("decode") -> ErrorCategory.DECODE_ERROR
        else -> ErrorCategory.UNKNOWN
    }
}
