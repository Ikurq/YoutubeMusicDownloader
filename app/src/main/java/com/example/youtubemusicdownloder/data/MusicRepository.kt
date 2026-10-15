package com.example.youtubemusicdownloder.data

import android.content.Context
import com.example.youtubemusicdownloder.model.Playlist
import com.example.youtubemusicdownloder.model.Track
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("music_app_prefs", Context.MODE_PRIVATE)

    fun getApiKey(): String {
        return prefs.getString("google_api_key", "") ?: ""
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("google_api_key", key.trim()).apply()
    }

    fun loadTracks(): List<Track> {
        val jsonStr = prefs.getString("saved_tracks", null)
        if (jsonStr.isNullOrEmpty()) {
            val initial = getInitialDemoTracks()
            saveTracks(initial)
            return initial
        }

        val list = mutableListOf<Track>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist", "YouTube Artist"),
                        album = obj.optString("album", "Single"),
                        youtubeUrl = obj.optString("youtubeUrl"),
                        audioUrl = obj.optString("audioUrl"),
                        thumbnailUrl = obj.optString("thumbnailUrl"),
                        durationMs = obj.optLong("durationMs"),
                        localFilePath = obj.optString("localFilePath").ifEmpty { null },
                        isDownloaded = obj.optBoolean("isDownloaded"),
                        addedAt = obj.optLong("addedAt")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTracks(tracks: List<Track>) {
        val array = JSONArray()
        for (track in tracks) {
            val obj = JSONObject()
            obj.put("id", track.id)
            obj.put("title", track.title)
            obj.put("artist", track.artist)
            obj.put("album", track.album)
            obj.put("youtubeUrl", track.youtubeUrl)
            obj.put("audioUrl", track.audioUrl)
            obj.put("thumbnailUrl", track.thumbnailUrl)
            obj.put("durationMs", track.durationMs)
            obj.put("localFilePath", track.localFilePath ?: "")
            obj.put("isDownloaded", track.isDownloaded)
            obj.put("addedAt", track.addedAt)
            array.put(obj)
        }
        prefs.edit().putString("saved_tracks", array.toString()).apply()
    }

    fun loadPlaylists(): List<Playlist> {
        val jsonStr = prefs.getString("saved_playlists", null)
        if (jsonStr.isNullOrEmpty()) {
            val initial = getInitialDemoPlaylists()
            savePlaylists(initial)
            return initial
        }

        val list = mutableListOf<Playlist>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val trackIdsArray = obj.optJSONArray("trackIds")
                val trackIds = mutableListOf<String>()
                if (trackIdsArray != null) {
                    for (j in 0 until trackIdsArray.length()) {
                        trackIds.add(trackIdsArray.getString(j))
                    }
                }
                list.add(
                    Playlist(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        trackIds = trackIds,
                        createdAt = obj.optLong("createdAt")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun savePlaylists(playlists: List<Playlist>) {
        val array = JSONArray()
        for (pl in playlists) {
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            val trackIdsArr = JSONArray()
            pl.trackIds.forEach { trackIdsArr.put(it) }
            obj.put("trackIds", trackIdsArr)
            obj.put("createdAt", pl.createdAt)
            array.put(obj)
        }
        prefs.edit().putString("saved_playlists", array.toString()).apply()
    }

    private fun getInitialDemoTracks(): List<Track> {
        return listOf(
            Track(
                id = "demo_1",
                title = "Cyberpunk Beats",
                artist = "SynthWave Unit",
                album = "Future Neon",
                youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                durationMs = 372000L
            ),
            Track(
                id = "demo_2",
                title = "Chill Out Groove",
                artist = "LoFi Lounge",
                album = "Midnight Session",
                youtubeUrl = "https://www.youtube.com/watch?v=kJQP7kiw5Fk",
                audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                thumbnailUrl = "https://img.youtube.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
                durationMs = 423000L
            ),
            Track(
                id = "demo_3",
                title = "Acoustic Melody",
                artist = "Sunset Trio",
                album = "Acoustic Journey",
                youtubeUrl = "https://www.youtube.com/watch?v=3JZ_D3ELwOQ",
                audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                thumbnailUrl = "https://img.youtube.com/vi/3JZ_D3ELwOQ/hqdefault.jpg",
                durationMs = 350000L
            )
        )
    }

    private fun getInitialDemoPlaylists(): List<Playlist> {
        return listOf(
            Playlist(
                id = "pl_favorites",
                name = "お気に入り (Favorites)",
                trackIds = listOf("demo_1", "demo_2", "demo_3")
            )
        )
    }
}
