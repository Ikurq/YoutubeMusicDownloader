package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchResultItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val url: String,
    val durationText: String = ""
)

object YoutubeSearchService {

    suspend fun searchVideos(query: String, apiKey: String? = null): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        if (!apiKey.isNullOrBlank()) {
            val apiResults = searchViaApi(trimmed, apiKey)
            if (apiResults.isNotEmpty()) return@withContext apiResults
        }

        val innerTubeResults = searchViaInnerTube(trimmed)
        if (innerTubeResults.isNotEmpty()) return@withContext innerTubeResults

        return@withContext searchViaWeb(trimmed)
    }

    private fun searchViaInnerTube(query: String): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/search")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("query", query)
                val context = JSONObject().apply {
                    val client = JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20241216.01.00")
                        put("hl", "ja")
                        put("gl", "JP")
                    }
                    put("client", client)
                }
                put("context", context)
            }

            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val contents = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val section = contents.getJSONObject(i)
                        val itemSection = section.optJSONObject("itemSectionRenderer") ?: continue
                        val items = itemSection.optJSONArray("contents") ?: continue

                        for (j in 0 until items.length()) {
                            val item = items.getJSONObject(j)
                            val videoRenderer = item.optJSONObject("videoRenderer") ?: continue

                            val videoId = videoRenderer.optString("videoId", "")
                            if (videoId.isBlank()) continue

                            val rawTitle = parseRunsText(videoRenderer.optJSONObject("title"))
                            val channelTitle = parseRunsText(videoRenderer.optJSONObject("ownerText"))
                                .ifBlank { parseRunsText(videoRenderer.optJSONObject("longBylineText")) }
                            val durationText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""

                            if (rawTitle.isNotBlank()) {
                                val cleaned = MetadataCleaner.clean(rawTitle, channelTitle)

                                list.add(
                                    SearchResultItem(
                                        videoId = videoId,
                                        title = cleaned.songTitle,
                                        channelTitle = cleaned.unitArtist,
                                        thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                        url = "https://www.youtube.com/watch?v=$videoId",
                                        durationText = durationText
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseRunsText(jsonObj: JSONObject?): String {
        if (jsonObj == null) return ""
        val simpleText = jsonObj.optString("simpleText", "")
        if (simpleText.isNotBlank()) return simpleText

        val runs = jsonObj.optJSONArray("runs")
        if (runs != null && runs.length() > 0) {
            val sb = StringBuilder()
            for (i in 0 until runs.length()) {
                val run = runs.getJSONObject(i)
                sb.append(run.optString("text", ""))
            }
            return sb.toString().trim()
        }
        return ""
    }

    private fun searchViaApi(query: String, apiKey: String): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=$encoded&maxResults=25&key=${apiKey.trim()}"
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val items = json.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val idObj = item.optJSONObject("id")
                        val videoId = idObj?.optString("videoId") ?: continue
                        val snippet = item.optJSONObject("snippet") ?: continue

                        val rawTitle = snippet.optString("title")
                        val channelTitle = snippet.optString("channelTitle")
                        val cleaned = MetadataCleaner.clean(rawTitle, channelTitle)

                        list.add(
                            SearchResultItem(
                                videoId = videoId,
                                title = cleaned.songTitle,
                                channelTitle = cleaned.unitArtist,
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                url = "https://www.youtube.com/watch?v=$videoId"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun searchViaWeb(query: String): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://www.youtube.com/results?search_query=$encoded"
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7")

            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val videoIdPattern = java.util.regex.Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]\\}")
                val matcher = videoIdPattern.matcher(html)
                val seen = mutableSetOf<String>()

                while (matcher.find() && list.size < 20) {
                    val vId = matcher.group(1) ?: continue
                    if (seen.contains(vId)) continue
                    seen.add(vId)

                    val titleText = matcher.group(2) ?: ""
                    val cleaned = MetadataCleaner.clean(titleText, "YouTube Artist")

                    list.add(
                        SearchResultItem(
                            videoId = vId,
                            title = cleaned.songTitle,
                            channelTitle = cleaned.unitArtist,
                            thumbnailUrl = "https://img.youtube.com/vi/$vId/hqdefault.jpg",
                            url = "https://www.youtube.com/watch?v=$vId"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
