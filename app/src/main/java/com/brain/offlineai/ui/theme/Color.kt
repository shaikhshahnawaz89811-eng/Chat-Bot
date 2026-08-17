package com.brain.offlineai.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Exact palette lifted from the provided mockup (1000156881.png).
// Kept as a single source of truth so every screen in every phase reuses
// these instead of hardcoding new hex values (Rule 10 - existing conventions).

// --- Dark palette (original mockup values - default theme) ---
private val DarkBgPrimary = Color(0xFF0A0E1C)
private val DarkBgCard = Color(0xFF131829)
private val DarkBgCardAlt = Color(0xFF161C30)
private val DarkBorder = Color(0xFF232A42)
private val DarkTextPrimary = Color(0xFFF5F6FA)
private val DarkTextSecondary = Color(0xFF8A90A8)
private val DarkTextMuted = Color(0xFF5C6280)

// --- Light palette (Phase 6 - a real second theme, not the dark values
// reused with alpha tricks; a genuinely separate set of surface/text
// tokens so "Light" actually looks and reads like a light theme). ---
private val LightBgPrimary = Color(0xFFF4F5FA)
private val LightBgCard = Color(0xFFFFFFFF)
private val LightBgCardAlt = Color(0xFFEDEFF7)
private val LightBorder = Color(0xFFD8DBE8)
private val LightTextPrimary = Color(0xFF14172B)
private val LightTextSecondary = Color(0xFF4A4F6B)
private val LightTextMuted = Color(0xFF868CA6)

/**
 * Phase 6: these surface/text tokens are real Compose [State] (via the
 * `by mutableStateOf(...)` delegate) instead of plain `val`s. Every screen
 * from every earlier phase already reads them by name
 * (`import com.brain.offlineai.ui.theme.*`, then e.g.
 * `.background(BrainBgPrimary)`) - none of those call sites need to
 * change. What changes is that those reads are now tracked by Compose, so
 * [applyBrainTheme] flipping these values from the General Settings
 * screen's real theme toggle causes every already-composed screen to
 * recompose with the new colors immediately - a genuine app-wide effect,
 * not a switch that only repaints the Settings screen itself (Rule 17).
 *
 * Brand accent colors (purple/cyan/pink/success/warning/danger) stay as
 * plain `val`s below - they're the app's identity color, not part of the
 * light/dark surface swap, same as most real light/dark theme designs
 * keep a constant brand accent across both modes.
 */
var BrainBgPrimary by mutableStateOf(DarkBgPrimary)
    private set
var BrainBgCard by mutableStateOf(DarkBgCard)
    private set
var BrainBgCardAlt by mutableStateOf(DarkBgCardAlt)
    private set
var BrainBorder by mutableStateOf(DarkBorder)
    private set
var BrainTextPrimary by mutableStateOf(DarkTextPrimary)
    private set
var BrainTextSecondary by mutableStateOf(DarkTextSecondary)
    private set
var BrainTextMuted by mutableStateOf(DarkTextMuted)
    private set

/** True = dark palette currently applied, false = light. Display-only mirror; AppSettingsRepository is the real persisted source of truth. */
var brainIsDarkTheme = true
    private set

/**
 * Real theme switch. Called once at process start from
 * [com.brain.offlineai.data.settings.AppSettingsState.init] (seeded from
 * the persisted [com.brain.offlineai.data.settings.AppSettingsRepository]),
 * and again immediately whenever the General Settings screen's toggle
 * changes, so the switch takes effect on every visible screen at once -
 * not just the Settings screen itself.
 */
fun applyBrainTheme(dark: Boolean) {
    brainIsDarkTheme = dark
    if (dark) {
        BrainBgPrimary = DarkBgPrimary
        BrainBgCard = DarkBgCard
        BrainBgCardAlt = DarkBgCardAlt
        BrainBorder = DarkBorder
        BrainTextPrimary = DarkTextPrimary
        BrainTextSecondary = DarkTextSecondary
        BrainTextMuted = DarkTextMuted
    } else {
        BrainBgPrimary = LightBgPrimary
        BrainBgCard = LightBgCard
        BrainBgCardAlt = LightBgCardAlt
        BrainBorder = LightBorder
        BrainTextPrimary = LightTextPrimary
        BrainTextSecondary = LightTextSecondary
        BrainTextMuted = LightTextMuted
    }
}

val BrainPurplePrimary = Color(0xFF7B5CF5)
val BrainPurpleDark = Color(0xFF5B3FD1)
val BrainPurpleBubble = Color(0xFF6C4EF0)

val BrainCyanAccent = Color(0xFF4FD8E8)
val BrainPinkAccent = Color(0xFFF0609B)

val BrainSuccessGreen = Color(0xFF2ECC71)
val BrainWarningAmber = Color(0xFFF5A623)
val BrainDangerRed = Color(0xFFEF4B4B)
