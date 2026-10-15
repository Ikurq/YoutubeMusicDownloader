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

    private fun getPublicMusicDirectory(context: Context): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val targetFolder = File(publicDir, "MusicPlayerDL")
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }
        return if (targetFolder.exists() && targetFolder.canWrite()) {
            targetFolder
        } else {
            context.getExternalFilesDir("Music") ?: context.filesDir
        }
    }

    suspend fun fetchDirectAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        // 1. Try InnerTube API (ANDROID_VR client)
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
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
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

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

        // 3. Dynamic fallback
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

        val metadata: CleanedMetadata = GeminiMetadataCleaner.cleanWithGemini(
            rawTitle = rawTitle,
            channelTitle = channelTitle,
            description = description,
            apiKey = apiKey
        )

        val finalTitle = if (!customTitle.isNullOrBlank()) customTitle else metadata.songTitle
        val thumbnailUrl = videoId?.let { getThumbnailUrl(it) } ?: ""

        val directAudioUrl = if (videoId != null) {
            fetchDirectAudioUrl(videoId) ?: ""
        } else if (cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".m4a") || cleanUrl.endsWith(".aac") || cleanUrl.endsWith(".webm")) {
            cleanUrl
        } else ""

        Track(
            title = finalTitle,
            artist = metadata.unitArtist,
            album = metadata.albumName,
            youtubeUrl = cleanUrl,
            audioUrl = directAudioUrl,
            thumbnailUrl = thumbnailUrl,
            durationMs = 210000L
        )
    }

    suspend fun downloadAudioToFile(context: Context, track: Track): File = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(track.youtubeUrl)
        var streamUrl = track.audioUrl

        if (streamUrl.isBlank() && videoId != null) {
            streamUrl = fetchDirectAudioUrl(videoId) ?: ""
        }

        if (streamUrl.isBlank()) {
            throw IllegalStateException("YouTube 音声ストリームの取得に失敗しました。URLをご確認ください。")
        }

        val targetDir = getPublicMusicDirectory(context)
        val cleanTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val safeFileName = "${cleanTitle}.m4a"
        val outputFile = File(targetDir, safeFileName)

        if (outputFile.exists() && outputFile.length() > 0) {
            return@withContext outputFile
        }

        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )

        if (connection.responseCode in 200..299) {
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            throw IllegalStateException("ダウンロードサーバーエラー: HTTP ${connection.responseCode}")
        }

        outputFile
    }
}
