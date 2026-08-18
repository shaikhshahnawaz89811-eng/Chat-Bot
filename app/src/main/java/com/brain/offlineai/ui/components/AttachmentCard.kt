package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.brain.offlineai.data.attachments.AttachmentInfo
import com.brain.offlineai.data.attachments.AttachmentKind
import com.brain.offlineai.ui.multimodal.UseCaseRole
import com.brain.offlineai.ui.screens.chat.PendingAttachment
import com.brain.offlineai.ui.screens.chat.PendingAttachmentState
import com.brain.offlineai.ui.theme.*

private fun iconFor(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.IMAGE -> Icons.Filled.Image
    AttachmentKind.VIDEO -> Icons.Filled.Movie
    AttachmentKind.ZIP -> Icons.Filled.FolderZip
    AttachmentKind.FILE -> Icons.Filled.InsertDriveFile
}

/** Real human-readable size - same divide-by-1_048_576 convention ModelsScreen/StorageScreen already use. */
fun formatAttachmentSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1024} KB"
    else -> "${bytes / 1_048_576} MB"
}

/**
 * Phase 10 - one chip in [com.brain.offlineai.ui.components.ChatInputBar]'s
 * pending-attachments row, shown between picking a file and actually
 * sending the message. Real per-attachment copy progress while
 * [PendingAttachmentState.Copying] (the same real byte-counted progress
 * [com.brain.offlineai.data.attachments.AttachmentFileManager] emits, not
 * a fake bar) - remove is disabled mid-copy the same way ModelsScreen
 * disables Replace/Delete while `loading` is true, since removing here
 * cancels the real in-flight copy coroutine (see `ChatViewModel.onRemoveAttachment`).
 */
@Composable
fun PendingAttachmentChip(attachment: PendingAttachment, onRemove: () -> Unit) {
    val copying = attachment.state is PendingAttachmentState.Copying
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrainBgCardAlt)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .widthIn(max = 180.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (val state = attachment.state) {
            is PendingAttachmentState.Copying -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BrainPurplePrimary)
            }
            is PendingAttachmentState.Ready -> {
                Icon(iconFor(state.info.kind), contentDescription = null, tint = BrainPurplePrimary, modifier = Modifier.size(16.dp))
            }
            is PendingAttachmentState.Failed -> {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = BrainDangerRed, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                attachment.fileName,
                color = BrainTextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            val subtitle = when (val state = attachment.state) {
                is PendingAttachmentState.Copying ->
                    if (state.totalBytes > 0) {
                        "${(state.bytesCopied * 100 / state.totalBytes)}%"
                    } else {
                        "${formatAttachmentSize(state.bytesCopied)}"
                    }
                is PendingAttachmentState.Ready -> formatAttachmentSize(state.info.sizeBytes)
                is PendingAttachmentState.Failed -> "Failed"
            }
            Text(subtitle, color = if (attachment.state is PendingAttachmentState.Failed) BrainDangerRed else BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onRemove, enabled = !copying, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove attachment", tint = BrainTextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Phase 10 - a real, already-sent attachment rendered inside a chat
 * bubble ([com.brain.offlineai.ui.components.UserBubble]). Every field
 * here comes straight from the real [AttachmentInfo] that was actually
 * copied and persisted with the message - no placeholder thumbnail/name.
 *
 * Phase 14 (Multimodal input use-case routing) - [role], when non-null, is
 * the real, already-decided [UseCaseRole] this attachment was routed to
 * (see [com.brain.offlineai.ui.multimodal.classifyAttachmentRole]),
 * rendered as a small real chip so the routing decision is genuinely
 * visible on the sent message itself, not hidden. Null (chip omitted) for
 * every message from every earlier phase, whose [ChatMessage.attachmentRoutes]
 * is empty (Document-Editing Convention - additive parameter, default null
 * keeps every existing call site compiling and rendering identically).
 */
@Composable
fun SentAttachmentCard(attachment: AttachmentInfo, role: UseCaseRole? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrainBgCardAlt)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(iconFor(attachment.kind), contentDescription = null, tint = BrainCyanAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(attachment.fileName, color = BrainTextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(formatAttachmentSize(attachment.sizeBytes), color = BrainTextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (role != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrainPurplePrimary.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(role.label, color = BrainPurplePrimary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
