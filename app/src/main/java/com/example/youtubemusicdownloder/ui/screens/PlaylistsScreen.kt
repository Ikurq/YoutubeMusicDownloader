package com.example.youtubemusicdownloder.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.youtubemusicdownloder.model.Playlist
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.ui.components.CreatePlaylistDialog
import com.example.youtubemusicdownloder.ui.components.PlaylistItem
import com.example.youtubemusicdownloder.ui.components.TrackItem

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    allTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadingTrackIds: Set<String> = emptySet(),
    onCreatePlaylist: (name: String) -> Unit,
    onGenerateAiPlaylists: () -> Unit,
    onDeletePlaylist: (playlistId: String) -> Unit,
    onPlayPlaylist: (playlist: Playlist, shuffle: Boolean) -> Unit,
    onPlayTrack: (track: Track, playlistTracks: List<Track>) -> Unit,
    onRemoveFromPlaylist: (playlistId: String, trackId: String) -> Unit,
    onAddToPlaylistClick: (track: Track) -> Unit
) {
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val currentSelected = playlists.find { it.id == selectedPlaylist?.id }

    Scaffold(
        floatingActionButton = {
            if (selectedPlaylist == null) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新規プレイリスト"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentSelected != null) {
                // Playlist Detail View
                val playlistTracks = allTracks.filter { currentSelected.trackIds.contains(it.id) }
                val totalDurationMs = playlistTracks.sumOf { it.durationMs }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { selectedPlaylist = null }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "戻る"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSelected.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${playlistTracks.size} 曲 • ${formatTotalDuration(totalDurationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayPlaylist(currentSelected, false) },
                            enabled = playlistTracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("通常再生")
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            onClick = { onPlayPlaylist(currentSelected, true) },
                            enabled = playlistTracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ランダム再生")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlistTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "このプレイリストには楽曲がありません。\nライブラリや検索画面から追加してください。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(playlistTracks) { track ->
                                TrackItem(
                                    track = track,
                                    isPlaying = currentTrack?.id == track.id && isPlaying,
                                    isDownloading = downloadingTrackIds.contains(track.id),
                                    onTrackClick = { onPlayTrack(track, playlistTracks) },
                                    onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                                    onDeleteClick = { onRemoveFromPlaylist(currentSelected.id, track.id) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Playlists List View
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "プレイリスト一覧",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "手動作成 & Gemini AI 自動作成",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onGenerateAiPlaylists,
                            enabled = allTracks.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✨ AI 自動作成", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "プレイリストがありません。\n右上の「✨ AI 自動作成」または右下の＋ボタンから作成できます。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                            items(playlists) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    onPlaylistClick = { selectedPlaylist = playlist },
                                    onPlayClick = { onPlayPlaylist(playlist, false) },
                                    onShuffleClick = { onPlayPlaylist(playlist, true) },
                                    onDeleteClick = { onDeletePlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onCreate = { onCreatePlaylist(it) },
            onDismiss = { showCreateDialog = false }
        )
    }
}

private fun formatTotalDuration(totalMs: Long): String {
    val totalMin = totalMs / 1000 / 60
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}時間${mins}分" else "${mins}分"
}
