package com.example.youtubemusicdownloder.data

import android.content.Context
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * YouTubeダウンロードおよびメタデータ解決の統合ファサード。
 * 内部責務は [YoutubeExtractor], [YoutubeStreamResolver], [AudioDownloader] に分割されています。
 */
object YoutubeDownloader {

    fun extractVideoId(url: String): String? = YoutubeExtractor.extractVideoId(url)

    fun getThumbnailUrl(videoId: String): String = YoutubeExtractor.getThumbnailUrl(videoId)

    suspend fun fetchOEmbedInfo(videoId: String): Pair<String, String>? =
        YoutubeExtractor.fetchOEmbedInfo(videoId)

    suspend fun fetchDirectAudioUrl(videoId: String, customServerUrl: String? = null): String? =
        YoutubeStreamResolver.fetchDirectAudioUrl(videoId, customServerUrl)

    suspend fun parseTrackFromUrl(
        urlInput: String,
        customTitle: String? = null,
        apiKey: String? = null,
        customServerUrl: String? = null
    ): Track = withContext(Dispatchers.IO) {
        val cleanUrl = urlInput.trim()
        val videoId = extractVideoId(cleanUrl)

        var rawTitle = cleanUrl
        var channelTitle = ""
        var description = ""

        if (videoId != null) {
            val oembed = fetchOEmbedInfo(videoId)
            if (oembed != null) {
                rawTitle = oembed.first
                channelTitle = oembed.second
            }
        }

        if (videoId != null && !apiKey.isNullOrBlank()) {
            val officialData = GoogleApiService.fetchVideoMetadata(videoId, apiKey)
            if (officialData.songTitle.isNotBlank()) rawTitle = officialData.songTitle
            if (officialData.unitArtist.isNotBlank()) channelTitle = officialData.unitArtist
            description = officialData.albumName
        }

        val metadata = OfficialMusicResolver.resolveOfficialMetadata(
            rawTitle = rawTitle,
            channelTitle = channelTitle,
            description = description,
            apiKey = apiKey
        )

        val finalTitle = if (!customTitle.isNullOrBlank()) customTitle else metadata.songTitle
        val thumbnailUrl = if (metadata.highResCoverUrl.isNotBlank()) {
            metadata.highResCoverUrl
        } else {
            videoId?.let { getThumbnailUrl(it) } ?: ""
        }

        Track(
            title = finalTitle,
            artist = metadata.unitArtist,
            album = metadata.albumName,
            youtubeUrl = cleanUrl,
            audioUrl = "",
            thumbnailUrl = thumbnailUrl,
            durationMs = 0L // ダウンロード完了時または再生準備時に正確な長さを反映
        )
    }

    suspend fun downloadAudioToFile(
        context: Context,
        track: Track,
        customServerUrl: String? = null,
        userCookie: String? = null,
        onProgress: ((percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File = AudioDownloader.downloadAudioToFile(context, track, customServerUrl, userCookie, onProgress)
}
