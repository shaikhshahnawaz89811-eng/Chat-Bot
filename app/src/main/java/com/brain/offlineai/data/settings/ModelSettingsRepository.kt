package com.brain.offlineai.data.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Real, persisted inference settings for Model Settings (mockup screen 11 -
 * context length, temperature, top-p, thread-count sliders). Through Phase
 * 4 these four values were hardcoded defaults baked into BrainEngine's
 * function signatures (nCtx=2048, nThreads=4) and ChatViewModel's call to
 * BrainEngine.generate() (temperature=0.7f, topP=0.9f) - there was no
 * screen that actually changed them. This repository is the single real
 * source of truth both ModelsViewModel (load) and ChatViewModel (generate)
 * now read from, so the settings screen has a genuine effect end-to-end
 * (Rule 17 - endpoint correctness, not just existence).
 *
 * Backed by plain SharedPreferences (not the encrypted store Phase 3 uses
 * for API keys) - these are inference tuning numbers, not secrets, so the
 * extra SQLCipher/EncryptedSharedPreferences machinery isn't warranted
 * here (Rule 20 - minimal necessary).
 */
class ModelSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("brain_model_settings", Context.MODE_PRIVATE)

    fun getSettings(): ModelSettings = ModelSettings(
        contextLength = prefs.getInt(KEY_CONTEXT_LENGTH, DEFAULT_CONTEXT_LENGTH),
        temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE),
        topP = prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P),
        threads = prefs.getInt(KEY_THREADS, defaultThreads())
    )

    fun saveSettings(settings: ModelSettings) {
        prefs.edit {
            putInt(KEY_CONTEXT_LENGTH, settings.contextLength.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH))
            putFloat(KEY_TEMPERATURE, settings.temperature.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE))
            putFloat(KEY_TOP_P, settings.topP.coerceIn(MIN_TOP_P, MAX_TOP_P))
            putInt(KEY_THREADS, settings.threads.coerceIn(MIN_THREADS, maxThreads()))
        }
    }

    companion object {
        private const val KEY_CONTEXT_LENGTH = "context_length"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_THREADS = "threads"

        const val DEFAULT_CONTEXT_LENGTH = 2048
        const val MIN_CONTEXT_LENGTH = 512
        const val MAX_CONTEXT_LENGTH = 8192

        const val DEFAULT_TEMPERATURE = 0.7f
        const val MIN_TEMPERATURE = 0.1f
        const val MAX_TEMPERATURE = 2.0f

        const val DEFAULT_TOP_P = 0.9f
        const val MIN_TOP_P = 0.1f
        const val MAX_TOP_P = 1.0f

        const val MIN_THREADS = 1

        /** Real device core count, same API Android's own perf tooling reads - not a placeholder cap. */
        fun maxThreads(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(MIN_THREADS)

        private fun defaultThreads(): Int = maxThreads().coerceAtMost(4)
    }
}

data class ModelSettings(
    val contextLength: Int,
    val temperature: Float,
    val topP: Float,
    val threads: Int
)
