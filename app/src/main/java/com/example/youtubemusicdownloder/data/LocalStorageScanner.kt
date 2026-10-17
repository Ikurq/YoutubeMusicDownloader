package com.example.youtubemusicdownloder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object LocalStorageScanner {

    private val AUDIO_EXTENSIONS = setOf(
        "m4a", "mp3", "mp4", "aac", "wav", "ogg", "flac", "opus", "webm"
    )

    suspend fun scanLocalAudioFiles(
        context: Context,
        existingTracks: List<Track>
    ): List<Track> = withContext(Dispatchers.IO) {
        val existingPaths = existingTracks.mapNotNull { it.localFilePath?.lowercase()?.trim() }.toSet()
        val candidateFiles = mutableListOf<File>()

        // 1. アプリ専用 Music ストレージ領域
        val appMusicDir = context.getExternalFilesDir("Music") ?: context.filesDir
        scanDirectoryForAudio(appMusicDir, candidateFiles)

        // 2. 内部ストレージの filesDir
        if (context.filesDir.exists() && context.filesDir != appMusicDir) {
            scanDirectoryForAudio(context.filesDir, candidateFiles)
        }

        // 3. 公開ストレージ Music/MusicPlayerDL
        try {
            val publicMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val appPublicDir = File(publicMusicDir, "MusicPlayerDL")
            scanDirectoryForAudio(appPublicDir, candidateFiles)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. MediaStore による MusicPlayerDL 及びダウンロード関連音声のスキャン
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    if (dataIndex != -1) {
                        val path = cursor.getString(dataIndex)
                        if (!path.isNullOrBlank()) {
                            val file = File(path)
                            if (file.exists() && file.isFile && file.length() > 1024) {
                                if (path.contains("MusicPlayerDL", ignoreCase = true) ||
                                    path.contains("youtubemusicdownloder", ignoreCase = true)
                                ) {
                                    candidateFiles.add(file)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 重複ファイルパスを除外
        val uniqueFiles = candidateFiles.distinctBy { it.absolutePath.lowercase() }

        // 現在のライブラリに未登録のファイルをフィルタリング
        val unindexedFiles = uniqueFiles.filter { file ->
            val normalizedPath = file.absolutePath.lowercase().trim()
            !existingPaths.contains(normalizedPath)
        }

        val recoveredTracks = mutableListOf<Track>()

        for (file in unindexedFiles) {
            val track = extractTrackFromFile(file)
            if (track != null) {
                recoveredTracks.add(track)
            }
        }

        recoveredTracks.sortedByDescending { it.addedAt }
    }

    private fun scanDirectoryForAudio(dir: File, outFiles: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        val list = dir.listFiles() ?: return
        for (item in list) {
            if (item.isFile && item.length() > 1024) {
                val ext = item.extension.lowercase()
                if (AUDIO_EXTENSIONS.contains(ext)) {
                    outFiles.add(item)
                }
            } else if (item.isDirectory && !item.name.startsWith(".")) {
                scanDirectoryForAudio(item, outFiles)
            }
        }
    }

    private fun extractTrackFromFile(file: File): Track? {
        if (!file.exists() || file.length() <= 0) return null

        var title = file.nameWithoutExtension
            .replace(Regex("""^local_\d+_"""), "")
            .replace("_", " ")
            .trim()
        var artist = "Local File"
        var album = "Device Music"
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaTitle.isNullOrBlank()) title = metaTitle.trim()
            if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum.trim()
            if (!metaDuration.isNullOrBlank()) durationMs = metaDuration.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        if (title.isBlank()) {
            title = file.nameWithoutExtension
        }

        return Track(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            album = album,
            youtubeUrl = "",
            audioUrl = file.absolutePath,
            thumbnailUrl = "",
            durationMs = durationMs,
            localFilePath = file.absolutePath,
            isDownloaded = true,
            addedAt = file.lastModified()
        )
    }
}
