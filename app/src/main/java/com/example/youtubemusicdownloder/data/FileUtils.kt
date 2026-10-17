package com.example.youtubemusicdownloder.data

import com.example.youtubemusicdownloder.model.Track
import java.io.File
import java.text.DecimalFormat

object FileUtils {

    fun getTrackFile(track: Track): File? {
        val path = track.localFilePath?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val file = File(path)
            if (file.exists() && file.isFile) file else null
        } catch (_: Exception) {
            null
        }
    }

    fun getTrackFileSizeBytes(track: Track): Long {
        return getTrackFile(track)?.length() ?: 0L
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val df = DecimalFormat("#,##0.#")
        val sizeInUnit = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "${df.format(sizeInUnit)} ${units[digitGroups]}"
    }

    fun formatFileSizeBytesExact(bytes: Long): String {
        val df = DecimalFormat("#,###")
        return "${df.format(bytes)} バイト (${formatFileSize(bytes)})"
    }

    fun calculateTotalStorageSize(tracks: List<Track>): Long {
        return tracks.sumOf { getTrackFileSizeBytes(it) }
    }
}
