package com.brain.offlineai.ui.screens.apikeys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.brain.offlineai.data.apikeys.KeyStatus
import com.brain.offlineai.ui.theme.BrainDangerRed
import com.brain.offlineai.ui.theme.BrainSuccessGreen
import com.brain.offlineai.ui.theme.BrainWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

internal fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

internal fun formatRelative(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - millis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min ago"
        days < 1 -> "$hours hour${if (hours != 1L) "s" else ""} ago"
        else -> "$days day${if (days != 1L) "s" else ""} ago"
    }
}

internal fun maskKey(key: String): String = "•".repeat(key.length.coerceAtMost(24))

internal fun truncateKey(key: String): String =
    if (key.length <= 20) key else "${key.take(12)}...${key.takeLast(4)}"

/** Real clipboard write via ClipboardManager - not a UI-only "success" state. */
internal fun copyKeyToClipboard(context: Context, keyValue: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Brain API Key", keyValue))
}

@Composable
internal fun StatusBadge(status: KeyStatus) {
    val (label, color) = when (status) {
        KeyStatus.ACTIVE -> "Active" to BrainSuccessGreen
        KeyStatus.EXPIRED -> "Expired" to BrainWarningAmber
        KeyStatus.REVOKED -> "Revoked" to BrainDangerRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
