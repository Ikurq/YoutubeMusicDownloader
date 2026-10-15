package com.example.youtubemusicdownloder.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.youtubemusicdownloder.model.Track
import com.example.youtubemusicdownloder.ui.components.TrackItem

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackClick: (track: Track, queue: List<Track>) -> Unit,
    onAddToPlaylistClick: (track: Track) -> Unit,
    onDownloadClick: (track: Track) -> Unit,
    onDeleteClick: (trackId: String) -> Unit,
    onShuffleAllClick: (tracks: List<Track>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "楽曲ライブラリ (${tracks.size} 曲)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("楽曲を検索") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.weight(1f)
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
                    text = if (searchQuery.isBlank()) "ライブラリに楽曲がありません。\n「URL検索」タブから追加してください。" else "検索条件に一致する楽曲がありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(filteredTracks) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = currentTrack?.id == track.id && isPlaying,
                        onTrackClick = { onTrackClick(track, filteredTracks) },
                        onAddToPlaylistClick = { onAddToPlaylistClick(track) },
                        onDownloadClick = if (!track.isDownloaded) {
                            { onDownloadClick(track) }
                        } else null,
                        onDeleteClick = { onDeleteClick(track.id) }
                    )
                }
            }
        }
    }
}
