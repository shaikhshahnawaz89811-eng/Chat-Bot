package com.brain.offlineai.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brain.offlineai.ui.theme.applyBrainTheme

/**
 * Single, process-wide holder of the real live values behind Phase 6's
 * theme + animation toggles - same one-owner-object shape as
 * [com.brain.offlineai.engine.BrainEngine] / [com.brain.offlineai.server.LocalApiServerManager].
 *
 * [init] is called once from MainActivity.onCreate (before setContent) to
 * seed these from the persisted [AppSettingsRepository]. GeneralSettingsViewModel
 * updates both this object and the repository together on every toggle, so
 * every already-open Composable that reads [animationsEnabled] (or, via
 * [applyBrainTheme], the theme tokens in Color.kt) recomposes with the new
 * value immediately - not just the Settings screen itself (Rule 17).
 */
object AppSettingsState {

    var animationsEnabled by mutableStateOf(true)
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val repo = AppSettingsRepository(context.applicationContext)
        animationsEnabled = repo.isAnimationsEnabled()
        applyBrainTheme(repo.isDarkTheme())
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
    }
}
