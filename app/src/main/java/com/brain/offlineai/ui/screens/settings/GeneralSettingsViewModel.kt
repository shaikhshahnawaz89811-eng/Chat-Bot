package com.brain.offlineai.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.brain.offlineai.data.settings.AppSettingsRepository
import com.brain.offlineai.data.settings.AppSettingsState
import com.brain.offlineai.ui.theme.applyBrainTheme
import kotlin.jvm.JvmName

/**
 * Screen 12 from the mockup ("General Settings"). Every switch here is
 * backed by [AppSettingsRepository] (real, persisted) and, for theme and
 * animations, also pushes into [AppSettingsState] / [applyBrainTheme]
 * immediately so the change is visible app-wide the instant it's toggled -
 * not just remembered for the next app launch (Rule 17).
 */
class GeneralSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppSettingsRepository(application)

    var darkThemeEnabled by mutableStateOf(repository.isDarkTheme())
        private set

    var animationsEnabled by mutableStateOf(repository.isAnimationsEnabled())
        private set

    var autoStartLocalApi by mutableStateOf(repository.isAutoStartLocalApi())
        private set

    fun setDarkTheme(enabled: Boolean) {
        darkThemeEnabled = enabled
        repository.setDarkTheme(enabled)
        applyBrainTheme(enabled)
    }

    @JvmName("updateAnimationsEnabled")
    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        repository.setAnimationsEnabled(enabled)
        AppSettingsState.setAnimationsEnabled(enabled)
    }

    /**
     * Persists the preference only - MainActivity reads it on the next
     * process launch (before setContent) to decide whether to start the
     * real foreground service. It's not started/stopped live from here
     * because doing so is already the Local API screen's own real,
     * explicit Start/Stop action (LocalApiViewModel) - duplicating that
     * side effect here would be two owners toggling the same real Service
     * (Rule 3/17 - one real endpoint per action, not two paths to it).
     */
    @JvmName("updateAutoStartLocalApi")
    fun setAutoStartLocalApi(enabled: Boolean) {
        autoStartLocalApi = enabled
        repository.setAutoStartLocalApi(enabled)
    }
}
