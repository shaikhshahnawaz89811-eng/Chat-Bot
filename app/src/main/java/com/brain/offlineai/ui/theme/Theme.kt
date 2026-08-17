package com.brain.offlineai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * Phase 1-5: this file built one fixed `darkColorScheme(...)` at file-load
 * time from what were then plain top-level Color `val`s.
 *
 * Phase 6 real theme toggle: [BrainBgPrimary]/[BrainTextPrimary]/etc. in
 * Color.kt are now reactive Compose `State` (see [applyBrainTheme]), so
 * the color scheme here is rebuilt inside the composable function on every
 * recomposition instead of once as a top-level constant - reading
 * [brainIsDarkTheme] and the (now-reactive) tokens here means Compose
 * tracks both and this whole app repaints for real when the General
 * Settings screen's theme switch changes them (Rule 17 - actual effect,
 * not just a UI switch).
 */
@Composable
fun BrainOfflineAITheme(content: @Composable () -> Unit) {
    val colorScheme = if (brainIsDarkTheme) {
        darkColorScheme(
            primary = BrainPurplePrimary,
            onPrimary = BrainTextPrimary,
            secondary = BrainCyanAccent,
            onSecondary = BrainBgPrimary,
            background = BrainBgPrimary,
            onBackground = BrainTextPrimary,
            surface = BrainBgCard,
            onSurface = BrainTextPrimary,
            surfaceVariant = BrainBgCardAlt,
            onSurfaceVariant = BrainTextSecondary,
            outline = BrainBorder,
            error = BrainDangerRed
        )
    } else {
        lightColorScheme(
            primary = BrainPurplePrimary,
            onPrimary = BrainTextPrimary,
            secondary = BrainCyanAccent,
            onSecondary = BrainBgPrimary,
            background = BrainBgPrimary,
            onBackground = BrainTextPrimary,
            surface = BrainBgCard,
            onSurface = BrainTextPrimary,
            surfaceVariant = BrainBgCardAlt,
            onSurfaceVariant = BrainTextSecondary,
            outline = BrainBorder,
            error = BrainDangerRed
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrainTypography,
        content = content
    )
}
