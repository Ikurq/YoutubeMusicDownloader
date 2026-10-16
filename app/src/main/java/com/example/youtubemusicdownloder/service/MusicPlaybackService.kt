package com.example.youtubemusicdownloder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.youtubemusicdownloder.MainActivity
import com.example.youtubemusicdownloder.R

class MusicPlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "music_playback_channel_media"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_TOGGLE_PLAY = "action_toggle_play"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ALBUM = "extra_album"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"

        var serviceCallback: ((action: String) -> Unit)? = null
        var seekCallback: ((positionMs: Long) -> Unit)? = null

        fun startService(
            context: Context,
            title: String,
            artist: String,
            album: String = "",
            isPlaying: Boolean,
            positionMs: Long = 0L,
            durationMs: Long = 0L,
            artworkUrl: String = ""
        ) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ALBUM, album)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION, positionMs)
                putExtra(EXTRA_DURATION, durationMs)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
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
            acquire(10 * 60 * 1000L)
        }

        // Native MediaSession for System Media Controls Panel & Lockscreen Player
        mediaSession = MediaSession(this, "MusicPlaybackService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    serviceCallback?.invoke(ACTION_TOGGLE_PLAY)
                }

                override fun onPause() {
                    serviceCallback?.invoke(ACTION_TOGGLE_PLAY)
                }

                override fun onSkipToNext() {
                    serviceCallback?.invoke(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    serviceCallback?.invoke(ACTION_PREVIOUS)
                }

                override fun onSeekTo(pos: Long) {
                    seekCallback?.invoke(pos)
                }

                override fun onStop() {
                    serviceCallback?.invoke(ACTION_STOP)
                }
            })
            isActive = true
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
        val album = intent?.getStringExtra(EXTRA_ALBUM) ?: "Single"
        val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true
        val positionMs = intent?.getLongExtra(EXTRA_POSITION, 0L) ?: 0L
        val durationMs = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L

        updateMediaSession(title, artist, album, isPlaying, positionMs, durationMs)

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

    private fun updateMediaSession(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.coerceAtLeast(1L))

        mediaSession?.setMetadata(metadataBuilder.build())

        val stateInt = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .setState(stateInt, positionMs, 1.0f)
            .build()

        mediaSession?.setPlaybackState(playbackState)
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

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken?.let { android.support.v4.media.session.MediaSessionCompat.Token.fromToken(it as android.media.session.MediaSession.Token) })
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "前へ", prevAction)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "一時停止" else "再生",
                playPauseAction
            )
            .addAction(android.R.drawable.ic_media_next, "次へ", nextAction)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "システム メディア コントロール",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "通知パネル上部およびロック画面のメディアプレイヤーコントロール"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
