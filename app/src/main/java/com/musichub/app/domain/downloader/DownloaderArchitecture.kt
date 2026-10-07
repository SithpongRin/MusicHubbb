package com.musichub.app.domain.downloader

import com.musichub.app.domain.model.DownloadStatus
import com.musichub.app.domain.model.DownloadTask
import com.musichub.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class MediaQualityOption(
    val label: String,
    val format: String,
    val url: String,
    val sizeBytes: Long = 0L
)

data class MediaInspection(
    val isValid: Boolean,
    val providerName: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String? = null,
    val qualities: List<MediaQualityOption> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Modular interface for legal, permitted audio download sources.
 * Strictly adheres to DRM rules: no circumvention, no encrypted content decryption.
 */
interface DownloadProvider {
    val providerName: String
    fun canHandle(url: String): Boolean
    suspend fun inspect(url: String): MediaInspection
    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean
}

class DirectAudioDownloadProvider(
    private val client: OkHttpClient = OkHttpClient()
) : DownloadProvider {
    override val providerName = "Direct Legal Audio Provider"

    override fun canHandle(url: String): Boolean {
        return try {
            val path = URL(url).path.lowercase()
            path.endsWith(".mp3") || path.endsWith(".wav") ||
                    path.endsWith(".ogg") || path.endsWith(".flac") ||
                    path.endsWith(".m4a") || path.endsWith(".aac")
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun inspect(url: String): MediaInspection = withContext(Dispatchers.IO) {
        try {
            val fileName = URL(url).path.substringAfterLast('/')
            val cleanTitle = fileName.substringBeforeLast('.').replace("[-_]".toRegex(), " ")
            val ext = fileName.substringAfterLast('.', "mp3")

            MediaInspection(
                isValid = true,
                providerName = providerName,
                title = cleanTitle,
                artist = "Web Source",
                album = "Downloaded Audio",
                qualities = listOf(
                    MediaQualityOption("Original Quality", ext, url)
                )
            )
        } catch (e: Exception) {
            MediaInspection(
                isValid = false,
                providerName = providerName,
                title = "",
                artist = "",
                album = "",
                errorMessage = "Invalid direct audio URL."
            )
        }
    }

    override suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext false

        val body = response.body ?: return@withContext false
        val totalBytes = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(destinationFile)

        val buffer = ByteArray(8192)
        var downloadedBytes = 0L
        var read: Int

        try {
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloadedBytes += read
                val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                onProgress(progress, downloadedBytes, totalBytes)
            }
            outputStream.flush()
            true
        } catch (e: Exception) {
            false
        } finally {
            outputStream.close()
            inputStream.close()
        }
    }
}

class PublicDomainArchiveProvider(
    private val directProvider: DirectAudioDownloadProvider = DirectAudioDownloadProvider()
) : DownloadProvider {
    override val providerName = "Internet Archive Public Domain"

    override fun canHandle(url: String): Boolean {
        return url.contains("archive.org")
    }

    override suspend fun inspect(url: String): MediaInspection = withContext(Dispatchers.IO) {
        val direct = directProvider.inspect(url)
        direct.copy(
            providerName = providerName,
            artist = "Public Domain Recording",
            album = "Historical Classics"
        )
    }

    override suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Boolean {
        return directProvider.download(url, destinationFile, onProgress)
    }
}
