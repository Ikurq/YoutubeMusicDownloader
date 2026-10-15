package com.example.youtubemusicdownloder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.youtubemusicdownloder.data.YoutubeDownloader

@Composable
fun UrlSearchScreen(
    apiKey: String,
    isDownloading: Boolean,
    onSaveApiKey: (key: String) -> Unit,
    onAddTrack: (url: String, customTitle: String?, autoDownload: Boolean) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var autoDownload by remember { mutableStateOf(true) }
    var showApiKeySettings by remember { mutableStateOf(false) }
    var inputApiKey by remember(apiKey) { mutableStateOf(apiKey) }

    val videoId = remember(urlInput) { YoutubeDownloader.extractVideoId(urlInput) }
    val previewThumbnail = remember(videoId) { videoId?.let { YoutubeDownloader.getThumbnailUrl(it) } }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "YouTube URL検索 & DL",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "楽曲名・ユニット・アルバム情報を綺麗に解析します",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showApiKeySettings = !showApiKeySettings }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Google API設定",
                    tint = if (apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }

        // Google API Key Section
        AnimatedVisibility(visible = showApiKeySettings) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Google Data API v3 キー設定",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Google Cloud Consoleで取得したYouTube Data API v3キーを設定すると、公式のユニット名・楽曲名・アルバム情報を自動抽出できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        label = { Text("Google API キー") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onSaveApiKey(inputApiKey)
                            showApiKeySettings = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("APIキーを保存")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // URL Field
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("YouTube URL または 動画ID") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Sample Links
        Text(
            text = "サンプルURLで試す:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            OutlinedButton(
                onClick = { urlInput = "https://www.youtube.com/watch?v=dQw4w9WgXcQ" },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("サンプル 1", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { urlInput = "https://www.youtube.com/watch?v=kJQP7kiw5Fk" }
            ) {
                Text("サンプル 2", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Custom Title
        OutlinedTextField(
            value = customTitle,
            onValueChange = { customTitle = it },
            label = { Text("タイトル手動指定 (任意)") },
            placeholder = { Text("未指定時は【MV】等のタグを除去した楽曲名のみ自動設定") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Download Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = autoDownload,
                onCheckedChange = { autoDownload = it }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "ローカル端末にファイルを保存する",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "オフライン環境・バックグラウンド再生が可能です",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Preview Card
        if (videoId != null || previewThumbnail != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!previewThumbnail.isNullOrEmpty()) {
                        AsyncImage(
                            model = previewThumbnail,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .width(90.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "YouTube 検出完了",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: $videoId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Add / Download Button
        Button(
            onClick = {
                onAddTrack(urlInput, customTitle.ifBlank { null }, autoDownload)
                urlInput = ""
                customTitle = ""
            },
            enabled = urlInput.isNotBlank() && !isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(24.dp)
                        .width(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("処理中...")
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("楽曲を追加・保存する", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
