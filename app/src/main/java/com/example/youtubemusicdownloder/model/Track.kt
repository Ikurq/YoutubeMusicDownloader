package com.example.youtubemusicdownloder.model

import java.util.UUID

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "YouTube Artist", // Unit / Artist name
    val album: String = "Single",          // Album name
    val youtubeUrl: String = "",
    val audioUrl: String = "",
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

data class DuplicateDownloadInfo(
    val url: String,
    val originalTitle: String,
    val customTitle: String? = null,
    val existingTrack: Track,
    val suggestedNewTitle: String
)

