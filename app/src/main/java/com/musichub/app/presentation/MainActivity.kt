package com.musichub.app.presentation

import android.content.ContentUris
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import coil.compose.AsyncImage
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
    var isKhmer by remember { mutableStateOf(true) }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Start with 0 songs - Completely clean as requested!
    var songsList by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var playlistsList by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }

    var currentSong by remember { mutableStateOf<SongItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0.0f) }
    var showNowPlayingModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }
    var showDownloadModal by remember { mutableStateOf(false) }
    var showCreatePlaylistModal by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf("Bass Boost") }
    var audioQuality by remember { mutableStateOf("High Quality (320 kbps)") }

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
            Toast.makeText(context, if (isKhmer) "បានបញ្ចូល ${newSongs.size} បទដោយជោគជ័យ!" else "Imported ${newSongs.size} tracks successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to scan device media storage
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
                Toast.makeText(context, if (isKhmer) "បានរកឃើញ ${scanned.size} បទលើទូរស័ព្ទ!" else "Found ${scanned.size} tracks on device!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (isKhmer) "មិនមានឯកសារចម្រៀងក្នុងទូរស័ព្ទទេ សូមចុច បញ្ចូលចម្រៀង" else "No music found on device, please tap Import", Toast.LENGTH_LONG).show()
                audioPickerLauncher.launch("audio/*")
            }
        } catch (e: Exception) {
            audioPickerLauncher.launch("audio/*")
        }
    }

    // Sleek Dark Theme matching Web Preview (Tailwind Dark & Indigo)
    val colorScheme = darkColorScheme(
        primary = Color(0xFF6366F1),        // Indigo 500
        secondary = Color(0xFF818CF8),      // Indigo 400
        background = Color(0xFF090A0F),     // True Deep Black / Slate
        surface = Color(0xFF13151F),        // Modern Card Surface
        surfaceVariant = Color(0xFF1C1E2D), // Accent Containers
        onPrimary = Color.White,
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF94A3B8)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            bottomBar = {
                Column {
                    // Floating Mini-Player (Only visible when a song is actively playing)
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
                                // Scrubber line
                                LinearProgressIndicator(
                                    progress = { playbackProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp),
                                    color = Color(0xFF6366F1),
                                    trackColor = Color(0xFF23263B)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Artwork / Music Icon Box
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
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
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

                                    IconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Color(0xFF6366F1), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (songsList.isNotEmpty()) {
                                                val currIdx = songsList.indexOf(currentSong)
                                                val nextIdx = (currIdx + 1) % songsList.size
                                                currentSong = songsList[nextIdx]
                                                isPlaying = true
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Next",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            currentSong = null
                                            isPlaying = false
                                        }
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

                    // Bottom Navigation Bar
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
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                        }
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
                        }
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

        // Full Now Playing Modal
        if (showNowPlayingModal && currentSong != null) {
            NowPlayingDialog(
                isKhmer = isKhmer,
                song = currentSong!!,
                isPlaying = isPlaying,
                progress = playbackProgress,
                onProgressChange = { playbackProgress = it },
                onPlayPause = { isPlaying = !isPlaying },
                onPrevious = {
                    if (songsList.isNotEmpty()) {
                        val currIdx = songsList.indexOf(currentSong)
                        val prevIdx = if (currIdx > 0) currIdx - 1 else songsList.size - 1
                        currentSong = songsList[prevIdx]
                        isPlaying = true
                    }
                },
                onNext = {
                    if (songsList.isNotEmpty()) {
                        val currIdx = songsList.indexOf(currentSong)
                        val nextIdx = (currIdx + 1) % songsList.size
                        currentSong = songsList[nextIdx]
                        isPlaying = true
                    }
                },
                onFavoriteToggle = {
                    currentSong?.let { song ->
                        songsList = songsList.map {
                            if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                        }
                        currentSong = currentSong?.copy(isFavorite = !(currentSong?.isFavorite ?: false))
                    }
                },
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

        // Download Modal
        if (showDownloadModal) {
            DownloadDialog(
                isKhmer = isKhmer,
                onDownloadSubmit = { url ->
                    val newSong = SongItem(
                        id = UUID.randomUUID().toString(),
                        title = url.substringAfterLast("/").substringBeforeLast(".").ifBlank { "Downloaded Audio" },
                        artist = "Legal Web Audio",
                        album = "Downloads",
                        duration = "3:40",
                        durationSec = 220,
                        uriString = url,
                        format = "MP3",
                        isFavorite = false
                    )
                    songsList = listOf(newSong) + songsList
                    currentSong = newSong
                    isPlaying = true
                    showDownloadModal = false
                    Toast.makeText(context, if (isKhmer) "បានទាញយក និងបញ្ចូលចម្រៀងជោគជ័យ!" else "Audio stream downloaded successfully!", Toast.LENGTH_LONG).show()
                },
                onDismiss = { showDownloadModal = false }
            )
        }

        // Create Playlist Modal
        if (showCreatePlaylistModal) {
            CreatePlaylistDialog(
                isKhmer = isKhmer,
                onCreate = { title, desc ->
                    val colors = listOf(Color(0xFF6366F1), Color(0xFFEF4444), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6))
                    val newPl = PlaylistItem(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = desc,
                        color = colors[playlistsList.size % colors.size]
                    )
                    playlistsList = playlistsList + newPl
                    showCreatePlaylistModal = false
                    Toast.makeText(context, if (isKhmer) "បានបង្កើតបញ្ជីចម្រៀង: $title" else "Created playlist: $title", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showCreatePlaylistModal = false }
            )
        }
    }
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
    onFavoriteToggle: (SongItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            // App Header (Matches Web Preview)
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

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Import Audio (+) Button
                    IconButton(
                        onClick = onImportClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1E2130), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Download Cloud Button
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF4F46E5), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Empty State: Matches Web Preview 100% when there are 0 songs!
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
                        // Glowing Music Icon
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = if (isKhmer) "មិនមានតន្ត្រីទេ" else "No Music Found",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isKhmer) "នាំចូលឯកសារសំឡេង ឬទាញយកចម្រៀងស្របច្បាប់ដើម្បីស្តាប់ក្រៅបណ្តាញ។"
                            else "Import local audio files or download permitted music to start listening offline.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Button 1: Import Local Audio Files
                        Button(
                            onClick = onImportClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKhmer) "បញ្ចូលចម្រៀងពីទូរស័ព្ទ (Pick Audio)" else "Import Audio Files",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Button 2: Scan Storage
                        OutlinedButton(
                            onClick = onScanClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE2E8F0)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E344E))
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isKhmer) "ស្កេនរកចម្រៀងក្នុងទូរស័ព្ទ (Scan Device)" else "Scan Device Audio",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            // If songs exist, display tracks cleanly
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

                    TextButton(onClick = onImportClick) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isKhmer) "បន្ថែម" else "Add", color = Color(0xFF818CF8))
                    }
                }
            }

            items(songs) { song ->
                SongRowItem(
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
fun SongRowItem(
    song: SongItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
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
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF3730A3), Color(0xFF4F46E5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
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

            Text(
                text = song.duration,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
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
    onFavoriteToggle: (SongItem) -> Unit
) {
    val categories = if (isKhmer) listOf("ចម្រៀងទាំងអស់", "ចូលចិត្ត", "បានទាញយក") else listOf("All", "Favorites", "Downloaded")

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
                        text = if (isKhmer) "${songs.size} បទក្នុងឧបករណ៍" else "${songs.size} tracks available",
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

            // Filter Chips
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

        if (songs.isEmpty()) {
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
                            text = if (isKhmer) "បណ្ណាល័យរបស់អ្នកទទេ" else "Your Library is Empty",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isKhmer) "នាំចូលឯកសារសំឡេង (MP3, M4A, FLAC, WAV) ពីឧបករណ៍របស់អ្នក។"
                            else "Import audio files from your device storage.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onImportClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text(text = if (isKhmer) "នាំចូលឯកសារចម្រៀង" else "Import Audio Files")
                        }
                    }
                }
            }
        } else {
            items(songs) { song ->
                SongRowItem(
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) }
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
                        text = "${if (isKhmer) "កំណែ" else "Version"}: v1.0.2 (Latest Release) • $totalSongs ${if (isKhmer) "បទ" else "songs"}",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, if (isKhmer) "អ្នកកំពុងប្រើប្រាស់កំណែចុងក្រោយបំផុត v1.0.2" else "You are on the latest version v1.0.2", Toast.LENGTH_SHORT).show()
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

                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color(0xFFEF4444) else Color(0xFF475569)
                        )
                    }
                }

                // Vinyl / Record Center Glow Art
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA), Color(0xFF1E1B4B))
                            )
                        )
                        .border(4.dp, Color(0xFF2E324B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
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

                // Controls Row
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Main circular button
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFF4F46E5), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
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

@Composable
fun DownloadDialog(
    isKhmer: Boolean,
    onDownloadSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131522),
        title = {
            Text(
                text = if (isKhmer) "ទាញយកចម្រៀងក្រៅបណ្តាញ" else "Download Offline Audio",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isKhmer) "បញ្ចូលតំណភ្ជាប់ឯកសារសំឡេងស្របច្បាប់ (.mp3, .wav, .flac):"
                    else "Enter direct audio stream URL (.mp3, .wav, .flac):",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/song.mp3", color = Color(0xFF64748B)) },
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
                        onDownloadSubmit(urlText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text(if (isKhmer) "ចាប់ផ្តើមទាញយក" else "Download")
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
