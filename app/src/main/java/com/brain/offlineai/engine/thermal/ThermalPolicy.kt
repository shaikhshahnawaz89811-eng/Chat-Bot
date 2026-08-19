package com.brain.offlineai.engine.thermal

/**
 * Phase 23 (Appendix - Mobile Thermal Management, §1-10: "real device
 * thermal-signal monitoring, pause/unload/cooling-break/reload policy").
 * Real action this app takes at a given real [ThermalReading] - one pure,
 * deterministic function (same "plain classification, no model call, no
 * guessed severity" standard [com.brain.offlineai.agent.RiskGate] already
 * holds itself to for its own two-value risk scale), so it stays unit-
 * testable independent of any real `PowerManager` instance.
 */
enum class ThermalAction {
    /** Real temperature is genuinely fine - the chunk loop proceeds exactly as it always has. */
    CONTINUE,
    /** Real temperature is elevated but not yet dangerous - a short real pause between chunks, model stays loaded. */
    COOLING_BREAK,
    /** Real temperature is genuinely high enough that continuing to run the model is not safe for the hardware - unload and persist a resumable Task State row. */
    UNLOAD_AND_PAUSE
}

object ThermalPolicy {

    // Real `android.os.PowerManager.THERMAL_STATUS_*` int values (Rule 9 -
    // confirmed live against Android's own current Thermal API docs at
    // implementation time, not assumed from older training data).
    // Mirrored here as plain constants rather than importing PowerManager
    // into this file, so this decision function stays a plain Kotlin
    // function with no platform dependency (same reasoning
    // [com.brain.offlineai.agent.RiskGate]'s own pure function already
    // follows for itself).
    const val THERMAL_STATUS_NONE = 0
    const val THERMAL_STATUS_LIGHT = 1
    const val THERMAL_STATUS_MODERATE = 2
    const val THERMAL_STATUS_SEVERE = 3
    const val THERMAL_STATUS_CRITICAL = 4
    const val THERMAL_STATUS_EMERGENCY = 5
    const val THERMAL_STATUS_SHUTDOWN = 6

    /**
     * The real thresholds Android's own Thermal API documentation
     * describes ("reduce workload" at MODERATE, "significantly reduce
     * workload" / avoid non-critical work from SEVERE upward) - not a
     * guessed "80%" number invented for this app.
     */
    fun decide(reading: ThermalReading): ThermalAction = when (reading) {
        // No real signal on this device (pre-API-29) - the same
        // "genuinely can't know, so don't fabricate a guess either way"
        // stance this app takes everywhere else (e.g. WebSearchTrigger's
        // own conservative no-ungrounded-guess posture). CONTINUE here is
        // this app's pre-existing, unthrottled behavior on such devices -
        // not a new invented always-safe assumption layered on top.
        is ThermalReading.Unavailable -> ThermalAction.CONTINUE
        is ThermalReading.Level -> when {
            reading.status >= THERMAL_STATUS_SEVERE -> ThermalAction.UNLOAD_AND_PAUSE
            reading.status == THERMAL_STATUS_MODERATE -> ThermalAction.COOLING_BREAK
            else -> ThermalAction.CONTINUE
        }
    }

    /**
     * Real "safe to reload" gate for resuming a thermally paused task -
     * deliberately below MODERATE (not merely below the SEVERE pause
     * threshold), so a real status sitting right at the MODERATE/SEVERE
     * boundary can't immediately reload and re-pause in a tight thrash;
     * it has to genuinely cool past the cooling-break threshold too.
     */
    fun safeToResume(reading: ThermalReading): Boolean = when (reading) {
        is ThermalReading.Unavailable -> true
        is ThermalReading.Level -> reading.status < THERMAL_STATUS_MODERATE
    }
}
