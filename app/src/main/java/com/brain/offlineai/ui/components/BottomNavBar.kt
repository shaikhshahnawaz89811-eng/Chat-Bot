package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.brain.offlineai.navigation.Screen
import com.brain.offlineai.ui.theme.BrainBgCard
import com.brain.offlineai.ui.theme.BrainPurplePrimary
import com.brain.offlineai.ui.theme.BrainTextMuted

private fun iconFor(screen: Screen): ImageVector = when (screen) {
    Screen.Chat -> Icons.Filled.Chat
    Screen.History -> Icons.Filled.History
    Screen.Models -> Icons.Filled.Layers
    Screen.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Chat
}

@Composable
fun BrainBottomNavBar(currentRoute: String, onNavigate: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrainBgCard)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            val tint = if (selected) BrainPurplePrimary else BrainTextMuted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickableNoRipple { onNavigate(screen) }
            ) {
                Icon(iconFor(screen), contentDescription = screen.label, tint = tint, modifier = Modifier.size(20.dp))
                Text(screen.label, color = tint, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)
