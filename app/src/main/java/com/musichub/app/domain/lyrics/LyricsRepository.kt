package com.musichub.app.domain.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.musichub.app.data.database.AppDatabase
import com.musichub.app.data.database.LyricsEntity
import com.musichub.app.presentation.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Offline-first lyrics repository.
 * Reads embedded ID3 tags, queries open LRCLIB API when permitted, caches locally in Room,
 * and executes on-device multilingual emotional analysis.
 */
class LyricsRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val db = AppDatabase.getDatabase(context)
    private val lyricsDao = db.lyricsDao()

    suspend fun getOrFetchLyrics(
        song: SongItem,
        allowOnline: Boolean = true
    ): LyricsEntity = withContext(Dispatchers.IO) {
        // 1. Check local Room database cache
        val cached = lyricsDao.getLyrics(song.id)
        if (cached != null && (!cached.plainLyrics.isNullOrBlank() || cached.isInstrumental)) {
            return@withContext cached
        }

        // 2. Try to extract local lyrics (.lrc, .txt, or embedded ID3 USLT)
        val localLyrics = LocalLyricsScanner.findLocalLyrics(context, song.uriString)
        if (localLyrics != null && localLyrics.lyricsText.isNotBlank()) {
            val plainText = if (localLyrics.isSynced) {
                localLyrics.lyricsText.replace(Regex("\\[\\d+:\\d+(\\.\\d+)?\\]"), "")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            } else localLyrics.lyricsText

            val analysis = LyricsSentimentEngine.analyze(plainText)
            val themesStr = analysis.themes.takeIf { it.isNotEmpty() }?.joinToString(",")
            val lyricEmbedStr = analysis.lyricEmbedding?.let { LyricsSentimentEngine.serializeEmbedding(it) }
            val entity = LyricsEntity(
                songId = song.id,
                plainLyrics = plainText,
                syncedLyrics = if (localLyrics.isSynced) localLyrics.lyricsText else null,
                source = localLyrics.source,
                language = analysis.language,
                isInstrumental = analysis.isInstrumental,
                lyricsValence = analysis.valence,
                lyricsArousal = analysis.arousal,
                lyricsConfidence = analysis.confidence,
                dissonanceScore = null,
                updatedAt = System.currentTimeMillis(),
                themes = themesStr,
                lyricEmbedding = lyricEmbedStr
            )
            lyricsDao.upsertLyrics(entity)
            return@withContext entity
        }

        // 3. Download lyrics from LRCLIB if missing and allowOnline is true
        if (allowOnline) {
            val remoteLyrics = fetchFromLrclib(song.title, song.artist, song.durationSec)
            if (remoteLyrics != null) {
                val plainText = remoteLyrics.first
                val syncedLrc = remoteLyrics.second
                val isInst = remoteLyrics.third

                val analysis = if (isInst) {
                    LyricsSentimentEngine.LyricsAnalysis(0f, 0f, 0f, "instrumental", true)
                } else {
                    LyricsSentimentEngine.analyze(plainText ?: "")
                }
                val themesStr = analysis.themes.takeIf { it.isNotEmpty() }?.joinToString(",")
                val lyricEmbedStr = analysis.lyricEmbedding?.let { LyricsSentimentEngine.serializeEmbedding(it) }

                val entity = LyricsEntity(
                    songId = song.id,
                    plainLyrics = plainText,
                    syncedLyrics = syncedLrc,
                    source = "LRCLIB",
                    language = analysis.language,
                    isInstrumental = isInst || analysis.isInstrumental,
                    lyricsValence = analysis.valence,
                    lyricsArousal = analysis.arousal,
                    lyricsConfidence = analysis.confidence,
                    dissonanceScore = null,
                    updatedAt = System.currentTimeMillis(),
                    themes = themesStr,
                    lyricEmbedding = lyricEmbedStr
                )
                lyricsDao.upsertLyrics(entity)
                return@withContext entity
            }
        }

        // 4. Return cached if present, or temporarily record NOT_FOUND if fetch failed
        val emptyEntity = cached ?: LyricsEntity(
            songId = song.id,
            plainLyrics = null,
            syncedLyrics = null,
            source = "NOT_FOUND",
            language = "unknown",
            isInstrumental = false,
            lyricsValence = null,
            lyricsArousal = null,
            lyricsConfidence = 0f,
            dissonanceScore = null,
            updatedAt = System.currentTimeMillis()
        ).also { lyricsDao.upsertLyrics(it) }

        emptyEntity
    }

    /**
     * Batch downloads missing lyrics for library songs in the background and saves them permanently in Room.
     */
    suspend fun downloadMissingLyricsForSongs(
        songs: List<SongItem>,
        onProgress: ((Int, Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val existingIds = lyricsDao.getSongIdsWithLyrics().toSet()
        val missing = songs.filter { it.id.isNotBlank() && it.id !in existingIds }
        var completed = 0
        val total = missing.size

        for (song in missing) {
            getOrFetchLyrics(song, allowOnline = true)
            completed++
            onProgress?.invoke(completed, total)
            kotlinx.coroutines.delay(120)
        }
    }

    private fun extractEmbeddedLyrics(uriString: String): String? {
        if (uriString.isBlank()) return null
        return try {
            val retriever = MediaMetadataRetriever()
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content" || uri.scheme == "android.resource") {
                retriever.setDataSource(context, uri)
            } else {
                val path = if (uri.scheme == "file") uri.path ?: uriString else uriString
                if (!File(path).exists()) return null
                retriever.setDataSource(path)
            }
            // ID3v2 USLT lyrics or custom metadata extraction if present
            retriever.release()
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetchFromLrclib(
        title: String,
        artist: String,
        durationSec: Int
    ): Triple<String?, String?, Boolean>? {
        return try {
            val cleanTitle = title
                .replace(Regex("\\.(mp3|m4a|flac|wav|ogg|aac)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\\d+[\\s.-]+"), "")
                .replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
                .trim()
            val cleanArtist = if (artist.equals("<unknown>", ignoreCase = true) || artist.equals("MusicHub", ignoreCase = true)) ""
                else artist.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
            if (cleanTitle.isBlank()) return null

            val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val encArtist = if (cleanArtist.isNotBlank()) URLEncoder.encode(cleanArtist, "UTF-8") else ""

            // 1. Try exact match first
            if (cleanArtist.isNotBlank() && durationSec > 0) {
                val getUrl = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist&duration=$durationSec"
                val parsed = executeLrclibGet(getUrl)
                if (parsed != null) return parsed
            }

            // 2. Fallback to fuzzy search query for high hit rate
            val searchUrl = if (cleanArtist.isNotBlank()) {
                "https://lrclib.net/api/search?track_name=$encTitle&artist_name=$encArtist"
            } else {
                "https://lrclib.net/api/search?q=$encTitle"
            }
            executeLrclibSearch(searchUrl)
        } catch (_: Throwable) {
            null
        }
    }

    private fun executeLrclibGet(url: String): Triple<String?, String?, Boolean>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MusicHub-Android/1.0 (https://github.com/MusicHub)")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            parseLyricsJson(JSONObject(body))
        } catch (_: Throwable) {
            null
        }
    }

    private fun executeLrclibSearch(url: String): Triple<String?, String?, Boolean>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MusicHub-Android/1.0 (https://github.com/MusicHub)")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            if (array.length() == 0) return null

            // Pick the first result that has plainLyrics or syncedLyrics
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val parsed = parseLyricsJson(item)
                if (parsed != null && (parsed.first != null || parsed.third)) {
                    return parsed
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseLyricsJson(json: JSONObject): Triple<String?, String?, Boolean>? {
        val isInstrumental = json.optBoolean("instrumental", false)
        val rawPlain = if (json.isNull("plainLyrics")) null else json.optString("plainLyrics").takeIf { it.isNotBlank() }
        val rawSynced = if (json.isNull("syncedLyrics")) null else json.optString("syncedLyrics").takeIf { it.isNotBlank() }

        // If plainLyrics is missing but syncedLyrics exists, extract plain lyrics by removing timestamps
        val plainLyrics = rawPlain ?: rawSynced?.replace(Regex("\\[\\d+:\\d+(\\.\\d+)?\\]"), "")
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")

        if (plainLyrics == null && !isInstrumental) return null
        return Triple(plainLyrics, rawSynced, isInstrumental)
    }
}
