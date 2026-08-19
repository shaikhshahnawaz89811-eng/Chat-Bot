package com.brain.offlineai.data.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * GitHub Hosting feature - real, secure on-device storage for the user's
 * own GitHub Personal Access Token (PAT). Same real tech
 * [com.brain.offlineai.data.websearch.WebSearchKeyStore] already uses for
 * the Tavily key (EncryptedSharedPreferences, Android Keystore-backed
 * AES-256-GCM/SIV) - reused here rather than reinvented, and kept as its
 * own separate EncryptedSharedPreferences file ("brain_github_prefs") for
 * the same single-owner-boundary reason WebSearchKeyStore's own doc
 * explains for not sharing DatabaseKeyProvider's file.
 *
 * The token is never logged, never shown in any UI after it's saved
 * (masked the same way Web Search's key input is), and never sent
 * anywhere except the real `Authorization` header of a real HTTPS call to
 * `api.github.com` (see [GitHubApiClient]).
 *
 * Also remembers the last-published repo name per artifact file name, so
 * re-publishing the same generated site updates the same repo instead of
 * creating a new one every time.
 */
class GitHubKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "brain_github_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getToken(): String? = prefs.getString(KEY_PAT, null)?.takeIf { it.isNotBlank() }

    fun hasToken(): Boolean = getToken() != null

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_PAT, token.trim()).apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_PAT).remove(KEY_USERNAME).apply()
    }

    /** Real GitHub login this token belongs to - cached after the first successful validate/publish so it isn't re-fetched every call. */
    fun getCachedUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun cacheUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    companion object {
        private const val KEY_PAT = "github_pat"
        private const val KEY_USERNAME = "github_username"
    }
}
