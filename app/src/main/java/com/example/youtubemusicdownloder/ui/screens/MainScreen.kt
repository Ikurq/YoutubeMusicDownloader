package com.example.youtubemusicdownloder.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.ui.MusicViewModel
import com.example.youtubemusicdownloder.ui.components.AddTrackToPlaylistDialog
import com.example.youtubemusicdownloder.ui.components.MiniPlayer

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
    var showPlayerSheet by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val tracks by viewModel.tracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    if (showPlayerSheet && currentTrack != null) {
        PlayerSheet(
            track = currentTrack!!,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onNextClick = { viewModel.nextTrack() },
            onPreviousClick = { viewModel.previousTrack() },
            onSeek = { viewModel.seekTo(it) },
            onToggleRepeat = { viewModel.toggleRepeatMode() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCloseClick = { showPlayerSheet = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "YouTube Music Downloader",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                Column {
                    // Mini Player
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

                    // Navigation Bar
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("URL検索") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                            label = { Text("プレイリスト") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text("ライブラリ") }
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
                        isDownloading = isDownloading,
                        onSaveApiKey = { viewModel.saveApiKey(it) },
                        onAddTrack = { url, title, download ->
                            viewModel.parseAndAddTrack(url, title, download)
                        }
                    )
                    1 -> PlaylistsScreen(
                        playlists = playlists,
                        allTracks = tracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onCreatePlaylist = { viewModel.createPlaylist(it) },
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
                        onAddToPlaylistClick = { trackToAddToPlaylist = it }
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
                        }
                    )
                }
            }
        }
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
}
