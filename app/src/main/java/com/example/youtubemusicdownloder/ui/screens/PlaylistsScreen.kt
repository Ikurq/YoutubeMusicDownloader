package com.example.youtubemusicdownloder.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.youtubemusicdownloder.model.Playlist
import com.example.youtubemusicdownloder.data.PlaylistInfo
import com.example.youtubemusicdownloder.data.SearchResultItem
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.ui.components.CreatePlaylistDialog
import com.example.youtubemusicdownloder.ui.components.EditTrackMetadataDialog
import com.example.youtubemusicdownloder.ui.components.ImportPlaylistDialog
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
    onAddToPlaylistClick: (track: Track) -> Unit,
    onUpdateTrackMetadata: ((trackId: String, title: String, artist: String, album: String, thumbnailUrl: String?) -> Unit)? = null,
    onSearchCandidates: (suspend (query: String) -> List<SearchResultItem>)? = null,
    onFetchPlaylistDirectly: (suspend (urlOrId: String) -> PlaylistInfo?)? = null,
    onImportPlaylist: ((playlistInfo: PlaylistInfo, downloadAll: Boolean) -> Unit)? = null
) {
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var trackToEdit by remember { mutableStateOf<Track?>(null) }

    val currentSelected = playlists.find { it.id == selectedPlaylistId }

    BackHandler(enabled = currentSelected != null) {
        selectedPlaylistId = null
    }

    Scaffold(
        floatingActionButton = {
            if (currentSelected == null) {
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
                val tracksById = allTracks.associateBy { it.id }
                val playlistTracks = currentSelected.trackIds.mapNotNull { tracksById[it] }
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
                        IconButton(onClick = { selectedPlaylistId = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "戻る"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentSelected.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                            items(playlistTracks, key = { it.id }) { track ->
                                TrackItem(
                                    track = track,
                                    isPlaying = currentTrack?.id == track.id,
                                    isDownloading = downloadingTrackIds.contains(track.id),
                                    onTrackClick = { onPlayTrack(track, playlistTracks) },
                                    onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                                    onEditClick = { trackToEdit = track },
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

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onFetchPlaylistDirectly != null && onImportPlaylist != null) {
                                OutlinedButton(
                                    onClick = { showImportDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("URL取得", fontWeight = FontWeight.Bold)
                                }
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
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI 自動作成", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "プレイリストがありません。\n右上の「AI 自動作成」または右下の＋ボタンから作成できます。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                            items(playlists, key = { it.id }) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    onPlaylistClick = { selectedPlaylistId = playlist.id },
                                    onPlayClick = { onPlayPlaylist(playlist, false) },
                                    onShuffleClick = { onPlayPlaylist(playlist, true) },
                                    onDeleteClick = { playlistToDelete = playlist }
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

    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("プレイリストの削除") },
            text = { Text("プレイリスト「${pl.name}」を削除しますか？\n（ライブラリ内の楽曲ファイルは削除されません）") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pl.id
                    playlistToDelete = null
                    onDeletePlaylist(id)
                }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    trackToEdit?.let { track ->
        EditTrackMetadataDialog(
            track = track,
            onSearchCandidates = { query ->
                onSearchCandidates?.invoke(query) ?: emptyList()
            },
            onSave = { newTitle, newArtist, newAlbum, newThumb ->
                onUpdateTrackMetadata?.invoke(track.id, newTitle, newArtist, newAlbum, newThumb)
                trackToEdit = null
            },
            onDismiss = { trackToEdit = null }
        )
    }

    if (showImportDialog && onFetchPlaylistDirectly != null && onImportPlaylist != null) {
        ImportPlaylistDialog(
            onDismiss = { showImportDialog = false },
            onFetchPlaylist = onFetchPlaylistDirectly,
            onImport = onImportPlaylist
        )
    }
}

private fun formatTotalDuration(totalMs: Long): String {
    val totalMin = totalMs / 1000 / 60
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}時間${mins}分" else "${mins}分"
}
