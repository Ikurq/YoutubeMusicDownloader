package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiMetadataCleaner {

    suspend fun cleanWithGemini(
        rawTitle: String,
        channelTitle: String = "",
        description: String = "",
        apiKey: String? = null
    ): CleanedMetadata = withContext(Dispatchers.IO) {
        val fallback = MetadataCleaner.clean(rawTitle, channelTitle, description)

        if (apiKey.isNullOrBlank()) {
            return@withContext fallback
        }

        try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}"
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val promptText = """
                You are an expert music metadata cleaner. Analyze the following YouTube video info and extract the clean song title, unit/artist name, and album name.
                Raw Title: "$rawTitle"
                Channel Name: "$channelTitle"
                Description Snippet: "${description.take(300)}"

                Respond ONLY with a valid JSON object with these exact keys:
                {
                  "songTitle": "exact pure song title without MV/Official tags/brackets",
                  "unitArtist": "unit or artist name",
                  "albumName": "album or single title"
                }
            """.trimIndent()

            val body = JSONObject().apply {
                val contents = JSONObject().apply {
                    val parts = JSONObject().apply {
                        put("text", promptText)
                    }
                    put("parts", listOf(parts))
                }
                put("contents", listOf(contents))

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode == 200) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        if (text.isNotBlank()) {
                            val parsed = JSONObject(text.trim())
                            val songTitle = parsed.optString("songTitle", fallback.songTitle)
                            val unitArtist = parsed.optString("unitArtist", fallback.unitArtist)
                            val albumName = parsed.optString("albumName", fallback.albumName)

                            if (songTitle.isNotBlank()) {
                                return@withContext CleanedMetadata(
                                    songTitle = songTitle,
                                    unitArtist = if (unitArtist.isNotBlank()) unitArtist else fallback.unitArtist,
                                    albumName = if (albumName.isNotBlank()) albumName else fallback.albumName
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext fallback
    }
}
