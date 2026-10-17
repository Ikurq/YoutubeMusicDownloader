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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.youtubemusicdownloder.data.FileUtils
import com.example.youtubemusicdownloder.ui.components.DuplicateCleanerDialog
import com.example.youtubemusicdownloder.ui.components.RecoverUnindexedTracksDialog
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
    unindexedTracks: List<Track> = emptyList(),
    isScanningLocal: Boolean = false,
    onScanLocalFiles: (() -> Unit)? = null,
    onAddRecoveredTracks: ((List<Track>) -> Unit)? = null,
    onDismissUnindexed: (() -> Unit)? = null,
    onDownloadAllUndownloaded: (() -> Unit)? = null,
    onRemoveUndownloaded: (() -> Unit)? = null,
    onUpdateTrackMetadata: ((trackId: String, title: String, artist: String, album: String, thumbnailUrl: String?) -> Unit)? = null,
    onSearchCandidates: (suspend (query: String) -> List<SearchResultItem>)? = null
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOnlyFavorites by rememberSaveable { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToEdit by remember { mutableStateOf<Track?>(null) }
    var showDuplicateCleanerDialog by rememberSaveable { mutableStateOf(false) }
    var showRecoverDialog by rememberSaveable { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportLocalFiles(uris)
        }
    }

    // ライブラリ一覧は端末に保存完了した楽曲のみを対象とする
    val downloadedTracks = remember(tracks) {
        tracks.filter { it.isDownloaded && !it.localFilePath.isNullOrBlank() }
    }
    val undownloadedTracks = remember(tracks) {
        tracks.filter { !it.isDownloaded || it.localFilePath.isNullOrBlank() }
    }

    val favoriteCount = remember(downloadedTracks) { downloadedTracks.count { it.isFavorite } }

    val duplicateGroups = remember(downloadedTracks) {
        downloadedTracks.groupBy { track ->
            track.title.trim()
                .replace(Regex("""\s*\(\d+\)$"""), "")
                .replace(Regex("""\s*\[\d+\]$"""), "")
                .trim()
                .lowercase()
        }.filter { it.value.size > 1 && it.key.isNotBlank() }
    }

    val filteredTracks = remember(downloadedTracks, searchQuery, showOnlyFavorites) {
        val base = if (showOnlyFavorites) downloadedTracks.filter { it.isFavorite } else downloadedTracks
        if (searchQuery.isBlank()) base
        else base.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalSizeBytes = remember(downloadedTracks) { FileUtils.calculateTotalStorageSize(downloadedTracks) }
    val formattedTotalSize = remember(totalSizeBytes) { FileUtils.formatFileSize(totalSizeBytes) }

    val filteredSizeBytes = remember(filteredTracks) { FileUtils.calculateTotalStorageSize(filteredTracks) }
    val formattedFilteredSize = remember(filteredSizeBytes) { FileUtils.formatFileSize(filteredSizeBytes) }

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
                    text = "楽曲ライブラリ (${downloadedTracks.size} 曲)",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (searchQuery.isNotBlank() || showOnlyFavorites) {
                        "表示: ${filteredTracks.size}曲 ($formattedFilteredSize) • 全体容量: $formattedTotalSize"
                    } else {
                        "全体容量: $formattedTotalSize • 保存済み楽曲"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onScanLocalFiles?.invoke() },
                    enabled = !isScanningLocal
                ) {
                    if (isScanningLocal) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "ローカル楽曲を再スキャン",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("端末から追加", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 未登録ローカル楽曲の検出バナー（見つかった場合のみ表示）
        if (unindexedTracks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "未登録の楽曲が見つかりました",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "端末内の音声ファイルから ${unindexedTracks.size} 件をライブラリに復元できます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showRecoverDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("確認・追加")
                    }
                }
            }
        }

        // 未ダウンロード楽曲の整理バナー（プレイリスト等から未DLの楽曲が混入している場合）
        if (undownloadedTracks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "未ダウンロードの楽曲が ${undownloadedTracks.size} 件あります",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "プレイリスト等から取り込まれた未保存の楽曲です",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onDownloadAllUndownloaded?.invoke() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("すべてDL")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { onRemoveUndownloaded?.invoke() }
                        ) {
                            Text("除外", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
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

    if (showRecoverDialog && unindexedTracks.isNotEmpty()) {
        RecoverUnindexedTracksDialog(
            unindexedTracks = unindexedTracks,
            onAddTracks = { selected ->
                onAddRecoveredTracks?.invoke(selected)
                showRecoverDialog = false
            },
            onDismiss = {
                onDismissUnindexed?.invoke()
                showRecoverDialog = false
            }
        )
    }
}
