package com.brain.offlineai.data.apikeys

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Generates and stores the passphrase that encrypts the api_keys SQLCipher
 * database. The passphrase is a real 256-bit SecureRandom value, generated
 * once on first run and held in EncryptedSharedPreferences (Android
 * Keystore-backed AES-256-GCM/SIV) - not a hardcoded string, and not sitting
 * in plain SharedPreferences next to the database it protects. Losing this
 * value (e.g. app data wiped) makes the encrypted DB unrecoverable by
 * design - that's the real tradeoff of on-device encryption, not hidden.
 */
class DatabaseKeyProvider(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "brain_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrCreatePassphrase(): CharArray {
        prefs.getString(KEY_PASSPHRASE, null)?.let { return it.toCharArray() }

        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val hex = randomBytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        prefs.edit().putString(KEY_PASSPHRASE, hex).commit()
        return hex.toCharArray()
    }

    companion object {
        private const val KEY_PASSPHRASE = "api_key_db_passphrase"
    }
}
