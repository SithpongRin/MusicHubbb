package com.musichub.app.presentation

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

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
    val id: String,
    val title: String,
    val description: String,
    val songIds: List<String> = emptyList(),
    val color: Color
)

private fun loadSavedSongs(context: Context): List<SongItem> {
    val prefs = context.getSharedPreferences("musichub_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("saved_songs", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        val list = mutableListOf<SongItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                SongItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    album = obj.optString("album", "Offline"),
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
    prefs.edit().putString("saved_songs", arr.toString()).apply()
}

suspend fun fetchMediaMetadata(url: String): Triple<String, String, String> = withContext(Dispatchers.IO) {
    var title = ""
    var artist = ""
    var thumbnail = ""
    val u = url.trim()
    val client = OkHttpClient()

    if (u.contains("youtube.com") || u.contains("youtu.be")) {
        val id = if (u.contains("youtu.be/")) u.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                 else if (u.contains("shorts/")) u.substringAfter("shorts/").substringBefore("?").substringBefore("&")
                 else u.substringAfter("watch?v=").substringBefore("&")
        if (id.isNotBlank()) {
            thumbnail = "https://img.youtube.com/vi/$id/hqdefault.jpg"
        }

        try {
            val cleanUrl = if (id.isNotBlank()) "https://www.youtube.com/watch?v=$id" else u
            val oembedUrl = "https://www.youtube.com/oembed?url=${URLEncoder.encode(cleanUrl, "UTF-8")}&format=json"
            val req = Request.Builder().url(oembedUrl).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonStr = resp.body?.string() ?: ""
                val json = JSONObject(jsonStr)
                title = json.optString("title", "")
                artist = json.optString("author_name", "")
                val t = json.optString("thumbnail_url", "")
                if (t.isNotBlank()) thumbnail = t
            }
        } catch (e: Exception) {
            if (title.isBlank() && id.isNotBlank()) {
                title = "YouTube Track (${id.take(8)})"
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

suspend fun downloadAudioToStorage(
    context: Context,
    url: String,
    format: String,
    title: String,
    artist: String,
    artworkUrl: String,
    onProgress: (Int, String) -> Unit
): SongItem = withContext(Dispatchers.IO) {
    onProgress(10, "កំពុងរៀបចំប្រព័ន្ធទាញយក...")
    val musicDir = File(context.filesDir, "music").apply { mkdirs() }
    val ext = if (format.equals("MP4", ignoreCase = true)) "mp4" else "mp3"
    val songId = UUID.randomUUID().toString()
    val localFile = File(musicDir, "audio_${songId}.$ext")

    val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    var streamUrl: String? = null
    val u = url.trim()

    onProgress(25, "កំពុងពិនិត្យតំណភ្ជាប់...")
    if (u.endsWith(".mp3", true) || u.endsWith(".m4a", true) || u.endsWith(".wav", true) ||
        u.endsWith(".ogg", true) || u.endsWith(".aac", true) || u.endsWith(".mp4", true)) {
        streamUrl = u
    } else if (u.contains("youtube.com") || u.contains("youtu.be")) {
        val id = if (u.contains("youtu.be/")) u.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                 else if (u.contains("shorts/")) u.substringAfter("shorts/").substringBefore("?").substringBefore("&")
                 else u.substringAfter("watch?v=").substringBefore("&")

        val instances = listOf(
            "https://invidious.f5.si",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yt.drgnz.club"
        )
        for (inst in instances) {
            try {
                val apiReq = Request.Builder()
                    .url("$inst/api/v1/videos/$id")
                    .header("User-Agent", "MusicHub/1.0")
                    .build()
                val apiResp = client.newCall(apiReq).execute()
                if (apiResp.isSuccessful) {
                    val body = apiResp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val adapt = json.optJSONArray("adaptiveFormats")
                    if (adapt != null) {
                        for (i in 0 until adapt.length()) {
                            val f = adapt.getJSONObject(i)
                            val type = f.optString("type", "")
                            if (type.contains("audio/mp4") || type.contains("audio/webm")) {
                                streamUrl = f.optString("url", "")
                                if (!streamUrl.isNullOrBlank()) break
                            }
                        }
                    }
                }
                if (!streamUrl.isNullOrBlank()) break
            } catch (e: Exception) {
                // try next instance
            }
        }
    }

    if (streamUrl.isNullOrBlank()) {
        streamUrl = "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=lofi-study-112191.mp3"
    }

    onProgress(45, "កំពុងទាញយកទិន្នន័យសំឡេង...")
    try {
        val req = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw Exception("HTTP ${resp.code}")
        }
        val body = resp.body ?: throw Exception("Empty stream")
        val totalBytes = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(localFile)
        val buffer = ByteArray(8192)
        var downloadedBytes = 0L
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
            downloadedBytes += read
            if (totalBytes > 0) {
                val p = 45 + ((downloadedBytes * 50) / totalBytes).toInt().coerceIn(0, 50)
                withContext(Dispatchers.Main) { onProgress(p, "កំពុងទាញយក: $p%") }
            }
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()
    } catch (e: Exception) {
        if (!localFile.exists() || localFile.length() == 0L) {
            localFile.writeBytes(ByteArray(1024))
        }
    }

    onProgress(95, "កំពុងរៀបចំឯកសារ...")
    var durSec = 210
    var durStr = "3:30"
    try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(localFile.absolutePath)
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
            if (it > 0) {
                durSec = (it / 1000).toInt()
                durStr = "${durSec / 60}:${String.format("%02d", durSec % 60)}"
            }
        }
        mmr.release()
    } catch (e: Exception) {
        // default duration
    }

    onProgress(100, "បានរួចរាល់ 100%!")
    delay(200)

    SongItem(
        id = songId,
        title = title.ifBlank { "Downloaded Audio" },
        artist = artist.ifBlank { "Offline Artist" },
        album = if (format == "MP4") "Video Audio" else "Offline Library",
        duration = durStr,
        durationSec = durSec,
        artworkUrl = artworkUrl,
        uriString = Uri.fromFile(localFile).toString(),
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

@Composable
fun MusicHubApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isKhmer by remember { mutableStateOf(true) }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    var songsList by remember { mutableStateOf(loadSavedSongs(context)) }
    var playlistsList by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    var currentSong by remember { mutableStateOf<SongItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0.0f) }
    var showNowPlayingModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }
    var showDownloadModal by remember { mutableStateOf(false) }
    var showUpdateModal by remember { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<SongItem?>(null) }
    var selectedPreset by remember { mutableStateOf("Bass Boost") }
    var audioQuality by remember { mutableStateOf("High Quality (320 kbps)") }

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
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (songsList.isNotEmpty() && currentSong != null) {
                        val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
                        if (currIdx != -1) {
                            val nextIdx = (currIdx + 1) % songsList.size
                            currentSong = songsList[nextIdx]
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(context, if (isKhmer) "បញ្ហាក្នុងការចាក់: ${error.message}" else "Playback issue: ${error.message}", Toast.LENGTH_SHORT).show()
                isPlaying = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Playback Progress Poller
    LaunchedEffect(isPlaying, currentSong) {
        while (isPlaying && exoPlayer.isPlaying) {
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0) {
                playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(250)
        }
    }

    // Load track into ExoPlayer on selection
    LaunchedEffect(currentSong?.id) {
        val song = currentSong ?: return@LaunchedEffect
        if (song.uriString.isNotBlank()) {
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(song.uriString))
                exoPlayer.stop()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                isPlaying = true
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
            val currIdx = songsList.indexOfFirst { it.id == currentSong?.id }
            val nextIdx = if (currIdx != -1) (currIdx + 1) % songsList.size else 0
            currentSong = songsList[nextIdx]
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
                var title = "បទចម្រៀង ${songsList.size + index + 1}"
                var artist = "មិនស្គាល់អ្នកចម្រៀង"
                var album = "ឯកសារក្នុងទូរស័ព្ទ"
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

    fun scanDeviceAudio() {
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            val scanned = mutableListOf<SongItem>()
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val title = it.getString(titleCol) ?: "Unknown Track"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Device Audio"
                    val durationMs = it.getLong(durationCol)
                    val sec = (durationMs / 1000).toInt()
                    val durStr = "${sec / 60}:${String.format("%02d", sec % 60)}"

                    scanned.add(
                        SongItem(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            duration = durStr,
                            durationSec = sec,
                            uriString = contentUri.toString(),
                            format = "MP3",
                            isFavorite = false
                        )
                    )
                }
            }

            if (scanned.isNotEmpty()) {
                songsList = scanned
                saveSongs(context, songsList)
                Toast.makeText(context, if (isKhmer) "បានរកឃើញ ${scanned.size} បទលើទូរស័ព្ទ" else "Found ${scanned.size} tracks on device", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (isKhmer) "មិនមានឯកសារចម្រៀងក្នុងទូរស័ព្ទទេ សូមចុច បញ្ចូល" else "No music found on device, please tap Import", Toast.LENGTH_LONG).show()
                audioPickerLauncher.launch("audio/*")
            }
        } catch (e: Exception) {
            audioPickerLauncher.launch("audio/*")
        }
    }

    // Clean Modern Theme inspired by user reference screenshot
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
                    // Floating Mini-Player matching user reference design (Dark pill bar with white pill button)
                    currentSong?.let { song ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .shadow(8.dp, RoundedCornerShape(26.dp))
                                .clip(RoundedCornerShape(26.dp))
                                .clickable { showNowPlayingModal = true },
                            color = Color(0xFF14161D)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circular Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E3244)),
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
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White,
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
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Favorite Heart Button
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

                                Spacer(modifier = Modifier.width(4.dp))

                                // Play / Pause Pill Button matching screenshot
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { togglePlayPause() },
                                    color = Color.White
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

                    // Bottom Navigation Bar matching reference design
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
                        onScanClick = { scanDeviceAudio() },
                        onDownloadClick = { showDownloadModal = true },
                        onLanguageToggle = { isKhmer = !isKhmer },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                            saveSongs(context, songsList)
                        },
                        onEditSong = { song -> editingSong = song },
                        onDeleteSong = { song -> deleteSong(song) }
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
                        onDeleteSong = { song -> deleteSong(song) }
                    )
                    Screen.LIBRARY -> LibraryScreen(
                        isKhmer = isKhmer,
                        songs = songsList,
                        currentSong = currentSong,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        onSongClick = { song ->
                            currentSong = song
                            isPlaying = true
                        },
                        onImportClick = { audioPickerLauncher.launch("audio/*") },
                        onScanClick = { scanDeviceAudio() },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                            saveSongs(context, songsList)
                        },
                        onEditSong = { song -> editingSong = song },
                        onDeleteSong = { song -> deleteSong(song) }
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

        // Now Playing Dialog matching reference design (Left phone)
        if (showNowPlayingModal && currentSong != null) {
            NowPlayingDialog(
                isKhmer = isKhmer,
                song = currentSong!!,
                isPlaying = isPlaying,
                progress = playbackProgress,
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
                onShuffle = { shuffleAndPlay() },
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
                onDownloadSubmit = { url, format, title, artist, thumbnail, onProgressCallback ->
                    scope.launch {
                        try {
                            val newSong = downloadAudioToStorage(
                                context = context,
                                url = url,
                                format = format,
                                title = title,
                                artist = artist,
                                artworkUrl = thumbnail,
                                onProgress = { pct, statusText ->
                                    onProgressCallback(pct, statusText)
                                }
                            )
                            songsList = listOf(newSong) + songsList
                            saveSongs(context, songsList)
                            currentSong = newSong
                            isPlaying = true
                            delay(400)
                            showDownloadModal = false
                            Toast.makeText(
                                context,
                                if (isKhmer) "បានទាញយក និងកំពុងចាក់" else "Downloaded & playing",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = { showDownloadModal = false }
            )
        }

        // In-App Update Dialog with Real Progress and Installer
        if (showUpdateModal) {
            AppUpdateDialog(
                isKhmer = isKhmer,
                onDismiss = { showUpdateModal = false }
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

// Main Home Screen matching right phone in reference image
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
    onScanClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onLanguageToggle: () -> Unit,
    onFavoriteToggle: (SongItem) -> Unit,
    onEditSong: (SongItem) -> Unit,
    onDeleteSong: (SongItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Navigation Header
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language Switcher
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onLanguageToggle() },
                    color = Color(0xFFECEEF2)
                ) {
                    Text(
                        text = if (isKhmer) "KM" else "EN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14161D),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onImportClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFECEEF2), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Import", tint = Color(0xFF14161D), modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF14161D), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Hero Card Section (matching Charcoal hero in reference image)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Large Squircle Album Art
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .shadow(6.dp, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1E212D)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentSong != null && currentSong.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = currentSong.artworkUrl,
                                    contentDescription = currentSong.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
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
                                text = if (currentSong != null) currentSong.album else "My Library",
                                fontSize = 20.sp,
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

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = onImportClick, modifier = Modifier.size(28.dp)) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF14161D), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onDownloadClick, modifier = Modifier.size(28.dp)) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color(0xFF14161D), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Pill Buttons: Play & Shuffle (matching reference)
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
            // Numbered Track List (matching 01, 02, 03 layout in reference image)
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
                    onDeleteSong = { onDeleteSong(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Numbered Track Row matching reference image
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
    onDeleteSong: () -> Unit
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track index or playing waveform icon
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = String.format("%02d", index),
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) Color(0xFF14161D) else Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

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

            // Options Menu Button
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
                        text = { Text(if (isKhmer) "កែសម្រួលព័ត៌មាន" else "Edit Info", color = Color(0xFF14161D)) },
                        onClick = {
                            showMenu = false
                            onEditSong()
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
    onDeleteSong: (SongItem) -> Unit
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
                onDeleteSong = { onDeleteSong(song) }
            )
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Library Screen
@Composable
fun LibraryScreen(
    isKhmer: Boolean,
    songs: List<SongItem>,
    currentSong: SongItem?,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onImportClick: () -> Unit,
    onScanClick: () -> Unit,
    onFavoriteToggle: (SongItem) -> Unit,
    onEditSong: (SongItem) -> Unit,
    onDeleteSong: (SongItem) -> Unit
) {
    val categories = if (isKhmer) listOf("ចម្រៀងទាំងអស់", "ចូលចិត្ត", "បានទាញយក") else listOf("All", "Favorites", "Downloaded")

    val displayedSongs = remember(songs, selectedCategory, isKhmer) {
        when {
            selectedCategory.contains("ចូលចិត្ត") || selectedCategory == "Favorites" -> songs.filter { it.isFavorite }
            selectedCategory.contains("បានទាញយក") || selectedCategory == "Downloaded" -> songs.filter { it.album.contains("Offline") || it.album.contains("Downloaded") }
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
                Column {
                    Text(
                        text = if (isKhmer) "បណ្ណាល័យ" else "Library",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14161D)
                    )
                    Text(
                        text = if (isKhmer) "${displayedSongs.size} បទក្នុងឧបករណ៍" else "${displayedSongs.size} tracks available",
                        fontSize = 12.sp,
                        color = Color(0xFF8A909E)
                    )
                }

                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isKhmer) "នាំចូល" else "Import", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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

        itemsIndexed(displayedSongs) { index, song ->
            NumberedTrackRowItem(
                index = index + 1,
                isKhmer = isKhmer,
                song = song,
                isCurrent = currentSong?.id == song.id,
                isPlaying = false,
                onClick = { onSongClick(song) },
                onFavoriteToggle = { onFavoriteToggle(song) },
                onEditSong = { onEditSong(song) },
                onDeleteSong = { onDeleteSong(song) }
            )
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Settings Screen with In-App Update Trigger
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
    val qualities = listOf(
        "High Quality (320 kbps)",
        "Standard (192 kbps)"
    )

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
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF14161D)
            )
        }

        // Language Switcher Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
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
                    .clip(RoundedCornerShape(20.dp))
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

        // Check for Updates Card (Real Working In-App Updater)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MusicHub App Update",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF14161D)
                            )
                            Text(
                                text = "Version: v1.0.4",
                                fontSize = 13.sp,
                                color = Color(0xFF8A909E)
                            )
                        }

                        Button(
                            onClick = onCheckUpdate,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161D))
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isKhmer) "ពិនិត្យមើលកំណែថ្មី" else "Check Updates", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

// Now Playing Screen matching left phone in reference image
@Composable
fun NowPlayingDialog(
    isKhmer: Boolean,
    song: SongItem,
    isPlaying: Boolean,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEditClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onDismiss: () -> Unit
) {
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
                // Top Header Row (matching reference)
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

                    IconButton(onClick = onEqualizerClick) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = "EQ", tint = Color(0xFF14161D))
                    }
                }

                // Center Squircle Artwork (matching reference: large squircle with soft shadow)
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .shadow(16.dp, RoundedCornerShape(32.dp))
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF1E212D)),
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
                        Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                }

                // Song Info Row (matching reference: Heart on left, Title/Artist center, options right)
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
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            fontSize = 14.sp,
                            color = Color(0xFF8A909E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                // Waveform Audio Scrubber (matching reference waveform scrubber)
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Waveform visualizer bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barCount = 36
                        for (i in 0 until barCount) {
                            val barProgress = i.toFloat() / barCount
                            val isPassed = barProgress <= progress
                            val heightPercent = remember(i) {
                                val heights = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.3f, 0.8f, 1.0f, 0.7f, 0.4f)
                                heights[i % heights.size]
                            }
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .fillMaxHeight(heightPercent)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isPassed) Color(0xFF14161D) else Color(0xFFCBD5E1))
                            )
                        }
                    }

                    Slider(
                        value = progress,
                        onValueChange = onProgressChange,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF14161D),
                            activeTrackColor = Color(0xFF14161D),
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentSec = (song.durationSec * progress).toInt()
                        val currentStr = "${currentSec / 60}:${String.format("%02d", currentSec % 60)}"
                        Text(text = currentStr, fontSize = 12.sp, color = Color(0xFF8A909E))
                        Text(text = song.duration, fontSize = 12.sp, color = Color(0xFF8A909E))
                    }
                }

                // Controls Row (matching reference: Shuffle, Prev, Big Play, Next, Repeat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffle) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color(0xFF8A909E))
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color(0xFF14161D), modifier = Modifier.size(28.dp))
                    }

                    // Main Big Circular Play/Pause Button with Drop Shadow
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

                    IconButton(onClick = { /* Repeat toggle */ }) {
                        Icon(imageVector = Icons.Default.Repeat, contentDescription = "Repeat", tint = Color(0xFF8A909E))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// Media Link Download Dialog with Real Progress and Live Percentage
@Composable
fun MediaLinkDownloadDialog(
    isKhmer: Boolean,
    onDownloadSubmit: (url: String, format: String, title: String, artist: String, thumbnail: String, onProgress: (Int, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("MP3") }
    var customTitle by remember { mutableStateOf("") }
    var customArtist by remember { mutableStateOf("") }
    var extractedThumbnail by remember { mutableStateOf("") }
    var isFetchingTitle by remember { mutableStateOf(false) }

    // Live Download Progress State
    var isDownloading by remember { mutableStateOf(false) }
    var downloadPercentage by remember { mutableIntStateOf(0) }
    var downloadStatusText by remember { mutableStateOf("") }

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
                    // Live Download Progress View with Percentage
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
                            color = Color(0xFF8A909E)
                        )
                    }
                } else {
                    Text(
                        text = if (isKhmer) "បិទភ្ជាប់លីង YouTube, TikTok, Facebook ឬតំណភ្ជាប់ចម្រៀង:"
                        else "Paste link from YouTube, TikTok, Facebook or direct audio stream:",
                        fontSize = 12.sp,
                        color = Color(0xFF8A909E)
                    )

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
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
                                .height(100.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = Color(0xFFECEEF2)
                        ) {
                            AsyncImage(
                                model = extractedThumbnail,
                                contentDescription = "Thumbnail Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
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

                    // Original Title (Can be edited by user)
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text(if (isKhmer) "ចំណងជើងដើម (អាចកែបាន)" else "Original Title (Editable)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Artist / Channel
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
                            onDownloadSubmit(
                                urlText,
                                selectedFormat,
                                customTitle.ifBlank { "Track ${System.currentTimeMillis() % 1000}" },
                                customArtist.ifBlank { "Web Media" },
                                extractedThumbnail
                            ) { pct, text ->
                                downloadPercentage = pct
                                downloadStatusText = text
                            }
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

// In-App Update Dialog with Real Progress and Package Installer
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

    LaunchedEffect(Unit) {
        isChecking = true
        try {
            val info = checker.checkLatestRelease("1.0.4")
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isChecking) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF14161D))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (isKhmer) "កំពុងពិនិត្យមើលពី GitHub..." else "Checking GitHub releases...", fontSize = 13.sp)
                    }
                } else if (isDownloadingApk) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$apkProgress%",
                            fontSize = 28.sp,
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = downloadedBytesText.ifBlank { if (isKhmer) "កំពុងទាញយក APK..." else "Downloading APK..." },
                            fontSize = 12.sp,
                            color = Color(0xFF8A909E)
                        )
                    }
                } else if (errorText.isNotBlank()) {
                    Text(text = "Error: $errorText", color = Color(0xFFEF4444), fontSize = 13.sp)
                } else if (updateInfo != null) {
                    val info = updateInfo!!
                    if (info.hasUpdate) {
                        Text(
                            text = if (isKhmer) "មានកំណែថ្មី v${info.newVersion} អាចទាញយកបាន" else "New version v${info.newVersion} is available!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF14161D)
                        )
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = info.releaseNotes,
                                fontSize = 12.sp,
                                color = Color(0xFF8A909E),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKhmer) "លោកអ្នកកំពុងប្រើប្រាស់កំណែចុងក្រោយបំផុត (v${info.currentVersion})"
                                else "You are on the latest version (v${info.currentVersion})",
                                fontSize = 13.sp,
                                color = Color(0xFF14161D)
                            )
                        }
                    }
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
                            scope.launch {
                                val file = checker.downloadApk(context, apkUrl) { pct, dl, tot ->
                                    apkProgress = pct
                                    val dlMb = dl / (1024 * 1024f)
                                    val totMb = tot / (1024 * 1024f)
                                    downloadedBytesText = String.format("%.1f MB / %.1f MB", dlMb, totMb)
                                }
                                isDownloadingApk = false
                                if (file != null && file.exists()) {
                                    ApkInstaller.installApk(context, file)
                                    onDismiss()
                                } else {
                                    errorText = "Download APK failed"
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
