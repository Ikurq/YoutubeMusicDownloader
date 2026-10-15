package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OfficialTrackMetadata(
    val songTitle: String,
    val unitArtist: String,
    val albumName: String,
    val highResCoverUrl: String = ""
)

object OfficialMusicResolver {

    suspend fun resolveOfficialMetadata(
        rawTitle: String,
        channelTitle: String = "",
        description: String = "",
        apiKey: String? = null
    ): OfficialTrackMetadata = withContext(Dispatchers.IO) {
        val cleaned = MetadataCleaner.clean(rawTitle, channelTitle, description)

        // 1. Query iTunes Official Music Database API (Japan Region & Global)
        try {
            val searchTerm = "${cleaned.unitArtist} ${cleaned.songTitle}".ifBlank { rawTitle }
            val encodedTerm = URLEncoder.encode(searchTerm, "UTF-8")
            val apiUrl = "https://itunes.apple.com/search?term=$encodedTerm&country=JP&media=music&entity=song&limit=1"

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val results = json.optJSONArray("results")

                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val officialTitle = item.optString("trackName", cleaned.songTitle)
                    val officialArtist = item.optString("artistName", cleaned.unitArtist)
                    val officialAlbum = item.optString("collectionName", "Single")
                    val artwork100 = item.optString("artworkUrl100", "")
                    val artwork600 = artwork100.replace("100x100bb", "600x600bb")

                    if (officialAlbum.isNotBlank() && officialTitle.isNotBlank()) {
                        return@withContext OfficialTrackMetadata(
                            songTitle = officialTitle,
                            unitArtist = officialArtist,
                            albumName = officialAlbum,
                            highResCoverUrl = artwork600
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Secondary fallback via Gemini AI Grounding
        val geminiCleaned = GeminiMetadataCleaner.cleanWithGemini(rawTitle, channelTitle, description, apiKey)

        return@withContext OfficialTrackMetadata(
            songTitle = geminiCleaned.songTitle,
            unitArtist = geminiCleaned.unitArtist,
            albumName = if (geminiCleaned.albumName.isBlank()) "Single" else geminiCleaned.albumName
        )
    }
}
