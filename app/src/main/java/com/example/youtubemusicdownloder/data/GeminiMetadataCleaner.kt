package com.example.youtubemusicdownloder.data

import com.example.youtubemusicdownloder.model.Playlist
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val promptText = """
                Perform an online web search and identify the official release metadata for this song:
                Song Query: "$rawTitle"
                Channel Name: "$channelTitle"
                Description Snippet: "${description.take(300)}"

                Find the EXACT official song title (楽曲名のみ), official unit/artist name (ユニット・アーティスト名), and official album/single name (アルバム名・シングル名).

                Respond ONLY with a valid JSON object with these exact keys:
                {
                  "songTitle": "exact pure song title without MV/Official tags/brackets",
                  "unitArtist": "exact unit or artist name",
                  "albumName": "exact official album or single name"
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

                // Enable Google Search Grounding for live web search
                val tools = JSONObject().apply {
                    put("googleSearch", JSONObject())
                }
                put("tools", listOf(tools))

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

    suspend fun generateSmartPlaylists(
        tracks: List<Track>,
        apiKey: String? = null
    ): List<Playlist> = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext emptyList()

        if (apiKey.isNullOrBlank()) {
            // Rule-based fallback playlists by artist
            val playlists = mutableListOf<Playlist>()
            val byArtist = tracks.groupBy { it.artist }
            for ((artist, artistTracks) in byArtist) {
                if (artistTracks.size >= 2) {
                    playlists.add(
                        Playlist(
                            name = "$artist メンバーズ選曲集",
                            trackIds = artistTracks.map { it.id }
                        )
                    )
                }
            }
            if (playlists.isEmpty() && tracks.size >= 2) {
                playlists.add(
                    Playlist(
                        name = "✨ マイソング コレクション",
                        trackIds = tracks.map { it.id }
                    )
                )
            }
            return@withContext playlists
        }

        try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}"
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val tracksArray = JSONArray()
            for (t in tracks) {
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                }
                tracksArray.put(obj)
            }

            val promptText = """
                Analyze the following list of music tracks and create 3 to 5 smart, creative, beautifully categorized playlists in Japanese (e.g. "🌸 J-POP ベストセレクション", "⚡ アニメ・ゲームテーマ集", "🌙 リラックス＆チルアウト").

                Tracks input:
                ${tracksArray.toString()}

                Respond ONLY with a valid JSON array of playlists:
                [
                  {
                    "name": "プレイリスト名 (絵文字付き)",
                    "trackIds": ["id1", "id2", "id3"]
                  }
                ]
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
                    val text = candidates.getJSONObject(0)
                        .optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.getJSONObject(0)
                        ?.optString("text")

                    if (!text.isNullOrBlank()) {
                        val parsedArray = JSONArray(text.trim())
                        val resultPlaylists = mutableListOf<Playlist>()
                        for (i in 0 until parsedArray.length()) {
                            val item = parsedArray.getJSONObject(i)
                            val name = item.optString("name", "✨ AIプレイリスト")
                            val trackIdsJson = item.optJSONArray("trackIds")
                            val ids = mutableListOf<String>()
                            if (trackIdsJson != null) {
                                for (j in 0 until trackIdsJson.length()) {
                                    ids.add(trackIdsJson.getString(j))
                                }
                            }
                            if (ids.isNotEmpty()) {
                                resultPlaylists.add(
                                    Playlist(
                                        name = name,
                                        trackIds = ids
                                    )
                                )
                            }
                        }
                        if (resultPlaylists.isNotEmpty()) {
                            return@withContext resultPlaylists
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext emptyList()
    }
}
