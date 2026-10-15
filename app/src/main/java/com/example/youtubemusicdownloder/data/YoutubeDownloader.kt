package com.example.youtubemusicdownloder.data

import android.content.Context
import android.os.Environment
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object YoutubeDownloader {

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:v=|/shorts/|/embed/|youtu\\.be/|v/)([a-zA-Z0-9_-]{11})"
    )

    private val FALLBACK_INVIDIOUS_NODES = listOf(
        "https://invidious.f5.si",
        "https://inv.nadeko.net",
        "https://yt.artemislena.eu",
        "https://invidious.perennialworks.net",
        "https://invidious.nerdvpn.de"
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

    suspend fun fetchDirectAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        // 1. Try InnerTube API (ANDROID_VR client)
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
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
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try Invidious nodes
        for (node in FALLBACK_INVIDIOUS_NODES) {
            try {
                val apiUrl = URL("$node/api/v1/videos/$videoId")
                val conn = apiUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        var bestAudioUrl: String? = null
                        var highestBitrate = 0
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val mimeType = format.optString("type")
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Dynamic Invidious instances
        try {
            val listUrl = URL("https://api.invidious.io/instances.json?sort_by=type,health")
            val conn = listUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                var tried = 0
                for (i in 0 until array.length()) {
                    if (tried >= 5) break
                    val inst = array.getJSONArray(i)
                    val domain = inst.optString(0)
                    val info = inst.optJSONObject(1)
                    val type = info?.optString("type")
                    if (type == "https" && domain.isNotBlank()) {
                        tried++
                        try {
                            val nodeUrl = URL("https://$domain/api/v1/videos/$videoId")
                            val nodeConn = nodeUrl.openConnection() as HttpURLConnection
                            nodeConn.connectTimeout = 3000
                            nodeConn.readTimeout = 3000
                            if (nodeConn.responseCode == 200) {
                                val resp = nodeConn.inputStream.bufferedReader().use { it.readText() }
                                val nodeJson = JSONObject(resp)
                                val formats = nodeJson.optJSONArray("adaptiveFormats")
                                if (formats != null) {
                                    for (j in 0 until formats.length()) {
                                        val f = formats.getJSONObject(j)
                                        val mime = f.optString("type")
                                        val audioUrl = f.optString("url")
                                        if (mime.contains("audio") && audioUrl.isNotBlank()) {
                                            return@withContext audioUrl
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        var rawTitle = if (videoId != null) "YouTube Track ($videoId)" else cleanUrl
        var channelTitle = "YouTube Artist"
        var description = ""

        if (videoId != null && !apiKey.isNullOrBlank()) {
            val officialData = GoogleApiService.fetchVideoMetadata(videoId, apiKey)
            rawTitle = officialData.songTitle
            channelTitle = officialData.unitArtist
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
            audioUrl = "", // Fresh URL will be resolved at download time
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
            throw IllegalStateException("YouTube 音声ストリームの取得に失敗しました。動画URLまたはネットワークをご確認ください。")
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
                conn.connectTimeout = 12000
                conn.readTimeout = 25000
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
                            val buffer = ByteArray(64 * 1024) // 64KB High-Speed Buffer
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
                } else {
                    throw IllegalStateException("HTTP Error $responseCode")
                }
            } catch (e: Exception) {
                lastError = e
                e.printStackTrace()
            }
        }

        if (!success || !outputFile.exists() || outputFile.length() == 0L) {
            if (outputFile.exists()) outputFile.delete()
            throw IllegalStateException("ダウンロード処理に失敗しました (${lastError?.message ?: "ネットワーク接続エラー"})")
        }

        copyToPublicMusicDir(outputFile, "$cleanTitle.m4a")
        outputFile
    }
}
