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
        "https://inv.nadeko.net",
        "https://yt.artemislena.eu",
        "https://invidious.nerdvpn.de",
        "https://invidious.lunar.icu"
    )

    suspend fun fetchDirectAudioUrl(videoId: String, customServerUrl: String? = null): String? = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Try Custom Antigravity / Cobalt Server if configured
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

        // 2. Try Invidious primary nodes (bypasses LOGIN_REQUIRED)
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

        // 3. Try InnerTube API
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/player")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
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
                } else {
                    errors.add("InnerTube: $status")
                }
            } else {
                errors.add("InnerTube HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            errors.add("InnerTube ${e.message}")
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
