package com.example.youtubemusicdownloder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    val rawTitle: String = "",
    val albumName: String = ""
)

data class ChannelInfo(
    val channelId: String,
    val title: String,
    val avatarUrl: String = "",
    val subscriberCount: String = "",
    val videoCount: String = "",
    val description: String = ""
)

data class PlaylistInfo(
    val playlistId: String,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String = "",
    val trackCount: Int = 0,
    val description: String = "",
    val tracks: List<SearchResultItem> = emptyList()
)

data class SearchResultData(
    val matchedChannels: List<ChannelInfo> = emptyList(),
    val matchedPlaylist: PlaylistInfo? = null,
    val matchedPlaylists: List<PlaylistInfo> = emptyList(),
    val videos: List<SearchResultItem> = emptyList()
)

enum class SearchFilter(val label: String) {
    ALL("すべて (公式優先)"),
    SONGS("公式楽曲"),
    VIDEOS("MV・動画"),
    PLAYLISTS("再生リスト"),
    YOUTUBE("YouTube全般")
}

object YoutubeSearchService {

    suspend fun searchVideos(
        query: String,
        apiKey: String? = null,
        filter: SearchFilter = SearchFilter.ALL
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        return@withContext search(query, apiKey, filter).videos
    }

    suspend fun search(
        query: String,
        apiKey: String? = null,
        filter: SearchFilter = SearchFilter.ALL,
        cookie: String? = null
    ): SearchResultData = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext SearchResultData()

        // 0. If query is a playlist URL or playlist ID, resolve playlist directly
        val explicitPlaylistId = YoutubeExtractor.extractPlaylistId(trimmed)
        if (explicitPlaylistId != null) {
            val plInfo = fetchPlaylistDetails(explicitPlaylistId, cookie = cookie, apiKey = apiKey)
            if (plInfo != null) {
                return@withContext SearchResultData(
                    matchedPlaylist = plInfo,
                    videos = plInfo.tracks
                )
            }
        }

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
        val playlists = mutableListOf<PlaylistInfo>()
        val videos = mutableListOf<SearchResultItem>()

        when (filter) {
            SearchFilter.PLAYLISTS -> {
                val plList = searchPlaylists(trimmed, apiKey, cookie)
                return@withContext SearchResultData(
                    matchedPlaylists = plList,
                    matchedPlaylist = plList.firstOrNull(),
                    videos = emptyList()
                )
            }

            SearchFilter.SONGS, SearchFilter.VIDEOS -> {
                // Targeted YouTube Music search
                val ytMusicData = searchViaYoutubeMusic(trimmed, filter)
                channels.addAll(ytMusicData.matchedChannels)
                videos.addAll(ytMusicData.videos)

                // Fallback to standard InnerTube if YouTube Music returned nothing
                if (videos.isEmpty()) {
                    val fallback = searchViaInnerTube(trimmed)
                    channels.addAll(fallback.matchedChannels)
                    playlists.addAll(fallback.matchedPlaylists)
                    val filtered = fallback.videos
                        .filter { !isShortsOrTooLong(it.durationText, it.rawTitle, trimmed) }
                        .filter { !isNoiseVideo(it.rawTitle, it.channelTitle, trimmed) }
                    videos.addAll(filtered)
                }
            }

            SearchFilter.YOUTUBE -> {
                // Standard YouTube search with noise filtering & reranking
                val ytData = searchViaInnerTube(trimmed)
                channels.addAll(ytData.matchedChannels)
                playlists.addAll(ytData.matchedPlaylists)
                val filtered = ytData.videos
                    .filter { !isShortsOrTooLong(it.durationText, it.rawTitle, trimmed) }
                    .filter { !isNoiseVideo(it.rawTitle, it.channelTitle, trimmed) }
                    .sortedByDescending { calculateRelevanceScore(it, trimmed) }
                videos.addAll(filtered)
            }

            SearchFilter.ALL -> {
                // High-precision combined search:
                // Step A: YouTube Music search for official audio tracks & artist card
                val ytMusicData = searchViaYoutubeMusic(trimmed, SearchFilter.ALL)
                channels.addAll(ytMusicData.matchedChannels)

                // Step B: Standard YouTube search for official MVs and related video entries
                val ytData = searchViaInnerTube(trimmed)
                channels.addAll(ytData.matchedChannels)
                playlists.addAll(ytData.matchedPlaylists)

                val cleanYtVideos = ytData.videos
                    .filter { !isShortsOrTooLong(it.durationText, it.rawTitle, trimmed) }
                    .filter { !isNoiseVideo(it.rawTitle, it.channelTitle, trimmed) }

                // Merge with priority: YT Music official songs first, followed by clean YouTube results
                videos.addAll(ytMusicData.videos)
                videos.addAll(cleanYtVideos)
            }
        }

        // Fallback to official API if provided and list was empty
        if (videos.isEmpty() && !apiKey.isNullOrBlank()) {
            val apiResults = searchViaApi(trimmed, apiKey)
            videos.addAll(apiResults.filter { !isNoiseVideo(it.rawTitle, it.channelTitle, trimmed) })
        }

        // Fallback to Web scrape if still empty
        if (videos.isEmpty()) {
            videos.addAll(searchViaWeb(trimmed))
        }

        // Surface artist channel if not explicitly detected in channel card
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

        // Deduplicate and apply final ranking
        val finalVideos = videos.distinctBy { it.videoId }
            .sortedByDescending { calculateRelevanceScore(it, trimmed) }

        val finalPlaylists = playlists.distinctBy { it.playlistId }

        return@withContext SearchResultData(
            matchedChannels = channels.distinctBy { it.channelId },
            matchedPlaylists = finalPlaylists,
            matchedPlaylist = finalPlaylists.firstOrNull(),
            videos = finalVideos
        )
    }

    suspend fun fetchSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArr = JSONArray(text)
                val list = mutableListOf<String>()
                val suggestions = jsonArr.optJSONArray(1)
                if (suggestions != null) {
                    for (i in 0 until suggestions.length()) {
                        val s = suggestions.optString(i, "")
                        if (s.isNotBlank() && !s.equals(trimmed, ignoreCase = true)) {
                            list.add(s)
                        }
                    }
                }
                return@withContext list.take(6)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
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

    suspend fun fetchPlaylistDetails(
        playlistId: String,
        cookie: String? = null,
        apiKey: String? = null
    ): PlaylistInfo? = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext null

        val cleanPlId = playlistId.trim()
        val browseId = if (cleanPlId.startsWith("VL")) cleanPlId else "VL$cleanPlId"

        // 1. Try YouTube Music InnerTube (browse API)
        try {
            val ytmResult = fetchPlaylistViaYtmBrowse(browseId, cleanPlId, cookie)
            if (ytmResult != null && (ytmResult.tracks.isNotEmpty() || ytmResult.title.isNotBlank())) {
                return@withContext ytmResult
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try YouTube Web InnerTube browse API
        try {
            val webResult = fetchPlaylistViaWebBrowse(browseId, cleanPlId, cookie)
            if (webResult != null && (webResult.tracks.isNotEmpty() || webResult.title.isNotBlank())) {
                return@withContext webResult
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Try YouTube Data API v3 if API key is provided
        if (!apiKey.isNullOrBlank()) {
            try {
                val apiResult = fetchPlaylistViaApi(cleanPlId, apiKey)
                if (apiResult != null) {
                    return@withContext apiResult
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext null
    }

    private fun fetchPlaylistViaYtmBrowse(browseId: String, rawPlaylistId: String, cookie: String?): PlaylistInfo? {
        try {
            val url = URL("https://music.youtube.com/youtubei/v1/browse")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Origin", "https://music.youtube.com")
            conn.setRequestProperty("Referer", "https://music.youtube.com/")
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
                val sapisid = UserAccountManager.extractCookieValue(cookie, "SAPISID")
                    ?: UserAccountManager.extractCookieValue(cookie, "__Secure-3PAPISID")
                if (sapisid != null) {
                    val authHash = UserAccountManager.generateSapisidHash(sapisid, "https://music.youtube.com")
                    conn.setRequestProperty("Authorization", "SAPISIDHASH $authHash")
                }
            }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("browseId", browseId)
                val context = JSONObject().apply {
                    val client = JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20241216.01.00")
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

                var title = ""
                var author = ""
                var thumbnailUrl = ""

                val twoColumn = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
                val tabs = twoColumn?.optJSONArray("tabs")
                val tabSections = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                val secSections = twoColumn?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                // Combine section lists from both tabs and secondaryContents
                val allSections = mutableListOf<JSONObject>()
                if (tabSections != null) {
                    for (i in 0 until tabSections.length()) {
                        tabSections.optJSONObject(i)?.let { allSections.add(it) }
                    }
                }
                if (secSections != null) {
                    for (i in 0 until secSections.length()) {
                        secSections.optJSONObject(i)?.let { allSections.add(it) }
                    }
                }

                val headerObj = json.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")
                    ?: allSections.firstNotNullOfOrNull { it.optJSONObject("musicResponsiveHeaderRenderer") }

                if (headerObj != null) {
                    title = parseRunsText(headerObj.optJSONObject("title"))
                    author = parseRunsText(headerObj.optJSONObject("straplineTextOne"))
                        .ifBlank { parseRunsText(headerObj.optJSONObject("subtitle")) }
                    thumbnailUrl = headerObj.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")?.let { thumbs ->
                            if (thumbs.length() > 0) thumbs.getJSONObject(thumbs.length() - 1).optString("url") else ""
                        } ?: ""
                }

                if (title.isBlank()) {
                    title = json.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")?.optString("title", "") ?: ""
                }

                val videos = mutableListOf<SearchResultItem>()

                for (sec in allSections) {
                    val shelf = sec.optJSONObject("musicPlaylistShelfRenderer")
                        ?: sec.optJSONObject("musicShelfRenderer")
                    val items = shelf?.optJSONArray("contents")
                    if (items != null) {
                        for (j in 0 until items.length()) {
                            val itemObj = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer")
                            if (itemObj != null) {
                                parseMusicResponsiveListItem(itemObj)?.let { videos.add(it) }
                            }
                        }
                    }
                }

                if (videos.isNotEmpty() || title.isNotBlank()) {
                    return PlaylistInfo(
                        playlistId = rawPlaylistId,
                        title = title.ifBlank { "再生リスト" },
                        author = author.ifBlank { "YouTube" },
                        thumbnailUrl = thumbnailUrl.ifBlank {
                            if (videos.isNotEmpty()) videos.first().thumbnailUrl else ""
                        },
                        trackCount = videos.size,
                        tracks = videos.distinctBy { it.videoId }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun fetchPlaylistViaWebBrowse(browseId: String, rawPlaylistId: String, cookie: String?): PlaylistInfo? {
        try {
            val url = URL("https://www.youtube.com/youtubei/v1/browse")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            if (!cookie.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", cookie)
            }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("browseId", browseId)
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

                var title = json.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")?.optString("title", "") ?: ""
                val header = json.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")
                if (title.isBlank() && header != null) {
                    title = parseRunsText(header.optJSONObject("title"))
                }

                var author = ""
                if (header != null) {
                    author = parseRunsText(header.optJSONObject("ownerText"))
                }

                val videos = mutableListOf<SearchResultItem>()
                val tabs = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                val sectionList = tabs?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                val sections = sectionList?.optJSONArray("contents")

                if (sections != null) {
                    for (i in 0 until sections.length()) {
                        val sec = sections.getJSONObject(i)
                        val itemSec = sec.optJSONObject("itemSectionRenderer")
                        val itemContents = itemSec?.optJSONArray("contents")
                        if (itemContents != null) {
                            for (j in 0 until itemContents.length()) {
                                val listRenderer = itemContents.getJSONObject(j).optJSONObject("playlistVideoListRenderer")
                                val videoItems = listRenderer?.optJSONArray("contents")
                                if (videoItems != null) {
                                    for (k in 0 until videoItems.length()) {
                                        val vObj = videoItems.getJSONObject(k).optJSONObject("playlistVideoRenderer")
                                        if (vObj != null) {
                                            val vid = vObj.optString("videoId", "")
                                            val vTitle = parseRunsText(vObj.optJSONObject("title"))
                                            val vAuthor = parseRunsText(vObj.optJSONObject("shortBylineText"))
                                            val vDuration = vObj.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""
                                            if (vid.isNotBlank() && vTitle.isNotBlank()) {
                                                videos.add(
                                                    SearchResultItem(
                                                        videoId = vid,
                                                        title = vTitle,
                                                        channelTitle = vAuthor.ifBlank { "YouTube" },
                                                        thumbnailUrl = YoutubeExtractor.getThumbnailUrl(vid),
                                                        url = "https://www.youtube.com/watch?v=$vid",
                                                        durationText = vDuration,
                                                        rawTitle = vTitle
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (videos.isNotEmpty() || title.isNotBlank()) {
                    return PlaylistInfo(
                        playlistId = rawPlaylistId,
                        title = title.ifBlank { "再生リスト" },
                        author = author.ifBlank { "YouTube" },
                        thumbnailUrl = if (videos.isNotEmpty()) videos.first().thumbnailUrl else "",
                        trackCount = videos.size,
                        tracks = videos.distinctBy { it.videoId }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun fetchPlaylistViaApi(playlistId: String, apiKey: String): PlaylistInfo? {
        try {
            var title = "再生リスト"
            var author = "YouTube"
            var thumb = ""
            val metaUrl = "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&id=$playlistId&key=$apiKey"
            val metaConn = URL(metaUrl).openConnection() as HttpURLConnection
            metaConn.connectTimeout = 8000
            metaConn.readTimeout = 8000
            if (metaConn.responseCode == 200) {
                val json = JSONObject(metaConn.inputStream.bufferedReader().use { it.readText() })
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val snippet = items.getJSONObject(0).optJSONObject("snippet")
                    title = snippet?.optString("title", title) ?: title
                    author = snippet?.optString("channelTitle", author) ?: author
                    thumb = snippet?.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url", "") ?: ""
                }
            }

            val itemsUrl = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=$playlistId&key=$apiKey"
            val itemsConn = URL(itemsUrl).openConnection() as HttpURLConnection
            itemsConn.connectTimeout = 8000
            itemsConn.readTimeout = 8000
            if (itemsConn.responseCode == 200) {
                val json = JSONObject(itemsConn.inputStream.bufferedReader().use { it.readText() })
                val items = json.optJSONArray("items")
                val videos = mutableListOf<SearchResultItem>()
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val snippet = items.getJSONObject(i).optJSONObject("snippet") ?: continue
                        val resId = snippet.optJSONObject("resourceId")
                        val vid = resId?.optString("videoId", "") ?: ""
                        val itemTitle = snippet.optString("title", "")
                        val itemChannel = snippet.optString("videoOwnerChannelTitle", "")
                            .ifBlank { snippet.optString("channelTitle", "YouTube") }
                        val itemThumb = snippet.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                            ?: YoutubeExtractor.getThumbnailUrl(vid)

                        if (vid.isNotBlank() && itemTitle.isNotBlank() && itemTitle != "Private video" && itemTitle != "Deleted video") {
                            videos.add(
                                SearchResultItem(
                                    videoId = vid,
                                    title = itemTitle,
                                    channelTitle = itemChannel,
                                    thumbnailUrl = itemThumb,
                                    url = "https://www.youtube.com/watch?v=$vid",
                                    rawTitle = itemTitle
                                )
                            )
                        }
                    }
                }
                return PlaylistInfo(
                    playlistId = playlistId,
                    title = title,
                    author = author,
                    thumbnailUrl = thumb.ifBlank { if (videos.isNotEmpty()) videos.first().thumbnailUrl else "" },
                    trackCount = videos.size,
                    tracks = videos
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun searchViaYoutubeMusic(query: String, filter: SearchFilter = SearchFilter.ALL): SearchResultData {
        val channels = mutableListOf<ChannelInfo>()
        val videos = mutableListOf<SearchResultItem>()

        val params = when (filter) {
            SearchFilter.SONGS -> "EgWKAQIIAWoIEAUQCRADEAQ="
            SearchFilter.VIDEOS -> "EgWKAQIQAWoIEAUQCRADEAQ="
            else -> "EgWKAQIIAWoIEAUQCRADEAQ="
        }

        try {
            val url = URL("https://music.youtube.com/youtubei/v1/search")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Origin", "https://music.youtube.com")
            conn.setRequestProperty("Referer", "https://music.youtube.com/")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("query", query)
                if (params.isNotBlank()) {
                    put("params", params)
                }
                val context = JSONObject().apply {
                    val client = JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20241216.01.00")
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
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val section = contents.getJSONObject(i)

                        // 1. musicCardShelfRenderer (Top artist/song card)
                        val card = section.optJSONObject("musicCardShelfRenderer")
                        if (card != null) {
                            val cardTitle = parseRunsText(card.optJSONObject("title"))
                            val cardSub = parseRunsText(card.optJSONObject("subtitle"))
                            val titleRuns = card.optJSONObject("title")?.optJSONArray("runs")
                            val cardBrowseId = if (titleRuns != null && titleRuns.length() > 0) {
                                titleRuns.getJSONObject(0)
                                    .optJSONObject("navigationEndpoint")
                                    ?.optJSONObject("browseEndpoint")
                                    ?.optString("browseId", "") ?: ""
                            } else ""

                            val thumbs = card.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            val avatarUrl = if (thumbs != null && thumbs.length() > 0) {
                                thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                            } else ""

                            if (cardBrowseId.startsWith("UC") && cardTitle.isNotBlank()) {
                                channels.add(
                                    ChannelInfo(
                                        channelId = cardBrowseId,
                                        title = cardTitle,
                                        avatarUrl = avatarUrl,
                                        subscriberCount = cardSub
                                    )
                                )
                            }

                            // Card contents (top tracks)
                            val cardContents = card.optJSONArray("contents")
                            if (cardContents != null) {
                                for (k in 0 until cardContents.length()) {
                                    val itemObj = cardContents.getJSONObject(k).optJSONObject("musicResponsiveListItemRenderer")
                                    if (itemObj != null) {
                                        parseMusicResponsiveListItem(itemObj)?.let { videos.add(it) }
                                    }
                                }
                            }
                        }

                        // 2. musicShelfRenderer
                        val shelf = section.optJSONObject("musicShelfRenderer")
                        if (shelf != null) {
                            val items = shelf.optJSONArray("contents")
                            if (items != null) {
                                for (k in 0 until items.length()) {
                                    val itemObj = items.getJSONObject(k).optJSONObject("musicResponsiveListItemRenderer")
                                    if (itemObj != null) {
                                        parseMusicResponsiveListItem(itemObj)?.let { videos.add(it) }
                                    }
                                }
                            }
                        }

                        // 3. itemSectionRenderer
                        val itemSection = section.optJSONObject("itemSectionRenderer")
                        if (itemSection != null) {
                            val subContents = itemSection.optJSONArray("contents")
                            if (subContents != null) {
                                for (k in 0 until subContents.length()) {
                                    val itemObj = subContents.getJSONObject(k).optJSONObject("musicResponsiveListItemRenderer")
                                    if (itemObj != null) {
                                        parseMusicResponsiveListItem(itemObj)?.let { videos.add(it) }
                                    }
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
            matchedChannels = channels.distinctBy { it.channelId },
            videos = videos.distinctBy { it.videoId }
        )
    }

    private fun parseMusicResponsiveListItem(item: JSONObject): SearchResultItem? {
        val flexCols = item.optJSONArray("flexColumns") ?: return null
        if (flexCols.length() == 0) return null

        val col0Text = flexCols.getJSONObject(0)
            .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val title = parseRunsText(col0Text).ifBlank { return null }

        var videoId = item.optJSONObject("playlistItemData")?.optString("videoId", "") ?: ""
        if (videoId.isBlank()) {
            videoId = item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId", "") ?: ""
        }
        if (videoId.isBlank()) {
            val runs0 = col0Text?.optJSONArray("runs")
            if (runs0 != null && runs0.length() > 0) {
                videoId = runs0.getJSONObject(0)
                    .optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
            }
        }
        if (videoId.isBlank()) return null

        var artistName = ""
        var albumName = ""
        var durationText = ""
        var channelId = ""

        if (flexCols.length() > 1) {
            val col1Text = flexCols.getJSONObject(1)
                .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
            val runs1 = col1Text?.optJSONArray("runs")
            if (runs1 != null) {
                for (k in 0 until runs1.length()) {
                    val r = runs1.getJSONObject(k)
                    val text = r.optString("text", "").trim()
                    if (text.isBlank() || text == "•" || text == "·" || text == "・") continue

                    val bId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId", "") ?: ""

                    if (Regex("""^\d+:\d+(?::\d+)?$""").matches(text)) {
                        durationText = text
                    } else if (bId.startsWith("UC")) {
                        artistName = text
                        channelId = bId
                    } else if (bId.startsWith("MPREb_") || bId.startsWith("FEmusic_")) {
                        albumName = text
                    } else if (artistName.isBlank() && text != "曲" && text != "動画" && text != "シングル" && text != "アルバム" && !text.contains("視聴")) {
                        artistName = text
                    } else if (albumName.isBlank() && text != "曲" && text != "動画" && text != "シングル" && text != "アルバム" && !text.contains("視聴")) {
                        albumName = text
                    }
                }
            }
        }

        if (artistName.isBlank()) artistName = "YouTube Music"

        var thumbUrl = ""
        val thumbObj = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        if (thumbObj != null && thumbObj.length() > 0) {
            thumbUrl = thumbObj.getJSONObject(thumbObj.length() - 1).optString("url", "")
        }
        if (thumbUrl.isBlank()) {
            thumbUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        }

        val cleaned = MetadataCleaner.clean(title, artistName)

        return SearchResultItem(
            videoId = videoId,
            title = cleaned.songTitle.ifBlank { title },
            channelTitle = artistName,
            thumbnailUrl = thumbUrl,
            url = "https://www.youtube.com/watch?v=$videoId",
            durationText = durationText,
            isTopic = true,
            channelId = channelId,
            channelThumbnailUrl = "",
            rawTitle = title,
            albumName = albumName
        )
    }

    private fun isShortsOrTooLong(durationText: String, title: String, query: String): Boolean {
        if (title.contains("#shorts", ignoreCase = true)) return true
        val isQueryLookingForLong = query.contains("作業用") || query.contains("bgm", ignoreCase = true) ||
                query.contains("耐久") || query.contains("メドレー") || query.contains("playlist", ignoreCase = true) ||
                query.contains("mix", ignoreCase = true)

        val parts = durationText.trim().split(":")
        if (parts.size == 2) {
            val min = parts[0].toIntOrNull() ?: 0
            val sec = parts[1].toIntOrNull() ?: 0
            // 60 seconds or less is likely a Short or teaser
            if (min == 0 && sec <= 60) return true
        } else if (parts.size >= 3 && !isQueryLookingForLong) {
            // Hours long (e.g. 1:00:00+) when user didn't ask for long BGM/mix
            val hours = parts[0].toIntOrNull() ?: 0
            if (hours >= 1) return true
        }
        return false
    }

    private fun isNoiseVideo(title: String, channelTitle: String, query: String): Boolean {
        val qLower = query.lowercase()
        val allowCovers = qLower.contains("歌ってみた") || qLower.contains("cover") || qLower.contains("カバー")
        val allowReaction = qLower.contains("リアクション") || qLower.contains("reaction")
        val allowDance = qLower.contains("踊ってみた") || qLower.contains("dance")
        val allowInstrument = qLower.contains("弾いてみた") || qLower.contains("叩いてみた")

        val tLower = title.lowercase()
        if (!allowCovers && (tLower.contains("歌ってみた") || tLower.contains("covered by") || tLower.contains("[cover]") || tLower.contains("(cover)"))) {
            return true
        }
        if (!allowReaction && (tLower.contains("リアクション") || tLower.contains("reaction") || tLower.contains("切り抜き"))) {
            return true
        }
        if (!allowDance && tLower.contains("踊ってみた")) {
            return true
        }
        if (!allowInstrument && (tLower.contains("弾いてみた") || tLower.contains("叩いてみた"))) {
            return true
        }
        return false
    }

    private fun calculateRelevanceScore(item: SearchResultItem, query: String): Int {
        var score = 0
        val qLower = query.lowercase().trim()
        val tLower = item.title.lowercase()
        val chLower = item.channelTitle.lowercase()

        // 1. YouTube Music / Topic official audio is highest priority
        if (item.isTopic || chLower.endsWith("- topic")) {
            score += 100
        }

        // 2. Official artist channels & official MV markers
        if (chLower.contains("official") || item.rawTitle.contains("【MV】") || item.rawTitle.contains("[MV]") ||
            item.rawTitle.contains("Official Music Video", ignoreCase = true) || item.rawTitle.contains("Official Video", ignoreCase = true)) {
            score += 50
        }

        // 3. Exact matching bonuses
        if (tLower == qLower) {
            score += 40
        } else if (tLower.contains(qLower)) {
            score += 20
        }

        val queryTokens = qLower.split(" ").filter { it.isNotBlank() }
        for (token in queryTokens) {
            if (tLower.contains(token)) score += 10
            if (chLower.contains(token)) score += 10
        }

        return score
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

    private fun searchViaInnerTube(query: String, params: String? = null): SearchResultData {
        val channels = mutableListOf<ChannelInfo>()
        val playlists = mutableListOf<PlaylistInfo>()
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

                            // Parse Playlist Renderer if present
                            val playlistRenderer = item.optJSONObject("playlistRenderer")
                            if (playlistRenderer != null) {
                                val plId = playlistRenderer.optString("playlistId", "")
                                val plTitle = parseRunsText(playlistRenderer.optJSONObject("title"))
                                val plAuthor = parseRunsText(playlistRenderer.optJSONObject("shortBylineText"))
                                    .ifBlank { parseRunsText(playlistRenderer.optJSONObject("longBylineText")) }
                                val videoCountStr = playlistRenderer.optString("videoCount", "")
                                    .ifBlank { parseRunsText(playlistRenderer.optJSONObject("videoCountText")) }
                                val vCount = Regex("""\d+""").find(videoCountStr)?.value?.toIntOrNull() ?: 0

                                val thumbs = playlistRenderer.optJSONObject("thumbnails")
                                    ?.optJSONArray("thumbnails")
                                    ?: playlistRenderer.optJSONArray("thumbnails")
                                val plThumb = if (thumbs != null && thumbs.length() > 0) {
                                    thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                                } else ""

                                if (plId.isNotBlank() && plTitle.isNotBlank()) {
                                    playlists.add(
                                        PlaylistInfo(
                                            playlistId = plId,
                                            title = plTitle,
                                            author = plAuthor.ifBlank { "YouTube" },
                                            thumbnailUrl = plThumb,
                                            trackCount = vCount
                                        )
                                    )
                                }
                            }

                            // Parse Lockup ViewModel (Playlist / Album)
                            val lockupVm = item.optJSONObject("lockupViewModel")
                            if (lockupVm != null) {
                                var cId = lockupVm.optString("contentId", "")
                                val cType = lockupVm.optString("contentType", "")

                                val watchEndpoint = lockupVm.optJSONObject("itemPlayback")
                                    ?.optJSONObject("inlinePlayerData")
                                    ?.optJSONObject("onSelect")
                                    ?.optJSONObject("innertubeCommand")
                                    ?.optJSONObject("watchEndpoint")
                                    ?: lockupVm.optJSONObject("rendererContext")
                                        ?.optJSONObject("commandContext")
                                        ?.optJSONObject("onTap")
                                        ?.optJSONObject("innertubeCommand")
                                        ?.optJSONObject("watchEndpoint")

                                if (cId.isBlank() && watchEndpoint != null) {
                                    cId = watchEndpoint.optString("playlistId", "")
                                }

                                if (cId.startsWith("PL") || cId.startsWith("OLAK5uy_") || cType.contains("PLAYLIST", ignoreCase = true)) {
                                    val meta = lockupVm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                                    val plTitle = meta?.optJSONObject("title")?.optString("content", "") ?: ""
                                    var plAuthor = ""
                                    val metaRows = meta?.optJSONObject("metadata")?.optJSONArray("metadataRows")
                                        ?: meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                                    if (metaRows != null && metaRows.length() > 0) {
                                        for (rIdx in 0 until metaRows.length()) {
                                            val parts = metaRows.getJSONObject(rIdx).optJSONArray("metadataParts")
                                            if (parts != null && parts.length() > 0) {
                                                val pText = parts.getJSONObject(0).optJSONObject("text")?.optString("content", "") ?: ""
                                                if (pText.isNotBlank()) {
                                                    plAuthor = pText
                                                    break
                                                }
                                            }
                                        }
                                    }

                                    // Extract high-quality thumbnail
                                    var thumbUrl = ""
                                    val contentImage = lockupVm.optJSONObject("contentImage")
                                    if (contentImage != null) {
                                        // 1. collectionThumbnailViewModel (Standard YouTube search playlist format)
                                        val collThumb = contentImage.optJSONObject("collectionThumbnailViewModel")
                                        val primaryThumb = collThumb?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
                                        val sources = primaryThumb?.optJSONObject("image")?.optJSONArray("sources")
                                        if (sources != null && sources.length() > 0) {
                                            thumbUrl = sources.getJSONObject(sources.length() - 1).optString("url", "")
                                        }

                                        // 2. Direct thumbnailViewModel
                                        if (thumbUrl.isBlank()) {
                                            val directThumbs = contentImage.optJSONObject("thumbnailViewModel")?.optJSONArray("image")
                                            if (directThumbs != null && directThumbs.length() > 0) {
                                                thumbUrl = directThumbs.getJSONObject(directThumbs.length() - 1).optString("url", "")
                                            }
                                        }
                                    }
                                    // 3. Fallback to videoId from watchEndpoint
                                    if (thumbUrl.isBlank() && watchEndpoint != null) {
                                        val vId = watchEndpoint.optString("videoId", "")
                                        if (vId.isNotBlank()) {
                                            thumbUrl = "https://img.youtube.com/vi/$vId/hqdefault.jpg"
                                        }
                                    }

                                    // Extract track count from overlays
                                    var vCount = 0
                                    val collThumb = contentImage?.optJSONObject("collectionThumbnailViewModel")
                                    val overlays = collThumb?.optJSONObject("primaryThumbnail")
                                        ?.optJSONObject("thumbnailViewModel")
                                        ?.optJSONArray("overlays")
                                    if (overlays != null && overlays.length() > 0) {
                                        for (oIdx in 0 until overlays.length()) {
                                            val overlay = overlays.getJSONObject(oIdx)
                                            val badges = overlay.optJSONObject("thumbnailOverlayBadgeViewModel")?.optJSONArray("thumbnailBadges")
                                            if (badges != null && badges.length() > 0) {
                                                val bText = badges.getJSONObject(0).optJSONObject("thumbnailBadgeViewModel")?.optString("text", "") ?: ""
                                                val num = Regex("""\d+""").find(bText)?.value?.toIntOrNull()
                                                if (num != null && num > 0) {
                                                    vCount = num
                                                    break
                                                }
                                            }
                                        }
                                    }

                                    if (cId.isNotBlank() && plTitle.isNotBlank()) {
                                        playlists.add(
                                            PlaylistInfo(
                                                playlistId = cId,
                                                title = plTitle,
                                                author = plAuthor.ifBlank { "YouTube" },
                                                thumbnailUrl = thumbUrl,
                                                trackCount = vCount
                                            )
                                        )
                                    }
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
            matchedPlaylists = playlists.distinctBy { it.playlistId },
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

    suspend fun searchPlaylists(
        query: String,
        apiKey: String? = null,
        cookie: String? = null
    ): List<PlaylistInfo> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val list = mutableListOf<PlaylistInfo>()

        // 1. InnerTube WEB search with playlist filter params ("EgIQAw==")
        try {
            val innerTubeData = searchViaInnerTube(trimmed, params = "EgIQAw==")
            list.addAll(innerTubeData.matchedPlaylists)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. If empty and query does not already contain "playlist" or "再生リスト", append keyword
        if (list.isEmpty()) {
            try {
                val innerTubeData = searchViaInnerTube("$trimmed プレイリスト")
                list.addAll(innerTubeData.matchedPlaylists)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to YouTube Data API v3 if API key is provided and list is still empty
        if (list.isEmpty() && !apiKey.isNullOrBlank()) {
            try {
                val apiPlaylists = searchPlaylistsViaApi(trimmed, apiKey)
                list.addAll(apiPlaylists)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext list.distinctBy { it.playlistId }
    }

    private fun searchPlaylistsViaApi(query: String, apiKey: String): List<PlaylistInfo> {
        val list = mutableListOf<PlaylistInfo>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=playlist&q=$encoded&maxResults=20&key=${apiKey.trim()}"
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
                        val playlistId = idObj?.optString("playlistId") ?: continue
                        val snippet = item.optJSONObject("snippet") ?: continue

                        val title = snippet.optString("title")
                        val channelTitle = snippet.optString("channelTitle")
                        val thumbnails = snippet.optJSONObject("thumbnails")
                        val thumbUrl = thumbnails?.optJSONObject("high")?.optString("url")
                            ?: thumbnails?.optJSONObject("medium")?.optString("url")
                            ?: thumbnails?.optJSONObject("default")?.optString("url")
                            ?: ""

                        if (playlistId.isNotBlank() && title.isNotBlank()) {
                            list.add(
                                PlaylistInfo(
                                    playlistId = playlistId,
                                    title = title,
                                    author = channelTitle.ifBlank { "YouTube" },
                                    thumbnailUrl = thumbUrl,
                                    trackCount = 0
                                )
                            )
                        }
                    }
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
