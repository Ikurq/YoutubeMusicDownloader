package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GoogleApiService {

    suspend fun fetchVideoMetadata(videoId: String, apiKey: String?): CleanedMetadata = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext MetadataCleaner.clean("YouTube Track ($videoId)", "YouTube Video")
        }

        try {
            val urlString = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=$videoId&key=${apiKey.trim()}"
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val snippet = items.getJSONObject(0).optJSONObject("snippet")
                    if (snippet != null) {
                        val title = snippet.optString("title")
                        val channelTitle = snippet.optString("channelTitle")
                        val description = snippet.optString("description")

                        return@withContext MetadataCleaner.clean(title, channelTitle, description)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext MetadataCleaner.clean("YouTube Track ($videoId)", "YouTube Video")
    }
}
