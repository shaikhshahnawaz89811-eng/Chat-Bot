package com.brain.offlineai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brain.offlineai.ui.screens.chat.PendingAttachment
import com.brain.offlineai.ui.screens.chat.PendingAttachmentState
import com.brain.offlineai.ui.theme.*

/**
 * UI-only redesign.
 *
 * Existing attachment picker/callbacks and send/busy state are untouched.
 * The visual layout follows the supplied reference: one glowing composer,
 * attachment chips inside the same box, and no new/fake functionality.
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

    val transition = rememberInfiniteTransition(label = "composer-glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "composer-glow-alpha"
    )

    val glowColor = BrainPurplePrimary.copy(alpha = glowAlpha)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrainBgPrimary)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // One composer box — attachments are visually part of the same box.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(BrainBgCard)
                .border(
                    width = 1.dp,
                    color = glowColor,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttachClick,
                enabled = !isBusy,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add attachment",
                    tint = BrainTextSecondary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                if (pendingAttachments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            pendingAttachments,
                            key = { it.localId }
                        ) { attachment ->
                            PendingAttachmentChip(
                                attachment = attachment,
                                onRemove = { onRemoveAttachment(attachment.localId) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp, max = 132.dp),
                    placeholder = {
                        Text(
                            "Message Chat Bot...",
                            color = BrainTextMuted
                        )
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedTextColor = BrainTextPrimary,
                        unfocusedTextColor = BrainTextPrimary,
                        disabledTextColor = BrainTextMuted,
                        cursorColor = BrainPurplePrimary
                    ),
                    minLines = 1,
                    maxLines = 6
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (sendEnabled) BrainPurplePrimary
                        else BrainBorder
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BrainPurplePrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = sendEnabled,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        )
    }
}
