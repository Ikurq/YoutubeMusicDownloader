package com.example.youtubemusicdownloder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.youtubemusicdownloder.data.ChannelInfo
import com.example.youtubemusicdownloder.data.PlaylistInfo
import com.example.youtubemusicdownloder.data.SearchFilter
import com.example.youtubemusicdownloder.data.SearchResultItem
import com.example.youtubemusicdownloder.data.YoutubeExtractor
import com.example.youtubemusicdownloder.ui.components.ApiSettingsCard
import com.example.youtubemusicdownloder.ui.components.ChannelCard
import com.example.youtubemusicdownloder.ui.components.DirectUrlPreviewCard
import com.example.youtubemusicdownloder.ui.components.PlaylistCard
import com.example.youtubemusicdownloder.ui.components.SearchResultRow

@Composable
fun UrlSearchScreen(
    apiKey: String,
    antigravityServerUrl: String,
    searchResults: List<SearchResultItem>,
    isSearching: Boolean,
    isDownloading: Boolean,
    onSearch: (query: String) -> Unit,
    onClearSearch: () -> Unit,
    onSaveApiKey: (key: String) -> Unit,
    onSaveAntigravityServerUrl: (url: String) -> Unit,
    onAddTrack: (url: String, customTitle: String?) -> Unit,
    matchedChannels: List<ChannelInfo> = emptyList(),
    selectedChannel: ChannelInfo? = null,
    channelVideos: List<SearchResultItem> = emptyList(),
    isLoadingChannel: Boolean = false,
    onOpenChannel: (channelId: String, channelTitle: String, avatarUrl: String) -> Unit = { _, _, _ -> },
    onCloseChannel: () -> Unit = {},
    matchedPlaylist: PlaylistInfo? = null,
    selectedPlaylist: PlaylistInfo? = null,
    onOpenPlaylist: (PlaylistInfo) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
    onImportPlaylist: (PlaylistInfo, Boolean) -> Unit = { _, _ -> },
    onAddMultipleTracks: (List<Pair<String, String>>) -> Unit = {},
    searchFilter: SearchFilter = SearchFilter.ALL,
    onFilterChange: (SearchFilter) -> Unit = {},
    searchSuggestions: List<String> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onClearSuggestions: () -> Unit = {},
    isLoggedIn: Boolean = false,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    var searchInput by remember { mutableStateOf("") }
    var channelFilterQuery by remember { mutableStateOf("") }
    var showApiKeySettings by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val videoId = remember(searchInput) { YoutubeExtractor.extractVideoId(searchInput) }
    val previewThumbnail = remember(videoId) { videoId?.let { YoutubeExtractor.getThumbnailUrl(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (selectedPlaylist != null) {
            // === Playlist Detail View ===
            var playlistFilterQuery by remember { mutableStateOf("") }
            val filteredPlaylistTracks = remember(selectedPlaylist, playlistFilterQuery) {
                if (playlistFilterQuery.isBlank()) selectedPlaylist.tracks
                else selectedPlaylist.tracks.filter {
                    it.title.contains(playlistFilterQuery, ignoreCase = true) ||
                    it.channelTitle.contains(playlistFilterQuery, ignoreCase = true)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClosePlaylist) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る"
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedPlaylist.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "再生リスト楽曲一覧 (${selectedPlaylist.tracks.size}曲)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Playlist Header Card with Batch Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedPlaylist.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = selectedPlaylist.thumbnailUrl,
                                contentDescription = selectedPlaylist.title,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedPlaylist.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedPlaylist.author.isNotBlank()) {
                                Text(
                                    text = "作成: ${selectedPlaylist.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onImportPlaylist(selectedPlaylist, false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("プレイリストに追加")
                        }

                        Button(
                            onClick = {
                                onImportPlaylist(selectedPlaylist, true)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("全曲を保存 (DL)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search filter within playlist
            OutlinedTextField(
                value = playlistFilterQuery,
                onValueChange = { playlistFilterQuery = it },
                label = { Text("リスト内の曲を絞り込み") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPlaylistTracks, key = { it.videoId }) { item ->
                    SearchResultRow(
                        item = item,
                        onDownloadClick = {
                            onAddTrack(it.url, it.title)
                        },
                        onChannelClick = onOpenChannel
                    )
                }
            }
        } else if (selectedChannel != null) {
            // === Channel Detail View ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCloseChannel) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る"
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedChannel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "チャンネル楽曲一覧 (${channelVideos.size}曲)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Channel Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedChannel.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = selectedChannel.avatarUrl,
                            contentDescription = selectedChannel.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedChannel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedChannel.subscriberCount.isNotBlank()) {
                            Text(
                                text = selectedChannel.subscriberCount,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // In-channel search & batch add controls
            OutlinedTextField(
                value = channelFilterQuery,
                onValueChange = { channelFilterQuery = it },
                label = { Text("このチャンネル内で絞り込み") },
                placeholder = { Text("曲名やキーワードを入力") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (channelFilterQuery.isNotBlank()) {
                        IconButton(onClick = { channelFilterQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            val filteredVideos = remember(channelVideos, channelFilterQuery) {
                if (channelFilterQuery.isBlank()) channelVideos
                else channelVideos.filter {
                    it.title.contains(channelFilterQuery, ignoreCase = true) ||
                    it.rawTitle.contains(channelFilterQuery, ignoreCase = true)
                }
            }

            if (filteredVideos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val items = filteredVideos.map { Pair(it.url, it.title) }
                        onAddMultipleTracks(items)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("表示中の${filteredVideos.size}曲をすべてDLキューに追加")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingChannel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "チャンネル楽曲を読み込み中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (channelFilterQuery.isBlank()) "このチャンネルの動画を取得できませんでした"
                        else "条件に一致する楽曲が見つかりませんでした",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredVideos, key = { it.videoId }) { item ->
                        SearchResultRow(
                            item = item,
                            onDownloadClick = {
                                onAddTrack(it.url, it.title)
                            }
                        )
                    }
                }
            }
        } else {
            // === Main Search View ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "楽曲検索 & DL",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "100% オフライン完全対応・ローカルファイル自動保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { showApiKeySettings = !showApiKeySettings },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "API設定",
                        tint = if (apiKey.isNotBlank() || antigravityServerUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Settings Modal Card
            AnimatedVisibility(visible = showApiKeySettings) {
                ApiSettingsCard(
                    currentApiKey = apiKey,
                    currentServerUrl = antigravityServerUrl,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick,
                    onSave = { key, url ->
                        onSaveApiKey(key)
                        onSaveAntigravityServerUrl(url)
                        showApiKeySettings = false
                    }
                )
            }

            // Search Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            if (it.isBlank()) {
                                onClearSearch()
                                onClearSuggestions()
                            } else if (videoId == null) {
                                onQueryChange(it)
                            }
                        },
                        label = { Text("楽曲名、アーティスト名、またはURL") },
                        placeholder = { Text("曲名、アーティスト、YouTube URL") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchInput.isNotBlank()) {
                                IconButton(onClick = {
                                    searchInput = ""
                                    onClearSearch()
                                    onClearSuggestions()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            onClearSuggestions()
                            if (videoId == null && searchInput.isNotBlank()) {
                                onSearch(searchInput)
                            }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Autocomplete Suggestions List
                    if (videoId == null && searchSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(vertical = 4.dp)
                        ) {
                            searchSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchInput = suggestion
                                            keyboardController?.hide()
                                            onClearSuggestions()
                                            onSearch(suggestion)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Search Filter Chips
                    if (videoId == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SearchFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = searchFilter == filter,
                                    onClick = {
                                        onClearSuggestions()
                                        onFilterChange(filter)
                                        if (searchInput.isNotBlank() && videoId == null && searchResults.isEmpty()) {
                                            onSearch(searchInput)
                                        }
                                    },
                                    label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = if (searchFilter == filter) {
                                        @Composable {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    if (videoId == null && searchInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onClearSuggestions()
                                onSearch(searchInput)
                            },
                            enabled = !isSearching,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("検索")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct URL Input Preview Card
            if (videoId != null) {
                DirectUrlPreviewCard(
                    videoId = videoId,
                    previewThumbnail = previewThumbnail,
                    isDownloading = isDownloading,
                    onDownload = { customTitle ->
                        onAddTrack(searchInput, customTitle)
                        searchInput = ""
                    }
                )
            }

            // Matched Playlist Banner
            if (matchedPlaylist != null) {
                Text(
                    text = "再生リスト",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                PlaylistCard(
                    playlistInfo = matchedPlaylist,
                    onOpenPlaylist = onOpenPlaylist,
                    onImportPlaylist = { pl -> onImportPlaylist(pl, false) },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Matched Channel Banners
            if (matchedChannels.isNotEmpty()) {
                Text(
                    text = "チャンネル",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                matchedChannels.forEach { channel ->
                    ChannelCard(
                        channelInfo = channel,
                        onChannelClick = onOpenChannel,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // Search Results List
            if (searchResults.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "検索結果 (${searchResults.size}件)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = onClearSearch) {
                        Text("クリア")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(searchResults, key = { it.videoId }) { item ->
                        SearchResultRow(
                            item = item,
                            onDownloadClick = {
                                onAddTrack(it.url, it.title)
                            },
                            onChannelClick = onOpenChannel
                        )
                    }
                }
            }
        }
    }
}
