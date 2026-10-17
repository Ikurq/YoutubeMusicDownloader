package com.example.youtubemusicdownloder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.youtubemusicdownloder.MainActivity
import com.example.youtubemusicdownloder.R

class DownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle: String = "楽曲"
    private var currentProgress: Int = 0
    private var currentRemaining: Int = 0

    companion object {
        const val CHANNEL_ID = "music_download_channel_v1"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_OR_UPDATE = "action_start_or_update"
        const val ACTION_STOP = "action_stop"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_REMAINING_COUNT = "extra_remaining_count"

        fun startOrUpdate(
            context: Context,
            title: String,
            progress: Int,
            remainingCount: Int
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_REMAINING_COUNT, remainingCount)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "YoutubeMusicDownloader::DownloadWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // Safety timeout: 1 hour
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: currentTitle
        currentProgress = intent?.getIntExtra(EXTRA_PROGRESS, currentProgress) ?: currentProgress
        currentRemaining = intent?.getIntExtra(EXTRA_REMAINING_COUNT, currentRemaining) ?: currentRemaining

        val notification = buildNotification(currentTitle, currentProgress, currentRemaining)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, progress: Int, remaining: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (remaining > 1) {
            "キュー残り: ${remaining - 1}件 | 進捗: $progress%"
        } else {
            "進捗: $progress%"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ダウンロード",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "楽曲のバックグラウンドダウンロード進捗を表示します"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
