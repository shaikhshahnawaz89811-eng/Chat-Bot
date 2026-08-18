package com.brain.offlineai.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.brain.offlineai.R

class ChatTaskForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Chat Bot")
            .setContentText("Working in the background…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Chat Bot background work",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "chat_bot_background_work"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_START = "com.brain.offlineai.tasks.START_CHAT_WORK"
        private const val ACTION_STOP = "com.brain.offlineai.tasks.STOP_CHAT_WORK"

        fun start(context: Context) {
            val intent = Intent(context, ChatTaskForegroundService::class.java)
                .setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ChatTaskForegroundService::class.java)
                    .setAction(ACTION_STOP)
            )
        }
    }
}
