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

class MusicPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_TOGGLE_PLAY = "action_toggle_play"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        var serviceCallback: ((action: String) -> Unit)? = null

        fun startService(context: Context, title: String, artist: String, isPlaying: Boolean) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YoutubeMusicDownloader::PlaybackWakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 min fallback
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            serviceCallback?.invoke(action)
            if (action == ACTION_STOP) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "再生中の楽曲"
        val artist = intent?.getStringExtra(EXTRA_ARTIST) ?: "YouTube Music"
        val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true

        val notification = buildNotification(title, artist, isPlaying)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextAction = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAction = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "前へ", prevAction)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "一時停止" else "再生",
                playPauseAction
            )
            .addAction(android.R.drawable.ic_media_next, "次へ", nextAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopAction)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "楽曲再生コントロール",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "バックグラウンドでの音楽再生コントロール通知"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
