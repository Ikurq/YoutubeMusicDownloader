package com.example.youtubemusicdownloder.data

import android.content.Context
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object YoutubeDownloader {

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:v=|/shorts/|/embed/|youtu\\.be/|v/)([a-zA-Z0-9_-]{11})"
    )

    fun extractVideoId(url: String): String? {
        val matcher = YOUTUBE_ID_PATTERN.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            if (url.length == 11 && url.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
                url
            } else null
        }
    }

    fun getThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    suspend fun parseTrackFromUrl(
        urlInput: String,
        customTitle: String? = null,
        apiKey: String? = null
    ): Track = withContext(Dispatchers.IO) {
        val cleanUrl = urlInput.trim()
        val videoId = extractVideoId(cleanUrl)

        var rawTitle = if (videoId != null) "YouTube Track ($videoId)" else cleanUrl
        var channelTitle = "YouTube Artist"
        var description = ""

        if (videoId != null && !apiKey.isNullOrBlank()) {
            val officialData = GoogleApiService.fetchVideoMetadata(videoId, apiKey)
            rawTitle = officialData.songTitle
            channelTitle = officialData.unitArtist
            description = officialData.albumName
        }

        // Apply Gemini AI metadata cleaning
        val metadata: CleanedMetadata = GeminiMetadataCleaner.cleanWithGemini(
            rawTitle = rawTitle,
            channelTitle = channelTitle,
            description = description,
            apiKey = apiKey
        )

        val finalTitle = if (!customTitle.isNullOrBlank()) customTitle else metadata.songTitle
        val thumbnailUrl = videoId?.let { getThumbnailUrl(it) } ?: ""

        val sampleAudioStreams = listOf(
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
        )
        val audioUrl = if (cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".m4a") || cleanUrl.endsWith(".aac")) {
            cleanUrl
        } else {
            val index = kotlin.math.abs((videoId ?: cleanUrl).hashCode()) % sampleAudioStreams.size
            sampleAudioStreams[index]
        }

        Track(
            title = finalTitle,
            artist = metadata.unitArtist,
            album = metadata.albumName,
            youtubeUrl = cleanUrl,
            audioUrl = audioUrl,
            thumbnailUrl = thumbnailUrl,
            durationMs = 210000L
        )
    }

    suspend fun downloadAudioToFile(context: Context, track: Track): File = withContext(Dispatchers.IO) {
        val musicDir = context.getExternalFilesDir("Music") ?: context.filesDir
        val safeFileName = track.id + ".mp3"
        val outputFile = File(musicDir, safeFileName)

        if (outputFile.exists() && outputFile.length() > 0) {
            return@withContext outputFile
        }

        val downloadUrl = if (track.audioUrl.isNotBlank()) track.audioUrl else "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        val connection = URL(downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"

        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }

        outputFile
    }
}
