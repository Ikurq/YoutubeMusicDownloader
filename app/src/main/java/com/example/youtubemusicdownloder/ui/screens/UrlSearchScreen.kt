package com.example.youtubemusicdownloder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.youtubemusicdownloder.data.SearchResultItem
import com.example.youtubemusicdownloder.data.YoutubeExtractor
import com.example.youtubemusicdownloder.ui.components.ApiSettingsCard
import com.example.youtubemusicdownloder.ui.components.DirectUrlPreviewCard
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
    onAddTrack: (url: String, customTitle: String?) -> Unit
) {
    var searchInput by remember { mutableStateOf("") }
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
        // Header
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = {
                        searchInput = it
                        if (it.isBlank()) onClearSearch()
                    },
                    label = { Text("楽曲名、アーティスト名、または YouTube URL") },
                    placeholder = { Text("検索キーワード または YouTube URLを入力") },
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
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        if (videoId == null && searchInput.isNotBlank()) {
                            onSearch(searchInput)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White
                    )
                )

                if (videoId == null && searchInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
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
                items(searchResults) { item ->
                    SearchResultRow(
                        item = item,
                        onDownloadClick = {
                            onAddTrack(it.url, it.title)
                        }
                    )
                }
            }
        }
    }
}
