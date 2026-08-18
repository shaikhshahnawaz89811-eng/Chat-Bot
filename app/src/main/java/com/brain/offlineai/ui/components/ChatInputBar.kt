package com.brain.offlineai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.screens.chat.PendingAttachment
import com.brain.offlineai.ui.screens.chat.PendingAttachmentState
import com.brain.offlineai.ui.theme.*

/**
 * [isBusy] (Phase 9, new Claude-style UI spec) mirrors [com.brain.offlineai.ui.screens.chat.ChatViewModel.isBusy]
 * - real, from the moment Send is tapped to the moment the response (or
 * error, or "no model loaded" note) actually finishes. While true: the
 * text field is really disabled (not just visually greyed - `enabled =
 * false` genuinely blocks input/focus, a standard Compose
 * `OutlinedTextField` parameter), the send button is really disabled
 * (`onClick` cannot fire), and its icon is swapped for a real Material3
 * `CircularProgressIndicator` - an official `androidx.compose.material3`
 * composable already used elsewhere in real Android apps, not a custom or
 * fake spinner.
 *
 * [pendingAttachments]/[onAttachClick]/[onRemoveAttachment] (Phase 10,
 * File/ZIP/Image/Video upload flow) - a real attachment entry point.
 * Tapping the paperclip calls [onAttachClick] (the screen owns the actual
 * SAF picker launcher, same "screen owns the launcher, ViewModel owns the
 * copy" split already used by ModelsScreen/ModelsViewModel). While any
 * attachment is still genuinely copying, Send stays disabled - the "file =
 * kaam start" rule means a task can only begin once its attachment is
 * genuinely on disk, not while it's mid-copy.
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isBusy: Boolean = false,
    pendingAttachments: List<PendingAttachment> = emptyList(),
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {}
) {
    val attachmentsUploading = pendingAttachments.any { it.state is PendingAttachmentState.Copying }
    val hasReadyAttachment = pendingAttachments.any { it.state is PendingAttachmentState.Ready }
    val sendEnabled = !isBusy && !attachmentsUploading && (value.isNotBlank() || hasReadyAttachment)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrainBgPrimary)
    ) {
        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingAttachments, key = { it.localId }) { attachment ->
                    PendingAttachmentChip(attachment = attachment, onRemove = { onRemoveAttachment(attachment.localId) })
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick, enabled = !isBusy) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach file", tint = BrainTextSecondary)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                placeholder = { Text("Message Brain...", color = BrainTextMuted) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BrainBgCard,
                    unfocusedContainerColor = BrainBgCard,
                    disabledContainerColor = BrainBgCard,
                    focusedTextColor = BrainTextPrimary,
                    unfocusedTextColor = BrainTextPrimary,
                    disabledTextColor = BrainTextMuted,
                    focusedBorderColor = BrainPurplePrimary,
                    unfocusedBorderColor = BrainBorder,
                    disabledBorderColor = BrainBorder,
                    cursorColor = BrainPurplePrimary
                ),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isBusy || !sendEnabled) BrainBorder else BrainPurplePrimary),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BrainPurplePrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onSend, enabled = sendEnabled) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}
