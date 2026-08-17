package com.brain.offlineai.data.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Real, persisted General Settings (mockup screen 12 - theme, animations,
 * and Local API auto-start toggles). Same SharedPreferences tier as
 * [ModelSettingsRepository]/[com.brain.offlineai.data.analytics.AnalyticsStore]
 * (Rule 20 - these are UI/behavior preferences, not secrets, so the
 * SQLCipher-encrypted store Phase 3 built for API keys isn't warranted).
 *
 * This is the single durable source of truth; [AppSettingsState] mirrors
 * the live values into reactive Compose state so the whole app reacts
 * immediately when they change, without every screen needing its own
 * SharedPreferences read.
 */
class AppSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("brain_app_settings", Context.MODE_PRIVATE)

    fun isDarkTheme(): Boolean = prefs.getBoolean(KEY_DARK_THEME, true)
    fun setDarkTheme(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_THEME, enabled) }
    }

    fun isAnimationsEnabled(): Boolean = prefs.getBoolean(KEY_ANIMATIONS, true)
    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ANIMATIONS, enabled) }
    }

    /**
     * When true, MainActivity starts the real Local API foreground service
     * (same [com.brain.offlineai.server.LocalApiForegroundService] the
     * Local API screen's own Start button uses) once on process launch,
     * instead of requiring a manual visit to that screen every time.
     */
    fun isAutoStartLocalApi(): Boolean = prefs.getBoolean(KEY_AUTO_START_API, false)
    fun setAutoStartLocalApi(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_START_API, enabled) }
    }

    companion object {
        private const val KEY_DARK_THEME = "dark_theme_enabled"
        private const val KEY_ANIMATIONS = "animations_enabled"
        private const val KEY_AUTO_START_API = "auto_start_local_api"
    }
}
