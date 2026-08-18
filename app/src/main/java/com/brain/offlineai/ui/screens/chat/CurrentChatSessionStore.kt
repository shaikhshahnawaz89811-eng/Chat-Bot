package com.brain.offlineai.ui.screens.chat

import android.content.Context

object CurrentChatSessionStore {
    private const val PREFS = "chat_bot_current_session"
    private const val KEY_SESSION_ID = "session_id"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_ID, null)

    fun set(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_ID)
            .apply()
    }
}
