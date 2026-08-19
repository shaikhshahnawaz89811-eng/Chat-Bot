package com.brain.offlineai.engine.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 23 (Appendix - Mobile Thermal Management). Real device thermal
 * reading - not a guessed or hardcoded threshold (Rule 9 - the real,
 * current Android API was confirmed live at implementation time, not
 * assumed from older training data): `PowerManager.getCurrentThermalStatus()`
 * / `PowerManager.addThermalStatusListener()`, the same real Thermal HAL
 * 2.0-backed signal Android's own framework uses internally, available
 * since API 29 (Android 10) with no extra manifest permission required
 * for reading the device's own status.
 *
 * [Level] carries the real, current `PowerManager.THERMAL_STATUS_*` int
 * (NONE=0, LIGHT=1, MODERATE=2, SEVERE=3, CRITICAL=4, EMERGENCY=5,
 * SHUTDOWN=6 - mirrored as plain constants in [ThermalPolicy] rather than
 * re-declared here, single source of truth). [Unavailable] is the honest
 * value for any device below API 29, where this real signal genuinely
 * does not exist on the platform - this is never silently treated as
 * "definitely cool" (Rule 10/17 - no invented always-safe default
 * standing in for a real reading that was never actually taken); see
 * [ThermalPolicy.decide]'s own doc for what this app actually does with
 * that honest gap.
 */
sealed class ThermalReading {
    data class Level(val status: Int) : ThermalReading()
    data object Unavailable : ThermalReading()
}

/**
 * Single process-wide real thermal observer - same one-real-owner object
 * pattern [com.brain.offlineai.engine.BrainEngine] already uses for the
 * one real native model handle (Rule 4 - one real implementation, not
 * reimplemented per call site). [start] is idempotent (safe to call from
 * every [com.brain.offlineai.ui.screens.chat.ChatViewModel] instance,
 * same as re-calling it costs nothing) and registers the real
 * `PowerManager.OnThermalStatusChangedListener` exactly once per process.
 */
object ThermalMonitor {

    private val _state = MutableStateFlow<ThermalReading>(ThermalReading.Unavailable)
    val state: StateFlow<ThermalReading> = _state

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Real, honest "no such signal exists on this device" - see
            // class doc above. Nothing further to register.
            _state.value = ThermalReading.Unavailable
            return
        }

        val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
        if (powerManager == null) {
            _state.value = ThermalReading.Unavailable
            return
        }

        // Real current reading immediately - not just future changes, so
        // a message sent right after app start still sees the genuine
        // status rather than a stale "Unavailable" default until the
        // first listener callback happens to fire.
        _state.value = ThermalReading.Level(powerManager.currentThermalStatus)

        powerManager.addThermalStatusListener(
            ContextCompat.getMainExecutor(context)
        ) { status -> _state.value = ThermalReading.Level(status) }
    }
}
