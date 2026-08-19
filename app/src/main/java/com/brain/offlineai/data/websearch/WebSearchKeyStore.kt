package com.brain.offlineai.data.websearch

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Phase 22 (Master Plan v2, revised scope - see PROGRESS.md's own "User
 * correction" note: the real user-supplied API key this app needs is a
 * web-search key, not a second AI model). Real, secure on-device storage
 * for the user's own Tavily API key - same real tech Phase 3's
 * [com.brain.offlineai.data.apikeys.DatabaseKeyProvider] already uses for
 * a real secret (EncryptedSharedPreferences, Android Keystore-backed
 * AES-256-GCM/SIV), reused here rather than reinvented (Rule 4). This is
 * deliberately its own, separate EncryptedSharedPreferences file
 * ("brain_websearch_prefs") rather than a new key inside
 * DatabaseKeyProvider's own "brain_secure_prefs" file - that file's one
 * real job is holding the SQLCipher passphrase; adding an unrelated
 * secret to it would blur a single-owner boundary this project's own Rule
 * 3 (one real action, one owner) already established elsewhere (e.g.
 * Storage's Clear Cache deliberately not duplicating Models' Delete).
 *
 * The key itself is never logged, never included in any [ProcessStep]
 * label, and never sent anywhere except the real `Authorization: Bearer
 * <key>` header of a real HTTPS call to `https://api.tavily.com/search`
 * (see [TavilySearchClient]) - the same "only what a real call needs"
 * minimal-payload standard this project already holds itself to (Rule 20).
 */
class WebSearchKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "brain_websearch_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getKey(): String? = prefs.getString(KEY_TAVILY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun hasKey(): Boolean = getKey() != null

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_TAVILY_API_KEY, key.trim()).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY_TAVILY_API_KEY).apply()
    }

    companion object {
        private const val KEY_TAVILY_API_KEY = "tavily_api_key"
    }
}
