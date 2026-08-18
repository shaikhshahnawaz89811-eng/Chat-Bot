package com.brain.offlineai.ui.screens.chat

import com.brain.offlineai.data.attachments.AttachmentInfo

/** Real state of one attachment between "picked" and "message actually sent". */
sealed class PendingAttachmentState {
    data class Copying(val bytesCopied: Long, val totalBytes: Long) : PendingAttachmentState()
    data class Ready(val info: AttachmentInfo) : PendingAttachmentState()
    data class Failed(val reason: String) : PendingAttachmentState()
}

/**
 * Phase 10 - one attachment the user has picked but not yet sent.
 * [localId] identifies it in the input bar's pending row before it has any
 * server/session-scoped identity; once the message is actually sent, the
 * matching [PendingAttachmentState.Ready.info] (whose own `id` == this
 * [localId]) is copied onto the real [ChatMessage.attachments] list.
 */
data class PendingAttachment(
    val localId: String,
    val fileName: String,
    val mimeType: String?,
    val state: PendingAttachmentState
)
