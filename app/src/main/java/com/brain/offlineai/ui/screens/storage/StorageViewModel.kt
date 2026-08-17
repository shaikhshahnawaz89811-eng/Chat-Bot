package com.brain.offlineai.ui.screens.storage

import android.app.Application
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File

data class StorageBreakdown(
    val modelsBytes: Long = 0L,
    val apiKeysDbBytes: Long = 0L,
    val settingsAndAnalyticsBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val deviceTotalBytes: Long = 0L
)

/**
 * Screen 13 from the mockup ("Storage"). Every number here is a real,
 * freshly-computed file/filesystem size at the moment the screen is
 * opened - `File.length()` walks and a real `StatFs` call on the device's
 * internal storage volume - not a sample/placeholder figure (Rule 10).
 */
class StorageViewModel(application: Application) : AndroidViewModel(application) {

    var breakdown by mutableStateOf(StorageBreakdown())
        private set

    var lastRefreshedAtMillis by mutableStateOf(0L)
        private set

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()

        // Real, imported .gguf model files - same directory ModelFileManager
        // writes into (app/src/main/java/.../engine/ModelFileManager.kt).
        val modelsDir = File(context.filesDir, "models")
        val modelsBytes = dirSizeBytes(modelsDir)

        // Real SQLCipher-encrypted database file on disk (Phase 3).
        val dbFile = context.getDatabasePath("brain_api_keys.db")
        val apiKeysDbBytes = if (dbFile.exists()) dbFile.length() else 0L

        // Every plain-SharedPreferences file this app writes (Model
        // Settings, Analytics, App Settings, the engine's last-model
        // pointer) - summed as one real "Settings & Analytics" bucket
        // rather than fabricating a breakdown finer than a user would
        // reasonably act on.
        val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
        val settingsBytes = dirSizeBytes(prefsDir)

        val cacheBytes = dirSizeBytes(context.cacheDir)

        val statFs = StatFs(context.filesDir.path)
        val deviceFree = statFs.availableBytes
        val deviceTotal = statFs.totalBytes

        breakdown = StorageBreakdown(
            modelsBytes = modelsBytes,
            apiKeysDbBytes = apiKeysDbBytes,
            settingsAndAnalyticsBytes = settingsBytes,
            cacheBytes = cacheBytes,
            deviceFreeBytes = deviceFree,
            deviceTotalBytes = deviceTotal
        )
        lastRefreshedAtMillis = System.currentTimeMillis()
    }

    /**
     * Real cache clear - `Context.cacheDir` is documented Android storage
     * the system can already reclaim on its own at any time, so deleting
     * its contents here is safe/reversible (nothing the app depends on for
     * correctness is stored there) and is a genuine disk-space action, not
     * a fake "Cleared!" toast over a no-op.
     */
    fun clearCache() {
        val context = getApplication<Application>()
        context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        refresh()
    }

    private fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSizeBytes(it) } ?: 0L
    }
}
