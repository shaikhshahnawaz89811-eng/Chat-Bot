package com.brain.offlineai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.theme.BrainBgPrimary
import com.brain.offlineai.ui.theme.BrainPurplePrimary
import com.brain.offlineai.ui.theme.BrainTextMuted
import com.brain.offlineai.ui.theme.BrainTextPrimary

/**
 * Used for every screen whose real UI/logic ships in a later phase
 * (Rule 1 - the nav route/endpoint exists now so nothing is orphaned,
 * but it honestly says what's pending instead of showing fake data).
 */
@Composable
fun PlaceholderScreen(title: String, arrivingInPhase: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Construction, contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Full functionality arrives in Phase $arrivingInPhase.\nThis route is wired and reachable now.",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
