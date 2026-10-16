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

    fun getAntigravityServerUrl(): String {
        return prefs.getString("antigravity_server_url", "") ?: ""
    }

    fun saveAntigravityServerUrl(url: String) {
        prefs.edit().putString("antigravity_server_url", url.trim()).apply()
    }

    fun loadTracks(): List<Track> {
        val jsonStr = prefs.getString("saved_tracks", null)
        if (jsonStr.isNullOrEmpty()) {
            return emptyList()
        }

        val list = mutableListOf<Track>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                val audioUrl = obj.optString("audioUrl")

                if (id.startsWith("demo_") || audioUrl.contains("soundhelix.com")) {
                    continue
                }

                list.add(
                    Track(
                        id = id,
                        title = obj.optString("title"),
                        artist = obj.optString("artist", "YouTube Artist"),
                        album = obj.optString("album", "Single"),
                        youtubeUrl = obj.optString("youtubeUrl"),
                        audioUrl = if (audioUrl.contains("soundhelix.com")) "" else audioUrl,
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

        saveTracks(list)
        return list
    }

    fun saveTracks(tracks: List<Track>) {
        val cleanList = tracks.filter { !it.id.startsWith("demo_") && !it.audioUrl.contains("soundhelix.com") }
        val array = JSONArray()
        for (track in cleanList) {
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
            return emptyList()
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
                        val tId = trackIdsArray.getString(j)
                        if (!tId.startsWith("demo_")) {
                            trackIds.add(tId)
                        }
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
            pl.trackIds.filter { !it.startsWith("demo_") }.forEach { trackIdsArr.put(it) }
            obj.put("trackIds", trackIdsArr)
            obj.put("createdAt", pl.createdAt)
            array.put(obj)
        }
        prefs.edit().putString("saved_playlists", array.toString()).apply()
    }
}
