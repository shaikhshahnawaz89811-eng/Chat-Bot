package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.brain.offlineai.engine.BrainEngine
import com.brain.offlineai.engine.EngineState
import com.brain.offlineai.ui.theme.BrainDangerRed
import com.brain.offlineai.ui.theme.BrainSuccessGreen
import com.brain.offlineai.ui.theme.BrainTextMuted
import com.brain.offlineai.ui.theme.BrainTextPrimary
import com.brain.offlineai.ui.theme.BrainTextSecondary
import com.brain.offlineai.ui.theme.BrainWarningAmber

/**
 * Rule 10/17 correctness fix: this bar previously showed a hardcoded green
 * dot + "Online" label regardless of whether a model was actually loaded -
 * the exact kind of fake status text this project's own rules (and the
 * already-real AiEngineStatusCard in the drawer) explicitly avoid
 * elsewhere. Now reads the same real BrainEngine.state the drawer card and
 * Models screen already use, so the label always matches genuine engine
 * state instead of silently lying when no model is loaded.
 */
@Composable
fun ChatTopBar(title: String, onMenuClick: () -> Unit) {
    val engineState by BrainEngine.state.collectAsState()
    val (statusLabel, statusColor) = when (engineState) {
        is EngineState.Loaded -> "Online" to BrainSuccessGreen
        is EngineState.Loading -> "Loading..." to BrainWarningAmber
        is EngineState.Error -> "Error" to BrainDangerRed
        is EngineState.Unloaded -> "No model" to BrainTextMuted
        is EngineState.ThermalPaused -> "Cooling down..." to BrainWarningAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = BrainTextPrimary)
        }
        Text(text = title, color = BrainTextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(text = statusLabel, color = BrainTextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
}
