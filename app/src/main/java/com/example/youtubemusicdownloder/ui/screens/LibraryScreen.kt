package com.example.youtubemusicdownloder.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.youtubemusicdownloder.ui.components.DuplicateCleanerDialog
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
import com.example.youtubemusicdownloder.data.SearchResultItem
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.ui.components.EditTrackMetadataDialog
import com.example.youtubemusicdownloder.ui.components.TrackItem

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    downloadingTrackIds: Set<String> = emptySet(),
    onTrackClick: (track: Track, queue: List<Track>) -> Unit,
    onAddToPlaylistClick: (track: Track) -> Unit,
    onDownloadClick: (track: Track) -> Unit,
    onDeleteClick: (trackId: String) -> Unit,
    onShuffleAllClick: (tracks: List<Track>) -> Unit,
    onImportLocalFiles: (uris: List<Uri>) -> Unit,
    onToggleFavorite: (trackId: String) -> Unit,
    onDeleteMultipleTracks: ((Set<String>) -> Unit)? = null,
    onUpdateTrackMetadata: ((trackId: String, title: String, artist: String, album: String, thumbnailUrl: String?) -> Unit)? = null,
    onSearchCandidates: (suspend (query: String) -> List<SearchResultItem>)? = null
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOnlyFavorites by rememberSaveable { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToEdit by remember { mutableStateOf<Track?>(null) }
    var showDuplicateCleanerDialog by rememberSaveable { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportLocalFiles(uris)
        }
    }

    val favoriteCount = remember(tracks) { tracks.count { it.isFavorite } }

    val duplicateGroups = remember(tracks) {
        tracks.groupBy { track ->
            track.title.trim()
                .replace(Regex("""\s*\(\d+\)$"""), "")
                .replace(Regex("""\s*\[\d+\]$"""), "")
                .trim()
                .lowercase()
        }.filter { it.value.size > 1 && it.key.isNotBlank() }
    }

    val filteredTracks = remember(tracks, searchQuery, showOnlyFavorites) {
        val base = if (showOnlyFavorites) tracks.filter { it.isFavorite } else tracks
        if (searchQuery.isBlank()) base
        else base.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "楽曲ライブラリ (${tracks.size} 曲)",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "保存済み楽曲 & ローカルファイル",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { audioPickerLauncher.launch("audio/*") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("端末から追加", fontWeight = FontWeight.Bold)
            }
        }

        // 重複楽曲の整理バナー（重複が存在する場合のみ表示）
        if (duplicateGroups.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "重複楽曲が検出されました",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        val dupCount = duplicateGroups.values.sumOf { it.size - 1 }
                        Text(
                            text = "${duplicateGroups.size} 組 (${dupCount} 曲の重複) を整理できます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showDuplicateCleanerDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("整理する")
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("ライブラリ内を検索") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips (All / Favorites)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !showOnlyFavorites,
                onClick = { showOnlyFavorites = false },
                label = { Text("すべての楽曲 (${tracks.size})") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilterChip(
                selected = showOnlyFavorites,
                onClick = { showOnlyFavorites = true },
                label = { Text("お気に入り ($favoriteCount)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (showOnlyFavorites) Color(0xFFE91E63) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons: Play All & Shuffle All
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (filteredTracks.isNotEmpty()) {
                        onTrackClick(filteredTracks.first(), filteredTracks)
                    }
                },
                enabled = filteredTracks.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("全曲再生")
            }

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedButton(
                onClick = {
                    if (filteredTracks.isNotEmpty()) {
                        onShuffleAllClick(filteredTracks)
                    }
                },
                enabled = filteredTracks.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("ランダム再生")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "検索条件に一致する楽曲がありません。"
                    else if (showOnlyFavorites) "お気に入りの楽曲がまだありません。\n楽曲のお気に入りアイコンをタップしてお気に入りに追加できます。"
                    else "ライブラリに楽曲がありません。\n「検索 & DL」タブから保存、または右上の「端末から追加」をご利用ください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(filteredTracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = currentTrack?.id == track.id,
                        isDownloading = downloadingTrackIds.contains(track.id),
                        onTrackClick = { onTrackClick(track, filteredTracks) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                        onDownloadClick = if (!track.isDownloaded) {
                            { onDownloadClick(track) }
                        } else null,
                        onEditClick = { trackToEdit = track },
                        onDeleteClick = { trackToDelete = track }
                    )
                }
            }
        }
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

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("楽曲の削除") },
            text = { Text("「${track.title}」を削除しますか？\n端末内の音声ファイルも削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = track.id
                    trackToDelete = null
                    onDeleteClick(id)
                }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (showDuplicateCleanerDialog) {
        DuplicateCleanerDialog(
            tracks = tracks,
            onDeleteTracks = { ids ->
                onDeleteMultipleTracks?.invoke(ids)
                showDuplicateCleanerDialog = false
            },
            onDismiss = { showDuplicateCleanerDialog = false }
        )
    }
}
