package com.example.youtubemusicdownloder.model

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
