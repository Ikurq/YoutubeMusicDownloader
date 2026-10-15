package com.example.youtubemusicdownloder.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubemusicdownloder.data.LocalAudioImporter
import com.example.youtubemusicdownloder.data.MusicRepository
import com.example.youtubemusicdownloder.data.ReleaseInfo
import com.example.youtubemusicdownloder.data.SearchResultItem
import com.example.youtubemusicdownloder.data.UpdateManager
import com.example.youtubemusicdownloder.data.YoutubeDownloader
import com.example.youtubemusicdownloder.data.YoutubeSearchService
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

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _relatedTracks = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val relatedTracks: StateFlow<List<SearchResultItem>> = _relatedTracks.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = _downloadingTrackIds.asStateFlow()

    // Update state
    private val _availableUpdate = MutableStateFlow<ReleaseInfo?>(null)
    val availableUpdate: StateFlow<ReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

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
        checkForUpdates(silent = true)
    }

    private fun loadData() {
        _tracks.value = repository.loadTracks()
        _playlists.value = repository.loadPlaylists()
        _apiKey.value = repository.getApiKey()
    }

    fun fetchRelatedTracks(track: Track) {
        viewModelScope.launch {
            val videoId = YoutubeDownloader.extractVideoId(track.youtubeUrl) ?: ""
            val related = YoutubeSearchService.fetchRelatedVideos(videoId, track.artist, track.title)
            _relatedTracks.value = related
        }
    }

    fun importLocalAudioFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isDownloading.value = true
            _statusMessage.value = "${uris.size}件のローカルファイルを読み込み中..."
            var importedCount = 0
            val newTracks = mutableListOf<Track>()

            for (uri in uris) {
                val track = LocalAudioImporter.importAudioFromUri(context, uri)
                if (track != null) {
                    newTracks.add(track)
                    importedCount++
                }
            }

            if (newTracks.isNotEmpty()) {
                val currentList = _tracks.value.toMutableList()
                currentList.addAll(0, newTracks)
                _tracks.value = currentList
                repository.saveTracks(currentList)
                _statusMessage.value = "${importedCount}件のローカル楽曲を追加しました！"
            } else {
                _statusMessage.value = "ローカルファイルの読み込みに失敗しました"
            }
            _isDownloading.value = false
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            if (!silent) _statusMessage.value = "アプリアップデートを確認中..."
            try {
                val update = UpdateManager.checkForUpdates()
                if (update != null) {
                    _availableUpdate.value = update
                    _statusMessage.value = "新しいバージョン（${update.tagName}）が見つかりました！"
                } else if (!silent) {
                    _statusMessage.value = "お使いのアプリは最新バージョンです（${UpdateManager.CURRENT_VERSION}）"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!silent) _statusMessage.value = "アップデート確認エラー"
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context, releaseInfo: ReleaseInfo) {
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0
            _statusMessage.value = "アップデートファイルをダウンロード中..."
            try {
                UpdateManager.downloadAndInstallApk(context, releaseInfo.apkDownloadUrl) { percent ->
                    _downloadProgress.value = percent
                }
                _statusMessage.value = "ダウンロード完了。インストール画面を開きます..."
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "アップデート失敗: ${e.message}"
            } finally {
                _isDownloadingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun saveApiKey(key: String) {
        repository.saveApiKey(key)
        _apiKey.value = key.trim()
        _statusMessage.value = "Google APIキーを保存しました"
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            _statusMessage.value = "YouTubeを検索中..."
            try {
                val results = YoutubeSearchService.searchVideos(query, _apiKey.value)
                _searchResults.value = results
                if (results.isEmpty()) {
                    _statusMessage.value = "検索結果が見つかりませんでした"
                } else {
                    _statusMessage.value = "${results.size}件の楽曲が見つかりました"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "検索エラー: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun parseAndAddTrack(urlInput: String, customTitle: String? = null) {
        if (urlInput.isBlank()) return

        viewModelScope.launch {
            _isDownloading.value = true
            _statusMessage.value = "楽曲情報およびローカルダウンロードを処理中..."
            try {
                var newTrack = YoutubeDownloader.parseTrackFromUrl(urlInput, customTitle, _apiKey.value)

                _downloadingTrackIds.value = _downloadingTrackIds.value + newTrack.id
                val file = YoutubeDownloader.downloadAudioToFile(getApplication(), newTrack)
                newTrack = newTrack.copy(
                    localFilePath = file.absolutePath,
                    isDownloaded = true
                )

                val currentList = _tracks.value.toMutableList()
                currentList.add(0, newTrack)
                _tracks.value = currentList
                repository.saveTracks(currentList)

                _statusMessage.value = "「${newTrack.title}」をMusicフォルダに保存しました！"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "ダウンロードに失敗しました: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadingTrackIds.value = _downloadingTrackIds.value - urlInput
            }
        }
    }

    fun downloadTrack(track: Track) {
        if (track.isDownloaded && !track.localFilePath.isNullOrBlank()) return

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadingTrackIds.value = _downloadingTrackIds.value + track.id
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
                _downloadingTrackIds.value = _downloadingTrackIds.value - track.id
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
        fetchRelatedTracks(track)
    }

    fun playPlaylist(playlist: Playlist, startShuffle: Boolean = false) {
        val playlistTracks = _tracks.value.filter { playlist.trackIds.contains(it.id) }
        if (playlistTracks.isEmpty()) return

        if (startShuffle) {
            if (!playerManager.isShuffle.value) {
                playerManager.toggleShuffle()
            }
            val randomTrack = playlistTracks.random()
            playTrack(randomTrack, playlistTracks)
        } else {
            playTrack(playlistTracks.first(), playlistTracks)
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
