package com.example.youtubemusicdownloder.data

data class CleanedMetadata(
    val songTitle: String,
    val unitArtist: String,
    val albumName: String
)

object MetadataCleaner {

    private val NOISE_PATTERNS = listOf(
        Regex("【MV】", RegexOption.IGNORE_CASE),
        Regex("【公式】", RegexOption.IGNORE_CASE),
        Regex("【Off Voice】", RegexOption.IGNORE_CASE),
        Regex("【Instrumental】", RegexOption.IGNORE_CASE),
        Regex("\\[Official Video\\]", RegexOption.IGNORE_CASE),
        Regex("\\[Official Music Video\\]", RegexOption.IGNORE_CASE),
        Regex("\\[Official Audio\\]", RegexOption.IGNORE_CASE),
        Regex("\\(Official Video\\)", RegexOption.IGNORE_CASE),
        Regex("\\(Official Music Video\\)", RegexOption.IGNORE_CASE),
        Regex("\\(Official Audio\\)", RegexOption.IGNORE_CASE),
        Regex("\\(MV\\)", RegexOption.IGNORE_CASE),
        Regex("\\[MV\\]", RegexOption.IGNORE_CASE),
        Regex("Music Video", RegexOption.IGNORE_CASE),
        Regex("Full Ver\\.", RegexOption.IGNORE_CASE),
        Regex("Short Ver\\.", RegexOption.IGNORE_CASE),
        Regex("\\(Full\\)", RegexOption.IGNORE_CASE),
        Regex("\\(HD\\)", RegexOption.IGNORE_CASE),
        Regex("1080p", RegexOption.IGNORE_CASE),
        Regex("4K", RegexOption.IGNORE_CASE)
    )

    fun clean(rawTitle: String, channelTitle: String = "", description: String = ""): CleanedMetadata {
        var cleanTitle = rawTitle.trim()

        for (pattern in NOISE_PATTERNS) {
            cleanTitle = cleanTitle.replace(pattern, "").trim()
        }

        var songTitle = cleanTitle
        var unitArtist = if (channelTitle.isNotBlank()) {
            channelTitle.replace(" - Topic", "").replace(" Official Channel", "").trim()
        } else {
            "YouTube Artist"
        }
        var albumName = "Single"

        if (cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ")
            if (parts.size >= 2) {
                unitArtist = parts[0].trim()
                songTitle = parts[1].trim()
            }
        } else if (cleanTitle.contains(" / ")) {
            val parts = cleanTitle.split(" / ")
            if (parts.size >= 2) {
                songTitle = parts[0].trim()
                unitArtist = parts[1].trim()
            }
        } else if (cleanTitle.contains("「") && cleanTitle.contains("」")) {
            val startIndex = cleanTitle.indexOf("「")
            val endIndex = cleanTitle.indexOf("」")
            if (startIndex < endIndex) {
                val extractedSong = cleanTitle.substring(startIndex + 1, endIndex).trim()
                val prefix = cleanTitle.substring(0, startIndex).trim()
                if (extractedSong.isNotBlank()) {
                    songTitle = extractedSong
                }
                if (prefix.isNotBlank() && prefix.length > 1) {
                    unitArtist = prefix
                }
            }
        }

        if (description.isNotBlank()) {
            val albumRegex = Regex("(?:Album|アルバム)[:：]\\s*(.+)", RegexOption.IGNORE_CASE)
            val match = albumRegex.find(description)
            if (match != null && match.groupValues.size > 1) {
                albumName = match.groupValues[1].trim()
            } else if (description.contains("Provided to YouTube by")) {
                val lines = description.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.size >= 3) {
                    val secondLine = lines[1]
                    if (secondLine.contains(" · ")) {
                        val parts = secondLine.split(" · ")
                        if (parts.size >= 2) {
                            songTitle = parts[0]
                            unitArtist = parts[1]
                        }
                    }
                    if (lines.size >= 3 && !lines[2].startsWith("Provided to") && !lines[2].startsWith("Released on")) {
                        albumName = lines[2]
                    }
                }
            }
        }

        songTitle = songTitle.removePrefix("「").removeSuffix("」")
            .removePrefix("\"").removeSuffix("\"")
            .trim()

        if (songTitle.isBlank()) songTitle = rawTitle.ifBlank { "Unknown Song" }

        return CleanedMetadata(
            songTitle = songTitle,
            unitArtist = unitArtist,
            albumName = albumName
        )
    }
}
