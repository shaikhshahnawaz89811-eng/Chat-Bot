package com.brain.offlineai.data.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Real, persisted usage counters - the data layer behind the Analytics
 * screen (mockup's Analytics destination, Phase 5). Every number here is
 * incremented from an actual real event (a message genuinely sent, tokens
 * a real llama.cpp decode loop actually produced, a real authenticated
 * Local API request) - there is no seeded/sample data and no fabricated
 * starting values. An app that has never been used shows all zeros.
 *
 * Counters survive process death (plain SharedPreferences, same tier as
 * ModelFileManager/ModelSettingsRepository - not the encrypted store,
 * since usage counts aren't secrets, Rule 20).
 */
class AnalyticsStore(context: Context) {

    private val prefs = context.getSharedPreferences("brain_analytics", Context.MODE_PRIVATE)

    /** Set once, on the very first real read - a genuine "since when" anchor, not a placeholder date. */
    val firstLaunchAtMillis: Long
        get() {
            val existing = prefs.getLong(KEY_FIRST_LAUNCH, -1L)
            if (existing != -1L) return existing
            val now = System.currentTimeMillis()
            prefs.edit { putLong(KEY_FIRST_LAUNCH, now) }
            return now
        }

    val totalMessagesSent: Int
        get() = prefs.getInt(KEY_MESSAGES_SENT, 0)

    val totalTokensGenerated: Long
        get() = prefs.getLong(KEY_TOKENS_GENERATED, 0L)

    val totalLocalApiRequests: Long
        get() = prefs.getLong(KEY_LOCAL_API_REQUESTS, 0L)

    /** Called once per real user message send (ChatViewModel.sendMessage()). */
    fun incrementMessagesSent() {
        prefs.edit { putInt(KEY_MESSAGES_SENT, totalMessagesSent + 1) }
    }

    /**
     * Called once per completed generation with the real final token count
     * (not per-token, to avoid a disk write per streamed token - Rule 20).
     */
    fun addTokensGenerated(count: Long) {
        if (count <= 0) return
        prefs.edit { putLong(KEY_TOKENS_GENERATED, totalTokensGenerated + count) }
    }

    /** Called once per real authenticated Local API request (LocalApiServerManager). */
    fun incrementLocalApiRequests() {
        prefs.edit { putLong(KEY_LOCAL_API_REQUESTS, totalLocalApiRequests + 1) }
    }

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch_at"
        private const val KEY_MESSAGES_SENT = "total_messages_sent"
        private const val KEY_TOKENS_GENERATED = "total_tokens_generated"
        private const val KEY_LOCAL_API_REQUESTS = "total_local_api_requests"
    }
}
