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
            val queryText = "${cleaned.unitArtist} ${cleaned.songTitle}"
                .replace("YouTube Artist", "")
                .replace("YouTube Video", "")
                .replace("YouTube Track", "")
                .trim()
                .ifBlank { rawTitle }

            val encodedTerm = URLEncoder.encode(queryText, "UTF-8")
            val apiUrl = "https://itunes.apple.com/search?term=$encodedTerm&country=JP&media=music&entity=song&limit=1"

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val results = json.optJSONArray("results")

                if (results != null && results.length() > 0) {
                    val item = results.getJSONObject(0)
                    val officialTitle = item.optString("trackName", cleaned.songTitle)
                    val officialArtist = item.optString("artistName", cleaned.unitArtist)
                    val officialAlbum = item.optString("collectionName", "")
                    val artwork100 = item.optString("artworkUrl100", "")
                    val artwork600 = artwork100.replace("100x100bb", "600x600bb")

                    // Verify title similarity to prevent completely unrelated matches (e.g. unrelated album matching)
                    val normOriginal = cleaned.songTitle.replace(Regex("[^a-zA-Z0-9\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]"), "").lowercase()
                    val normOfficial = officialTitle.replace(Regex("[^a-zA-Z0-9\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]"), "").lowercase()
                    val isFuzzyMatch = normOriginal.isNotBlank() && (
                        normOfficial.contains(normOriginal) ||
                        normOriginal.contains(normOfficial) ||
                        (normOriginal.length >= 3 && normOfficial.length >= 3 && normOriginal.take(4) == normOfficial.take(4))
                    )

                    if (officialTitle.isNotBlank() && isFuzzyMatch) {
                        return@withContext OfficialTrackMetadata(
                            songTitle = officialTitle,
                            unitArtist = if (officialArtist.contains("YouTube")) cleaned.unitArtist else officialArtist,
                            albumName = if (officialAlbum.isNotBlank()) officialAlbum else "${officialTitle} - Single",
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

        val finalAlbum = if (geminiCleaned.albumName.isNotBlank() && !geminiCleaned.albumName.contains("YouTube")) {
            geminiCleaned.albumName
        } else {
            "${geminiCleaned.songTitle} - Single"
        }

        return@withContext OfficialTrackMetadata(
            songTitle = geminiCleaned.songTitle,
            unitArtist = if (geminiCleaned.unitArtist.contains("YouTube")) channelTitle.ifBlank { "YouTube Artist" } else geminiCleaned.unitArtist,
            albumName = finalAlbum
        )
    }
}
