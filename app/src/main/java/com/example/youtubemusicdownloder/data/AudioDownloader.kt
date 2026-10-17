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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
                val activeUrl = if (attempts > 1 && videoId != null) {
                    YoutubeStreamResolver.fetchDirectAudioUrl(videoId, customServerUrl, effectiveCookie) ?: streamUrl
                } else streamUrl

                val conn = URL(activeUrl).openConnection() as HttpURLConnection
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

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val totalLength = conn.contentLengthLong.coerceAtLeast(1L)

                    conn.inputStream.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val percent = ((totalRead * 100) / totalLength).toInt().coerceIn(0, 100)
                                onProgress?.invoke(percent, totalRead, totalLength)
                            }
                        }
                    }
                    success = true
                } else if (responseCode == 403 || responseCode == 410) {
                    if (videoId != null) {
                        val freshUrl = YoutubeStreamResolver.fetchDirectAudioUrl(videoId, customServerUrl, effectiveCookie)
                        if (freshUrl != null) streamUrl = freshUrl
                    }
                    lastError = IllegalStateException("HTTP Error $responseCode (トークン期限切れ)")
                } else {
                    lastError = IllegalStateException("HTTP Error $responseCode")
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
}
