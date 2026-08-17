package com.brain.offlineai.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brain.offlineai.data.history.ChatSessionEntity
import com.brain.offlineai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Phase 7 - real History screen (bottom-nav "History" tab). Lists every
 * real, persisted chat session from [ChatHistoryRepository] (Room), newest
 * first. Tapping a row reopens that transcript in `ChatScreen` via
 * [onOpenSession]; nothing here is sample/seeded data - a fresh install
 * shows the real empty state below until a real conversation happens.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onOpenSession: (String) -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    var pendingDelete by remember { mutableStateOf<ChatSessionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrainBgPrimary)
            .padding(20.dp)
    ) {
        Text("History", color = BrainTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        if (sessions.isEmpty()) {
            EmptyHistoryState()
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDeleteClick = { pendingDelete = session }
                    )
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this conversation?") },
            text = { Text("\"${toDelete.title}\" and all its messages will be permanently removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(toDelete.id)
                    pendingDelete = null
                }) { Text("Delete", color = BrainDangerRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = BrainTextMuted, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text("No conversations yet", color = BrainTextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Chats you send from the Chat tab are saved here automatically.",
            color = BrainTextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SessionRow(session: ChatSessionEntity, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrainBgCard)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.title, color = BrainTextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.messageCount} message${if (session.messageCount != 1) "s" else ""} · ${formatRelative(session.updatedAt)}",
                color = BrainTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete conversation", tint = BrainTextSecondary)
        }
    }
}

private fun formatRelative(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - millis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min ago"
        days < 1 -> "$hours hour${if (hours != 1L) "s" else ""} ago"
        days < 7 -> "$days day${if (days != 1L) "s" else ""} ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}
