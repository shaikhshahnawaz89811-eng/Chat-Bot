package com.brain.offlineai.engine

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class MemorySnapshot(val usedBytes: Long, val totalBytes: Long) {
    val usedGb: Float get() = usedBytes / 1_073_741_824f
    val totalGb: Float get() = totalBytes / 1_073_741_824f
    val fraction: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Polls the real system memory state via ActivityManager.getMemoryInfo() -
 * the same API Android's own Settings > Memory screen is built on. This
 * replaces the Phase 1 AiEngineStatusCard's hardcoded "1.24 GB / 4.00 GB"
 * placeholder text with genuine device numbers.
 */
class DeviceMemoryMonitor(private val context: Context) {

    fun snapshots(intervalMs: Long = 3000L): Flow<MemorySnapshot> = flow {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            val used = info.totalMem - info.availMem
            emit(MemorySnapshot(usedBytes = used, totalBytes = info.totalMem))
            delay(intervalMs)
        }
    }
}
