package com.brain.offlineai.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.brain.offlineai.MainActivity

/**
 * Real foreground service - required for the local server to keep serving
 * requests while the app is backgrounded (mockup's "Works in Background"
 * highlight badge, and the "Background Operation" footer badge). This
 * class itself does no HTTP work; it delegates start/stop to
 * [LocalApiServerManager], which owns the actual [LocalApiServer] instance,
 * and its only other job is posting the ongoing notification Android
 * requires for a foreground service.
 */
class LocalApiForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LocalApiServerManager.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        LocalApiServerManager.start(applicationContext)
        if (!LocalApiServerManager.isRunning) {
            // Real bind failure (e.g. port already in use) - nothing for
            // this service to keep running for, so it honestly stops
            // instead of sitting in the foreground pretending to serve.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LocalApiServerManager.stop()
        super.onDestroy()
    }

    /**
     * Phase 7 background-service-stability fix: Android 14 (API 34) caps a
     * `dataSync` foreground service at ~6 hours of cumulative background
     * runtime per day and calls this real, platform-provided callback
     * (`Service.onTimeout(int, int)`, added in API 34 - not a fake/dummy
     * method) shortly before it force-stops the service itself. Without
     * this override the service would previously get killed by the system
     * with a `ForegroundServiceDidNotStopInTimeException` crash instead of
     * shutting down cleanly. Stopping the real server here (same call
     * `ACTION_STOP` already uses) means the Connection Status screen's
     * state genuinely reflects "Stopped" instead of looking Running while
     * the process is actually being torn down underneath it.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        LocalApiServerManager.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local API Server",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Brain Local API Server")
            .setContentText("Running at http://127.0.0.1:${LocalApiServerManager.PORT}/${LocalApiServerManager.API_VERSION}")
            // Stock platform icon for now - a dedicated notification vector
            // (white-on-transparent, per Android's status-bar icon rules)
            // is a small polish item, not functionality, so it's left for
            // a later phase rather than blocking Phase 4 on an asset.
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "local_api_server"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_STOP = "com.brain.offlineai.server.ACTION_STOP"

        fun startIntent(context: Context): Intent = Intent(context, LocalApiForegroundService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, LocalApiForegroundService::class.java).apply { action = ACTION_STOP }
    }
}
