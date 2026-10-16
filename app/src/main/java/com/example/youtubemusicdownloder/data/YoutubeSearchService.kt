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
    val durationText: String = "",
    val isTopic: Boolean = false,
    val channelId: String = "",
    val channelThumbnailUrl: String = "",
    val rawTitle: String = ""
)

data class ChannelInfo(
    val channelId: String,
    val title: String,
    val avatarUrl: String = "",
    val subscriberCount: String = "",
    val videoCount: String = "",
    val description: String = ""
)

data class SearchResultData(
    val matchedChannels: List<ChannelInfo> = emptyList(),
    val videos: List<SearchResultItem> = emptyList()
)

object YoutubeSearchService {

    suspend fun searchVideos(query: String, apiKey: String? = null): List<SearchResultItem> = withContext(Dispatchers.IO) {
        return@withContext search(query, apiKey).videos
    }

    suspend fun search(query: String, apiKey: String? = null): SearchResultData = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext SearchResultData()

        // 1. If query is a channel URL (e.g. youtube.com/channel/UC...), resolve directly
        val explicitChannelId = extractChannelIdFromUrl(trimmed)
        if (explicitChannelId != null) {
            val (chInfo, chVideos) = fetchChannelDetails(explicitChannelId)
            return@withContext SearchResultData(
                matchedChannels = listOf(chInfo),
                videos = chVideos
            )
        }

        val channels = mutableListOf<ChannelInfo>()
        val videos = mutableListOf<SearchResultItem>()

        // 2. Search InnerTube (primary and most accurate search)
        val innerTubeData = searchViaInnerTube(trimmed)
        channels.addAll(innerTubeData.matchedChannels)
        videos.addAll(innerTubeData.videos)

        // 3. Fallback to official API if provided and InnerTube was empty
        if (videos.isEmpty() && !apiKey.isNullOrBlank()) {
            val apiResults = searchViaApi(trimmed, apiKey)
            videos.addAll(apiResults)
        }

        // 4. Fallback to Web scrape if still empty
        if (videos.isEmpty()) {
            videos.addAll(searchViaWeb(trimmed))
        }

        // 5. If no explicit channel matched, but top video matches query, surface top artist channel
        if (channels.isEmpty() && videos.isNotEmpty()) {
            val topWithChannel = videos.firstOrNull { it.channelId.isNotBlank() && it.channelTitle.isNotBlank() }
            if (topWithChannel != null) {
                val cleanArtist = topWithChannel.channelTitle.replace(" - Topic", "").trim()
                if (cleanArtist.contains(trimmed, ignoreCase = true) || trimmed.contains(cleanArtist, ignoreCase = true)) {
                    channels.add(
                        ChannelInfo(
                            channelId = topWithChannel.channelId,
                            title = topWithChannel.channelTitle,
                            avatarUrl = topWithChannel.channelThumbnailUrl
                        )
                    )
                }
            }
        }

        return@withContext SearchResultData(
            matchedChannels = channels.distinctBy { it.channelId },
            videos = videos.distinctBy { it.videoId }
        )
    }

    suspend fun fetchChannelDetails(channelId: String): Pair<ChannelInfo, List<SearchResultItem>> = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) {
            return@withContext Pair(ChannelInfo(channelId = "", title = "チャンネル"), emptyList())
        }

        try {
            val (info, videos) = fetchChannelViaBrowse(channelId, params = "EgZ2aWRlb3PyBgQKAjoA")
            if (videos.isNotEmpty() || info.title.isNotBlank()) {
                return@withContext Pair(info, videos)
            }
            // Fallback: try browsing channel without params (channel home tab)
            return@withContext fetchChannelViaBrowse(channelId, params = null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext Pair(ChannelInfo(channelId = channelId, title = "チャンネル"), emptyList())
    }

    private fun fetchChannelViaBrowse(channelId: String, params: String?): Pair<ChannelInfo, List<SearchResultItem>> {
        val url = URL("https://www.youtube.com/youtubei/v1/browse")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.doOutput = true

        val body = JSONObject().apply {
            put("browseId", channelId)
            if (!params.isNullOrBlank()) {
                put("params", params)
            }
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

            val meta = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
            val chTitle = meta?.optString("title", "") ?: ""
            val chAvatar = meta?.optJSONObject("avatar")?.optJSONArray("thumbnails")?.let { thumbs ->
                if (thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url") else ""
            } ?: ""
            val chDesc = meta?.optString("description", "") ?: ""

            var subCount = ""
            val pageHeaderRows = json.optJSONObject("header")
                ?.optJSONObject("pageHeaderRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("pageHeaderViewModel")
                ?.optJSONObject("metadata")
                ?.optJSONObject("contentMetadataViewModel")
                ?.optJSONArray("metadataRows")

            if (pageHeaderRows != null) {
                for (r in 0 until pageHeaderRows.length()) {
                    val rowObj = pageHeaderRows.getJSONObject(r)
                    val parts = rowObj.optJSONArray("metadataParts") ?: continue
                    for (p in 0 until parts.length()) {
                        val part = parts.getJSONObject(p)
                        val txt = part.optJSONObject("text")?.optString("content", "") ?: ""
                        if (txt.contains("人") || txt.contains("登録者") || txt.contains("subscribers", ignoreCase = true)) {
                            subCount = txt
                            break
                        }
                    }
                }
            }

            val channelInfo = ChannelInfo(
                channelId = channelId,
                title = chTitle.ifBlank { "チャンネル" },
                avatarUrl = chAvatar,
                subscriberCount = subCount,
                description = chDesc
            )

            val videos = mutableListOf<SearchResultItem>()
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")

            if (tabs != null) {
                for (t in 0 until tabs.length()) {
                    val tab = tabs.getJSONObject(t)
                    val tabRenderer = tab.optJSONObject("tabRenderer") ?: continue
                    if (tabRenderer.optBoolean("selected", false) || tabs.length() == 1) {
                        val richGrid = tabRenderer.optJSONObject("content")?.optJSONObject("richGridRenderer")
                        val contentsArray = richGrid?.optJSONArray("contents")
                        if (contentsArray != null) {
                            for (i in 0 until contentsArray.length()) {
                                val itObj = contentsArray.getJSONObject(i)
                                val rir = itObj.optJSONObject("richItemRenderer")?.optJSONObject("content") ?: continue

                                if (rir.has("lockupViewModel")) {
                                    val lockup = rir.getJSONObject("lockupViewModel")
                                    val vId = lockup.optString("contentId", "")
                                    val metaVm = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                                    val rawTitle = metaVm?.optJSONObject("title")?.optString("content", "") ?: ""

                                    var dur = ""
                                    val overlays = lockup.optJSONObject("contentImage")
                                        ?.optJSONObject("thumbnailViewModel")
                                        ?.optJSONArray("overlays")
                                    if (overlays != null && overlays.length() > 0) {
                                        val badges = overlays.getJSONObject(0)
                                            .optJSONObject("thumbnailBottomOverlayViewModel")
                                            ?.optJSONArray("badges")
                                        if (badges != null && badges.length() > 0) {
                                            dur = badges.getJSONObject(0).optJSONObject("thumbnailBadgeViewModel")?.optString("text", "") ?: ""
                                        }
                                    }

                                    if (vId.isNotBlank() && rawTitle.isNotBlank()) {
                                        val cleaned = MetadataCleaner.clean(rawTitle, chTitle)
                                        videos.add(
                                            SearchResultItem(
                                                videoId = vId,
                                                title = cleaned.songTitle.ifBlank { rawTitle },
                                                channelTitle = chTitle,
                                                thumbnailUrl = "https://img.youtube.com/vi/$vId/hqdefault.jpg",
                                                url = "https://www.youtube.com/watch?v=$vId",
                                                durationText = dur,
                                                channelId = channelId,
                                                channelThumbnailUrl = chAvatar,
                                                rawTitle = rawTitle
                                            )
                                        )
                                    }
                                } else if (rir.has("videoRenderer")) {
                                    val vr = rir.getJSONObject("videoRenderer")
                                    val vId = vr.optString("videoId", "")
                                    val rawTitle = parseRunsText(vr.optJSONObject("title"))
                                    val dur = vr.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""

                                    if (vId.isNotBlank() && rawTitle.isNotBlank()) {
                                        val cleaned = MetadataCleaner.clean(rawTitle, chTitle)
                                        videos.add(
                                            SearchResultItem(
                                                videoId = vId,
                                                title = cleaned.songTitle.ifBlank { rawTitle },
                                                channelTitle = chTitle,
                                                thumbnailUrl = "https://img.youtube.com/vi/$vId/hqdefault.jpg",
                                                url = "https://www.youtube.com/watch?v=$vId",
                                                durationText = dur,
                                                channelId = channelId,
                                                channelThumbnailUrl = chAvatar,
                                                rawTitle = rawTitle
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return Pair(channelInfo, videos)
        }

        return Pair(ChannelInfo(channelId = channelId, title = "チャンネル"), emptyList())
    }

    suspend fun fetchRelatedVideos(videoId: String, artistName: String = "", songTitle: String = ""): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()

        val list = mutableListOf<SearchResultItem>()
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/next")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("videoId", videoId)
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

                val results = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnWatchNextResults")
                    ?.optJSONObject("secondaryResults")
                    ?.optJSONObject("secondaryResults")
                    ?.optJSONArray("results")

                if (results != null) {
                    for (i in 0 until results.length()) {
                        val r = results.getJSONObject(i)
                        val lockup = r.optJSONObject("lockupViewModel")
                        if (lockup != null) {
                            val vId = lockup.optString("contentId")
                            val label = lockup.optJSONObject("rendererContext")
                                ?.optJSONObject("accessibilityContext")
                                ?.optString("label", "") ?: ""

                            if (vId.isNotBlank() && label.isNotBlank()) {
                                val cleanTitle = label.substringBefore(" 分").substringBefore(" 時間").trim()
                                val cleaned = MetadataCleaner.clean(cleanTitle, artistName)

                                list.add(
                                    SearchResultItem(
                                        videoId = vId,
                                        title = cleaned.songTitle,
                                        channelTitle = cleaned.unitArtist.ifBlank { artistName.ifBlank { "YouTube" } },
                                        thumbnailUrl = "https://img.youtube.com/vi/$vId/hqdefault.jpg",
                                        url = "https://www.youtube.com/watch?v=$vId",
                                        isTopic = cleaned.unitArtist.contains("Topic", ignoreCase = true),
                                        rawTitle = cleanTitle
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

        // Search query fallback if Next API is sparse
        if (list.size < 5 && (artistName.isNotBlank() || songTitle.isNotBlank())) {
            val searchFallback = searchVideos("$artistName $songTitle")
            list.addAll(searchFallback.filter { it.videoId != videoId })
        }

        return@withContext list.distinctBy { it.videoId }.take(15)
    }

    private fun searchViaInnerTube(query: String): SearchResultData {
        val channels = mutableListOf<ChannelInfo>()
        val videos = mutableListOf<SearchResultItem>()

        try {
            val url = URL("https://www.youtube.com/youtubei/v1/search")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

                            // Parse Channel Renderer if present
                            val channelRenderer = item.optJSONObject("channelRenderer")
                            if (channelRenderer != null) {
                                val chId = channelRenderer.optString("channelId", "")
                                val chTitle = channelRenderer.optJSONObject("title")?.optString("simpleText", "")
                                    ?: parseRunsText(channelRenderer.optJSONObject("title"))
                                val chThumbs = channelRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val chAvatar = if (chThumbs != null && chThumbs.length() > 0) {
                                    chThumbs.getJSONObject(chThumbs.length() - 1).optString("url", "")
                                } else ""
                                val subText = channelRenderer.optJSONObject("videoCountText")?.optString("simpleText", "")
                                    ?: channelRenderer.optJSONObject("subscriberCountText")?.optString("simpleText", "")
                                    ?: ""
                                if (chId.isNotBlank() && chTitle.isNotBlank()) {
                                    channels.add(
                                        ChannelInfo(
                                            channelId = chId,
                                            title = chTitle,
                                            avatarUrl = chAvatar,
                                            subscriberCount = subText
                                        )
                                    )
                                }
                            }

                            // Parse Video Renderer
                            val videoRenderer = item.optJSONObject("videoRenderer")
                            if (videoRenderer != null) {
                                val videoId = videoRenderer.optString("videoId", "")
                                if (videoId.isBlank()) continue

                                val rawTitle = parseRunsText(videoRenderer.optJSONObject("title"))
                                val ownerRuns = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")
                                val ownerObj = if (ownerRuns != null && ownerRuns.length() > 0) ownerRuns.getJSONObject(0) else null

                                val channelTitle = ownerObj?.optString("text", "")
                                    ?.ifBlank { parseRunsText(videoRenderer.optJSONObject("ownerText")) }
                                    ?.ifBlank { parseRunsText(videoRenderer.optJSONObject("longBylineText")) }
                                    ?: "YouTube"

                                val browseEndpoint = ownerObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                                val channelId = browseEndpoint?.optString("browseId", "") ?: ""

                                val channelThumbnailUrl = videoRenderer.optJSONObject("channelThumbnailSupportedRenderers")
                                    ?.optJSONObject("channelThumbnailWithLinkRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")?.let { thumbs ->
                                        if (thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url", "") else ""
                                    } ?: ""

                                val durationText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""

                                if (rawTitle.isNotBlank()) {
                                    val cleaned = MetadataCleaner.clean(rawTitle, channelTitle)
                                    val isTopic = channelTitle.contains("Topic", ignoreCase = true) || rawTitle.contains("Topic", ignoreCase = true)

                                    videos.add(
                                        SearchResultItem(
                                            videoId = videoId,
                                            title = cleaned.songTitle.ifBlank { rawTitle },
                                            channelTitle = channelTitle,
                                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                            url = "https://www.youtube.com/watch?v=$videoId",
                                            durationText = durationText,
                                            isTopic = isTopic,
                                            channelId = channelId,
                                            channelThumbnailUrl = channelThumbnailUrl,
                                            rawTitle = rawTitle
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return SearchResultData(
            matchedChannels = channels,
            videos = videos
        )
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
                        val channelId = snippet.optString("channelId", "")
                        val cleaned = MetadataCleaner.clean(rawTitle, channelTitle)
                        val isTopic = channelTitle.contains("Topic", ignoreCase = true)

                        list.add(
                            SearchResultItem(
                                videoId = videoId,
                                title = cleaned.songTitle.ifBlank { rawTitle },
                                channelTitle = channelTitle,
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                                url = "https://www.youtube.com/watch?v=$videoId",
                                isTopic = isTopic,
                                channelId = channelId,
                                rawTitle = rawTitle
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
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )

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
                    val cleaned = MetadataCleaner.clean(titleText, "YouTube")

                    list.add(
                        SearchResultItem(
                            videoId = vId,
                            title = cleaned.songTitle.ifBlank { titleText },
                            channelTitle = "YouTube",
                            thumbnailUrl = "https://img.youtube.com/vi/$vId/hqdefault.jpg",
                            url = "https://www.youtube.com/watch?v=$vId",
                            rawTitle = titleText
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun extractChannelIdFromUrl(input: String): String? {
        val regex = Regex("""(?:youtube\.com/(?:channel/|c/|user/)?|youtu\.be/)?(UC[a-zA-Z0-9_-]{22})""")
        val match = regex.find(input)
        return match?.groupValues?.get(1)
    }
}
