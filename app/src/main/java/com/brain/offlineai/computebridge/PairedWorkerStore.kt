package com.brain.offlineai.computebridge

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists every paired Compute Bridge worker as a real JSON array, not a
 * single slot - the architecture doc's "Multiple Worker Future" section
 * (and the user's own instruction not to hardcode one device) means the
 * Main APK's Pairing Manager has to hold a list from day one. Each
 * worker's access token is a real bearer credential for that worker's
 * `/v1/` API endpoints, so this uses the same EncryptedSharedPreferences approach
 * this app's WebSearchKeyStore already uses for its own outbound API
 * secret - no new storage pattern introduced.
 */
class PairedWorkerStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "compute_bridge_workers",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun list(): List<PairedWorker> {
        val raw = prefs.getString(KEY_WORKERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PairedWorker(
                    workerId = o.getString("workerId"),
                    host = o.getString("host"),
                    port = o.getInt("port"),
                    token = o.getString("token"),
                    label = o.optString("label", o.getString("workerId")),
                    enabled = o.optBoolean("enabled", true),
                    priority = o.optInt("priority", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Adds [worker], or replaces the existing entry with the same
     * [PairedWorker.workerId] (re-pairing after the worker's LAN address
     * changed) - every other paired worker already in the list is left
     * untouched, so this is always additive for a genuinely new device. */
    fun upsert(worker: PairedWorker) {
        val current = list().filterNot { it.workerId == worker.workerId }
        save(current + worker)
    }

    fun remove(workerId: String) {
        save(list().filterNot { it.workerId == workerId })
    }

    fun setEnabled(workerId: String, enabled: Boolean) {
        save(list().map { if (it.workerId == workerId) it.copy(enabled = enabled) else it })
    }

    fun setPriority(workerId: String, priority: Int) {
        save(list().map { if (it.workerId == workerId) it.copy(priority = priority) else it })
    }

    private fun save(workers: List<PairedWorker>) {
        val arr = JSONArray()
        workers.forEach { w ->
            arr.put(
                JSONObject()
                    .put("workerId", w.workerId)
                    .put("host", w.host)
                    .put("port", w.port)
                    .put("token", w.token)
                    .put("label", w.label)
                    .put("enabled", w.enabled)
                    .put("priority", w.priority)
            )
        }
        prefs.edit().putString(KEY_WORKERS, arr.toString()).apply()
    }

    /** Defaults to LOCAL, so an install that has never opened the Compute
     * Bridge screen behaves exactly as before this feature existed. */
    var mode: ComputeMode
        get() = runCatching {
            ComputeMode.valueOf(prefs.getString(KEY_MODE, ComputeMode.LOCAL.name) ?: ComputeMode.LOCAL.name)
        }.getOrDefault(ComputeMode.LOCAL)
        set(value) { prefs.edit().putString(KEY_MODE, value.name).apply() }

    companion object {
        private const val KEY_WORKERS = "workers_json"
        private const val KEY_MODE = "compute_mode"
    }
}
