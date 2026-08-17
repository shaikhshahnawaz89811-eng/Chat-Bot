package com.brain.offlineai.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 7: one row per real chat session. This is the gap the Rule 1/16
 * audit found - the "History" bottom-nav destination (mockup bottom bar,
 * wired since Phase 1) still showed `PlaceholderScreen("History",
 * arrivingInPhase = 2)`, but no phase 2-6 ever actually claimed History in
 * PROGRESS.md's own phase map (it isn't one of the 14 mockup screens,
 * only a bottom-nav slot) - so that placeholder text had gone stale/false
 * (it named a phase that shipped without touching History). Phase 7 fixes
 * that honestly by giving History real content instead of just editing
 * the placeholder's wording.
 *
 * `title` is derived once from the first real user message (truncated),
 * same "computed from real data, not user-typed" spirit as
 * `ApiKeyEntity.statusAt()` being computed rather than stored-and-stale.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

/** One real chat bubble, persisted so a session survives process death and can be reopened from History. */
@Entity(tableName = "chat_history_messages")
data class ChatHistoryMessageEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId: String,
    val messageId: Long,
    val text: String,
    val isUser: Boolean,
    val timestampMillis: Long
)
