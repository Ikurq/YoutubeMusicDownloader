package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Matcher
import java.util.regex.Pattern

data class SearchResultItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val url: String
)

object YoutubeSearchService {

    suspend fun searchVideos(query: String, apiKey: String? = null): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        if (!apiKey.isNullOrBlank()) {
            val apiResults = searchViaApi(trimmed, apiKey)
            if (apiResults.isNotEmpty()) return@withContext apiResults
        }

        return@withContext searchViaWeb(trimmed)
    }

    private fun searchViaApi(query: String, apiKey: String): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=$encoded&maxResults=15&key=${apiKey.trim()}"
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
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.setRequestProperty("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7")

            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().use { it.readText() }

                val pattern = Pattern.compile(
                    "\"videoId\":\"([a-zA-Z0-9_-]{11})\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]\\}.*?\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]\\}"
                )
                val matcher = pattern.matcher(html)
                val seenIds = mutableSetOf<String>()

                while (matcher.find() && list.size < 12) {
                    val videoId = matcher.group(1) ?: continue
                    if (seenIds.contains(videoId)) continue
                    seenIds.add(videoId)

                    val rawTitle = unescapeUnicode(matcher.group(2) ?: "")
                    val channelTitle = unescapeUnicode(matcher.group(3) ?: "")

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

                if (list.isEmpty()) {
                    val fallbackPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
                    val fallbackMatcher = fallbackPattern.matcher(html)
                    while (fallbackMatcher.find() && list.size < 8) {
                        val videoId = fallbackMatcher.group(1) ?: continue
                        if (seenIds.contains(videoId)) continue
                        seenIds.add(videoId)

                        list.add(
                            SearchResultItem(
                                videoId = videoId,
                                title = "YouTube Song ($videoId)",
                                channelTitle = "YouTube Channel",
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

    private fun unescapeUnicode(input: String): String {
        return try {
            val regex = Pattern.compile("\\\\u([0-9a-fA-F]{4})")
            val matcher = regex.matcher(input)
            val sb = StringBuffer()
            while (matcher.find()) {
                val hex = matcher.group(1)
                val ch = hex?.toInt(16)?.toChar() ?: ' '
                matcher.appendReplacement(sb, Matcher.quoteReplacement(ch.toString()))
            }
            matcher.appendTail(sb)
            sb.toString().replace("\\\"", "\"").replace("\\\\", "\\")
        } catch (e: Exception) {
            input
        }
    }
}
