package com.example.youtubemusicdownloder.data

import android.content.Context
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val customTitle: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

class DownloadQueueManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var processJob: Job? = null

    private val _queue = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
    val queue: StateFlow<List<DownloadQueueItem>> = _queue.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    var onTrackDownloaded: ((track: Track) -> Unit)? = null
    var onStatusMessage: ((message: String) -> Unit)? = null

    fun addToQueue(url: String, title: String, customTitle: String? = null, apiKey: String? = null) {
        if (url.isBlank()) return
        val item = DownloadQueueItem(
            url = url,
            title = title,
            customTitle = customTitle
        )
        _queue.value = _queue.value + item
        onStatusMessage?.invoke("「$title」をDLキューに追加しました")
        triggerQueueProcessing(apiKey)
    }

    fun addMultipleToQueue(items: List<Pair<String, String>>, apiKey: String? = null) {
        val newItems = items.map { (url, title) ->
            DownloadQueueItem(url = url, title = title)
        }
        _queue.value = _queue.value + newItems
        onStatusMessage?.invoke("${items.size}件の楽曲をDLキューに追加しました")
        triggerQueueProcessing(apiKey)
    }

    fun removeFromQueue(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
    }

    fun clearCompleted() {
        _queue.value = _queue.value.filter { it.status != DownloadStatus.COMPLETED }
    }

    fun clearAll() {
        _queue.value = emptyList()
    }

    private fun triggerQueueProcessing(apiKey: String?) {
        if (_isProcessing.value) return

        processJob = scope.launch {
            _isProcessing.value = true

            while (true) {
                val currentQueue = _queue.value
                val pendingItem = currentQueue.firstOrNull { it.status == DownloadStatus.PENDING } ?: break

                // Update item status to DOWNLOADING
                updateItem(pendingItem.id) {
                    it.copy(status = DownloadStatus.DOWNLOADING, progressPercent = 0)
                }

                try {
                    // Step 1: Parse Track metadata
                    var track = YoutubeDownloader.parseTrackFromUrl(
                        urlInput = pendingItem.url,
                        customTitle = pendingItem.customTitle ?: pendingItem.title,
                        apiKey = apiKey
                    )

                    // Step 2: Download audio file with live progress
                    val file = YoutubeDownloader.downloadAudioToFile(context, track) { percent, downloaded, total ->
                        updateItem(pendingItem.id) {
                            it.copy(
                                progressPercent = percent,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        }
                    }

                    track = track.copy(
                        localFilePath = file.absolutePath,
                        isDownloaded = true
                    )

                    // Step 3: Complete & Notify
                    updateItem(pendingItem.id) {
                        it.copy(status = DownloadStatus.COMPLETED, progressPercent = 100)
                    }

                    onTrackDownloaded?.invoke(track)
                    onStatusMessage?.invoke("DL完了: 「${track.title}」")
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateItem(pendingItem.id) {
                        it.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = e.message ?: "ダウンロードエラー"
                        )
                    }
                    onStatusMessage?.invoke("DL失敗: 「${pendingItem.title}」")
                }
            }

            _isProcessing.value = false
        }
    }

    private fun updateItem(id: String, transform: (DownloadQueueItem) -> DownloadQueueItem) {
        _queue.value = _queue.value.map {
            if (it.id == id) transform(it) else it
        }
    }
}
