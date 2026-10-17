package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object YoutubeStreamResolver {

    private val PRIMARY_INVIDIOUS_NODES = listOf(
        "https://invidious.f5.si",
        "https://invidious.projectsegfau.lt",
        "https://yewtu.be",
        "https://iv.melmac.space"
    )

    private data class ClientConfig(
        val endpoint: String,
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val referer: String,
        val origin: String,
        val isMusic: Boolean = false,
        val androidSdkVersion: Int? = null
    )

    private val INNER_TUBE_CLIENTS = listOf(
        ClientConfig(
            endpoint = "https://music.youtube.com/youtubei/v1/player",
            clientName = "WEB_REMIX",
            clientVersion = "1.20240708.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            referer = "https://music.youtube.com/",
            origin = "https://music.youtube.com",
            isMusic = true
        ),
        ClientConfig(
            endpoint = "https://music.youtube.com/youtubei/v1/player",
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.02.52",
            userAgent = "com.google.android.apps.youtube.music/7.02.52 (Linux; U; Android 14; ja_JP)",
            referer = "https://music.youtube.com/",
            origin = "https://music.youtube.com",
            isMusic = true,
            androidSdkVersion = 34
        ),
        ClientConfig(
            endpoint = "https://www.youtube.com/youtubei/v1/player",
            clientName = "ANDROID",
            clientVersion = "19.29.37",
            userAgent = "com.google.android.youtube/19.29.37 (Linux; U; Android 14; ja_JP)",
            referer = "https://www.youtube.com/",
            origin = "https://www.youtube.com",
            isMusic = false,
            androidSdkVersion = 34
        ),
        ClientConfig(
            endpoint = "https://www.youtube.com/youtubei/v1/player",
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1",
            referer = "https://www.youtube.com/tv",
            origin = "https://www.youtube.com",
            isMusic = false
        ),
        ClientConfig(
            endpoint = "https://www.youtube.com/youtubei/v1/player",
            clientName = "ANDROID_VR",
            clientVersion = "1.53.22",
            userAgent = "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0",
            referer = "https://www.youtube.com/",
            origin = "https://www.youtube.com",
            isMusic = false
        )
    )

    private fun requestInnerTube(
        videoId: String,
        client: ClientConfig,
        userCookie: String?
    ): Pair<String?, String?> {
        try {
            val url = URL(client.endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", client.userAgent)
            conn.setRequestProperty("Referer", client.referer)
            conn.setRequestProperty("Origin", client.origin)

            if (!userCookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", userCookie)
                val sapisid = UserAccountManager.extractCookieValue(userCookie, "SAPISID")
                    ?: UserAccountManager.extractCookieValue(userCookie, "__Secure-3PAPISID")
                if (sapisid != null) {
                    val authOrigin = if (client.isMusic) "https://music.youtube.com" else "https://www.youtube.com"
                    val sapisidHash = UserAccountManager.generateSapisidHash(sapisid, authOrigin)
                    conn.setRequestProperty("Authorization", "SAPISIDHASH $sapisidHash")
                    conn.setRequestProperty("X-Origin", authOrigin)
                }
                conn.setRequestProperty("X-Goog-AuthUser", "0")
            }
            conn.setRequestProperty("X-Goog-Api-Key", "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")

            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("videoId", videoId)
                val contextObj = JSONObject().apply {
                    val clientObj = JSONObject().apply {
                        put("clientName", client.clientName)
                        put("clientVersion", client.clientVersion)
                        put("hl", "ja")
                        put("gl", "JP")
                        if (client.androidSdkVersion != null) {
                            put("androidSdkVersion", client.androidSdkVersion)
                        }
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
                            val mimeType = format.optString("mimeType", format.optString("type"))
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
                            return Pair(bestAudioUrl, null)
                        }
                    }
                    return Pair(null, "${client.clientName}: No stream")
                } else {
                    return Pair(null, "${client.clientName}: $status")
                }
            } else {
                return Pair(null, "${client.clientName} HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            return Pair(null, "${client.clientName}: ${e.message}")
        }
    }

    suspend fun fetchDirectAudioUrl(
        videoId: String,
        customServerUrl: String? = null,
        userCookie: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Direct Official InnerTube Clients (bypasses WEB_REMIX UNPLAYABLE restrictions via ANDROID/TV fallbacks)
        for (client in INNER_TUBE_CLIENTS) {
            val (audioUrl, err) = requestInnerTube(videoId, client, userCookie)
            if (!audioUrl.isNullOrBlank()) {
                return@withContext audioUrl
            }
            if (err != null) {
                errors.add(err)
            }
        }

        // 2. Try Custom Antigravity / Cobalt Server if configured
        if (!customServerUrl.isNullOrBlank()) {
            try {
                val serverUrl = if (customServerUrl.endsWith("/")) customServerUrl else "$customServerUrl/"
                val url = URL(serverUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("url", "https://www.youtube.com/watch?v=$videoId")
                    put("downloadMode", "audio")
                }

                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    val audioUrl = json.optString("url", "").ifBlank { json.optString("audio", "") }
                    if (audioUrl.isNotBlank()) {
                        return@withContext audioUrl
                    }
                }
            } catch (e: Exception) {
                errors.add("AntigravityServer: ${e.message}")
            }
        }

        // 3. Try Invidious primary nodes as fallback
        for (node in PRIMARY_INVIDIOUS_NODES) {
            try {
                val apiUrl = URL("$node/api/v1/videos/$videoId")
                val conn = apiUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
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
                } else {
                    errors.add("$node HTTP ${conn.responseCode}")
                }
            } catch (e: Exception) {
                errors.add("$node ${e.message}")
            }
        }

        // 4. Dynamic Live Invidious Pool fallback
        try {
            val listUrl = URL("https://api.invidious.io/instances.json?sort_by=type,health")
            val conn = listUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
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
                            nodeConn.connectTimeout = 4000
                            nodeConn.readTimeout = 4000
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
                            errors.add("$domain ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("DynamicPool ${e.message}")
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException(errors.take(2).joinToString("; "))
        }

        return@withContext null
    }
}
