package com.example.youtubemusicdownloder.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

object AudioDownloader {

    fun getPrimaryStorageDir(context: Context): File {
        val appMusicDir = context.getExternalFilesDir("Music") ?: context.filesDir
        if (!appMusicDir.exists()) appMusicDir.mkdirs()
        return appMusicDir
    }

    fun getAudioDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun copyToPublicMusicDir(context: Context, sourceFile: File, title: String, artist: String, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MusicPlayerDL")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri: Uri? = resolver.insert(audioCollection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val appDir = File(publicDir, "MusicPlayerDL")
                if (!appDir.exists()) appDir.mkdirs()
                val destFile = File(appDir, fileName)
                sourceFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun downloadAudioToFile(
        context: Context,
        track: Track,
        customServerUrl: String? = null,
        userCookie: String? = null,
        onProgress: ((percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val effectiveCookie = if (!userCookie.isNullOrBlank()) userCookie else UserAccountManager(context).getCookie().ifBlank { null }
        val videoId = YoutubeExtractor.extractVideoId(track.youtubeUrl) ?: YoutubeExtractor.extractVideoId(track.audioUrl)

        var streamUrl: String? = null
        var lastResolverError: String? = null

        // 1. Primary Attempt: Resolve stream from the track's original videoId (with user authentication if logged in)
        if (videoId != null) {
            try {
                streamUrl = YoutubeStreamResolver.fetchDirectAudioUrl(videoId, customServerUrl, effectiveCookie)
            } catch (e: Exception) {
                lastResolverError = e.message
            }
        }

        if (streamUrl.isNullOrBlank() && track.audioUrl.isNotBlank()) {
            streamUrl = track.audioUrl
        }

        // 2. Intelligent Auto-Fallback: If primary stream is blocked (e.g. YouTube Topic Content ID restriction HTTP 500/403),
        // automatically search for an alternative official MV or clean video upload of the same song
        if (streamUrl.isNullOrBlank() && track.title.isNotBlank()) {
            try {
                val searchQuery = "${track.title} ${track.artist}".trim()
                val candidateVideos = YoutubeSearchService.searchVideos(searchQuery, filter = SearchFilter.VIDEOS)
                    .filter { it.videoId != videoId }

                for (candidate in candidateVideos.take(4)) {
                    try {
                        val fallbackStream = YoutubeStreamResolver.fetchDirectAudioUrl(candidate.videoId, customServerUrl)
                        if (!fallbackStream.isNullOrBlank()) {
                            streamUrl = fallbackStream
                            break
                        }
                    } catch (_: Exception) {
                        // Continue trying next candidate
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (streamUrl.isNullOrBlank()) {
            val errorReason = lastResolverError ?: "YouTube 音声ストリームの取得に失敗しました。動画ID: $videoId"
            throw IllegalStateException(errorReason)
        }

        val primaryDir = getPrimaryStorageDir(context)
        val cleanTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "track" }
        val safeFileName = "${cleanTitle}_${track.id.take(8)}.m4a"
        val outputFile = File(primaryDir, safeFileName)

        if (outputFile.exists() && outputFile.length() > 0) {
            onProgress?.invoke(100, outputFile.length(), outputFile.length())
            copyToPublicMusicDir(context, outputFile, track.title, track.artist, "$cleanTitle.m4a")
            return@withContext outputFile
        }

        var attempts = 0
        var success = false
        var lastError: Exception? = null

        while (attempts < 3 && !success) {
            attempts++
            try {
                val candidateUrl = if (attempts > 1 && videoId != null) {
                    YoutubeStreamResolver.fetchDirectAudioUrl(videoId, customServerUrl, effectiveCookie) ?: streamUrl
                } else streamUrl

                if (candidateUrl.isNullOrBlank()) {
                    continue
                }
                val activeUrl: String = candidateUrl

                // Probe Content-Length & Range capability
                val probeConn = URL(activeUrl).openConnection() as HttpURLConnection
                probeConn.instanceFollowRedirects = true
                probeConn.connectTimeout = 15000
                probeConn.readTimeout = 20000
                probeConn.requestMethod = "GET"
                probeConn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                probeConn.setRequestProperty("Accept", "*/*")
                probeConn.setRequestProperty("Referer", "https://www.youtube.com/")
                probeConn.setRequestProperty("Range", "bytes=0-1023")

                val probeCode = probeConn.responseCode
                if (probeCode == 206) {
                    val contentRange = probeConn.getHeaderField("Content-Range")
                    val totalLength = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: probeConn.contentLengthLong
                    probeConn.disconnect()

                    if (totalLength >= 512 * 1024) {
                        // Multi-part concurrent download (4 chunks parallel) to bypass YouTube bandwidth throttling
                        val multiSuccess = downloadMultiPart(activeUrl, outputFile, totalLength, chunkCount = 4, onProgress)
                        if (multiSuccess) {
                            success = true
                        } else {
                            if (outputFile.exists()) outputFile.delete()
                            // Fallback to single stream if multi-part encountered network failure
                            val directConn = URL(activeUrl).openConnection() as HttpURLConnection
                            directConn.instanceFollowRedirects = true
                            directConn.connectTimeout = 15000
                            directConn.readTimeout = 30000
                            directConn.requestMethod = "GET"
                            directConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            directConn.setRequestProperty("Accept", "*/*")
                            directConn.setRequestProperty("Referer", "https://www.youtube.com/")

                            if (directConn.responseCode in 200..299) {
                                success = downloadSingleStream(directConn, outputFile, totalLength, onProgress)
                            }
                        }
                    } else {
                        // Small file: single connection
                        val directConn = URL(activeUrl).openConnection() as HttpURLConnection
                        directConn.instanceFollowRedirects = true
                        directConn.connectTimeout = 15000
                        directConn.readTimeout = 30000
                        directConn.requestMethod = "GET"
                        directConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        directConn.setRequestProperty("Accept", "*/*")
                        directConn.setRequestProperty("Referer", "https://www.youtube.com/")

                        if (directConn.responseCode in 200..299) {
                            success = downloadSingleStream(directConn, outputFile, totalLength, onProgress)
                        }
                    }
                } else if (probeCode in 200..299) {
                    // Server does not support Range (returned full 200 OK), stream directly
                    val totalLength = probeConn.contentLengthLong.coerceAtLeast(1L)
                    success = downloadSingleStream(probeConn, outputFile, totalLength, onProgress)
                } else if (probeCode == 403 || probeCode == 410) {
                    if (videoId != null) {
                        val freshUrl = YoutubeStreamResolver.fetchDirectAudioUrl(videoId, customServerUrl, effectiveCookie)
                        if (freshUrl != null) streamUrl = freshUrl
                    }
                    lastError = IllegalStateException("HTTP Error $probeCode (トークン期限切れ)")
                } else {
                    lastError = IllegalStateException("HTTP Error $probeCode")
                }
            } catch (e: Exception) {
                lastError = e
                e.printStackTrace()
            }
        }

        if (!success || !outputFile.exists() || outputFile.length() == 0L) {
            if (outputFile.exists()) outputFile.delete()
            throw IllegalStateException("DL失敗: ${lastError?.message ?: "ネットワークエラー"}")
        }

        copyToPublicMusicDir(context, outputFile, track.title, track.artist, "$cleanTitle.m4a")
        outputFile
    }

    private suspend fun downloadMultiPart(
        activeUrl: String,
        outputFile: File,
        totalLength: Long,
        chunkCount: Int = 4,
        onProgress: ((percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit)?
    ): Boolean = coroutineScope {
        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.setLength(totalLength)
            }

            val totalDownloaded = AtomicLong(0L)
            val lastReportTime = AtomicLong(0L)
            val chunkSize = totalLength / chunkCount

            val deferreds = (0 until chunkCount).map { i ->
                val startByte = i * chunkSize
                val endByte = if (i == chunkCount - 1) totalLength - 1 else (i + 1) * chunkSize - 1

                async(Dispatchers.IO) {
                    var conn: HttpURLConnection? = null
                    try {
                        conn = URL(activeUrl).openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = true
                        conn.connectTimeout = 15000
                        conn.readTimeout = 30000
                        conn.requestMethod = "GET"
                        conn.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                        conn.setRequestProperty("Accept", "*/*")
                        conn.setRequestProperty("Referer", "https://www.youtube.com/")
                        conn.setRequestProperty("Range", "bytes=$startByte-$endByte")
                        conn.setRequestProperty("Connection", "keep-alive")

                        val code = conn.responseCode
                        if (code != 206 && code != 200) {
                            return@async false
                        }

                        RandomAccessFile(outputFile, "rw").use { raf ->
                            raf.seek(startByte)
                            BufferedInputStream(conn.inputStream, 128 * 1024).use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    raf.write(buffer, 0, bytesRead)
                                    val current = totalDownloaded.addAndGet(bytesRead.toLong())

                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime.get() >= 100 || current >= totalLength) {
                                        lastReportTime.set(now)
                                        val percent = ((current * 100) / totalLength).toInt().coerceIn(0, 100)
                                        onProgress?.invoke(percent, current, totalLength)
                                    }
                                }
                            }
                        }
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    } finally {
                        conn?.disconnect()
                    }
                }
            }

            val results = deferreds.awaitAll()
            results.all { it } && outputFile.exists() && outputFile.length() == totalLength
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun downloadSingleStream(
        conn: HttpURLConnection,
        outputFile: File,
        totalLength: Long,
        onProgress: ((percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit)?
    ): Boolean {
        return try {
            val effLength = if (totalLength > 0) totalLength else conn.contentLengthLong.coerceAtLeast(1L)
            BufferedInputStream(conn.inputStream, 128 * 1024).use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 100 || totalRead >= effLength) {
                            lastReportTime = now
                            val percent = ((totalRead * 100) / effLength).toInt().coerceIn(0, 100)
                            onProgress?.invoke(percent, totalRead, effLength)
                        }
                    }
                }
            }
            outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
