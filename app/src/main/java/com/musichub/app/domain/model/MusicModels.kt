package com.musichub.app.domain.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val path: String, // local file path or URI
    val artworkUri: String?,
    val dateAdded: Long,
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val isFavorite: Boolean = false,
    val format: String = "mp3",
    val bitRate: String? = null,
    val sizeBytes: Long = 0L,
    val isDownloaded: Boolean = true
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val artworkUri: String? = null,
    val songIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String? = null,
    val quality: String = "320kbps",
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AudioQuality(val label: String, val bitRateKbps: Int) {
    COMPACT("128kbps", 128),
    STANDARD("192kbps", 192),
    HIGH("256kbps", 256),
    MAXIMUM("320kbps", 320)
}
