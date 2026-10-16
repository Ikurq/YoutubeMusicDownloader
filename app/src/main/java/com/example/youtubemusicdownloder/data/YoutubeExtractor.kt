package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object YoutubeExtractor {

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:v=|/shorts/|/embed/|youtu\\.be/|v/)([a-zA-Z0-9_-]{11})"
    )

    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        val matcher = YOUTUBE_ID_PATTERN.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
                trimmed
            } else null
        }
    }

    fun getThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
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
}
