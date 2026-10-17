package com.example.youtubemusicdownloder.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youtubemusicdownloder.data.DownloadStatus
import com.example.youtubemusicdownloder.data.SearchFilter
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.ui.MusicViewModel
import com.example.youtubemusicdownloder.ui.components.AddTrackToPlaylistDialog
import com.example.youtubemusicdownloder.ui.components.DownloadQueueSheet
import com.example.youtubemusicdownloder.ui.components.DuplicateFileDialog
import com.example.youtubemusicdownloder.ui.components.LoginWebViewDialog
import com.example.youtubemusicdownloder.ui.components.MiniPlayer
import com.example.youtubemusicdownloder.ui.components.UpdateDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                (context as? Activity)?.let { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showPlayerSheet by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val tracks by viewModel.tracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val antigravityServerUrl by viewModel.antigravityServerUrl.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val matchedChannels by viewModel.matchedChannels.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val channelVideos by viewModel.channelVideos.collectAsState()
    val isLoadingChannel by viewModel.isLoadingChannel.collectAsState()
    val matchedPlaylist by viewModel.matchedPlaylist.collectAsState()
    val matchedPlaylists by viewModel.matchedPlaylists.collectAsState()
    val selectedPlaylistInfo by viewModel.selectedPlaylistInfo.collectAsState()
    val isLoadingPlaylist by viewModel.isLoadingPlaylist.collectAsState()
    val relatedTracks by viewModel.relatedTracks.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()

    BackHandler(enabled = showPlayerSheet) {
        showPlayerSheet = false
    }

    BackHandler(enabled = !showPlayerSheet && selectedPlaylistInfo != null) {
        viewModel.closePlaylist()
    }

    BackHandler(enabled = !showPlayerSheet && selectedPlaylistInfo == null && selectedChannel != null) {
        viewModel.closeChannel()
    }

    val downloadQueue by viewModel.downloadQueue.collectAsState()
    val isQueueProcessing by viewModel.isQueueProcessing.collectAsState()
    val activeQueueCount = remember(downloadQueue) {
        downloadQueue.count { it.status != DownloadStatus.COMPLETED }
    }

    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val duplicateDownloadInfo by viewModel.duplicateDownloadInfo.collectAsState()
    val isScanningLocal by viewModel.isScanningLocal.collectAsState()
    val unindexedTracks by viewModel.unindexedTracks.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Music DL & Player",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        // DL Queue Button
                        IconButton(onClick = { showQueueSheet = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "DLキュー",
                                    tint = if (activeQueueCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (activeQueueCount > 0) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "$activeQueueCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // App Update Check
                        IconButton(onClick = { viewModel.checkForUpdates(silent = false) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "更新確認",
                                tint = if (isCheckingUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                Column {
                    currentTrack?.let { track ->
                        MiniPlayer(
                            track = track,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            onPlayerClick = { showPlayerSheet = true },
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.nextTrack() }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("検索 & DL") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                            label = { Text("プレイリスト") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text("ライブラリ") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> UrlSearchScreen(
                        apiKey = apiKey,
                        antigravityServerUrl = antigravityServerUrl,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        isDownloading = isQueueProcessing,
                        onSearch = { viewModel.searchYouTube(it) },
                        onClearSearch = { viewModel.clearSearchResults() },
                        onSaveApiKey = { viewModel.saveApiKey(it) },
                        onSaveAntigravityServerUrl = { viewModel.saveAntigravityServerUrl(it) },
                        onAddTrack = { url, title ->
                            viewModel.requestDownloadTrack(url, title ?: "YouTube Track", title)
                        },
                        matchedChannels = matchedChannels,
                        selectedChannel = selectedChannel,
                        channelVideos = channelVideos,
                        isLoadingChannel = isLoadingChannel,
                        onOpenChannel = { id, title, avatar -> viewModel.openChannel(id, title, avatar) },
                        onCloseChannel = { viewModel.closeChannel() },
                        matchedPlaylist = matchedPlaylist,
                        matchedPlaylists = matchedPlaylists,
                        selectedPlaylist = selectedPlaylistInfo,
                        isLoadingPlaylist = isLoadingPlaylist,
                        onOpenPlaylist = { viewModel.openPlaylist(it) },
                        onClosePlaylist = { viewModel.closePlaylist() },
                        onImportPlaylist = { pl, dlAll -> viewModel.importPlaylistToApp(pl, dlAll) },
                        onAddMultipleTracks = { items -> viewModel.addMultipleToDownloadQueue(items) },
                        searchFilter = searchFilter,
                        onFilterChange = { viewModel.setSearchFilter(it) },
                        searchSuggestions = searchSuggestions,
                        onQueryChange = { viewModel.fetchSuggestions(it) },
                        onClearSuggestions = { viewModel.clearSuggestions() },
                        isLoggedIn = isUserLoggedIn,
                        onLoginClick = { showLoginDialog = true },
                        onLogoutClick = { viewModel.logoutUser() }
                    )
                    1 -> PlaylistsScreen(
                        playlists = playlists,
                        allTracks = tracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onCreatePlaylist = { viewModel.createPlaylist(it) },
                        onGenerateAiPlaylists = { viewModel.generateAiPlaylists() },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onPlayPlaylist = { playlist, shuffle ->
                            viewModel.playPlaylist(playlist, shuffle)
                        },
                        onPlayTrack = { track, queue ->
                            viewModel.playTrack(track, queue)
                        },
                        onRemoveFromPlaylist = { playlistId, trackId ->
                            viewModel.removeTrackFromPlaylist(playlistId, trackId)
                        },
                        onAddToPlaylistClick = { trackToAddToPlaylist = it },
                        onUpdateTrackMetadata = { trackId, title, artist, album, thumbUrl ->
                            viewModel.updateTrackMetadata(trackId, title, artist, album, thumbUrl)
                        },
                        onSearchCandidates = { query ->
                            viewModel.searchMetadataCandidates(query)
                        },
                        onFetchPlaylistDirectly = { viewModel.fetchPlaylistDirectly(it) },
                        onImportPlaylist = { pl, dlAll -> viewModel.importPlaylistToApp(pl, dlAll) }
                    )
                    2 -> LibraryScreen(
                        tracks = tracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onTrackClick = { track, queue ->
                            viewModel.playTrack(track, queue)
                        },
                        onAddToPlaylistClick = { trackToAddToPlaylist = it },
                        onDownloadClick = { viewModel.downloadTrack(it) },
                        onDeleteClick = { viewModel.deleteTrack(it) },
                        onShuffleAllClick = { queue ->
                            if (queue.isNotEmpty()) {
                                if (!isShuffle) viewModel.toggleShuffle()
                                viewModel.playTrack(queue.random(), queue)
                            }
                        },
                        onImportLocalFiles = { uris ->
                            viewModel.importLocalAudioFiles(context, uris)
                        },
                        onToggleFavorite = { trackId ->
                            viewModel.toggleFavorite(trackId)
                        },
                        onDeleteMultipleTracks = { trackIds ->
                            viewModel.deleteMultipleTracks(trackIds)
                        },
                        unindexedTracks = unindexedTracks,
                        isScanningLocal = isScanningLocal,
                        onScanLocalFiles = { viewModel.scanLocalFiles(context, silent = false) },
                        onAddRecoveredTracks = { viewModel.addRecoveredTracks(it) },
                        onDismissUnindexed = { viewModel.dismissUnindexedTracksDialog() },
                        onDownloadAllUndownloaded = { viewModel.downloadAllUndownloadedTracks() },
                        onRemoveUndownloaded = { viewModel.removeUndownloadedTracks() },
                        onUpdateTrackMetadata = { trackId, title, artist, album, thumbUrl ->
                            viewModel.updateTrackMetadata(trackId, title, artist, album, thumbUrl)
                        },
                        onSearchCandidates = { query ->
                            viewModel.searchMetadataCandidates(query)
                        }
                    )
                }
            }
        }

    if (showPlayerSheet && currentTrack != null) {
        val currentTrackId = currentTrack!!.id
        val isFav = tracks.find { it.id == currentTrackId }?.isFavorite ?: currentTrack!!.isFavorite

        PlayerSheet(
            track = currentTrack!!,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            isFavorite = isFav,
            playbackSpeed = playbackSpeed,
            sleepTimerMinutes = sleepTimerMinutes,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            relatedTracks = relatedTracks,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onNextClick = { viewModel.nextTrack() },
            onPreviousClick = { viewModel.previousTrack() },
            onSeek = { viewModel.seekTo(it) },
            onToggleRepeat = { viewModel.toggleRepeatMode() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleFavorite = { viewModel.toggleFavorite(currentTrackId) },
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            onAddRelatedTrack = { url, title -> viewModel.requestDownloadTrack(url, title) },
            onCloseClick = { showPlayerSheet = false }
        )
    }

    // Download Queue Sheet
    if (showQueueSheet) {
        DownloadQueueSheet(
            queue = downloadQueue,
            isProcessing = isQueueProcessing,
            onRemoveItem = { viewModel.removeFromDownloadQueue(it) },
            onRetryItem = { viewModel.retryDownloadQueueItem(it) },
            onClearCompleted = { viewModel.clearCompletedQueue() },
            onClearAll = { viewModel.clearAllQueue() },
            onDismiss = { showQueueSheet = false }
        )
    }

    // Duplicate File Confirmation Dialog
    duplicateDownloadInfo?.let { info ->
        DuplicateFileDialog(
            info = info,
            onKeepBoth = { viewModel.confirmKeepBothDownload(info) },
            onOverwrite = { viewModel.confirmOverwriteDownload(info) },
            onCancel = { viewModel.cancelDuplicateDownload() }
        )
    }

    // In-App Update Dialog
    availableUpdate?.let { releaseInfo ->
        UpdateDialog(
            releaseInfo = releaseInfo,
            isDownloading = isDownloadingUpdate,
            progress = downloadProgress,
            onUpdateClick = {
                viewModel.downloadAndInstallUpdate(context, releaseInfo)
            },
            onDismiss = {
                viewModel.dismissUpdateDialog()
            }
        )
    }

    trackToAddToPlaylist?.let { track ->
        AddTrackToPlaylistDialog(
            track = track,
            playlists = playlists,
            onPlaylistSelected = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, track.id)
            },
            onDismiss = { trackToAddToPlaylist = null }
        )
    }

    if (showLoginDialog) {
        LoginWebViewDialog(
            onDismissRequest = { showLoginDialog = false },
            onLoginSuccess = { cookie ->
                viewModel.saveUserCookie(cookie)
                showLoginDialog = false
            }
        )
    }
}
