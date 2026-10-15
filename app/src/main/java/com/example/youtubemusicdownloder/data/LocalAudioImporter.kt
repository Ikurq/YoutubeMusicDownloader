package com.example.youtubemusicdownloder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.youtubemusicdownloder.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LocalAudioImporter {

    suspend fun importAudioFromUri(context: Context, uri: Uri): Track? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // Get display name
            var fileName = "Local Song"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "Local Song"
                }
            }

            // Copy file to app music storage
            val musicDir = context.getExternalFilesDir("Music") ?: context.filesDir
            val safeFileName = "local_${System.currentTimeMillis()}_${fileName.replace(" ", "_")}"
            val outputFile = File(musicDir, safeFileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Extract ID3 metadata via MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            var title = fileName.substringBeforeLast(".")
            var artist = "Local File"
            var album = "Device Music"
            var durationMs = 180000L

            try {
                retriever.setDataSource(outputFile.absolutePath)
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                if (!metaTitle.isNullOrBlank()) title = metaTitle
                if (!metaArtist.isNullOrBlank()) artist = metaArtist
                if (!metaAlbum.isNullOrBlank()) album = metaAlbum
                if (!metaDuration.isNullOrBlank()) durationMs = metaDuration.toLongOrNull() ?: 180000L
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext Track(
                title = title,
                artist = artist,
                album = album,
                youtubeUrl = "",
                audioUrl = outputFile.absolutePath,
                thumbnailUrl = "",
                durationMs = durationMs,
                localFilePath = outputFile.absolutePath,
                isDownloaded = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
