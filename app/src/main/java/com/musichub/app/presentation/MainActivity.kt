package com.musichub.app.presentation

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.musichub.app.update.ApkInstaller
import com.musichub.app.update.GitHubUpdateChecker
import com.musichub.app.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.musichub.app.player.MediaPlaybackService
import com.musichub.app.domain.downloader.LocalMediaExtractor
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

enum class Screen(val kmTitle: String, val enTitle: String, val icon: ImageVector) {
    HOME("ទំព័រដើម", "Home", Icons.Default.Home),
    SEARCH("ស្វែងរក", "Search", Icons.Default.Search),
    LIBRARY("បណ្ណាល័យ", "Library", Icons.Default.List),
    SETTINGS("ការកំណត់", "Settings", Icons.Default.Settings)
}

data class SongItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val durationSec: Int,
    val artworkUrl: String = "",
    val uriString: String = "",
    val format: String = "MP3",
    var isFavorite: Boolean = false
)

data class PlaylistItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val songIds: List<String> = emptyList(),
    val color: Color = Color(0xFF14161D),
    val createdAt: Long = System.currentTimeMillis()
)

private fun loadSavedPlaylists(context: Context): List<PlaylistItem> {
    val prefs = context.getSharedPreferences("musichub_prefs", Context.MODE_PRIVATE)
    var json = prefs.getString("saved_playlists", null)
    if (json == null) {
        try {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MusicHub")
            val pFile = File(publicDir, "musichub_playlists.json")
            if (pFile.exists()) {
                json = pFile.readText()
            }
        } catch (e: Exception) {}
    }
    if (json == null) return emptyList()
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<PlaylistItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val songsArr = obj.optJSONArray("songIds")
            val songIds = mutableListOf<String>()
            if (songsArr != null) {
                for (j in 0 until songsArr.length()) {
                    songIds.add(songsArr.getString(j))
                }
            }
            list.add(
                PlaylistItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    songIds = songIds,
                    color = Color(0xFF14161D),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

private fun savePlaylists(context: Context, list: List<PlaylistItem>) {
    val prefs = context.getSharedPreferences("musichub_prefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    for (p in list) {
        val obj = JSONObject().apply {
            put("id", p.id)
            put("title", p.title)
            put("description", p.description)
            val songsArr = JSONArray()
            p.songIds.forEach { songsArr.put(it) }
            put("songIds", songsArr)
            put("createdAt", p.createdAt)
        }
        arr.put(obj)
    }
    val jsonStr = arr.toString()
    prefs.edit().putString("saved_playlists", jsonStr).apply()
    try {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MusicHub")
        if (!publicDir.exists()) publicDir.mkdirs()
        val pFile = File(publicDir, "musichub_playlists.json")
        pFile.writeText(jsonStr)
    } catch (e: Exception) {}
}

private fun parseSongsFromJson(json: String): List<SongItem> {
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<SongItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                SongItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.getString("title"),
                    artist = obj.optString("artist", "MusicHub"),
                    album = obj.optString("album", "MusicHub"),
                    duration = obj.optString("duration", "3:30"),
                    durationSec = obj.optInt("durationSec", 210),
                    artworkUrl = obj.optString("artworkUrl", ""),
                    uriString = obj.optString("uriString", ""),
                    format = obj.optString("format", "MP3"),
                    isFavorite = obj.optBoolean("isFavorite", false)
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

private fun loadSavedSongs(context: Context): List<SongItem> {
    val prefs = context.getSharedPreferences("musichub_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("saved_songs", null) ?: return emptyList()
    return parseSongsFromJson(json)
}

private fun saveSongs(context: Context, list: List<SongItem>) {
    val prefs = context.getSharedPreferences("musichub_prefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    for (s in list) {
        val obj = JSONObject().apply {
            put("id", s.id)
            put("title", s.title)
            put("artist", s.artist)
            put("album", s.album)
            put("duration", s.duration)
            put("durationSec", s.durationSec)
            put("artworkUrl", s.artworkUrl)
            put("uriString", s.uriString)
            put("format", s.format)
            put("isFavorite", s.isFavorite)
        }
        arr.put(obj)
    }
    val jsonStr = arr.toString()
    prefs.edit().putString("saved_songs", jsonStr).apply()

    // Persist to public Music/MusicHub so it survives uninstalls
    try {
        val publicDir = MusicHubStorage.getBaseDir()
        val manifestFile = File(publicDir, "musichub_library.json")
        manifestFile.writeText(jsonStr)
    } catch (e: Exception) {}
}

object MusicHubStorage {
    fun getBaseDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MusicHub")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSongsDir(): File {
        val dir = File(getBaseDir(), "Songs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCoversDir(): File {
        val dir = File(getBaseDir(), "Covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

fun resolvePlayableUriInternal(context: Context, song: SongItem, publicDir: File): String {
    // 1. Check existing URI
    if (song.uriString.isNotBlank()) {
        try {
            val u = Uri.parse(song.uriString)
            if (song.uriString.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(u, "r")?.use { pfd ->
                    if (pfd.statSize > 1024) return song.uriString
                }
            } else if (song.uriString.startsWith("file://")) {
                val f = File(u.path ?: "")
                if (f.exists() && f.length() > 1024) return song.uriString
            }
        } catch (e: Exception) {}
    }

    // 2. Look in Music/MusicHub/Songs and Music/MusicHub root
    val songsDir = MusicHubStorage.getSongsDir()
    val cleanArtist = song.artist.trim().replace(Regex("[/\\\\:*?\"<>|]"), " ").trim()
    val cleanTitle = song.title.trim().replace(Regex("[/\\\\:*?\"<>|]"), " ").trim()
    val searchDirs = listOf(songsDir, publicDir)

    for (dir in searchDirs) {
        if (!dir.exists()) continue
        val candidates = listOf(
            File(dir, "$cleanArtist - $cleanTitle.${song.format.lowercase()}"),
            File(dir, "$cleanArtist - $cleanTitle.mp3"),
            File(dir, "$cleanArtist - $cleanTitle.m4a"),
            File(dir, "$cleanArtist - $cleanTitle.mp4")
        )
        for (f in candidates) {
            if (f.exists() && f.length() > 1024) {
                return Uri.fromFile(f).toString()
            }
        }

        val allFiles = dir.listFiles() ?: emptyArray()
        for (f in allFiles) {
            if (f.isFile && f.length() > 1024) {
                val fname = f.nameWithoutExtension.lowercase()
                if (fname.contains(cleanTitle.lowercase()) || (cleanArtist.isNotBlank() && fname.contains(cleanArtist.lowercase()))) {
                    return Uri.fromFile(f).toString()
                }
            }
        }
    }

    return song.uriString
}

fun restoreAndSyncLibrary(context: Context): List<SongItem> {
    val resultList = mutableListOf<SongItem>()
    val seenKeys = mutableSetOf<String>()

    fun makeKey(artist: String, title: String) = "${artist.trim().lowercase()} - ${title.trim().lowercase()}"

    val publicDir = MusicHubStorage.getBaseDir()
    val songsSubDir = MusicHubStorage.getSongsDir()
    val coversSubDir = MusicHubStorage.getCoversDir()

    // 1. SharedPreferences
    val prefSongs = loadSavedSongs(context)
    for (s in prefSongs) {
        val key = makeKey(s.artist, s.title)
        if (seenKeys.add(key)) {
            var art = s.artworkUrl
            val companionArt = File(coversSubDir, "${s.artist} - ${s.title}.jpg")
            val rootArt = File(publicDir, "${s.artist} - ${s.title}.jpg")
            if (companionArt.exists()) {
                art = Uri.fromFile(companionArt).toString()
            } else if (rootArt.exists()) {
                art = Uri.fromFile(rootArt).toString()
            }
            val validUri = resolvePlayableUriInternal(context, s, publicDir)
            resultList.add(s.copy(artworkUrl = art, uriString = validUri))
        }
    }

    // 2. Public manifest from Music/MusicHub/musichub_library.json
    try {
        if (publicDir.exists()) {
            val manifestFile = File(publicDir, "musichub_library.json")
            if (manifestFile.exists()) {
                val manifestSongs = parseSongsFromJson(manifestFile.readText())
                for (s in manifestSongs) {
                    val key = makeKey(s.artist, s.title)
                    if (key !in seenKeys) {
                        val validUri = resolvePlayableUriInternal(context, s, publicDir)
                        var art = s.artworkUrl
                        val companionArt = File(coversSubDir, "${s.artist} - ${s.title}.jpg")
                        val rootArt = File(publicDir, "${s.artist} - ${s.title}.jpg")
                        if (companionArt.exists()) {
                            art = Uri.fromFile(companionArt).toString()
                        } else if (rootArt.exists()) {
                            art = Uri.fromFile(rootArt).toString()
                        }

                        seenKeys.add(key)
                        resultList.add(s.copy(uriString = validUri, artworkUrl = art))
                    }
                }
            }
        }
    } catch (e: Exception) {}

    // 3. Scan physical audio files in Music/MusicHub/Songs and Music/MusicHub
    val scanDirs = listOf(songsSubDir, publicDir)
    for (dir in scanDirs) {
        if (!dir.exists() || !dir.isDirectory) continue
        val audioFiles = dir.listFiles { f ->
            val name = f.name.lowercase()
            name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".mp4") ||
            name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".ogg")
        } ?: emptyArray()

        for (file in audioFiles) {
            val baseName = file.nameWithoutExtension
            val ext = file.extension.uppercase()
            val parts = if (baseName.contains(" - ")) baseName.split(" - ", limit = 2) else listOf("MusicHub", baseName)
            val parsedArtist = parts.getOrNull(0)?.trim() ?: "MusicHub"
            val parsedTitle = parts.getOrNull(1)?.trim() ?: baseName
            val key = makeKey(parsedArtist, parsedTitle)

            if (key in seenKeys) continue

            val coverCandidate = File(coversSubDir, "$baseName.jpg")
            val rootCoverCandidate = File(publicDir, "$baseName.jpg")
            var artUri = when {
                coverCandidate.exists() -> Uri.fromFile(coverCandidate).toString()
                rootCoverCandidate.exists() -> Uri.fromFile(rootCoverCandidate).toString()
                else -> ""
            }

            var durStr = "3:30"
            var durSec = 210
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (d != null && d > 0) {
                    durSec = (d / 1000).toInt()
                    durStr = "%d:%02d".format(durSec / 60, durSec % 60)
                }
                if (artUri.isBlank()) {
                    val embedded = mmr.embeddedPicture
                    if (embedded != null) {
                        try {
                            val cachedArt = File(context.cacheDir, "art_${file.name}.jpg")
                            cachedArt.writeBytes(embedded)
                            artUri = Uri.fromFile(cachedArt).toString()
                        } catch (e: Exception) {}
                    }
                }
                mmr.release()
            } catch (e: Exception) {}

            seenKeys.add(key)
            resultList.add(
                SongItem(
                    id = UUID.randomUUID().toString(),
                    title = parsedTitle,
                    artist = parsedArtist,
                    album = "MusicHub",
                    duration = durStr,
                    durationSec = durSec,
                    artworkUrl = artUri,
                    uriString = Uri.fromFile(file).toString(),
                    format = ext,
                    isFavorite = false
                )
            )
        }
    }

    // Clean and de-duplicate: Keep highest quality metadata (prefer known artist & real artwork)
    val cleanedList = mutableListOf<SongItem>()
    val processedTitles = mutableSetOf<String>()

    val sorted = resultList.sortedWith(
        compareByDescending<SongItem> { it.artworkUrl.isNotBlank() }
            .thenByDescending { it.artist != "<unknown>" && it.artist != "MusicHub" }
    )

    for (s in sorted) {
        val normTitle = s.title.lowercase()
            .replace(Regex("[^a-z0-9\\u1780-\\u17ff]"), "")
            .take(12)
        val normKey = if (normTitle.isNotBlank()) normTitle else s.title.lowercase().trim()
        if (processedTitles.add(normKey)) {
            cleanedList.add(s)
        }
    }

    saveSongs(context, cleanedList)
    return cleanedList
}

suspend fun fetchMediaMetadata(url: String): Triple<String, String, String> = withContext(Dispatchers.IO) {
    var title = ""
    var artist = ""
    var thumbnail = ""
    val u = url.trim()
    val client = OkHttpClient()

    val ytId = LocalMediaExtractor.extractYouTubeId(u)
    if (ytId != null) {
        thumbnail = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
        try {
            val meta = LocalMediaExtractor.fetchMetadata(ytId, client)
            if (meta != null) {
                title = meta.title
                artist = meta.artist
                if (meta.thumbnailUrl.isNotBlank()) {
                    thumbnail = meta.thumbnailUrl
                }
            }
        } catch (e: Exception) {
            if (title.isBlank()) {
                title = "YouTube Track (${ytId.take(8)})"
                artist = "YouTube"
            }
        }
    } else if (u.contains("tiktok.com")) {
        thumbnail = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80"
        try {
            val oembedUrl = "https://www.tiktok.com/oembed?url=${URLEncoder.encode(u, "UTF-8")}"
            val req = Request.Builder().url(oembedUrl).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: ""
                val json = JSONObject(jsonStr)
                title = json.optString("title", "")
                artist = json.optString("author_name", "")
            }
        } catch (e: Exception) {
            title = "TikTok Audio"
            artist = "TikTok Creator"
        }
    } else if (u.contains("facebook.com") || u.contains("fb.watch")) {
        thumbnail = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80"
        title = "Facebook Video Audio"
        artist = "Facebook"
    } else {
        thumbnail = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80"
        val filename = u.substringAfterLast("/").substringBefore("?").substringBeforeLast(".")
        if (filename.isNotBlank()) {
            title = try { URLDecoder.decode(filename, "UTF-8").replace("[-_]".toRegex(), " ") } catch (e: Exception) { filename }
            artist = "Web Audio"
        }
    }

    Triple(title, artist, thumbnail)
}

fun shareSongFile(context: Context, song: SongItem) {
    try {
        val uri: Uri = if (song.uriString.startsWith("content://")) {
            Uri.parse(song.uriString)
        } else if (song.uriString.startsWith("file://")) {
            val file = File(Uri.parse(song.uriString).path ?: "")
            if (file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            Toast.makeText(context, "Invalid song file", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (song.format.equals("MP4", true)) "video/mp4" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${song.artist} - ${song.title}")
            putExtra(Intent.EXTRA_TEXT, "${song.title} by ${song.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share or Copy Audio File").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

suspend fun downloadAudioToStorage(
    context: Context,
    url: String,
    format: String,
    title: String,
    artist: String,
    artworkUrl: String,
    isKhmer: Boolean = false,
    onProgress: (Int, String) -> Unit
): SongItem = withContext(Dispatchers.IO) {
    onProgress(10, if (isKhmer) "កំពុងរៀបចំ..." else "Initializing...")
    val ext = if (format.equals("MP4", ignoreCase = true)) "mp4" else "mp3"
    val songId = UUID.randomUUID().toString()
    val tempFile = File(context.cacheDir, "temp_dl_${songId}.$ext")
    if (tempFile.exists()) tempFile.delete()

    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val u = url.trim()
    val candidateUrls = mutableListOf<String>()
    var extractedNameFromCobalt = ""
    var resolvedTitle = title.trim()
    var resolvedArtist = artist.trim()
    var resolvedArtworkUrl = artworkUrl.trim()

    onProgress(15, if (isKhmer) "កំពុងស្វែងរក Audio Stream..." else "Extracting audio stream...")

    if (u.endsWith(".mp3", true) || u.endsWith(".m4a", true) || u.endsWith(".wav", true) ||
        u.endsWith(".ogg", true) || u.endsWith(".aac", true) || u.endsWith(".mp4", true) ||
        u.endsWith(".flac", true)) {
        candidateUrls.add(u)
    }

    // YouTube stream extraction (100% On-Device, Local Chromium Interception)
    val ytId = LocalMediaExtractor.extractYouTubeId(u)
    if (ytId != null) {
        onProgress(20, if (isKhmer) "កំពុងទាញយកព័ត៌មានពី YouTube..." else "Fetching YouTube info...")
        try {
            val ytMeta = LocalMediaExtractor.fetchMetadata(ytId, client)
            if (ytMeta != null) {
                if (resolvedTitle.isBlank() || resolvedTitle.startsWith("Track ") || resolvedTitle == "YouTube Video") {
                    resolvedTitle = ytMeta.title
                }
                if (resolvedArtist.isBlank() || resolvedArtist == "MusicHub" || resolvedArtist == "Web Source") {
                    resolvedArtist = ytMeta.artist
                }
                if (resolvedArtworkUrl.isBlank()) {
                    resolvedArtworkUrl = ytMeta.thumbnailUrl
                }
            }
        } catch (e: Exception) {}

        onProgress(25, if (isKhmer) "កំពុងដំណើរការ On-Device Audio Engine..." else "Extracting on-device stream...")
        try {
            val localStream = LocalMediaExtractor.extractStreamUrl(context, ytId)
            if (localStream != null && localStream.streamUrl.isNotBlank()) {
                candidateUrls.add(localStream.streamUrl)
            }
        } catch (e: Exception) {}
    }

    // Supplementary fallback for social media or if on-device extractor missed
    val isSocialOrYt = (ytId != null && candidateUrls.isEmpty()) || u.contains("tiktok.com") || u.contains("facebook.com") || u.contains("fb.watch") ||
            u.contains("instagram.com") || u.contains("soundcloud.com") || u.contains("twitter.com") || u.contains("x.com")

    if (isSocialOrYt && candidateUrls.isEmpty()) {
        val cobaltInstances = listOf(
            "https://rue-cobalt.xenon.zone/",
            "https://cobaltapi.cjs.nz/"
        )

        for (inst in cobaltInstances) {
            try {
                val payload = JSONObject().apply {
                    put("url", u)
                    put("downloadMode", if (format.equals("MP4", true)) "auto" else "audio")
                    put("audioFormat", if (format.equals("MP4", true)) "mp4" else "mp3")
                    put("audioBitrate", "128")
                }
                val cobaltReq = Request.Builder()
                    .url(inst)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "MusicHub/1.0")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = client.newCall(cobaltReq).execute()
                if (resp.isSuccessful) {
                    val respStr = resp.body?.string() ?: ""
                    val json = JSONObject(respStr)
                    val streamUrl = json.optString("url", "")
                    val fname = json.optString("filename", "")
                    if (fname.isNotBlank()) {
                        extractedNameFromCobalt = fname
                    }
                    if (streamUrl.isNotBlank() && !candidateUrls.contains(streamUrl)) {
                        candidateUrls.add(streamUrl)
                        break
                    }
                }
            } catch (e: Exception) {}
        }
    }

    if (candidateUrls.isEmpty()) {
        candidateUrls.add(u)
    }

    onProgress(45, if (isKhmer) "កំពុងទាញយកទិន្នន័យចម្រៀង..." else "Downloading audio data...")
    var downloadSucceeded = false

    for (targetUrl in candidateUrls) {
        try {
            if (targetUrl.contains("googlevideo.com")) {
                val clen = targetUrl.substringAfter("clen=").substringBefore("&").toLongOrNull() ?: -1L
                val totalLength = if (clen > 0) clen else {
                    val headReq = Request.Builder()
                        .url(targetUrl)
                        .head()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .build()
                    val headResp = client.newCall(headReq).execute()
                    val len = headResp.body?.contentLength() ?: -1L
                    headResp.close()
                    len
                }

                if (totalLength > 50000L) {
                    val outputStream = FileOutputStream(tempFile)
                    var downloadedBytes = 0L
                    val chunkSize = 1048576L // 1 MB chunks
                    var currentStart = 0L
                    var lastProgressTime = 0L
                    var chunkSuccess = true

                    while (currentStart < totalLength) {
                        val currentEnd = minOf(currentStart + chunkSize - 1, totalLength - 1)
                        val rangeReq = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .header("Range", "bytes=$currentStart-$currentEnd")
                            .build()
                        val rangeResp = client.newCall(rangeReq).execute()
                        if (rangeResp.isSuccessful || rangeResp.code == 206) {
                            val rangeBody = rangeResp.body
                            if (rangeBody != null) {
                                val buffer = ByteArray(32768)
                                val inStream = rangeBody.byteStream()
                                var r: Int
                                while (inStream.read(buffer).also { r = it } != -1) {
                                    outputStream.write(buffer, 0, r)
                                    downloadedBytes += r

                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressTime > 120) {
                                        lastProgressTime = now
                                        val mb = downloadedBytes / (1024.0 * 1024.0)
                                        val totalMb = totalLength / (1024.0 * 1024.0)
                                        val p = 45 + ((downloadedBytes * 45) / totalLength).toInt().coerceIn(0, 45)
                                        val sizeStr = if (mb >= 1.0) String.format("%.1f MB", mb) else "${downloadedBytes / 1024} KB"
                                        val msg = if (isKhmer) "កំពុងទាញយក: $p% ($sizeStr / ${String.format("%.1f MB", totalMb)})"
                                                  else "Downloading: $p% ($sizeStr / ${String.format("%.1f MB", totalMb)})"
                                        withContext(Dispatchers.Main) {
                                            onProgress(p, msg)
                                        }
                                    }
                                }
                                inStream.close()
                            }
                            rangeResp.close()
                            currentStart = currentEnd + 1
                        } else {
                            rangeResp.close()
                            chunkSuccess = false
                            break
                        }
                    }
                    outputStream.flush()
                    outputStream.close()

                    if (chunkSuccess && tempFile.exists() && tempFile.length() > 50000L) {
                        downloadSucceeded = true
                        break
                    } else {
                        tempFile.delete()
                    }
                }
            }

            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", LocalMediaExtractor.USER_AGENT)
                .header("Referer", "https://www.youtube.com/")
                .header("Origin", "https://www.youtube.com")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body
                if (body != null) {
                    val contentType = body.contentType()?.toString()?.lowercase() ?: ""
                    if (contentType.contains("text/html")) {
                        body.close()
                        continue
                    }

                    val totalBytes = body.contentLength()
                    if (totalBytes == 0L) {
                        body.close()
                        continue
                    }
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(tempFile)
                    val buffer = ByteArray(32768)
                    var downloadedBytes = 0L
                    var read: Int
                    var lastProgressTime = 0L

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 120) {
                            lastProgressTime = now
                            val mb = downloadedBytes / (1024.0 * 1024.0)
                            val p = if (totalBytes > 0) {
                                45 + ((downloadedBytes * 45) / totalBytes).toInt().coerceIn(0, 45)
                            } else {
                                (45 + (downloadedBytes / (4.0 * 1024 * 1024) * 45).toInt()).coerceIn(45, 89)
                            }
                            val sizeStr = if (mb >= 1.0) String.format("%.1f MB", mb) else "${downloadedBytes / 1024} KB"
                            val msg = if (totalBytes > 0) {
                                val totalMb = totalBytes / (1024.0 * 1024.0)
                                if (isKhmer) "កំពុងទាញយក: $p% ($sizeStr / ${String.format("%.1f MB", totalMb)})"
                                else "Downloading: $p% ($sizeStr / ${String.format("%.1f MB", totalMb)})"
                            } else {
                                if (isKhmer) "កំពុងទាញយក: $p% ($sizeStr)"
                                else "Downloading: $p% ($sizeStr)"
                            }
                            withContext(Dispatchers.Main) {
                                onProgress(p, msg)
                            }
                        }
                    }
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    if (tempFile.exists() && tempFile.length() > 50000L) {
                        downloadSucceeded = true
                        break
                    } else {
                        tempFile.delete()
                    }
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
        }
    }

    if (!downloadSucceeded || !tempFile.exists() || tempFile.length() < 50000L) {
        if (tempFile.exists()) tempFile.delete()
        throw IllegalStateException(
            if (isKhmer) "មិនអាចទាញយកចម្រៀងពីលីងនេះបានទេ។ សូមពិនិត្យមើលលីង ឬសាកល្បងប្រើលីង MP3 ផ្ទាល់ ឬនាំចូលឯកសារពីទូរស័ព្ទ។"
            else "Could not extract audio stream from this link. Please check the URL, use a direct audio link, or use the Import button."
        )
    }

    onProgress(90, if (isKhmer) "កំពុងរក្សាទុកក្នុង Music..." else "Saving to Music folder...")

    if ((resolvedTitle.isBlank() || resolvedTitle.startsWith("Track ")) && extractedNameFromCobalt.isNotBlank()) {
        val nameWithoutExt = extractedNameFromCobalt.substringBeforeLast(".")
        if (nameWithoutExt.contains(" - ")) {
            resolvedArtist = nameWithoutExt.substringBefore(" - ").trim()
            resolvedTitle = nameWithoutExt.substringAfter(" - ").trim()
        } else {
            resolvedTitle = nameWithoutExt.trim()
        }
    }

    val cleanTitle = resolvedTitle
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "Track" }
    val cleanArtist = resolvedArtist
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace("\\s+".toRegex(), " ")
        .ifBlank { "MusicHub" }
    val baseName = "$cleanArtist - $cleanTitle".take(100)
    val fileName = "$baseName.$ext"

    val publicDir = MusicHubStorage.getBaseDir()
    val songsDir = MusicHubStorage.getSongsDir()
    val coversDir = MusicHubStorage.getCoversDir()

    var savedArtworkUriString = ""
    if (resolvedArtworkUrl.isNotBlank() && (resolvedArtworkUrl.startsWith("http://") || resolvedArtworkUrl.startsWith("https://"))) {
        try {
            val artReq = Request.Builder()
                .url(resolvedArtworkUrl)
                .header("User-Agent", LocalMediaExtractor.USER_AGENT)
                .build()
            val artResp = client.newCall(artReq).execute()
            if (artResp.isSuccessful) {
                val artBytes = artResp.body?.bytes()
                if (artBytes != null && artBytes.isNotEmpty()) {
                    val coverFile = File(coversDir, "$baseName.jpg")
                    coverFile.writeBytes(artBytes)
                    try {
                        val rootCoverFile = File(publicDir, "$baseName.jpg")
                        rootCoverFile.writeBytes(artBytes)
                    } catch (e: Exception) {}
                    savedArtworkUriString = Uri.fromFile(coverFile).toString()

                    try {
                        val internalArtDir = File(context.filesDir, "artwork").apply { mkdirs() }
                        val internalArtFile = File(internalArtDir, "$songId.jpg")
                        internalArtFile.writeBytes(artBytes)
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
    }

    var targetFile: File? = null
    var savedUriString = ""

    // 1. Save directly into dedicated Songs/ folder
    try {
        val destFile = File(songsDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        targetFile = destFile
        savedUriString = Uri.fromFile(destFile).toString()

        try {
            val rootDestFile = File(publicDir, fileName)
            tempFile.copyTo(rootDestFile, overwrite = true)
        } catch (e: Exception) {}

        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf(if (ext == "mp4") "audio/mp4" else "audio/mpeg"),
            null
        )
    } catch (e: Exception) {
        try {
            val extMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val destFile = File(extMusicDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            targetFile = destFile
            savedUriString = Uri.fromFile(destFile).toString()
        } catch (e2: Exception) {
            val musicDir = File(context.filesDir, "music").apply { mkdirs() }
            val destFile = File(musicDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            targetFile = destFile
            savedUriString = Uri.fromFile(destFile).toString()
        }
    }

    // 2. Also register into MediaStore if Android 10+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, cleanTitle)
                put(MediaStore.Audio.Media.ARTIST, cleanArtist)
                put(MediaStore.Audio.Media.ALBUM, "MusicHub")
                put(MediaStore.Audio.Media.MIME_TYPE, if (ext == "mp4") "audio/mp4" else "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MusicHub/Songs")
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            val mediaUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (mediaUri != null && targetFile != null) {
                resolver.openOutputStream(mediaUri)?.use { outStream ->
                    targetFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    try { tempFile.delete() } catch (e: Exception) {}

    onProgress(95, if (isKhmer) "កំពុងបញ្ចប់..." else "Finalizing track...")
    var durSec = 210
    var durStr = "3:30"
    try {
        val mmr = MediaMetadataRetriever()
        if (savedUriString.startsWith("content://")) {
            mmr.setDataSource(context, Uri.parse(savedUriString))
        } else if (targetFile != null && targetFile.exists()) {
            mmr.setDataSource(targetFile.absolutePath)
        }
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
            if (it > 0) {
                durSec = (it / 1000).toInt()
                durStr = "${durSec / 60}:${String.format("%02d", durSec % 60)}"
            }
        }
        mmr.release()
    } catch (e: Exception) {}

    onProgress(100, if (isKhmer) "បានទាញយកជោគជ័យ!" else "Download complete!")
    delay(150)

    val finalArtwork = savedArtworkUriString.ifBlank { resolvedArtworkUrl }

    SongItem(
        id = songId,
        title = cleanTitle,
        artist = cleanArtist,
        album = if (format == "MP4") "Video Audio" else "MusicHub",
        duration = durStr,
        durationSec = durSec,
        artworkUrl = finalArtwork,
        uriString = savedUriString,
        format = format,
        isFavorite = false
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicHubApp()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicHubApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // English as Default Language
    var isKhmer by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var songsList by remember { mutableStateOf(restoreAndSyncLibrary(context)) }
    var playlists by remember { mutableStateOf(loadSavedPlaylists(context)) }
    var viewingPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var playlistForAddSong by remember { mutableStateOf<SongItem?>(null) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }

    var currentSong by remember { mutableStateOf<SongItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0.0f) }

    var showNowPlayingModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }
    var showDownloadModal by remember { mutableStateOf(false) }
    var showUpdateModal by remember { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<SongItem?>(null) }
    var selectedPreset by remember { mutableStateOf("Bass Boost") }
    var audioQuality by remember { mutableStateOf("High Quality (320 kbps)") }

    // Multi-Permission Launcher for Notifications and Media Storage
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        songsList = restoreAndSyncLibrary(context)
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    // ExoPlayer Instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            MediaPlaybackService.stop(context)
        }
    }

    // Hook up MediaPlaybackService notification action and seek listener
    DisposableEffect(Unit) {
        MediaPlaybackService.onActionReceived = { action ->
            when (action) {
                MediaPlaybackService.ACTION_PLAY -> {
                    if (!exoPlayer.isPlaying) {
                        exoPlayer.play()
                        isPlaying = true
                    }
                }
                MediaPlaybackService.ACTION_PAUSE -> {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    }
                }
                MediaPlaybackService.ACTION_NEXT -> {
                    if (songsList.isNotEmpty()) {
                        if (isShuffle) {
                            currentSong = songsList.random()
                        } else {
                            val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
                            val nextIdx = if (currIdx != -1) (currIdx + 1) % songsList.size else 0
                            currentSong = songsList[nextIdx]
                        }
                    }
                }
                MediaPlaybackService.ACTION_PREV -> {
                    if (songsList.isNotEmpty()) {
                        val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
                        val prevIdx = if (currIdx > 0) currIdx - 1 else songsList.size - 1
                        currentSong = songsList[prevIdx]
                    }
                }
                MediaPlaybackService.ACTION_STOP -> {
                    if (exoPlayer.isPlaying) exoPlayer.pause()
                    isPlaying = false
                }
            }
        }
        MediaPlaybackService.onSeekReceived = { seekPos ->
            exoPlayer.seekTo(seekPos)
            val dur = exoPlayer.duration
            if (dur > 0) {
                playbackProgress = (seekPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
        }
        onDispose {
            MediaPlaybackService.onActionReceived = null
            MediaPlaybackService.onSeekReceived = null
        }
    }

    // Update Foreground Notification whenever track or playback state changes
    LaunchedEffect(currentSong, isPlaying) {
        val song = currentSong
        if (song != null) {
            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else (song.durationSec * 1000L)
            val pos = exoPlayer.currentPosition
            MediaPlaybackService.updateNotification(
                context = context,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                isPlaying = isPlaying,
                positionMs = pos,
                durationMs = dur
            )
        } else {
            MediaPlaybackService.stop(context)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val song = currentSong
                    if (song != null) {
                        val dur = if (exoPlayer.duration > 0) exoPlayer.duration else (song.durationSec * 1000L)
                        MediaPlaybackService.updateNotification(
                            context = context,
                            title = song.title,
                            artist = song.artist,
                            artworkUrl = song.artworkUrl,
                            isPlaying = exoPlayer.isPlaying,
                            positionMs = exoPlayer.currentPosition,
                            durationMs = dur
                        )
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (isRepeat) {
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else if (songsList.isNotEmpty()) {
                        if (isShuffle) {
                            currentSong = songsList.random()
                        } else {
                            val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
                            if (currIdx != -1) {
                                val nextIdx = (currIdx + 1) % songsList.size
                                currentSong = songsList[nextIdx]
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(
                    context,
                    if (isKhmer) "ឯកសារចម្រៀងនេះមិនទាន់មាន ឬខូច។ សូមទាញយកបទនេះឡើងវិញ!"
                    else "Track file is missing or corrupted. Please re-download this track!",
                    Toast.LENGTH_LONG
                ).show()
                isPlaying = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Playback Progress Poller (100ms for ultra smooth scrubber motion)
    LaunchedEffect(isPlaying, currentSong) {
        while (isPlaying && exoPlayer.isPlaying) {
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0) {
                playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(100)
        }
    }

    // Load track into ExoPlayer on selection with auto-resolving URI
    LaunchedEffect(currentSong?.id) {
        val song = currentSong ?: return@LaunchedEffect
        if (song.uriString.isNotBlank()) {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MusicHub")
            val resolvedUriStr = resolvePlayableUriInternal(context, song, publicDir)
            if (resolvedUriStr != song.uriString) {
                val updated = song.copy(uriString = resolvedUriStr)
                currentSong = updated
                songsList = songsList.map { if (it.id == song.id) updated else it }
                saveSongs(context, songsList)
            }
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(resolvedUriStr))
                exoPlayer.stop()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                isPlaying = true
            } catch (e: Exception) {
                Toast.makeText(context, if (isKhmer) "មិនអាចចាក់បទនេះបានទេ" else "Cannot play track: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            isPlaying = false
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                currentSong?.let {
                    if (it.uriString.isNotBlank()) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(it.uriString)))
                        exoPlayer.prepare()
                    }
                }
            }
            exoPlayer.play()
            isPlaying = true
        }
    }

    fun playNextTrack() {
        if (songsList.isNotEmpty()) {
            if (isShuffle) {
                currentSong = songsList.random()
            } else {
                val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
                val nextIdx = if (currIdx != -1) (currIdx + 1) % songsList.size else 0
                currentSong = songsList[nextIdx]
            }
        }
    }

    fun playPrevTrack() {
        if (songsList.isNotEmpty()) {
            val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
            val prevIdx = if (currIdx > 0) currIdx - 1 else songsList.size - 1
            currentSong = songsList[prevIdx]
        }
    }

    fun shuffleAndPlay() {
        if (songsList.isNotEmpty()) {
            val shuffled = songsList.shuffled()
            currentSong = shuffled.first()
            isPlaying = true
        }
    }

    fun deleteSong(song: SongItem) {
        if (currentSong?.id == song.id) {
            exoPlayer.stop()
            currentSong = null
            isPlaying = false
        }
        songsList = songsList.filter { it.id != song.id }
        saveSongs(context, songsList)
        try {
            if (song.uriString.startsWith("file://")) {
                val f = File(Uri.parse(song.uriString).path ?: "")
                if (f.exists()) f.delete()
            } else if (song.uriString.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(song.uriString), null, null)
            }
        } catch (e: Exception) {}
        Toast.makeText(context, if (isKhmer) "បានលុបបទចម្រៀងរួចរាល់" else "Track deleted", Toast.LENGTH_SHORT).show()
    }

    // Audio File Picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newSongs = uris.mapIndexed { index, uri ->
                var title = "Track ${songsList.size + index + 1}"
                var artist = "Local Artist"
                var album = "Device Audio"
                var duration = "3:30"
                var durationSec = 210
                var format = "MP3"

                try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) title = it }
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                        val sec = (it / 1000).toInt()
                        durationSec = sec
                        duration = "${sec / 60}:${String.format("%02d", sec % 60)}"
                    }
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let {
                        if (it.contains("flac")) format = "FLAC"
                        else if (it.contains("wav")) format = "WAV"
                        else if (it.contains("aac")) format = "AAC"
                        else if (it.contains("ogg")) format = "OGG"
                    }
                    mmr.release()
                } catch (e: Exception) {
                    uri.lastPathSegment?.let {
                        title = it.substringAfterLast("/").substringBeforeLast(".")
                    }
                }

                SongItem(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    durationSec = durationSec,
                    uriString = uri.toString(),
                    format = format,
                    isFavorite = false
                )
            }
            songsList = songsList + newSongs
            saveSongs(context, songsList)
            Toast.makeText(context, if (isKhmer) "បានបញ្ចូល ${newSongs.size} បទដោយជោគជ័យ" else "Imported ${newSongs.size} tracks successfully", Toast.LENGTH_SHORT).show()
        }
    }

    val colorScheme = lightColorScheme(
        primary = Color(0xFF14161D),
        secondary = Color(0xFF6366F1),
        background = Color(0xFFF5F6F9),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEBEDF2),
        onPrimary = Color.White,
        onBackground = Color(0xFF111318),
        onSurface = Color(0xFF181A20),
        onSurfaceVariant = Color(0xFF757B89)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            containerColor = Color(0xFFF5F6F9),
            bottomBar = {
                Column {
                    // Floating Mini-Player with AnimatedVisibility
                    AnimatedVisibility(
                        visible = currentSong != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentSong?.let { song ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .shadow(12.dp, RoundedCornerShape(26.dp))
                                    .clip(RoundedCornerShape(26.dp))
                                    .clickable { showNowPlayingModal = true },
                                color = Color(0xFF14161D)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Circular Thumbnail with Full-Bleed Crop
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E3244)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (song.artworkUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = song.artworkUrl,
                                                contentDescription = song.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .scale(1.15f)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                delayMillis = 1200,
                                                initialDelayMillis = 1500,
                                                velocity = 35.dp
                                            )
                                        )
                                        Text(
                                            text = song.artist,
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                delayMillis = 1200,
                                                initialDelayMillis = 1500,
                                                velocity = 35.dp
                                            )
                                        )
                                    }

                                    // Favorite Heart
                                    IconButton(
                                        onClick = {
                                            songsList = songsList.map {
                                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                                            }
                                            currentSong = currentSong?.copy(isFavorite = !(currentSong?.isFavorite ?: false))
                                            saveSongs(context, songsList)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (song.isFavorite) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Play / Pause Pill with Spring Motion
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { togglePlayPause() },
                                        color = Color.White
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = Color(0xFF14161D),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 8.dp,
                        modifier = Modifier.shadow(12.dp)
                    ) {
                        Screen.values().forEach { screen ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = if (isKhmer) screen.kmTitle else screen.enTitle
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isKhmer) screen.kmTitle else screen.enTitle,
                                        fontSize = 11.sp,
                                        fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF14161D),
                                    selectedTextColor = Color(0xFF14161D),
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF94A3B8),
                                    indicatorColor = Color(0xFFECEEF2)
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF5F6F9))
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        isKhmer = isKhmer,
                        songs = songsList,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { song ->
                            currentSong = song
                            isPlaying = true
                        },
                        onPlayAll = {
                            if (songsList.isNotEmpty()) {
                                currentSong = songsList.first()
                                isPlaying = true
                            }
                        },
                        onShufflePlay = { shuffleAndPlay() },
                        onImportClick = { audioPickerLauncher.launch("audio/*") },
                        onDownloadClick = { showDownloadModal = true },
                        onLanguageToggle = { isKhmer = !isKhmer },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                            saveSongs(context, songsList)
                        },
                        onEditSong = { song -> editingSong = song },
                        onDeleteSong = { song -> deleteSong(song) },
                        onAddToPlaylist = { song -> playlistForAddSong = song },
                        onShareSong = { song -> shareSongFile(context, song) }
                    )
                    Screen.SEARCH -> SearchScreen(
                        isKhmer = isKhmer,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        songs = songsList.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true) ||
                            it.album.contains(searchQuery, ignoreCase = true)
                        },
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { song ->
                            currentSong = song
                            isPlaying = true
                        },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                            saveSongs(context, songsList)
                        },
                        onEditSong = { song -> editingSong = song },
                        onDeleteSong = { song -> deleteSong(song) },
                        onAddToPlaylist = { song -> playlistForAddSong = song },
                        onShareSong = { song -> shareSongFile(context, song) }
                    )
                    Screen.LIBRARY -> LibraryScreen(
                        isKhmer = isKhmer,
                        songs = songsList,
                        playlists = playlists,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        onSongClick = { song ->
                            currentSong = song
                            isPlaying = true
                        },
                        onImportClick = { audioPickerLauncher.launch("audio/*") },
                        onCreatePlaylistClick = { showCreatePlaylistModal = true },
                        onPlaylistClick = { playlist -> viewingPlaylist = playlist },
                        onDeletePlaylist = { playlist ->
                            playlists = playlists.filter { it.id != playlist.id }
                            savePlaylists(context, playlists)
                            Toast.makeText(context, if (isKhmer) "បានលុប Playlist" else "Playlist deleted", Toast.LENGTH_SHORT).show()
                        },
                        onPlayPlaylist = { playlist ->
                            val pSongs = songsList.filter { playlist.songIds.contains(it.id) }
                            if (pSongs.isNotEmpty()) {
                                currentSong = pSongs.first()
                                isPlaying = true
                            }
                        },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                            saveSongs(context, songsList)
                        },
                        onEditSong = { song -> editingSong = song },
                        onDeleteSong = { song -> deleteSong(song) },
                        onAddToPlaylist = { song -> playlistForAddSong = song },
                        onShareSong = { song -> shareSongFile(context, song) },
                        onRescanLibrary = {
                            songsList = restoreAndSyncLibrary(context)
                            Toast.makeText(context, if (isKhmer) "បានធ្វើបច្ចុប្បន្នភាពបណ្ណាល័យ (${songsList.size} បទ)" else "Library updated (${songsList.size} tracks)", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        isKhmer = isKhmer,
                        onLanguageToggle = { isKhmer = !isKhmer },
                        audioQuality = audioQuality,
                        onQualityChange = { audioQuality = it },
                        selectedPreset = selectedPreset,
                        onOpenEqualizer = { showEqualizerModal = true },
                        onCheckUpdate = { showUpdateModal = true },
                        totalSongs = songsList.size
                    )
                }
            }
        }

        // Edit Metadata Dialog
        if (editingSong != null) {
            EditSongDialog(
                isKhmer = isKhmer,
                song = editingSong!!,
                onSave = { newTitle, newArtist, newAlbum ->
                    songsList = songsList.map {
                        if (it.id == editingSong?.id) {
                            it.copy(title = newTitle, artist = newArtist, album = newAlbum)
                        } else it
                    }
                    if (currentSong?.id == editingSong?.id) {
                        currentSong = currentSong?.copy(title = newTitle, artist = newArtist, album = newAlbum)
                    }
                    saveSongs(context, songsList)
                    Toast.makeText(context, if (isKhmer) "បានកែសម្រួលព័ត៌មានរួចរាល់" else "Song info updated", Toast.LENGTH_SHORT).show()
                    editingSong = null
                },
                onDismiss = { editingSong = null }
            )
        }

        // Now Playing Dialog with Custom Waveform Scrubber
        if (showNowPlayingModal && currentSong != null) {
            NowPlayingDialog(
                isKhmer = isKhmer,
                song = currentSong!!,
                isPlaying = isPlaying,
                progress = playbackProgress,
                isShuffle = isShuffle,
                isRepeat = isRepeat,
                onProgressChange = { frac ->
                    playbackProgress = frac
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        exoPlayer.seekTo((frac * dur).toLong())
                    }
                },
                onPlayPause = { togglePlayPause() },
                onPrevious = { playPrevTrack() },
                onNext = { playNextTrack() },
                onShuffleToggle = { isShuffle = !isShuffle },
                onRepeatToggle = { isRepeat = !isRepeat },
                onFavoriteToggle = {
                    currentSong?.let { song ->
                        songsList = songsList.map {
                            if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                        }
                        currentSong = currentSong?.copy(isFavorite = !(currentSong?.isFavorite ?: false))
                        saveSongs(context, songsList)
                    }
                },
                onEditClick = { editingSong = currentSong },
                onEqualizerClick = { showEqualizerModal = true },
                onShareClick = { currentSong?.let { shareSongFile(context, it) } },
                onDismiss = { showNowPlayingModal = false }
            )
        }

        // Equalizer Modal
        if (showEqualizerModal) {
            EqualizerDialog(
                isKhmer = isKhmer,
                currentPreset = selectedPreset,
                onSelectPreset = {
                    selectedPreset = it
                    Toast.makeText(context, if (isKhmer) "បានកំណត់ Equalizer: $it" else "Equalizer set to $it", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showEqualizerModal = false }
            )
        }

        // Download Modal with Live Percentage Progress
        if (showDownloadModal) {
            MediaLinkDownloadDialog(
                isKhmer = isKhmer,
                onDownloadSubmit = { url, format, title, artist, thumbnail, onProgressCallback, onErrorCallback ->
                    scope.launch {
                        try {
                            val newSong = downloadAudioToStorage(
                                context = context,
                                url = url,
                                format = format,
                                title = title,
                                artist = artist,
                                artworkUrl = thumbnail,
                                isKhmer = isKhmer,
                                onProgress = { pct, statusText ->
                                    onProgressCallback(pct, statusText)
                                }
                            )
                            songsList = listOf(newSong) + songsList.filter { it.id != newSong.id }
                            saveSongs(context, songsList)
                            currentSong = newSong
                            isPlaying = true
                            delay(300)
                            showDownloadModal = false
                            Toast.makeText(
                                context,
                                if (isKhmer) "បានទាញយក និងកំពុងចាក់" else "Downloaded & playing",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            onErrorCallback(e.message ?: "Download failed")
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = { showDownloadModal = false }
            )
        }

        // In-App Update Dialog with Animated Radar and Progress
        if (showUpdateModal) {
            AppUpdateDialog(
                isKhmer = isKhmer,
                onDismiss = { showUpdateModal = false }
            )
        }

        // Create Playlist Dialog
        if (showCreatePlaylistModal) {
            CreatePlaylistDialog(
                isKhmer = isKhmer,
                onCreate = { title ->
                    val newPlaylist = PlaylistItem(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        songIds = emptyList()
                    )
                    playlists = playlists + newPlaylist
                    savePlaylists(context, playlists)
                    showCreatePlaylistModal = false
                    Toast.makeText(context, if (isKhmer) "បានបង្កើត Playlist: $title" else "Created playlist: $title", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCreatePlaylistModal = false }
            )
        }

        // Add Song to Playlist Dialog
        if (playlistForAddSong != null) {
            val songToAdd = playlistForAddSong!!
            AddToPlaylistDialog(
                isKhmer = isKhmer,
                song = songToAdd,
                playlists = playlists,
                onAddToPlaylist = { playlist ->
                    val updatedPlaylists = playlists.map { p ->
                        if (p.id == playlist.id) {
                            val newSongIds = if (p.songIds.contains(songToAdd.id)) {
                                p.songIds.filter { it != songToAdd.id }
                            } else {
                                p.songIds + songToAdd.id
                            }
                            p.copy(songIds = newSongIds)
                        } else p
                    }
                    playlists = updatedPlaylists
                    savePlaylists(context, updatedPlaylists)
                    val isNowIn = updatedPlaylists.find { it.id == playlist.id }?.songIds?.contains(songToAdd.id) == true
                    Toast.makeText(
                        context,
                        if (isNowIn) {
                            if (isKhmer) "បានបញ្ចូលទៅក្នុង ${playlist.title}" else "Added to ${playlist.title}"
                        } else {
                            if (isKhmer) "បានដកចេញពី ${playlist.title}" else "Removed from ${playlist.title}"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onCreateNewPlaylist = {
                    playlistForAddSong = null
                    showCreatePlaylistModal = true
                },
                onDismiss = { playlistForAddSong = null }
            )
        }

        // Playlist Detail Dialog
        if (viewingPlaylist != null) {
            val vp = viewingPlaylist!!
            val currentP = playlists.find { it.id == vp.id } ?: vp
            PlaylistDetailDialog(
                isKhmer = isKhmer,
                playlist = currentP,
                allSongs = songsList,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onSongClick = { song ->
                    currentSong = song
                    isPlaying = true
                },
                onPlayAll = {
                    val pSongs = songsList.filter { currentP.songIds.contains(it.id) }
                    if (pSongs.isNotEmpty()) {
                        currentSong = pSongs.first()
                        isPlaying = true
                    }
                },
                onRemoveSong = { song ->
                    val updatedPlaylists = playlists.map { p ->
                        if (p.id == currentP.id) {
                            p.copy(songIds = p.songIds.filter { it != song.id })
                        } else p
                    }
                    playlists = updatedPlaylists
                    savePlaylists(context, updatedPlaylists)
                },
                onDismiss = { viewingPlaylist = null }
            )
        }
    }
}

// Animated Equalizer Bars for Playing Tracks
@Composable
fun AnimatedEqualizerBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = Modifier
            .width(20.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(bar1)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF14161D))
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(bar2)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF14161D))
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(bar3)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF14161D))
        )
    }
}

// Interactive Waveform Scrubber
@Composable
fun WaveformScrubber(
    progress: Float,
    durationSec: Int,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barCount = 38
    val heights = remember {
        listOf(
            0.35f, 0.5f, 0.7f, 0.45f, 0.9f, 0.6f, 0.3f, 0.8f, 1.0f, 0.75f,
            0.55f, 0.85f, 0.4f, 0.65f, 0.95f, 0.7f, 0.5f, 0.8f, 0.6f, 0.9f,
            0.4f, 0.75f, 0.55f, 0.85f, 0.65f, 0.45f, 0.7f, 0.95f, 0.6f, 0.8f,
            0.5f, 0.75f, 0.6f, 0.4f, 0.7f, 0.85f, 0.5f, 0.35f
        )
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        isDragging = true
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0) {
                            dragProgress = (down.position.x / widthPx).coerceIn(0f, 1f)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                if (widthPx > 0) {
                                    dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                                }
                            } else {
                                onSeek(dragProgress)
                                isDragging = false
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth

            // Waveform Bars
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until barCount) {
                    val barThreshold = i.toFloat() / barCount.toFloat()
                    val isPlayed = barThreshold <= currentProgress
                    val heightRatio = heights[i % heights.size]
                    val targetHeight = 42.dp * heightRatio

                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(targetHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isPlayed) Color(0xFF14161D) else Color(0xFFDCE0E8)
                            )
                    )
                }
            }

            // Visible Playhead / Thumb Line
            val thumbOffset = totalWidth * currentProgress
            Box(
                modifier = Modifier
                    .offset(x = (thumbOffset - 2.dp).coerceAtLeast(0.dp))
                    .width(4.dp)
                    .height(48.dp)
                    .shadow(3.dp, RoundedCornerShape(2.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF14161D))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentSec = (durationSec * currentProgress).toInt().coerceAtLeast(0)
            val currentStr = "${currentSec / 60}:${String.format("%02d", currentSec % 60)}"
            val totalStr = "${durationSec / 60}:${String.format("%02d", durationSec % 60)}"
            Text(
                text = currentStr,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8A909E)
            )
            Text(
                text = totalStr,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8A909E)
            )
        }
    }
}

// Edit Song Metadata Dialog
@Composable
fun EditSongDialog(
    isKhmer: Boolean,
    song: SongItem,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color(0xFF14161D))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "កែសម្រួលព័ត៌មានបទចម្រៀង" else "Edit Song Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF14161D)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isKhmer) "ចំណងជើងបទចម្រៀង" else "Track Title", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(if (isKhmer) "អ្នកចម្រៀង" else "Artist", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(if (isKhmer) "អាល់ប៊ុម" else "Album", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, artist, album) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
            ) {
                Text(text = if (isKhmer) "រក្សាទុក" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isKhmer) "បោះបង់" else "Cancel", color = Color(0xFF64748B))
            }
        }
    )
}

// Create Playlist Dialog
@Composable
fun CreatePlaylistDialog(
    isKhmer: Boolean,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = Color(0xFF14161D))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "បង្កើត Playlist ថ្មី" else "Create New Playlist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF14161D)
                )
            }
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (isKhmer) "ឈ្មោះ Playlist" else "Playlist Name", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
            ) {
                Text(if (isKhmer) "បង្កើត" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isKhmer) "បោះបង់" else "Cancel", color = Color(0xFF8A909E))
            }
        }
    )
}

// Add Song to Playlist Dialog
@Composable
fun AddToPlaylistDialog(
    isKhmer: Boolean,
    song: SongItem,
    playlists: List<PlaylistItem>,
    onAddToPlaylist: (PlaylistItem) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, tint = Color(0xFF14161D))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "បញ្ចូលក្នុង Playlist" else "Add to Playlist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF14161D)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = song.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8A909E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onDismiss()
                            onCreateNewPlaylist()
                        },
                    color = Color(0xFFF5F6F9)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF14161D), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isKhmer) "+ បង្កើត Playlist ថ្មី" else "+ Create New Playlist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF14161D)
                        )
                    }
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = if (isKhmer) "មិនទាន់មាន Playlist ណាមួយទេ" else "No playlists created yet",
                        fontSize = 12.sp,
                        color = Color(0xFF8A909E),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists) { playlist ->
                            val alreadyIn = playlist.songIds.contains(song.id)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onAddToPlaylist(playlist)
                                    },
                                color = if (alreadyIn) Color(0xFFF1F5F9) else Color(0xFFECEEF2)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF14161D),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isKhmer) "${playlist.songIds.size} បទ" else "${playlist.songIds.size} tracks",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8A909E)
                                        )
                                    }
                                    if (alreadyIn) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Added",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isKhmer) "បិទ" else "Close", color = Color(0xFF14161D))
            }
        }
    )
}

// Playlist Detail Dialog
@Composable
fun PlaylistDetailDialog(
    isKhmer: Boolean,
    playlist: PlaylistItem,
    allSongs: List<SongItem>,
    currentSong: SongItem?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveSong: (SongItem) -> Unit,
    onDismiss: () -> Unit
) {
    val playlistSongs = remember(playlist.songIds, allSongs) {
        playlist.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F6F9)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF14161D))
                    }
                    Text(
                        text = playlist.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14161D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (playlistSongs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isKhmer) "${playlistSongs.size} បទចម្រៀង" else "${playlistSongs.size} tracks",
                            fontSize = 13.sp,
                            color = Color(0xFF8A909E),
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isKhmer) "ចាក់ទាំងអស់" else "Play All")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (playlistSongs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isKhmer) "មិនទាន់មានចម្រៀងក្នុង Playlist នេះទេ" else "No songs in this playlist yet",
                                fontSize = 14.sp,
                                color = Color(0xFF8A909E)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(playlistSongs) { index, song ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSongClick(song) },
                                color = if (currentSong?.id == song.id) Color.White else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format("%02d", index + 1),
                                        fontSize = 12.sp,
                                        color = Color(0xFF8A909E),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(26.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFECEEF2)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (song.artworkUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = song.artworkUrl,
                                                contentDescription = song.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF14161D),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${song.artist} • ${song.duration}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8A909E),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveSong(song) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Home Screen
@Composable
fun HomeScreen(
    isKhmer: Boolean,
    songs: List<SongItem>,
    currentSong: SongItem?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onImportClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onLanguageToggle: () -> Unit,
    onFavoriteToggle: (SongItem) -> Unit,
    onEditSong: (SongItem) -> Unit,
    onDeleteSong: (SongItem) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onShareSong: (SongItem) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Polished Header Row
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MusicHub",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF14161D),
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = if (isKhmer) "តន្ត្រីរបស់អ្នក គ្រប់ពេលវេលា" else "Listen Offline Everywhere",
                        fontSize = 12.sp,
                        color = Color(0xFF8A909E),
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language Switcher Pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onLanguageToggle() },
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEEF2))
                    ) {
                        Text(
                            text = if (isKhmer) "ខ្មែរ" else "EN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14161D),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    // Import Button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onImportClick() },
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEEF2))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Import",
                                tint = Color(0xFF14161D),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Download by Link Button
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onDownloadClick() },
                        color = Color(0xFF14161D)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hero Section
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp)),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Full-Bleed Scaled Artwork Box (Eliminating Black Bars)
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .shadow(6.dp, RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1E212D)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentSong != null && currentSong.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = currentSong.artworkUrl,
                                    contentDescription = currentSong.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(1.15f)
                                )
                            } else {
                                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isKhmer) "ចម្រៀង • ${songs.size} បទ • Offline" else "MusicHub • ${songs.size} songs • Offline",
                                fontSize = 11.sp,
                                color = Color(0xFF8A909E),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (currentSong != null) currentSong.title else "Offline Library",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14161D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (currentSong != null) currentSong.artist else "MusicHub Player",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Play & Shuffle Action Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isKhmer) "ចាក់ទាំងអស់" else "Play", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onShufflePlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECEEF2))
                        ) {
                            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = Color(0xFF14161D), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isKhmer) "ច្របល់" else "Shuffle", color = Color(0xFF14161D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Empty state
        if (songs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isKhmer) "មិនទាន់មានបទចម្រៀងទេ" else "No Music Found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14161D)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isKhmer) "ទាញយកតាមលីង ឬនាំចូលចម្រៀងពីទូរស័ព្ទដើម្បីស្តាប់" else "Download via link or import local audio files to start",
                            fontSize = 12.sp,
                            color = Color(0xFF8A909E),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onDownloadClick,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                        ) {
                            Text(if (isKhmer) "ទាញយកតាមលីង" else "Download by Link")
                        }
                    }
                }
            }
        } else {
            // Numbered Track List with Live Animated Equalizer and Song Artwork
            itemsIndexed(songs) { index, song ->
                NumberedTrackRowItem(
                    index = index + 1,
                    isKhmer = isKhmer,
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    isPlaying = isPlaying && currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) },
                    onEditSong = { onEditSong(song) },
                    onDeleteSong = { onDeleteSong(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onShareSong = { onShareSong(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Track Row with Artwork Thumbnail
@Composable
fun NumberedTrackRowItem(
    index: Int,
    isKhmer: Boolean,
    song: SongItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEditSong: () -> Unit,
    onDeleteSong: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onShareSong: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (isCurrent) Color.White else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track index or live animated equalizer bars
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isPlaying) {
                    AnimatedEqualizerBars()
                } else {
                    Text(
                        text = String.format("%02d", index),
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) Color(0xFF14161D) else Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Song Artwork Thumbnail (48x48 rounded squircle)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFECEEF2)),
                contentAlignment = Alignment.Center
            ) {
                if (song.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.12f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF14161D),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.artist} • ${song.duration}",
                    fontSize = 12.sp,
                    color = Color(0xFF8A909E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) Color(0xFFEF4444) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Options",
                        tint = Color(0xFF8A909E),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isKhmer) "បញ្ចូលក្នុង Playlist" else "Add to Playlist", color = Color(0xFF14161D)) },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isKhmer) "កែសម្រួលព័ត៌មាន" else "Edit Info", color = Color(0xFF14161D)) },
                        onClick = {
                            showMenu = false
                            onEditSong()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isKhmer) "ចែករំលែក ឬ Copy ឯកសារ" else "Share / Copy File", color = Color(0xFF14161D)) },
                        onClick = {
                            showMenu = false
                            onShareSong()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isKhmer) "លុបចេញ" else "Delete", color = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onDeleteSong()
                        }
                    )
                }
            }
        }
    }
}

// Search Screen
@Composable
fun SearchScreen(
    isKhmer: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    songs: List<SongItem>,
    currentSong: SongItem?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onFavoriteToggle: (SongItem) -> Unit,
    onEditSong: (SongItem) -> Unit,
    onDeleteSong: (SongItem) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onShareSong: (SongItem) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isKhmer) "ស្វែងរកចម្រៀង" else "Search Songs",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF14161D)
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(if (isKhmer) "ស្វែងរកតាមចំណងជើង ឬអ្នកចម្រៀង..." else "Search by title or artist...", fontSize = 13.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF8A909E)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF14161D),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )
        }

        itemsIndexed(songs) { index, song ->
            NumberedTrackRowItem(
                index = index + 1,
                isKhmer = isKhmer,
                song = song,
                isCurrent = currentSong?.id == song.id,
                isPlaying = isPlaying && currentSong?.id == song.id,
                onClick = { onSongClick(song) },
                onFavoriteToggle = { onFavoriteToggle(song) },
                onEditSong = { onEditSong(song) },
                onDeleteSong = { onDeleteSong(song) },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onShareSong = { onShareSong(song) }
            )
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Library Screen with Playlist Creation & Organization
@Composable
fun LibraryScreen(
    isKhmer: Boolean,
    songs: List<SongItem>,
    playlists: List<PlaylistItem>,
    currentSong: SongItem?,
    isPlaying: Boolean,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onImportClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onDeletePlaylist: (PlaylistItem) -> Unit,
    onPlayPlaylist: (PlaylistItem) -> Unit,
    onFavoriteToggle: (SongItem) -> Unit,
    onEditSong: (SongItem) -> Unit,
    onDeleteSong: (SongItem) -> Unit,
    onAddToPlaylist: (SongItem) -> Unit,
    onShareSong: (SongItem) -> Unit = {},
    onRescanLibrary: () -> Unit = {}
) {
    val categories = if (isKhmer) listOf("ចម្រៀងទាំងអស់", "បញ្ជីចម្រៀង", "ចូលចិត្ត", "បានទាញយក")
                     else listOf("All", "Playlists", "Favorites", "Downloaded")

    val isPlaylistsTab = selectedCategory.contains("បញ្ជីចម្រៀង") || selectedCategory == "Playlists"

    val displayedSongs = remember(songs, selectedCategory, isKhmer) {
        when {
            selectedCategory.contains("ចូលចិត្ត") || selectedCategory == "Favorites" -> songs.filter { it.isFavorite }
            selectedCategory.contains("បានទាញយក") || selectedCategory == "Downloaded" -> songs.filter { it.album.contains("Offline") || it.album.contains("Downloaded") || it.album.contains("MusicHub") }
            else -> songs
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = if (isKhmer) "បណ្ណាល័យ" else "Library",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14161D),
                        maxLines = 1
                    )
                    Text(
                        text = if (isPlaylistsTab) {
                            if (isKhmer) "${playlists.size} បញ្ជីចម្រៀង" else "${playlists.size} playlists available"
                        } else {
                            if (isKhmer) "${displayedSongs.size} បទក្នុងឧបករណ៍" else "${displayedSongs.size} tracks available"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF8A909E),
                        maxLines = 1
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rescan Library Circle Button
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onRescanLibrary() },
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEEF2))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = if (isKhmer) "ស្កេន" else "Scan",
                                tint = Color(0xFF14161D),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isPlaylistsTab) {
                        // Create Playlist Button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onCreatePlaylistClick() },
                            color = Color(0xFF14161D)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isKhmer) "បញ្ជីចម្រៀង" else "+ List",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Import Audio Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onImportClick() },
                        color = if (isPlaylistsTab) Color.White else Color(0xFF14161D),
                        border = if (isPlaylistsTab) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEEF2)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isPlaylistsTab) Color(0xFF14161D) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isKhmer) "នាំចូល" else "Import",
                                color = if (isPlaylistsTab) Color(0xFF14161D) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF14161D),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF8A909E)
                        ),
                        border = null
                    )
                }
            }
        }

        if (isPlaylistsTab) {
            // Playlists List
            if (playlists.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp)),
                        color = Color.White
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isKhmer) "មិនទាន់មាន Playlist ទេ" else "No Playlists Yet",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14161D)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isKhmer) "បង្កើត Playlist ផ្ទាល់ខ្លួនដើម្បីចាត់ចែងចម្រៀងតាមចំណូលចិត្ត" else "Create your first playlist to organize your favorite songs",
                                fontSize = 12.sp,
                                color = Color(0xFF8A909E),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onCreatePlaylistClick,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                            ) {
                                Text(if (isKhmer) "បង្កើត Playlist ថ្មី" else "+ Create Playlist")
                            }
                        }
                    }
                }
            } else {
                items(playlists) { playlist ->
                    val firstSong = songs.find { it.id == playlist.songIds.firstOrNull() }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPlaylistClick(playlist) },
                        color = Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1E212D)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (firstSong != null && firstSong.artworkUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = firstSong.artworkUrl,
                                        contentDescription = playlist.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF14161D),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isKhmer) "${playlist.songIds.size} បទ" else "${playlist.songIds.size} tracks",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8A909E)
                                )
                            }

                            if (playlist.songIds.isNotEmpty()) {
                                IconButton(
                                    onClick = { onPlayPlaylist(playlist) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFECEEF2), CircleShape)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = Color(0xFF14161D), modifier = Modifier.size(18.dp))
                                }
                            }

                            IconButton(
                                onClick = { onDeletePlaylist(playlist) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(displayedSongs) { index, song ->
                NumberedTrackRowItem(
                    index = index + 1,
                    isKhmer = isKhmer,
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    isPlaying = isPlaying && currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) },
                    onEditSong = { onEditSong(song) },
                    onDeleteSong = { onDeleteSong(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onShareSong = { onShareSong(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Settings Screen
@Composable
fun SettingsScreen(
    isKhmer: Boolean,
    onLanguageToggle: () -> Unit,
    audioQuality: String,
    onQualityChange: (String) -> Unit,
    selectedPreset: String,
    onOpenEqualizer: () -> Unit,
    onCheckUpdate: () -> Unit,
    totalSongs: Int
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isKhmer) "ការកំណត់" else "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF14161D)
            )
        }

        // Language Switcher Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onLanguageToggle),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isKhmer) "ភាសា (Language)" else "Language",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF14161D)
                        )
                        Text(
                            text = if (isKhmer) "ភាសាខ្មែរ (Khmer)" else "English",
                            fontSize = 13.sp,
                            color = Color(0xFF8A909E)
                        )
                    }

                    TextButton(onClick = onLanguageToggle) {
                        Text(text = if (isKhmer) "Switch to EN" else "ប្តូរទៅភាសាខ្មែរ", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Equalizer Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenEqualizer),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFECEEF2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = "EQ", tint = Color(0xFF14161D), modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isKhmer) "ប្រព័ន្ធកែសំឡេង Equalizer" else "Sound Equalizer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF14161D)
                        )
                        Text(
                            text = selectedPreset,
                            fontSize = 13.sp,
                            color = Color(0xFF8A909E)
                        )
                    }

                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = Color(0xFF8A909E))
                }
            }
        }

        // In-App Update Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MusicHub App Update",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF14161D)
                        )
                        Text(
                            text = "Version: v1.0.15",
                            fontSize = 13.sp,
                            color = Color(0xFF8A909E)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onCheckUpdate() },
                        color = Color(0xFF14161D)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isKhmer) "ពិនិត្យមើល" else "Check Updates",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Now Playing Screen matching reference mockup
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingDialog(
    isKhmer: Boolean,
    song: SongItem,
    isPlaying: Boolean,
    progress: Float,
    isShuffle: Boolean,
    isRepeat: Boolean,
    onProgressChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEditClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "artScale"
    )

    var discRotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            withFrameMillis {
                discRotation = (discRotation + 0.35f) % 360f
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F6F9)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = Color(0xFF14161D), modifier = Modifier.size(28.dp))
                    }

                    Text(
                        text = if (isKhmer) "កំពុងចាក់" else "Now Playing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14161D)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShareClick) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF14161D))
                        }
                        IconButton(onClick = onEqualizerClick) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = "EQ", tint = Color(0xFF14161D))
                        }
                    }
                }

                // Rotating Vinyl Record Disc with realistic grooves and center spindle hole
                Box(
                    modifier = Modifier
                        .size(285.dp)
                        .scale(artScale)
                        .shadow(24.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF101217))
                        .graphicsLayer { rotationZ = discRotation },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Vinyl Grooves (concentric realistic rings)
                    Box(
                        modifier = Modifier
                            .size(272.dp)
                            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(248.dp)
                            .border(1.dp, Color(0x14FFFFFF), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(224.dp)
                            .border(1.dp, Color(0x18FFFFFF), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(1.dp, Color(0x12FFFFFF), CircleShape)
                    )

                    // Center Vinyl Label / Album Artwork Sticker
                    Box(
                        modifier = Modifier
                            .size(155.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFF222632), CircleShape)
                            .background(Color(0xFF1E212D)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.artworkUrl.isNotBlank()) {
                            AsyncImage(
                                model = song.artworkUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(1.2f)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        // Center Vinyl Spindle Hole & Metallic Silver Ring
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF101217))
                                .border(2.dp, Color(0xFFCBD5E1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                            )
                        }
                    }
                }

                // Song Info Row: Heart on left, Centered Title/Artist, Options on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color(0xFFEF4444) else Color(0xFF8A909E),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = song.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14161D),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                delayMillis = 1200,
                                initialDelayMillis = 1500,
                                velocity = 35.dp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            fontSize = 14.sp,
                            color = Color(0xFF8A909E),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                delayMillis = 1200,
                                initialDelayMillis = 1500,
                                velocity = 35.dp
                            )
                        )
                    }

                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Options",
                            tint = Color(0xFF8A909E),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Interactive Waveform Audio Scrubber
                WaveformScrubber(
                    progress = progress,
                    durationSec = song.durationSec,
                    onSeek = onProgressChange
                )

                // Controls Row: Shuffle, Prev, Big Play/Pause, Next, Repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffleToggle) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) Color(0xFF14161D) else Color(0xFFB0B5C0)
                        )
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color(0xFF14161D), modifier = Modifier.size(28.dp))
                    }

                    // Main Big Circular Play/Pause Button
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(12.dp, CircleShape)
                            .clip(CircleShape)
                            .clickable { onPlayPause() },
                        color = Color(0xFF14161D)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color(0xFF14161D), modifier = Modifier.size(28.dp))
                    }

                    IconButton(onClick = onRepeatToggle) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isRepeat) Color(0xFF14161D) else Color(0xFFB0B5C0)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// Media Link Download Dialog with Live Percentage Progress
@Composable
fun MediaLinkDownloadDialog(
    isKhmer: Boolean,
    onDownloadSubmit: (
        url: String,
        format: String,
        title: String,
        artist: String,
        thumbnail: String,
        onProgress: (Int, String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("MP3") }
    var customTitle by remember { mutableStateOf("") }
    var customArtist by remember { mutableStateOf("") }
    var extractedThumbnail by remember { mutableStateOf("") }
    var isFetchingTitle by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadPercentage by remember { mutableIntStateOf(0) }
    var downloadStatusText by remember { mutableStateOf("") }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }

    val formats = listOf("MP3", "MP4", "M4A", "FLAC")

    val detectedPlatform = remember(urlText) {
        val u = urlText.lowercase().trim()
        when {
            u.contains("youtube.com") || u.contains("youtu.be") -> "YouTube"
            u.contains("tiktok.com") -> "TikTok"
            u.contains("facebook.com") || u.contains("fb.watch") -> "Facebook"
            u.contains(".mp3") || u.contains(".wav") || u.contains(".flac") -> "Direct Audio"
            u.contains(".mp4") || u.contains(".mkv") || u.contains(".webm") -> "Direct Video"
            u.isNotBlank() -> "Web Media"
            else -> ""
        }
    }

    LaunchedEffect(urlText) {
        val u = urlText.trim()
        if (u.length > 8 && (u.startsWith("http://") || u.startsWith("https://"))) {
            isFetchingTitle = true
            downloadErrorMessage = null
            try {
                val meta = fetchMediaMetadata(u)
                if (meta.first.isNotBlank()) customTitle = meta.first
                if (meta.second.isNotBlank()) customArtist = meta.second
                if (meta.third.isNotBlank()) extractedThumbnail = meta.third
            } catch (e: Exception) {
                // ignore
            } finally {
                isFetchingTitle = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color(0xFF14161D))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "ទាញយកតាមរយៈលីង" else "Download by Link",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF14161D)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isDownloading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$downloadPercentage%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14161D)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadPercentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF14161D),
                            trackColor = Color(0xFFECEEF2)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = downloadStatusText.ifBlank { if (isKhmer) "កំពុងទាញយក..." else "Downloading..." },
                            fontSize = 13.sp,
                            color = Color(0xFF8A909E),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                isDownloading = false
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isKhmer) "បោះបង់" else "Cancel", fontSize = 12.sp, color = Color(0xFF8A909E))
                        }
                    }
                } else {
                    if (downloadErrorMessage != null) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = downloadErrorMessage ?: "",
                                    color = Color(0xFFDC2626),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isKhmer) "បិទភ្ជាប់លីង YouTube, TikTok, Facebook ឬតំណភ្ជាប់ចម្រៀង:"
                        else "Paste link from YouTube, TikTok, Facebook or direct audio stream:",
                        fontSize = 12.sp,
                        color = Color(0xFF8A909E)
                    )

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            downloadErrorMessage = null
                        },
                        placeholder = { Text("https://www.youtube.com/watch?v=...", color = Color(0xFF8A909E), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (detectedPlatform.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFECEEF2),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Platform: $detectedPlatform",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14161D),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (isFetchingTitle) {
                                Text(
                                    text = if (isKhmer) "កំពុងទាញយក Title ដើម..." else "Fetching title...",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6366F1)
                                )
                            }
                        }
                    }

                    if (extractedThumbnail.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            color = Color(0xFFECEEF2)
                        ) {
                            AsyncImage(
                                model = extractedThumbnail,
                                contentDescription = "Thumbnail Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(1.15f)
                            )
                        }
                    }

                    // Format Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        formats.forEach { fmt ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFormat = fmt },
                                color = if (selectedFormat == fmt) Color(0xFF14161D) else Color(0xFFECEEF2)
                            ) {
                                Text(
                                    text = fmt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedFormat == fmt) Color.White else Color(0xFF8A909E),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text(if (isKhmer) "ចំណងជើងដើម (អាចកែបាន)" else "Original Title (Editable)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customArtist,
                        onValueChange = { customArtist = it },
                        label = { Text(if (isKhmer) "អ្នកចម្រៀង / Channel" else "Artist / Channel", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        if (urlText.isNotBlank()) {
                            isDownloading = true
                            downloadPercentage = 5
                            downloadErrorMessage = null
                            onDownloadSubmit(
                                urlText,
                                selectedFormat,
                                customTitle.ifBlank { "Track ${System.currentTimeMillis() % 1000}" },
                                customArtist.ifBlank { "Web Media" },
                                extractedThumbnail,
                                { pct, text ->
                                    downloadPercentage = pct
                                    downloadStatusText = text
                                },
                                { err ->
                                    isDownloading = false
                                    downloadErrorMessage = err
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isKhmer) "ទាញយក ($selectedFormat)" else "Download ($selectedFormat)")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(if (isKhmer) "បោះបង់" else "Cancel", color = Color(0xFF8A909E))
                }
            }
        }
    )
}

// In-App Update Dialog with Animated Radar and Smooth Motion
@Composable
fun AppUpdateDialog(
    isKhmer: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingApk by remember { mutableStateOf(false) }
    var apkProgress by remember { mutableIntStateOf(0) }
    var downloadedBytesText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    val checker = remember { GitHubUpdateChecker() }

    // Rotating animation for checking updates
    val infiniteTransition = rememberInfiniteTransition(label = "update_rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        isChecking = true
        delay(400)
        try {
            val currentVer = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.15"
            } catch (e: Exception) {
                "1.0.15"
            }
            val info = checker.checkLatestRelease(currentVer)
            updateInfo = info
        } catch (e: Exception) {
            errorText = e.message ?: "Failed to check update"
        } finally {
            isChecking = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isDownloadingApk) onDismiss() },
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF14161D))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "ពិនិត្យមើលកំណែថ្មី" else "Check for Updates",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF14161D)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isChecking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF14161D),
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (isKhmer) "កំពុងពិនិត្យមើលពី GitHub..." else "Checking GitHub releases...", fontSize = 13.sp)
                    }
                } else if (isDownloadingApk) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$apkProgress%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14161D)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { apkProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF14161D),
                            trackColor = Color(0xFFECEEF2)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = downloadedBytesText.ifBlank { if (isKhmer) "កំពុងទាញយក APK..." else "Downloading APK..." },
                            fontSize = 12.sp,
                            color = Color(0xFF8A909E)
                        )
                    }
                } else if (updateInfo != null) {
                    val info = updateInfo!!
                    if (info.hasUpdate) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = Color(0xFFF5F6F9)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (isKhmer) "មានកំណែថ្មី v${info.newVersion}" else "New Version v${info.newVersion} Available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF14161D)
                                )
                                if (info.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = info.releaseNotes,
                                        fontSize = 12.sp,
                                        color = Color(0xFF8A909E),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (errorText.isNotBlank()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp)),
                                color = Color(0xFFFEF2F2)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (isKhmer) "ការទាញយកមានបញ្ហា: $errorText" else "Download error: $errorText",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Direct Browser Download Button as instant reliable fallback
                        OutlinedButton(
                            onClick = {
                                val apkUrl = info.apkUrl.ifBlank { "https://github.com/SithpongRin/MusicHubbb/releases/latest" }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isKhmer) "ទាញយកតាម Browser (Chrome)" else "Download via Browser")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isKhmer) "លោកអ្នកកំពុងប្រើប្រាស់កំណែចុងក្រោយបំផុត (v${info.currentVersion})"
                                else "You are on the latest version (v${info.currentVersion})",
                                fontSize = 13.sp,
                                color = Color(0xFF14161D),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (errorText.isNotBlank()) {
                    Text(text = "Error: $errorText", color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (!isChecking && !isDownloadingApk && updateInfo?.hasUpdate == true) {
                Button(
                    onClick = {
                        val apkUrl = updateInfo?.apkUrl ?: ""
                        if (apkUrl.isNotBlank()) {
                            isDownloadingApk = true
                            errorText = ""
                            scope.launch {
                                val result = checker.downloadApk(context, apkUrl) { pct, dl, tot ->
                                    apkProgress = pct
                                    val dlMb = dl / (1024 * 1024f)
                                    val totMb = tot / (1024 * 1024f)
                                    downloadedBytesText = String.format("%.1f MB / %.1f MB", dlMb, totMb)
                                }
                                isDownloadingApk = false
                                result.onSuccess { file ->
                                    ApkInstaller.installApk(context, file)
                                    onDismiss()
                                }.onFailure { e ->
                                    errorText = e.message ?: "Download APK failed"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                ) {
                    Text(if (isKhmer) "ទាញយក និងដំឡើង" else "Download & Install")
                }
            } else if (!isChecking && !isDownloadingApk) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                ) {
                    Text(if (isKhmer) "បិទ" else "Close")
                }
            }
        },
        dismissButton = {
            if (!isDownloadingApk && updateInfo?.hasUpdate == true) {
                TextButton(onClick = onDismiss) {
                    Text(if (isKhmer) "ពេលក្រោយ" else "Later", color = Color(0xFF8A909E))
                }
            }
        }
    )
}

// Equalizer Dialog
@Composable
fun EqualizerDialog(
    isKhmer: Boolean,
    currentPreset: String,
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf("Bass Boost", "Vocal Boost", "Electronic", "Rock", "Flat", "Acoustic")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (isKhmer) "ប្រព័ន្ធកែសំឡេង Equalizer" else "Sound Equalizer",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF14161D)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectPreset(preset)
                                onDismiss()
                            },
                        color = if (currentPreset == preset) Color(0xFF14161D) else Color(0xFFECEEF2)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = preset,
                                fontWeight = if (currentPreset == preset) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentPreset == preset) Color.White else Color(0xFF14161D)
                            )
                            if (currentPreset == preset) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isKhmer) "បិទ" else "Close", color = Color(0xFF14161D))
            }
        }
    )
}
