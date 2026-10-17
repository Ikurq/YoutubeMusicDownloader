package com.example.youtubemusicdownloder.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.example.youtubemusicdownloder.model.RepeatMode
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.service.MusicPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.random.Random

class MusicPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.NONE)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    // 1. Playback Speed (0.5x ~ 2.0x)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // 2. Sleep Timer (0 = OFF, -1 = End of track, >0 = minutes)
    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    private var currentPlaylist: List<Track> = emptyList()

    // Audio Focus management
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = _isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower volume while ducking (e.g. notification / navigation voice)
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    start()
                }
            }
        }
    }

    // Becoming Noisy BroadcastReceiver (Unplugging headphones / disconnecting Bluetooth)
    private var isNoisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                // Headphones unplugged, pause music to prevent accidental loud speaker playback
                if (_isPlaying.value) {
                    pause()
                }
            }
        }
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    _currentPositionMs.value = player.currentPosition.toLong()
                    _durationMs.value = player.duration.toLong().coerceAtLeast(1L)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    // Sleep Timer countdown runnable
    private val sleepTimerRunnable = object : Runnable {
        override fun run() {
            val remaining = _sleepTimerRemainingSeconds.value
            if (remaining > 1) {
                _sleepTimerRemainingSeconds.value = remaining - 1
                handler.postDelayed(this, 1000)
            } else if (remaining == 1) {
                _sleepTimerRemainingSeconds.value = 0
                _sleepTimerMinutes.value = 0
                releasePlayer(stopService = true)
            }
        }
    }

    init {
        MusicPlaybackService.serviceCallback = { action ->
            when (action) {
                MusicPlaybackService.ACTION_TOGGLE_PLAY -> togglePlayPause()
                MusicPlaybackService.ACTION_NEXT -> nextTrack()
                MusicPlaybackService.ACTION_PREVIOUS -> previousTrack()
                MusicPlaybackService.ACTION_STOP -> releasePlayer(stopService = true)
            }
        }
        MusicPlaybackService.seekCallback = { posMs ->
            seekTo(posMs)
        }
    }

    fun setPlaylist(playlist: List<Track>) {
        currentPlaylist = playlist
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.registerReceiver(noisyReceiver, filter)
                isNoisyReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener, handler)
                .build()

            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun playTrack(track: Track, playlist: List<Track>? = null) {
        if (playlist != null) {
            currentPlaylist = playlist
        }

        _currentTrack.value = track
        releasePlayer(stopService = false)

        val localFile = track.localFilePath?.let { File(it) }
        if (localFile == null || !localFile.exists() || localFile.length() == 0L) {
            _isPlaying.value = false
            return
        }

        try {
            val player = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(localFile.absolutePath)
                prepareAsync()
            }

            player.setOnPreparedListener { mp ->
                if (requestAudioFocus()) {
                    applyPlaybackSpeedToPlayer(mp, _playbackSpeed.value)
                    mp.start()
                    _isPlaying.value = true
                    registerNoisyReceiver()
                } else {
                    _isPlaying.value = false
                }
                _durationMs.value = mp.duration.toLong().coerceAtLeast(1L)
                handler.post(progressRunnable)

                MusicPlaybackService.startService(
                    context = context,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    isPlaying = _isPlaying.value,
                    positionMs = 0L,
                    durationMs = _durationMs.value,
                    artworkUrl = track.thumbnailUrl
                )
            }

            player.setOnCompletionListener {
                onTrackCompleted()
            }

            player.setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                unregisterNoisyReceiver()
                true
            }

            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun updateCurrentTrackMetadata(track: Track) {
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track
            currentPlaylist = currentPlaylist.map { if (it.id == track.id) track else it }

            MusicPlaybackService.startService(
                context = context,
                title = track.title,
                artist = track.artist,
                album = track.album,
                isPlaying = _isPlaying.value,
                positionMs = _currentPositionMs.value,
                durationMs = _durationMs.value,
                artworkUrl = track.thumbnailUrl
            )
        }
    }

    private fun applyPlaybackSpeedToPlayer(player: MediaPlayer, speed: Float) {
        try {
            val params = player.playbackParams ?: PlaybackParams()
            params.speed = speed
            player.playbackParams = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                applyPlaybackSpeedToPlayer(player, speed)
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepTimerRunnable)
        _sleepTimerMinutes.value = minutes

        if (minutes > 0) {
            _sleepTimerRemainingSeconds.value = minutes * 60
            handler.post(sleepTimerRunnable)
        } else {
            _sleepTimerRemainingSeconds.value = 0
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(0)
    }

    private fun start() {
        val player = mediaPlayer ?: run {
            _currentTrack.value?.let { playTrack(it) }
            return
        }
        val track = _currentTrack.value ?: return

        if (requestAudioFocus()) {
            applyPlaybackSpeedToPlayer(player, _playbackSpeed.value)
            player.start()
            _isPlaying.value = true
            registerNoisyReceiver()
            handler.post(progressRunnable)
            MusicPlaybackService.startService(
                context = context,
                title = track.title,
                artist = track.artist,
                album = track.album,
                isPlaying = true,
                positionMs = _currentPositionMs.value,
                durationMs = _durationMs.value,
                artworkUrl = track.thumbnailUrl
            )
        }
    }

    private fun pause() {
        val player = mediaPlayer ?: return
        val track = _currentTrack.value ?: return

        player.pause()
        _isPlaying.value = false
        unregisterNoisyReceiver()
        handler.removeCallbacks(progressRunnable)
        MusicPlaybackService.startService(
            context = context,
            title = track.title,
            artist = track.artist,
            album = track.album,
            isPlaying = false,
            positionMs = _currentPositionMs.value,
            durationMs = _durationMs.value,
            artworkUrl = track.thumbnailUrl
        )
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            start()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
            _currentTrack.value?.let { track ->
                MusicPlaybackService.startService(
                    context = context,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    isPlaying = _isPlaying.value,
                    positionMs = positionMs,
                    durationMs = _durationMs.value,
                    artworkUrl = track.thumbnailUrl
                )
            }
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun nextTrack() {
        val tracks = currentPlaylist.ifEmpty {
            _currentTrack.value?.let { listOf(it) } ?: emptyList()
        }
        if (tracks.isEmpty()) return

        val current = _currentTrack.value

        if (_isShuffle.value && tracks.size > 1) {
            var randomIndex = Random.nextInt(tracks.size)
            if (current != null && tracks[randomIndex].id == current.id) {
                randomIndex = (randomIndex + 1) % tracks.size
            }
            playTrack(tracks[randomIndex])
            return
        }

        val currentIndex = tracks.indexOfFirst { it.id == current?.id }
        if (currentIndex != -1 && currentIndex < tracks.size - 1) {
            playTrack(tracks[currentIndex + 1])
        } else if (tracks.isNotEmpty()) {
            playTrack(tracks[0])
        }
    }

    fun previousTrack() {
        val tracks = currentPlaylist.ifEmpty {
            _currentTrack.value?.let { listOf(it) } ?: emptyList()
        }
        if (tracks.isEmpty()) return

        val current = _currentTrack.value
        val currentIndex = tracks.indexOfFirst { it.id == current?.id }

        if (currentIndex > 0) {
            playTrack(tracks[currentIndex - 1])
        } else if (tracks.isNotEmpty()) {
            playTrack(tracks.last())
        }
    }

    private fun onTrackCompleted() {
        handler.removeCallbacks(progressRunnable)

        // Check if Sleep Timer is set to "End of track" (-1)
        if (_sleepTimerMinutes.value == -1) {
            _sleepTimerMinutes.value = 0
            _sleepTimerRemainingSeconds.value = 0
            releasePlayer(stopService = true)
            return
        }

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentTrack.value?.let { playTrack(it) }
            }
            RepeatMode.ALL -> {
                nextTrack()
            }
            RepeatMode.NONE -> {
                val tracks = currentPlaylist
                val current = _currentTrack.value
                val currentIndex = tracks.indexOfFirst { it.id == current?.id }

                if (_isShuffle.value && tracks.size > 1) {
                    nextTrack()
                } else if (currentIndex != -1 && currentIndex < tracks.size - 1) {
                    playTrack(tracks[currentIndex + 1])
                } else {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    releasePlayer(stopService = true)
                }
            }
        }
    }

    fun releasePlayer(stopService: Boolean = true) {
        unregisterNoisyReceiver()
        abandonAudioFocus()
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        if (stopService) {
            MusicPlaybackService.stopService(context)
        }
    }
}
