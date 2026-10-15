package com.example.youtubemusicdownloder.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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

    private var currentPlaylist: List<Track> = emptyList()

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

    init {
        MusicPlaybackService.serviceCallback = { action ->
            when (action) {
                MusicPlaybackService.ACTION_TOGGLE_PLAY -> togglePlayPause()
                MusicPlaybackService.ACTION_NEXT -> nextTrack()
                MusicPlaybackService.ACTION_PREVIOUS -> previousTrack()
                MusicPlaybackService.ACTION_STOP -> releasePlayer()
            }
        }
    }

    fun setPlaylist(playlist: List<Track>) {
        currentPlaylist = playlist
    }

    fun playTrack(track: Track, playlist: List<Track>? = null) {
        if (playlist != null) {
            currentPlaylist = playlist
        }

        _currentTrack.value = track
        releasePlayer()

        // Pure offline playback check
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
                mp.start()
                _isPlaying.value = true
                _durationMs.value = mp.duration.toLong().coerceAtLeast(1L)
                handler.post(progressRunnable)

                MusicPlaybackService.startService(
                    context,
                    track.title,
                    track.artist,
                    true
                )
            }

            player.setOnCompletionListener {
                onTrackCompleted()
            }

            player.setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                true
            }

            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: run {
            _currentTrack.value?.let { playTrack(it) }
            return
        }

        val track = _currentTrack.value ?: return

        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            handler.removeCallbacks(progressRunnable)
            MusicPlaybackService.startService(context, track.title, track.artist, false)
        } else {
            player.start()
            _isPlaying.value = true
            handler.post(progressRunnable)
            MusicPlaybackService.startService(context, track.title, track.artist, true)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
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
                    MusicPlaybackService.stopService(context)
                }
            }
        }
    }

    fun releasePlayer() {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        MusicPlaybackService.stopService(context)
    }
}
