package com.example.youtubemusicdownloder.data

import android.content.Context
import android.os.Environment
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object YoutubeDownloader {

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:v=|/shorts/|/embed/|youtu\\.be/|v/)([a-zA-Z0-9_-]{11})"
    )

    private val CANDIDATE_NODES = listOf(
        "https://invidious.f5.si",
        "https://inv.nadeko.net",
        "https://yt.artemislena.eu",
        "https://invidious.perennialworks.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.lunar.icu"
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

    private fun getPrimaryStorageDir(context: Context): File {
        val appMusicDir = context.getExternalFilesDir("Music") ?: context.filesDir
        if (!appMusicDir.exists()) appMusicDir.mkdirs()
        return appMusicDir
    }

    private fun copyToPublicMusicDir(sourceFile: File, fileName: String) {
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val appDir = File(publicDir, "MusicPlayerDL")
            if (!appDir.exists()) appDir.mkdirs()
            val destFile = File(appDir, fileName)
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchOEmbedInfo(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val conn = URL(oembedUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val title = json.optString("title", "")
                val author = json.optString("author_name", "")
                if (title.isNotBlank()) {
                    return@withContext Pair(title, author)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun fetchDirectAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. InnerTube API with 15s timeout
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.53.22")
                    }
                    put("client", clientObj)
                }
                put("context", contextObj)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val status = json.optJSONObject("playabilityStatus")?.optString("status")

                if (status == "OK") {
                    val streamingData = json.optJSONObject("streamingData")
                    val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        var bestAudioUrl: String? = null
                        var highestBitrate = 0
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val mimeType = format.optString("mimeType")
                            val audioUrl = format.optString("url")
                            val bitrate = format.optInt("bitrate", 0)

                            if (mimeType.contains("audio") && audioUrl.isNotBlank()) {
                                if (bitrate > highestBitrate) {
                                    highestBitrate = bitrate
                                    bestAudioUrl = audioUrl
                                }
                            }
                        }
                        if (bestAudioUrl != null) {
                            return@withContext bestAudioUrl
                        }
                    }
                } else {
                    errors.add("InnerTube: $status")
                }
            } else {
                errors.add("InnerTube HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            errors.add("InnerTube: ${e.message}")
        }

        // 2. Parallel Node Racing with 15s timeout
        try {
            val parallelResult = coroutineScope {
                val jobs = CANDIDATE_NODES.map { node ->
                    async(Dispatchers.IO) {
                        try {
                            val apiUrl = URL("$node/api/v1/videos/$videoId")
                            val conn = apiUrl.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connectTimeout = 15000
                            conn.readTimeout = 20000

                            if (conn.responseCode == 200) {
                                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                                val json = JSONObject(responseStr)
                                val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                                if (adaptiveFormats != null) {
                                    for (i in 0 until adaptiveFormats.length()) {
                                        val format = adaptiveFormats.getJSONObject(i)
                                        val mimeType = format.optString("type")
                                        val audioUrl = format.optString("url")
                                        if (mimeType.contains("audio") && audioUrl.isNotBlank()) {
                                            return@async audioUrl
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore individual node failure
                        }
                        null
                    }
                }

                var winnerUrl: String? = null
                for (j in jobs) {
                    val res = j.await()
                    if (res != null && winnerUrl == null) {
                        winnerUrl = res
                        break
                    }
                }
                winnerUrl
            }

            if (parallelResult != null) {
                return@withContext parallelResult
            }
        } catch (e: Exception) {
            errors.add("ParallelRace: ${e.message}")
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException("音源取得失敗: ${errors.take(2).joinToString("; ")}")
        }

        return@withContext null
    }

    suspend fun parseTrackFromUrl(
        urlInput: String,
        customTitle: String? = null,
        apiKey: String? = null
    ): Track = withContext(Dispatchers.IO) {
        val cleanUrl = urlInput.trim()
        val videoId = extractVideoId(cleanUrl)

        var rawTitle = cleanUrl
        var channelTitle = ""
        var description = ""

        // Fetch real video title & author using YouTube oEmbed API if URL
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
            durationMs = 210000L
        )
    }

    suspend fun downloadAudioToFile(
        context: Context,
        track: Track,
        onProgress: ((percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(track.youtubeUrl) ?: extractVideoId(track.audioUrl)

        var streamUrl = if (videoId != null) {
            fetchDirectAudioUrl(videoId) ?: track.audioUrl
        } else track.audioUrl

        if (streamUrl.isBlank() && videoId != null) {
            streamUrl = fetchDirectAudioUrl(videoId) ?: ""
        }

        if (streamUrl.isBlank()) {
            throw IllegalStateException("YouTube 音声ストリームの取得に失敗しました。動画ID: $videoId")
        }

        val primaryDir = getPrimaryStorageDir(context)
        val cleanTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "track" }
        val safeFileName = "${cleanTitle}_${track.id.take(8)}.m4a"
        val outputFile = File(primaryDir, safeFileName)

        if (outputFile.exists() && outputFile.length() > 0) {
            onProgress?.invoke(100, outputFile.length(), outputFile.length())
            copyToPublicMusicDir(outputFile, "$cleanTitle.m4a")
            return@withContext outputFile
        }

        var attempts = 0
        var success = false
        var lastError: Exception? = null

        while (attempts < 3 && !success) {
            attempts++
            try {
                val activeUrl = if (attempts > 1 && videoId != null) {
                    fetchDirectAudioUrl(videoId) ?: streamUrl
                } else streamUrl

                val conn = URL(activeUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 30000
                conn.readTimeout = 45000
                conn.requestMethod = "GET"
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Referer", "https://www.youtube.com/")

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val totalLength = conn.contentLengthLong.coerceAtLeast(1L)

                    conn.inputStream.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val percent = ((totalRead * 100) / totalLength).toInt().coerceIn(0, 100)
                                onProgress?.invoke(percent, totalRead, totalLength)
                            }
                        }
                    }
                    success = true
                } else if (responseCode == 403 || responseCode == 410) {
                    if (videoId != null) {
                        val freshUrl = fetchDirectAudioUrl(videoId)
                        if (freshUrl != null) streamUrl = freshUrl
                    }
                    lastError = IllegalStateException("HTTP Error $responseCode (トークン期限切れ)")
                } else {
                    lastError = IllegalStateException("HTTP Error $responseCode")
                }
            } catch (e: Exception) {
                lastError = e
                e.printStackTrace()
            }
        }

        if (!success || !outputFile.exists() || outputFile.length() == 0L) {
            if (outputFile.exists()) outputFile.delete()
            throw IllegalStateException("DL失敗: ${lastError?.message ?: "接続タイムアウト/エラー"}")
        }

        copyToPublicMusicDir(outputFile, "$cleanTitle.m4a")
        outputFile
    }
}
