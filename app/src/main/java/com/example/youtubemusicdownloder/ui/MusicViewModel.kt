package com.example.youtubemusicdownloder.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubemusicdownloder.data.ChannelInfo
import com.example.youtubemusicdownloder.data.DownloadQueueItem
import com.example.youtubemusicdownloder.data.DownloadQueueManager
import com.example.youtubemusicdownloder.data.GeminiMetadataCleaner
import com.example.youtubemusicdownloder.data.LocalAudioImporter
import com.example.youtubemusicdownloder.data.MusicRepository
import com.example.youtubemusicdownloder.data.ReleaseInfo
import com.example.youtubemusicdownloder.data.SearchResultItem
import com.example.youtubemusicdownloder.data.SearchFilter
import com.example.youtubemusicdownloder.data.UpdateManager
import com.example.youtubemusicdownloder.data.YoutubeDownloader
import com.example.youtubemusicdownloder.data.YoutubeSearchService
import com.example.youtubemusicdownloder.media.MusicPlayerManager
import com.example.youtubemusicdownloder.model.Playlist
import com.example.youtubemusicdownloder.model.RepeatMode
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    private val repository = MusicRepository(application)
    val playerManager = MusicPlayerManager(application)
    val downloadQueueManager = DownloadQueueManager(application)
    val userAccountManager = com.example.youtubemusicdownloder.data.UserAccountManager(application)

    val isUserLoggedIn: StateFlow<Boolean> = userAccountManager.isLoggedIn

    fun saveUserCookie(cookie: String) {
        userAccountManager.saveCookie(cookie)
        _statusMessage.value = "YouTubeアカウントにログインしました"
    }

    fun logoutUser() {
        userAccountManager.clearAccount()
        _statusMessage.value = "ログアウトしました"
    }

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _antigravityServerUrl = MutableStateFlow("")
    val antigravityServerUrl: StateFlow<String> = _antigravityServerUrl.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _matchedChannels = MutableStateFlow<List<ChannelInfo>>(emptyList())
    val matchedChannels: StateFlow<List<ChannelInfo>> = _matchedChannels.asStateFlow()

    private val _selectedChannel = MutableStateFlow<ChannelInfo?>(null)
    val selectedChannel: StateFlow<ChannelInfo?> = _selectedChannel.asStateFlow()

    private val _channelVideos = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val channelVideos: StateFlow<List<SearchResultItem>> = _channelVideos.asStateFlow()

    private val _isLoadingChannel = MutableStateFlow(false)
    val isLoadingChannel: StateFlow<Boolean> = _isLoadingChannel.asStateFlow()

    private val _relatedTracks = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val relatedTracks: StateFlow<List<SearchResultItem>> = _relatedTracks.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter.ALL)
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _lastSearchQuery = MutableStateFlow("")

    // Download Queue
    val downloadQueue: StateFlow<List<DownloadQueueItem>> = downloadQueueManager.queue
    val isQueueProcessing: StateFlow<Boolean> = downloadQueueManager.isProcessing

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
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val sleepTimerMinutes: StateFlow<Int> = playerManager.sleepTimerMinutes
    val sleepTimerRemainingSeconds: StateFlow<Int> = playerManager.sleepTimerRemainingSeconds

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadData()
        checkForUpdates(silent = true)

        downloadQueueManager.onTrackDownloaded = { downloadedTrack ->
            val updated = (_tracks.value.filter { it.id != downloadedTrack.id }).toMutableList()
            updated.add(0, downloadedTrack)
            _tracks.value = updated
            repository.saveTracks(updated)
        }

        downloadQueueManager.onStatusMessage = { msg ->
            _statusMessage.value = msg
        }
    }

    private fun loadData() {
        _tracks.value = repository.loadTracks()
        _playlists.value = repository.loadPlaylists()
        _apiKey.value = repository.getApiKey()
        _antigravityServerUrl.value = repository.getAntigravityServerUrl()
    }

    fun addToDownloadQueue(url: String, title: String, customTitle: String? = null) {
        downloadQueueManager.addToQueue(url, title, customTitle, _apiKey.value, _antigravityServerUrl.value)
    }

    fun addMultipleToDownloadQueue(items: List<Pair<String, String>>) {
        downloadQueueManager.addMultipleToQueue(items, _apiKey.value)
    }

    fun removeFromDownloadQueue(id: String) {
        downloadQueueManager.removeFromQueue(id)
    }

    fun retryDownloadQueueItem(id: String) {
        downloadQueueManager.retryItem(id, _apiKey.value)
    }

    fun clearCompletedQueue() {
        downloadQueueManager.clearCompleted()
    }

    fun clearAllQueue() {
        downloadQueueManager.clearAll()
    }

    fun generateAiPlaylists() {
        val currentTracks = _tracks.value
        if (currentTracks.isEmpty()) return

        viewModelScope.launch {
            _statusMessage.value = "Gemini AIが楽曲を分析してプレイリストを自動作成中..."
            try {
                val aiPlaylists = GeminiMetadataCleaner.generateSmartPlaylists(currentTracks, _apiKey.value)
                if (aiPlaylists.isNotEmpty()) {
                    val merged = (_playlists.value + aiPlaylists).distinctBy { it.name }
                    _playlists.value = merged
                    repository.savePlaylists(merged)
                    _statusMessage.value = "Gemini AIが${aiPlaylists.size}個のプレイリストを自動作成しました"
                } else {
                    _statusMessage.value = "プレイリスト作成対象の楽曲が不足しています"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "AIプレイリスト作成エラー: ${e.message}"
            }
        }
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
                _statusMessage.value = "${importedCount}件のローカル楽曲を追加しました"
            } else {
                _statusMessage.value = "ローカルファイルの読み込みに失敗しました"
            }
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
                    _statusMessage.value = "新しいバージョン（${update.tagName}）が見つかりました"
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
        _statusMessage.value = "Google / Gemini APIキーを保存しました"
    }

    fun saveAntigravityServerUrl(url: String) {
        repository.saveAntigravityServerUrl(url)
        _antigravityServerUrl.value = url.trim()
        _statusMessage.value = "Antigravity サーバー URLを保存しました"
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setSearchFilter(filter: SearchFilter) {
        _searchFilter.value = filter
        val currentQuery = _lastSearchQuery.value
        if (currentQuery.isNotBlank()) {
            searchYouTube(currentQuery, filter)
        }
    }

    fun fetchSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            suggestJob?.cancel()
            _searchSuggestions.value = emptyList()
            return
        }
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            val suggestions = YoutubeSearchService.fetchSearchSuggestions(trimmed)
            _searchSuggestions.value = suggestions
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        _searchSuggestions.value = emptyList()
    }

    fun searchYouTube(query: String, filter: SearchFilter = _searchFilter.value) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _lastSearchQuery.value = trimmed
        clearSuggestions()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _statusMessage.value = "YouTube Music / YouTube を高精度検索中..."
            try {
                val searchData = YoutubeSearchService.search(trimmed, _apiKey.value, filter)
                _searchResults.value = searchData.videos
                _matchedChannels.value = searchData.matchedChannels

                if (searchData.videos.isEmpty() && searchData.matchedChannels.isEmpty()) {
                    _statusMessage.value = "検索結果が見つかりませんでした"
                } else {
                    val channelMsg = if (searchData.matchedChannels.isNotEmpty()) "（チャンネル${searchData.matchedChannels.size}件）" else ""
                    _statusMessage.value = "${searchData.videos.size}件の楽曲が見つかりました$channelMsg"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "検索エラー: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun openChannel(channelId: String, channelTitle: String = "", avatarUrl: String = "") {
        if (channelId.isBlank()) return
        viewModelScope.launch {
            _isLoadingChannel.value = true
            _selectedChannel.value = ChannelInfo(
                channelId = channelId,
                title = channelTitle.ifBlank { "チャンネル" },
                avatarUrl = avatarUrl
            )
            _statusMessage.value = "${channelTitle.ifBlank { "チャンネル" }} の楽曲を読み込み中..."
            try {
                val (info, videos) = YoutubeSearchService.fetchChannelDetails(channelId)
                _selectedChannel.value = info
                _channelVideos.value = videos
                _statusMessage.value = "${info.title} から${videos.size}件の楽曲を読み込みました"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "チャンネル読み込みエラー: ${e.message}"
            } finally {
                _isLoadingChannel.value = false
            }
        }
    }

    fun closeChannel() {
        _selectedChannel.value = null
        _channelVideos.value = emptyList()
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _matchedChannels.value = emptyList()
        _selectedChannel.value = null
        _channelVideos.value = emptyList()
        _searchSuggestions.value = emptyList()
        _lastSearchQuery.value = ""
    }

    fun downloadTrack(track: Track) {
        if (track.isDownloaded && !track.localFilePath.isNullOrBlank()) return
        addToDownloadQueue(track.youtubeUrl, track.title)
    }

    fun deleteTrack(trackId: String) {
        val trackToDelete = _tracks.value.find { it.id == trackId }
        // 端末ストレージ上の実ファイルを安全に削除
        trackToDelete?.localFilePath?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 削除対象が再生中の楽曲なら再生を停止
        if (currentTrack.value?.id == trackId) {
            playerManager.releasePlayer(stopService = true)
        }

        val updatedTracks = _tracks.value.filter { it.id != trackId }
        _tracks.value = updatedTracks
        repository.saveTracks(updatedTracks)

        val updatedPlaylists = _playlists.value.map { pl ->
            pl.copy(trackIds = pl.trackIds.filter { it != trackId })
        }
        _playlists.value = updatedPlaylists
        repository.savePlaylists(updatedPlaylists)
        _statusMessage.value = "「${trackToDelete?.title ?: "楽曲"}」を削除しました"
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val newPl = Playlist(name = trimmed)
        val updatedList = _playlists.value + newPl
        _playlists.value = updatedList
        repository.savePlaylists(updatedList)
        _statusMessage.value = "プレイリスト「$trimmed」を作成しました"
    }

    fun deletePlaylist(playlistId: String) {
        val plName = _playlists.value.find { it.id == playlistId }?.name ?: ""
        val updatedList = _playlists.value.filter { it.id != playlistId }
        _playlists.value = updatedList
        repository.savePlaylists(updatedList)
        _statusMessage.value = "プレイリスト「$plName」を削除しました"
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        var alreadyExists = false
        val updatedPlaylists = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (!pl.trackIds.contains(trackId)) {
                    pl.copy(trackIds = pl.trackIds + trackId)
                } else {
                    alreadyExists = true
                    pl
                }
            } else pl
        }
        _playlists.value = updatedPlaylists
        repository.savePlaylists(updatedPlaylists)
        val plName = updatedPlaylists.find { it.id == playlistId }?.name ?: ""
        if (alreadyExists) {
            _statusMessage.value = "既に「$plName」に追加されています"
        } else {
            _statusMessage.value = "プレイリスト「$plName」に追加しました"
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val updatedPlaylists = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackIds = pl.trackIds.filter { it != trackId })
            } else pl
        }
        _playlists.value = updatedPlaylists
        repository.savePlaylists(updatedPlaylists)
        _statusMessage.value = "プレイリストから楽曲を削除しました"
    }

    fun playTrack(track: Track, playlistTracks: List<Track>? = null) {
        val queue = playlistTracks ?: _tracks.value
        playerManager.playTrack(track, queue)
        fetchRelatedTracks(track)
    }

    fun playPlaylist(playlist: Playlist, startShuffle: Boolean = false) {
        val tracksById = _tracks.value.associateBy { it.id }
        val playlistTracks = playlist.trackIds.mapNotNull { tracksById[it] }
        if (playlistTracks.isEmpty()) return

        if (startShuffle) {
            if (!playerManager.isShuffle.value) {
                playerManager.toggleShuffle()
            }
            val randomTrack = playlistTracks.random()
            playTrack(randomTrack, playlistTracks)
        } else {
            if (playerManager.isShuffle.value) {
                playerManager.toggleShuffle()
            }
            playTrack(playlistTracks.first(), playlistTracks)
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun nextTrack() = playerManager.nextTrack()
    fun previousTrack() = playerManager.previousTrack()
    fun toggleRepeatMode() = playerManager.toggleRepeatMode()
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun setPlaybackSpeed(speed: Float) = playerManager.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int) = playerManager.setSleepTimer(minutes)

    fun toggleFavorite(trackId: String) {
        var isNowFavorite = false
        val updated = _tracks.value.map { track ->
            if (track.id == trackId) {
                isNowFavorite = !track.isFavorite
                track.copy(isFavorite = isNowFavorite)
            } else track
        }
        _tracks.value = updated
        repository.saveTracks(updated)

        // If currently playing track is the one being toggled, update currentTrack state flow
        if (currentTrack.value?.id == trackId) {
            val updatedCurrent = updated.find { it.id == trackId }
            if (updatedCurrent != null) {
                // Since currentTrack comes from playerManager, we can update it or let UI observe tracks
            }
        }

        _statusMessage.value = if (isNowFavorite) "お気に入りに追加しました" else "お気に入りを解除しました"
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.releasePlayer()
    }
}
