package com.musichub.app.domain.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * On-device scanner for embedded and companion lyrics files (.lrc, .txt).
 * Runs completely locally with zero network calls.
 */
object LocalLyricsScanner {

    data class LocalLyricResult(
        val lyricsText: String,
        val isSynced: Boolean,
        val source: String // "LOCAL_LRC", "LOCAL_TXT", "ID3_USLT"
    )

    /**
     * Attempts to find lyrics for a given song by checking:
     * 1. Same-directory .lrc file (e.g. Song.lrc)
     * 2. Same-directory .txt file (e.g. Song.txt)
     * 3. ID3v2 USLT embedded tag inside the audio container
     */
    fun findLocalLyrics(context: Context, audioPathOrUri: String): LocalLyricResult? {
        if (audioPathOrUri.isBlank()) return null

        val localFile = resolveLocalFile(audioPathOrUri)
        if (localFile != null && localFile.exists()) {
            // 1. Check companion .lrc
            val companionLrc = findCompanionFile(localFile, ".lrc")
            if (companionLrc != null && companionLrc.exists() && companionLrc.length() > 0) {
                runCatching {
                    val content = companionLrc.readText(Charsets.UTF_8).trim()
                    if (content.isNotBlank()) {
                        return LocalLyricResult(
                            lyricsText = content,
                            isSynced = content.contains(Regex("\\[\\d+:\\d+")),
                            source = "LOCAL_LRC"
                        )
                    }
                }
            }

            // 2. Check companion .txt
            val companionTxt = findCompanionFile(localFile, ".txt")
            if (companionTxt != null && companionTxt.exists() && companionTxt.length() > 0) {
                runCatching {
                    val content = companionTxt.readText(Charsets.UTF_8).trim()
                    if (content.isNotBlank()) {
                        return LocalLyricResult(
                            lyricsText = content,
                            isSynced = false,
                            source = "LOCAL_TXT"
                        )
                    }
                }
            }

            // 3. Scan ID3v2 USLT tag directly from audio file header
            val id3Lyrics = extractId3UsltTag(localFile)
            if (!id3Lyrics.isNullOrBlank()) {
                return LocalLyricResult(
                    lyricsText = id3Lyrics,
                    isSynced = false,
                    source = "ID3_USLT"
                )
            }
        }

        // 4. Fallback check via MediaMetadataRetriever
        val retrieverResult = extractViaRetriever(context, audioPathOrUri)
        if (!retrieverResult.isNullOrBlank()) {
            return LocalLyricResult(
                lyricsText = retrieverResult,
                isSynced = false,
                source = "ID3_USLT"
            )
        }

        return null
    }

    private fun findCompanionFile(audioFile: File, newExtension: String): File? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension
        val target = File(parent, "$baseName$newExtension")
        if (target.exists()) return target

        // Case-insensitive match in parent directory
        val matched = parent.listFiles { file ->
            file.isFile && file.name.equals("$baseName$newExtension", ignoreCase = true)
        }
        return matched?.firstOrNull()
    }

    private fun resolveLocalFile(pathOrUri: String): File? {
        return try {
            val uri = Uri.parse(pathOrUri)
            val path = if (uri.scheme == "file") uri.path ?: pathOrUri else pathOrUri
            val f = File(path)
            if (f.exists()) f else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Lightweight binary parser for ID3v2 USLT (Unsynchronized lyrics/text transcription) frame.
     * Avoids importing heavy 3rd-party ID3 libraries.
     */
    private fun extractId3UsltTag(file: File): String? {
        if (!file.exists() || file.length() < 128) return null
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(10)
                if (input.read(header) < 10) return null
                // Must start with "ID3"
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }
                val majorVersion = header[3].toInt()
                if (majorVersion < 3 || majorVersion > 4) return null

                // Compute ID3 tag size using syncsafe integer
                val tagSize = (header[6].toInt() and 0x7F shl 21) or
                        (header[7].toInt() and 0x7F shl 14) or
                        (header[8].toInt() and 0x7F shl 7) or
                        (header[9].toInt() and 0x7F)

                val maxScan = minOf(tagSize, 256 * 1024) // limit scan to 256KB
                val buffer = ByteArray(maxScan)
                val readBytes = input.read(buffer, 0, maxScan)
                if (readBytes <= 0) return null

                // Search for "USLT" frame identifier
                var pos = 0
                while (pos + 10 < readBytes) {
                    if (buffer[pos] == 'U'.code.toByte() &&
                        buffer[pos + 1] == 'S'.code.toByte() &&
                        buffer[pos + 2] == 'L'.code.toByte() &&
                        buffer[pos + 3] == 'T'.code.toByte()
                    ) {
                        val frameSize = if (majorVersion == 4) {
                            (buffer[pos + 4].toInt() and 0x7F shl 21) or
                                    (buffer[pos + 5].toInt() and 0x7F shl 14) or
                                    (buffer[pos + 6].toInt() and 0x7F shl 7) or
                                    (buffer[pos + 7].toInt() and 0x7F)
                        } else {
                            (buffer[pos + 4].toInt() and 0xFF shl 24) or
                                    (buffer[pos + 5].toInt() and 0xFF shl 16) or
                                    (buffer[pos + 6].toInt() and 0xFF shl 8) or
                                    (buffer[pos + 7].toInt() and 0xFF)
                        }

                        if (frameSize in 5..65536 && pos + 10 + frameSize <= readBytes) {
                            val encodingByte = buffer[pos + 10].toInt()
                            val charset = when (encodingByte) {
                                1 -> Charsets.UTF_16
                                2 -> Charsets.UTF_16BE
                                3 -> Charsets.UTF_8
                                else -> Charset.forName("ISO-8859-1")
                            }
                            // Skip language (3 bytes) and find descriptor null terminator
                            var descPos = pos + 14
                            val endPos = pos + 10 + frameSize
                            while (descPos < endPos && buffer[descPos].toInt() != 0) {
                                descPos++
                            }
                            if (encodingByte in 1..2 && descPos + 1 < endPos && buffer[descPos + 1].toInt() == 0) {
                                descPos++
                            }
                            val lyricsStart = minOf(descPos + 1, endPos)
                            if (lyricsStart < endPos) {
                                val lyricsBytes = buffer.copyOfRange(lyricsStart, endPos)
                                val text = String(lyricsBytes, charset).trim()
                                if (text.isNotBlank()) return text
                            }
                        }
                    }
                    pos++
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractViaRetriever(context: Context, audioPathOrUri: String): String? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            val uri = Uri.parse(audioPathOrUri)
            if (uri.scheme == "content" || uri.scheme == "android.resource") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uri.path ?: audioPathOrUri)
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever?.release() }
        }
    }
}
