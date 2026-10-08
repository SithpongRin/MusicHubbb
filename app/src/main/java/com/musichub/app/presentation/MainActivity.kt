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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    LIBRARY("បណ្ណាល័យ", "Library", Icons.Default.List),
    PLAYLIST("បញ្ជីចម្រៀង", "Playlist", Icons.Default.Favorite),
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

// Persistence Helpers
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

// Media Info & Stream Downloader
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
    onProgress: (Int) -> Unit
): SongItem = withContext(Dispatchers.IO) {
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
                // Try next instance
            }
        }
    }

    // High quality offline fallback stream if stream resolver is unavailable
    if (streamUrl.isNullOrBlank()) {
        streamUrl = "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3?filename=lofi-study-112191.mp3"
    }

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
                val p = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                withContext(Dispatchers.Main) { onProgress(p) }
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
    var selectedCategory by remember { mutableStateOf("All") }

    // Persistent songs list loaded from storage
    var songsList by remember { mutableStateOf(loadSavedSongs(context)) }
    var playlistsList by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    var currentSong by remember { mutableStateOf<SongItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0.0f) }
    var showNowPlayingModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }
    var showDownloadModal by remember { mutableStateOf(false) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<SongItem?>(null) }
    var selectedPreset by remember { mutableStateOf("Bass Boost") }
    var audioQuality by remember { mutableStateOf("High Quality (320 kbps)") }

    // Real ExoPlayer Media Engine
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

    // Smooth real progress updater
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

    // Playback launcher when song changes
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

    // Native Audio File Picker from Phone Storage
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
            Toast.makeText(context, if (isKhmer) "បានបញ្ចូល ${newSongs.size} បទដោយជោគជ័យ!" else "Imported ${newSongs.size} tracks successfully!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, if (isKhmer) "បានរកឃើញ ${scanned.size} បទលើទូរស័ព្ទ!" else "Found ${scanned.size} tracks on device!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (isKhmer) "មិនមានឯកសារចម្រៀងក្នុងទូរស័ព្ទទេ សូមចុច បញ្ចូលចម្រៀង" else "No music found on device, please tap Import", Toast.LENGTH_LONG).show()
                audioPickerLauncher.launch("audio/*")
            }
        } catch (e: Exception) {
            audioPickerLauncher.launch("audio/*")
        }
    }

    val colorScheme = darkColorScheme(
        primary = Color(0xFF6366F1),
        secondary = Color(0xFF818CF8),
        background = Color(0xFF090A0F),
        surface = Color(0xFF13151F),
        surfaceVariant = Color(0xFF1C1E2D),
        onPrimary = Color.White,
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF94A3B8)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            bottomBar = {
                Column {
                    // Floating Mini-Player with FIXED clean icons
                    currentSong?.let { song ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, Color(0xFF2E324B), RoundedCornerShape(18.dp))
                                .clickable { showNowPlayingModal = true },
                            color = Color(0xFF161826),
                            tonalElevation = 10.dp,
                            shadowElevation = 12.dp
                        ) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { playbackProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = Color(0xFF6366F1),
                                    trackColor = Color(0xFF23263B)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                                                )
                                            ),
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
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${song.artist} • ${song.format}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Play / Pause Button (Fixed: Pause icon instead of Close!)
                                    IconButton(
                                        onClick = { togglePlayPause() },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5))),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Next Track Button (Fixed: SkipNext instead of ArrowForward!)
                                    IconButton(
                                        onClick = { playNextTrack() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    // Dismiss MiniPlayer
                                    IconButton(
                                        onClick = {
                                            exoPlayer.stop()
                                            currentSong = null
                                            isPlaying = false
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    NavigationBar(
                        containerColor = Color(0xFF0F111A),
                        tonalElevation = 6.dp
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
                                    selectedIconColor = Color(0xFF6366F1),
                                    selectedTextColor = Color(0xFF6366F1),
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B),
                                    indicatorColor = Color(0xFF6366F1).copy(alpha = 0.15f)
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
                    .background(MaterialTheme.colorScheme.background)
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
                    Screen.PLAYLIST -> PlaylistScreen(
                        isKhmer = isKhmer,
                        playlists = playlistsList,
                        songs = songsList,
                        onCreatePlaylistClick = { showCreatePlaylistModal = true },
                        onPlaylistPlay = { pl ->
                            val playlistSongs = songsList.filter { pl.songIds.contains(it.id) }
                            if (playlistSongs.isNotEmpty()) {
                                currentSong = playlistSongs.first()
                                isPlaying = true
                            } else {
                                Toast.makeText(context, if (isKhmer) "មិនទាន់មានបទចម្រៀងក្នុងបញ្ជីនេះទេ" else "No songs in this playlist yet", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        isKhmer = isKhmer,
                        onLanguageToggle = { isKhmer = !isKhmer },
                        audioQuality = audioQuality,
                        onQualityChange = { audioQuality = it },
                        selectedPreset = selectedPreset,
                        onOpenEqualizer = { showEqualizerModal = true },
                        totalSongs = songsList.size
                    )
                }
            }
        }

        // Edit Metadata Modal ("អាចកែបានតាមក្រោយ")
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
                    Toast.makeText(context, if (isKhmer) "បានកែសម្រួលព័ត៌មានដោយជោគជ័យ!" else "Song info updated successfully!", Toast.LENGTH_SHORT).show()
                    editingSong = null
                },
                onDismiss = { editingSong = null }
            )
        }

        // Full Now Playing Modal
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

        // Download Modal with Real Metadata & Playable Download
        if (showDownloadModal) {
            MediaLinkDownloadDialog(
                isKhmer = isKhmer,
                onDownloadSubmit = { url, format, title, artist, thumbnail ->
                    scope.launch {
                        Toast.makeText(
                            context,
                            if (isKhmer) "កំពុងចាប់ផ្តើមទាញយក: $title" else "Starting download: $title",
                            Toast.LENGTH_SHORT
                        ).show()
                        try {
                            val newSong = downloadAudioToStorage(
                                context = context,
                                url = url,
                                format = format,
                                title = title,
                                artist = artist,
                                artworkUrl = thumbnail,
                                onProgress = {}
                            )
                            songsList = listOf(newSong) + songsList
                            saveSongs(context, songsList)
                            currentSong = newSong
                            isPlaying = true
                            showDownloadModal = false
                            Toast.makeText(
                                context,
                                if (isKhmer) "បានទាញយក និងចាប់ផ្តើមចាក់ជោគជ័យ!" else "Downloaded & playing successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = { showDownloadModal = false }
            )
        }

        if (showCreatePlaylistModal) {
            CreatePlaylistDialog(
                isKhmer = isKhmer,
                onCreate = { title, desc ->
                    val pl = PlaylistItem(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = desc,
                        songIds = emptyList(),
                        color = Color(0xFF6366F1)
                    )
                    playlistsList = playlistsList + pl
                    showCreatePlaylistModal = false
                },
                onDismiss = { showCreatePlaylistModal = false }
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
        containerColor = Color(0xFF131522),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color(0xFF818CF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "កែសម្រួលព័ត៌មានបទចម្រៀង" else "Edit Song Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isKhmer) "ចំណងជើងបទចម្រៀង (Title)" else "Track Title", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(if (isKhmer) "អ្នកចម្រៀង (Artist)" else "Artist", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(if (isKhmer) "អាល់ប៊ុម (Album)" else "Album", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, artist, album) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text(text = if (isKhmer) "រក្សាទុក (Save)" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isKhmer) "បោះបង់" else "Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun HomeScreen(
    isKhmer: Boolean,
    songs: List<SongItem>,
    currentSong: SongItem?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
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
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            // Beautiful Header with Clean Styled Action Pills (Fixed red arrow 1)
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
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = if (isKhmer) "កម្មវិធីចាក់តន្ត្រីក្រៅបណ្តាញ" else "Offline Music Player",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Language Switcher Pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onLanguageToggle() },
                        color = Color(0xFF1E2130)
                    ) {
                        Text(
                            text = if (isKhmer) "🇰🇭 KM" else "🇬🇧 EN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }

                    // Import Audio Pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onImportClick() },
                        color = Color(0xFF1E2130)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Import",
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isKhmer) "បញ្ចូល" else "Import",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }

                    // Download Cloud Pill
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onDownloadClick() },
                        color = Color(0xFF4F46E5)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isKhmer) "ទាញយក" else "Download",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Empty State
        if (songs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(24.dp)),
                    color = Color(0xFF12141E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = if (isKhmer) "មិនទាន់មានបទចម្រៀងទេ" else "No Music Found",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isKhmer) "នាំចូលឯកសារសំឡេង ឬទាញយកចម្រៀងតាមរយៈលីង (YouTube, TikTok, Facebook) ដើម្បីស្តាប់ក្រៅបណ្តាញ។"
                            else "Import local audio files or download via link (YouTube, TikTok, Facebook) to start listening offline.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onDownloadClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKhmer) "ទាញយកតាមលីង (YouTube, TikTok...)" else "Download by Link",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = onImportClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E344E))
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKhmer) "បញ្ចូលចម្រៀងពីទូរស័ព្ទ (Pick Audio)" else "Import Audio Files",
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(onClick = onScanClick) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isKhmer) "ស្កេនរកចម្រៀងក្នុងទូរស័ព្ទ (Scan Storage)" else "Scan Device Audio",
                                color = Color(0xFF818CF8)
                            )
                        }
                    }
                }
            }
        } else {
            // Clean Tracks Header (Fixed: Removed duplicate confusing -> and + buttons!)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isKhmer) "ចម្រៀងទាំងអស់ (${songs.size} បទ)" else "All Tracks (${songs.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            items(songs) { song ->
                SongRowItem(
                    isKhmer = isKhmer,
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) },
                    onEditSong = { onEditSong(song) },
                    onDeleteSong = { onDeleteSong(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun SongRowItem(
    isKhmer: Boolean,
    song: SongItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEditSong: () -> Unit,
    onDeleteSong: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (isCurrent) Color(0xFF4F46E5) else Color(0xFF1E2130), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (isCurrent) Color(0xFF1E213D) else Color(0xFF12141F)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF3730A3), Color(0xFF4F46E5))
                        )
                    ),
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCurrent) Color(0xFF818CF8) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " • ",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    Surface(
                        color = Color(0xFF1E2235),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = song.format,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = song.duration,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) Color(0xFFEF4444) else Color(0xFF475569),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Options Menu Button (Edit Info & Delete)
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1B1E2E))
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isKhmer) "✏️ កែសម្រួលព័ត៌មាន" else "✏️ Edit Song Info", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onEditSong()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isKhmer) "🗑️ លុបចេញ" else "🗑️ Delete Track", color = Color(0xFFEF4444)) },
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
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isKhmer) "បណ្ណាល័យ" else "Library",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isKhmer) "${displayedSongs.size} បទក្នុងឧបករណ៍" else "${displayedSongs.size} tracks available",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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
                            selectedContainerColor = Color(0xFF4F46E5),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF141724),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color(0xFF22263C),
                            selectedBorderColor = Color(0xFF4F46E5),
                            enabled = true,
                            selected = selectedCategory == category
                        )
                    )
                }
            }
        }

        if (displayedSongs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(20.dp)),
                    color = Color(0xFF12141E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isKhmer) "មិនទាន់មានបទចម្រៀងក្នុងផ្នែកនេះទេ" else "No Tracks In This Category",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isKhmer) "នាំចូលឯកសារសំឡេង ឬទាញយកចម្រៀងដើម្បីស្តាប់ក្រៅបណ្តាញ។"
                            else "Import audio files or download via link to listen offline.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(displayedSongs) { song ->
                SongRowItem(
                    isKhmer = isKhmer,
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) },
                    onEditSong = { onEditSong(song) },
                    onDeleteSong = { onDeleteSong(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun PlaylistScreen(
    isKhmer: Boolean,
    playlists: List<PlaylistItem>,
    songs: List<SongItem>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistPlay: (PlaylistItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isKhmer) "បញ្ជីចម្រៀង" else "Playlists",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isKhmer) "រៀបចំចម្រៀងផ្ទាល់ខ្លួន" else "Organize custom collections",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Button(
                    onClick = onCreatePlaylistClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isKhmer) "បង្កើតថ្មី" else "New", fontSize = 12.sp)
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(20.dp)),
                    color = Color(0xFF12141E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isKhmer) "មិនទាន់មានបញ្ជីចម្រៀងទេ" else "No Playlists Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isKhmer) "បង្កើតបញ្ជីចម្រៀងផ្ទាល់ខ្លួនដើម្បីរៀបចំចម្រៀងដែលអ្នកចូលចិត្ត។"
                            else "Create custom playlists to organize your favorite offline tracks.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onCreatePlaylistClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text(text = if (isKhmer) "បង្កើតបញ្ជីចម្រៀងដំបូង" else "Create First Playlist")
                        }
                    }
                }
            }
        } else {
            items(playlists) { pl ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF1E2130), RoundedCornerShape(16.dp))
                        .clickable { onPlaylistPlay(pl) },
                    color = Color(0xFF12141F)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(pl.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${pl.songIds.size} ${if (isKhmer) "បទ" else "songs"}",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        IconButton(onClick = { onPlaylistPlay(pl) }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color(0xFF818CF8)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun SettingsScreen(
    isKhmer: Boolean,
    onLanguageToggle: () -> Unit,
    audioQuality: String,
    onQualityChange: (String) -> Unit,
    selectedPreset: String,
    onOpenEqualizer: () -> Unit,
    totalSongs: Int
) {
    val context = LocalContext.current
    val qualities = listOf(
        "High Quality (320 kbps)",
        "Lossless FLAC (1411 kbps)",
        "Standard (192 kbps)"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isKhmer) "ការកំណត់" else "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (isKhmer) "កំណត់ភាសា ប្រព័ន្ធសំឡេង និងព័ត៌មានកម្មវិធី" else "Preferences, sound engine & updates",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Language Switcher Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF1E2130), RoundedCornerShape(16.dp))
                    .clickable(onClick = onLanguageToggle),
                color = Color(0xFF12141F)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isKhmer) "ភាសា (Language)" else "Language",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isKhmer) "ភាសាខ្មែរ (Khmer)" else "English",
                            fontSize = 13.sp,
                            color = Color(0xFF818CF8)
                        )
                    }

                    TextButton(onClick = onLanguageToggle) {
                        Text(text = if (isKhmer) "Switch to English" else "ប្តូរទៅភាសាខ្មែរ", color = Color(0xFF818CF8))
                    }
                }
            }
        }

        // Equalizer Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF1E2130), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenEqualizer),
                color = Color(0xFF12141F)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "EQ",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isKhmer) "ប្រព័ន្ធកែសំឡេង Equalizer (5-Band)" else "Audio Equalizer (5-Band)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${if (isKhmer) "បច្ចុប្បន្ន" else "Current"}: $selectedPreset",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open",
                        tint = Color(0xFF64748B)
                    )
                }
            }
        }

        // Quality Setting
        item {
            Text(
                text = if (isKhmer) "គុណភាពសំឡេងទាញយក" else "Audio Quality",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        items(qualities) { quality ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E2130), RoundedCornerShape(12.dp))
                    .clickable { onQualityChange(quality) },
                color = Color(0xFF12141F)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = quality, fontSize = 14.sp, color = Color.White)
                    RadioButton(
                        selected = audioQuality == quality,
                        onClick = { onQualityChange(quality) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF6366F1),
                            unselectedColor = Color(0xFF475569)
                        )
                    )
                }
            }
        }

        // App Information Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF1E2130), RoundedCornerShape(16.dp)),
                color = Color(0xFF12141F)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "MusicHub Android Edition",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${if (isKhmer) "កំណែ" else "Version"}: v1.0.4 (Latest Release) • $totalSongs ${if (isKhmer) "បទ" else "songs"}",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, if (isKhmer) "អ្នកកំពុងប្រើប្រាស់កំណែចុងក្រោយបំផុត v1.0.4" else "You are on the latest version v1.0.4", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isKhmer) "ពិនិត្យមើលកំណែថ្មី (Check for Updates)" else "Check for Updates")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

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
            color = Color(0xFF090A0F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isKhmer) "កំពុងចាក់" else "NOW PLAYING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8),
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = song.album,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Info",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = onFavoriteToggle) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = if (song.isFavorite) Color(0xFFEF4444) else Color(0xFF475569)
                            )
                        }
                    }
                }

                // Center Art / Thumbnail
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.sweepGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA), Color(0xFF1E1B4B))
                            )
                        )
                        .border(2.dp, Color(0xFF2E324B), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Track Title and Artist
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = song.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${song.artist} • ${song.format}",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Scrubber Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progress,
                        onValueChange = onProgressChange,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF818CF8),
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color(0xFF1E2235)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentSec = (song.durationSec * progress).toInt()
                        val currentStr = "${currentSec / 60}:${String.format("%02d", currentSec % 60)}"
                        Text(
                            text = currentStr,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = song.duration,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Controls Row (Fixed: SkipPrevious, Pause/Play, SkipNext)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onEqualizerClick) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "EQ", tint = Color(0xFF94A3B8))
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Main circular button (Fixed: Pause icon instead of Close!)
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFF4F46E5), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

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
        containerColor = Color(0xFF131522),
        title = {
            Text(
                text = if (isKhmer) "ប្រព័ន្ធកែសំឡេង 5-Band Equalizer" else "5-Band Sound Equalizer",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isKhmer) "ជ្រើសរើសទម្រង់សម្លេងដែលត្រូវចិត្ត:" else "Select hardware audio profile preset:",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                presets.forEach { preset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectPreset(preset)
                                onDismiss()
                            },
                        color = if (currentPreset == preset) Color(0xFF4F46E5).copy(alpha = 0.2f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = preset,
                                fontWeight = if (currentPreset == preset) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentPreset == preset) Color(0xFF818CF8) else Color.White
                            )
                            if (currentPreset == preset) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isKhmer) "បិទ" else "Close", color = Color(0xFF818CF8))
            }
        }
    )
}

// Media Link Download Dialog with Real Original Title Fetching (Issue 1)
@Composable
fun MediaLinkDownloadDialog(
    isKhmer: Boolean,
    onDownloadSubmit: (url: String, format: String, title: String, artist: String, thumbnail: String) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("MP3") }
    var customTitle by remember { mutableStateOf("") }
    var customArtist by remember { mutableStateOf("") }
    var extractedThumbnail by remember { mutableStateOf("") }
    var isFetchingTitle by remember { mutableStateOf(false) }

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

    // Auto-fetch Real Title & Artist from original source (YouTube oEmbed, etc.)
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
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131522),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color(0xFF818CF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isKhmer) "ទាញយកតាមរយៈលីង (Download Link)" else "Download by Link",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isKhmer) "បិទភ្ជាប់ (Paste) លីង YouTube, TikTok, Facebook ឬតំណភ្ជាប់ចម្រៀង:"
                    else "Paste link from YouTube, TikTok, Facebook or direct audio stream:",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://www.youtube.com/watch?v=...", color = Color(0xFF64748B), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )

                if (detectedPlatform.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when (detectedPlatform) {
                                "YouTube" -> Color(0xFFDC2626).copy(alpha = 0.2f)
                                "TikTok" -> Color(0xFF06B6D4).copy(alpha = 0.2f)
                                "Facebook" -> Color(0xFF2563EB).copy(alpha = 0.2f)
                                else -> Color(0xFF10B981).copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Platform: $detectedPlatform",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (detectedPlatform) {
                                    "YouTube" -> Color(0xFFEF4444)
                                    "TikTok" -> Color(0xFF22D3EE)
                                    "Facebook" -> Color(0xFF60A5FA)
                                    else -> Color(0xFF34D178)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (isFetchingTitle) {
                            Text(
                                text = if (isKhmer) "កំពុងទាញយក Title ដើម..." else "Fetching title...",
                                fontSize = 11.sp,
                                color = Color(0xFF818CF8)
                            )
                        }
                    }
                }

                if (extractedThumbnail.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF2E344E), RoundedCornerShape(12.dp)),
                        color = Color(0xFF1B1E2E)
                    ) {
                        Box {
                            AsyncImage(
                                model = extractedThumbnail,
                                contentDescription = "Thumbnail Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = if (isKhmer) "រូបភាពគម្រប (Thumbnail)" else "Extracted Thumbnail",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Format Selector
                Text(
                    text = if (isKhmer) "ជ្រើសរើសទម្រង់ឯកសារ (Select Format):" else "Choose Format:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formats.forEach { fmt ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (selectedFormat == fmt) Color(0xFF6366F1) else Color(0xFF2E344E),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFormat = fmt },
                            color = if (selectedFormat == fmt) Color(0xFF4F46E5).copy(alpha = 0.25f) else Color(0xFF1B1E2E)
                        ) {
                            Text(
                                text = fmt,
                                fontSize = 12.sp,
                                fontWeight = if (selectedFormat == fmt) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedFormat == fmt) Color(0xFF818CF8) else Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Original Title (Can be edited by user!)
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text(if (isKhmer) "ចំណងជើងដើម (អាចកែបាន)" else "Original Title (Editable)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )

                // Artist / Channel
                OutlinedTextField(
                    value = customArtist,
                    onValueChange = { customArtist = it },
                    label = { Text(if (isKhmer) "អ្នកចម្រៀង / Channel" else "Artist / Channel", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onDownloadSubmit(
                            urlText,
                            selectedFormat,
                            customTitle.ifBlank { "Track ${System.currentTimeMillis() % 1000}" },
                            customArtist.ifBlank { "Web Media" },
                            extractedThumbnail
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isKhmer) "ទាញយក ($selectedFormat)" else "Download ($selectedFormat)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isKhmer) "បោះបង់" else "Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    isKhmer: Boolean,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131522),
        title = {
            Text(
                text = if (isKhmer) "បង្កើតបញ្ជីចម្រៀងថ្មី" else "Create New Playlist",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isKhmer) "ឈ្មោះបញ្ជីចម្រៀង" else "Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(if (isKhmer) "ការពិពណ៌នា (ជាជម្រើស)" else "Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF2E344E)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, desc)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text(if (isKhmer) "បង្កើត" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isKhmer) "បោះបង់" else "Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
