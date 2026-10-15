package com.example.youtubemusicdownloder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubemusicdownloder.data.MusicRepository
import com.example.youtubemusicdownloder.data.YoutubeDownloader
import com.example.youtubemusicdownloder.media.MusicPlayerManager
import com.example.youtubemusicdownloder.model.Playlist
import com.example.youtubemusicdownloder.model.RepeatMode
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playerManager = MusicPlayerManager(application)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    val currentTrack: StateFlow<Track?> = playerManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val durationMs: StateFlow<Long> = playerManager.durationMs
    val repeatMode: StateFlow<RepeatMode> = playerManager.repeatMode
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _tracks.value = repository.loadTracks()
        _playlists.value = repository.loadPlaylists()
        _apiKey.value = repository.getApiKey()
    }

    fun saveApiKey(key: String) {
        repository.saveApiKey(key)
        _apiKey.value = key.trim()
        _statusMessage.value = "Google APIキーを保存しました"
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun parseAndAddTrack(urlInput: String, customTitle: String? = null, downloadLocally: Boolean = true) {
        if (urlInput.isBlank()) return

        viewModelScope.launch {
            _isDownloading.value = true
            _statusMessage.value = "URLおよび楽曲メタデータを解析中..."
            try {
                var newTrack = YoutubeDownloader.parseTrackFromUrl(urlInput, customTitle, _apiKey.value)

                if (downloadLocally) {
                    _statusMessage.value = "楽曲をダウンロード保存中..."
                    try {
                        val file = YoutubeDownloader.downloadAudioToFile(getApplication(), newTrack)
                        newTrack = newTrack.copy(
                            localFilePath = file.absolutePath,
                            isDownloaded = true
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _statusMessage.value = "ストリーミング形式で保存しました"
                    }
                }

                val currentList = _tracks.value.toMutableList()
                currentList.add(0, newTrack)
                _tracks.value = currentList
                repository.saveTracks(currentList)

                _statusMessage.value = "「${newTrack.title}」を追加しました！"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "追加に失敗しました: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun downloadTrack(track: Track) {
        if (track.isDownloaded) return

        viewModelScope.launch {
            _isDownloading.value = true
            _statusMessage.value = "「${track.title}」をダウンロード中..."
            try {
                val file = YoutubeDownloader.downloadAudioToFile(getApplication(), track)
                val updatedTrack = track.copy(
                    localFilePath = file.absolutePath,
                    isDownloaded = true
                )

                val updatedTracks = _tracks.value.map {
                    if (it.id == track.id) updatedTrack else it
                }
                _tracks.value = updatedTracks
                repository.saveTracks(updatedTracks)

                _statusMessage.value = "ダウンロード完了: ${track.title}"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "ダウンロードに失敗しました"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun deleteTrack(trackId: String) {
        val updatedTracks = _tracks.value.filter { it.id != trackId }
        _tracks.value = updatedTracks
        repository.saveTracks(updatedTracks)

        val updatedPlaylists = _playlists.value.map { pl ->
            pl.copy(trackIds = pl.trackIds.filter { it != trackId })
        }
        _playlists.value = updatedPlaylists
        repository.savePlaylists(updatedPlaylists)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val newPl = Playlist(name = name)
        val updatedList = _playlists.value + newPl
        _playlists.value = updatedList
        repository.savePlaylists(updatedList)
        _statusMessage.value = "プレイリスト「$name」を作成しました"
    }

    fun deletePlaylist(playlistId: String) {
        val updatedList = _playlists.value.filter { it.id != playlistId }
        _playlists.value = updatedList
        repository.savePlaylists(updatedList)
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val updatedPlaylists = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (!pl.trackIds.contains(trackId)) {
                    pl.copy(trackIds = pl.trackIds + trackId)
                } else pl
            } else pl
        }
        _playlists.value = updatedPlaylists
        repository.savePlaylists(updatedPlaylists)
        val plName = updatedPlaylists.find { it.id == playlistId }?.name ?: ""
        _statusMessage.value = "プレイリスト「$plName」に追加しました"
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val updatedPlaylists = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackIds = pl.trackIds.filter { it != trackId })
            } else pl
        }
        _playlists.value = updatedPlaylists
        repository.savePlaylists(updatedPlaylists)
    }

    fun playTrack(track: Track, playlistTracks: List<Track>? = null) {
        val queue = playlistTracks ?: _tracks.value
        playerManager.playTrack(track, queue)
    }

    fun playPlaylist(playlist: Playlist, startShuffle: Boolean = false) {
        val playlistTracks = _tracks.value.filter { playlist.trackIds.contains(it.id) }
        if (playlistTracks.isEmpty()) return

        if (startShuffle) {
            if (!playerManager.isShuffle.value) {
                playerManager.toggleShuffle()
            }
            val randomTrack = playlistTracks.random()
            playerManager.playTrack(randomTrack, playlistTracks)
        } else {
            playerManager.playTrack(playlistTracks.first(), playlistTracks)
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun nextTrack() = playerManager.nextTrack()
    fun previousTrack() = playerManager.previousTrack()
    fun toggleRepeatMode() = playerManager.toggleRepeatMode()
    fun toggleShuffle() = playerManager.toggleShuffle()

    override fun onCleared() {
        super.onCleared()
        playerManager.releasePlayer()
    }
}
