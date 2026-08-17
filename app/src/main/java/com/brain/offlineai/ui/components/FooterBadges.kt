package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.theme.BrainBgCardAlt
import com.brain.offlineai.ui.theme.BrainCyanAccent
import com.brain.offlineai.ui.theme.BrainPinkAccent
import com.brain.offlineai.ui.theme.BrainSuccessGreen
import com.brain.offlineai.ui.theme.BrainTextSecondary

private data class Badge(val icon: ImageVector, val label: String, val tint: Color)

// The mockup's footer highlight badge row (Phase map entry: "Footer
// highlight badges (Offline/Secure/etc.)", Phase 6). Every badge restates
// a capability that is already real and load-bearing elsewhere in this
// codebase, not a new claim invented for this row:
//  - "100% Offline" mirrors network_security_config.xml's loopback-only
//    cleartext rule and the drawer header's existing "100% Offline" text.
//  - "Encrypted Storage" mirrors the real SQLCipher-encrypted API key
//    database (Phase 3, ApiKeyDatabase.kt).
//  - "Local API Secure" mirrors the real per-request Bearer key check
//    (Phase 4, LocalApiServer.kt).
//  - "Runs in Background" mirrors the real foreground Service (Phase 4,
//    LocalApiForegroundService.kt).
private val footerBadges = listOf(
    Badge(Icons.Filled.CloudOff, "100% Offline", BrainSuccessGreen),
    Badge(Icons.Filled.Lock, "Encrypted Storage", BrainCyanAccent),
    Badge(Icons.Filled.VerifiedUser, "Local API Secure", BrainPinkAccent),
    Badge(Icons.Filled.Sync, "Runs in Background", BrainSuccessGreen)
)

/**
 * Renders as two rows of two - a fixed, known set of four badges, so a
 * plain wrap avoids pulling in an experimental FlowRow API for one
 * component (Rule 20 - no unrelated API surface for a single use site).
 */
@Composable
fun FooterBadges(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeChip(footerBadges[0])
            BadgeChip(footerBadges[1])
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeChip(footerBadges[2])
            BadgeChip(footerBadges[3])
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BrainBgCardAlt)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(badge.icon, contentDescription = null, tint = badge.tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(badge.label, color = BrainTextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
