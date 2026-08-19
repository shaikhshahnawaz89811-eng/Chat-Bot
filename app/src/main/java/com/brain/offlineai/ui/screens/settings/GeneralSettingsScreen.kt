package com.brain.offlineai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.ui.components.FooterBadges
import com.brain.offlineai.ui.theme.*

/**
 * Screen 12 from the mockup ("General Settings"). Replaces the Phase 1-5
 * Placeholder route (Rule 1 - route already existed and was reachable from
 * both bottom nav and drawer, now has real content). "Storage" navigates
 * to screen 13; "About" is its own existing drawer destination
 * (unchanged, see MainActivity/Screen.kt) so it isn't duplicated here.
 */
@Composable
fun GeneralSettingsScreen(
    viewModel: GeneralSettingsViewModel = viewModel(),
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenWebSearch: () -> Unit,
    // GitHub Hosting feature - additive, safe no-op default (same
    // Document-Editing Convention as every other additive param already
    // on this function) so this composable keeps compiling until
    // MainActivity wires it to a real navController.navigate(Screen.GitHubSettings...).
    onOpenGitHub: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Settings", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Every switch below has a real, immediate effect on the app - nothing here is decorative.",
            color = BrainTextMuted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))

        SettingsSection(title = "Appearance") {
            SwitchRow(
                label = "Dark Theme",
                description = "Switches every screen's background, card, and text colors right now.",
                checked = viewModel.darkThemeEnabled,
                onCheckedChange = { viewModel.setDarkTheme(it) }
            )
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                label = "Chat Animations",
                description = "Live waveform / typing / thinking-dot animations in Chat. Off shows a static equivalent instead.",
                checked = viewModel.animationsEnabled,
                onCheckedChange = { viewModel.setAnimationsEnabled(it) }
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = "Local API") {
            SwitchRow(
                label = "Auto-Start on Launch",
                description = "Starts the real Local API foreground service automatically the next time you open Brain.",
                checked = viewModel.autoStartLocalApi,
                onCheckedChange = { viewModel.setAutoStartLocalApi(it) }
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = "More") {
            NavRow(icon = Icons.Filled.Storage, label = "Storage", onClick = onOpenStorage)
            Spacer(Modifier.height(4.dp))
            NavRow(icon = Icons.Filled.Public, label = "Web Search", onClick = onOpenWebSearch)
            Spacer(Modifier.height(4.dp))
            NavRow(icon = Icons.Filled.Cloud, label = "GitHub Publishing", onClick = onOpenGitHub)
            Spacer(Modifier.height(4.dp))
            NavRow(icon = Icons.Filled.Info, label = "About", onClick = onOpenAbout)
        }

        Spacer(Modifier.height(24.dp))
        FooterBadges()
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = BrainTextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = BrainTextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = BrainPurplePrimary,
                uncheckedThumbColor = BrainTextMuted,
                uncheckedTrackColor = BrainBorder
            )
        )
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = BrainTextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = BrainTextMuted)
    }
}
