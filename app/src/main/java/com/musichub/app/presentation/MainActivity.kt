package com.musichub.app.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.List),
    PLAYLIST("Playlist", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings)
}

data class SongItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val durationSec: Int,
    val artworkUrl: String,
    val format: String = "320kbps",
    var isFavorite: Boolean = false
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
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Sample Music Library
    val initialSongs = remember {
        listOf(
            SongItem(
                id = "1",
                title = "Moonlight Sonata (Remastered)",
                artist = "Ludwig van Beethoven",
                album = "Classical Masterpieces",
                duration = "3:45",
                durationSec = 225,
                artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80",
                format = "FLAC",
                isFavorite = true
            ),
            SongItem(
                id = "2",
                title = "Neon Horizons",
                artist = "Cyberwave Collective",
                album = "Retrofuturism Vol. 1",
                duration = "4:12",
                durationSec = 252,
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80",
                format = "320kbps",
                isFavorite = false
            ),
            SongItem(
                id = "3",
                title = "Golden Hour Chillhop",
                artist = "Aurora Beats",
                album = "Sunset Coffee Sessions",
                duration = "2:58",
                durationSec = 178,
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80",
                format = "320kbps",
                isFavorite = true
            ),
            SongItem(
                id = "4",
                title = "Traditional Acoustic Strings",
                artist = "Khmer Heritage Ensemble",
                album = "Sounds of Angkor",
                duration = "3:30",
                durationSec = 210,
                artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80",
                format = "FLAC",
                isFavorite = true
            ),
            SongItem(
                id = "5",
                title = "Starlight Symphony",
                artist = "Modern Orchestra",
                album = "Cosmic Reverie",
                duration = "5:15",
                durationSec = 315,
                artworkUrl = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=500&q=80",
                format = "320kbps",
                isFavorite = false
            ),
            SongItem(
                id = "6",
                title = "Midnight Espresso",
                artist = "Tokyo Lo-Fi Club",
                album = "Shibuya Rain",
                duration = "3:05",
                durationSec = 185,
                artworkUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&q=80",
                format = "320kbps",
                isFavorite = false
            )
        )
    }

    var songsList by remember { mutableStateOf(initialSongs) }
    var currentSong by remember { mutableStateOf<SongItem?>(initialSongs.first()) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0.35f) }
    var showNowPlayingModal by remember { mutableStateOf(false) }
    var showEqualizerModal by remember { mutableStateOf(false) }
    var showDownloadModal by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf("Bass Boost") }
    var audioQuality by remember { mutableStateOf("High Quality (320 kbps)") }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF6366F1),
            secondary = Color(0xFF818CF8),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            onPrimary = Color.White,
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFE2E8F0)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF6366F1),
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            bottomBar = {
                Column {
                    // Mini Player (Only visible if song is selected)
                    currentSong?.let { song ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showNowPlayingModal = true },
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            Column {
                                // Progress line
                                LinearProgressIndicator(
                                    progress = { playbackProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = song.artworkUrl,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${song.artist} • ${song.format}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = {
                                            val currentIndex = songsList.indexOf(currentSong)
                                            val nextIndex = (currentIndex + 1) % songsList.size
                                            currentSong = songsList[nextIndex]
                                            isPlaying = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Next"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Screen.values().forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                        songs = songsList,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { song ->
                            currentSong = song
                            isPlaying = true
                        },
                        onEqualizerClick = { showEqualizerModal = true },
                        onDownloadClick = { showDownloadModal = true },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                        }
                    )
                    Screen.LIBRARY -> LibraryScreen(
                        songs = songsList,
                        currentSong = currentSong,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        onSongClick = { song ->
                            currentSong = song
                            isPlaying = true
                        },
                        onFavoriteToggle = { song ->
                            songsList = songsList.map {
                                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                            }
                        }
                    )
                    Screen.PLAYLIST -> PlaylistScreen(
                        songs = songsList,
                        onPlaylistPlay = {
                            currentSong = songsList.firstOrNull()
                            isPlaying = true
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        audioQuality = audioQuality,
                        onQualityChange = { audioQuality = it },
                        selectedPreset = selectedPreset,
                        onOpenEqualizer = { showEqualizerModal = true }
                    )
                }
            }
        }

        // Full Now Playing Modal
        if (showNowPlayingModal && currentSong != null) {
            NowPlayingDialog(
                song = currentSong!!,
                isPlaying = isPlaying,
                progress = playbackProgress,
                onProgressChange = { playbackProgress = it },
                onPlayPause = { isPlaying = !isPlaying },
                onPrevious = {
                    val currentIndex = songsList.indexOf(currentSong)
                    val prevIndex = if (currentIndex > 0) currentIndex - 1 else songsList.size - 1
                    currentSong = songsList[prevIndex]
                    isPlaying = true
                },
                onNext = {
                    val currentIndex = songsList.indexOf(currentSong)
                    val nextIndex = (currentIndex + 1) % songsList.size
                    currentSong = songsList[nextIndex]
                    isPlaying = true
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
                currentPreset = selectedPreset,
                onSelectPreset = {
                    selectedPreset = it
                    Toast.makeText(context, "Equalizer set to $it", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showEqualizerModal = false }
            )
        }

        // Download Modal
        if (showDownloadModal) {
            DownloadDialog(
                onDownloadSubmit = { url ->
                    Toast.makeText(context, "Started background download from URL", Toast.LENGTH_LONG).show()
                    showDownloadModal = false
                },
                onDismiss = { showDownloadModal = false }
            )
        }
    }
}

@Composable
fun HomeScreen(
    songs: List<SongItem>,
    currentSong: SongItem?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onEqualizerClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFavoriteToggle: (SongItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            // App Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MusicHub",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Offline & High-Fidelity Audio",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onEqualizerClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Featured Hero Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "LOSSLESS FLAC & 320 KBPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Studio Sound Quality",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Enjoy offline media with 5-band sound tuning",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Recently Played Header
        item {
            Text(
                text = "Recently Played",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Recently Played Horizontal Carousel
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(songs) { song ->
                    Surface(
                        modifier = Modifier
                            .width(135.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSongClick(song) },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box {
                                AsyncImage(
                                    model = song.artworkUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(115.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                if (currentSong?.id == song.id && isPlaying) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = Color.White,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = song.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Top Tracks Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Tracks (${songs.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Tracks List
        items(songs) { song ->
            SongListItem(
                song = song,
                isCurrent = currentSong?.id == song.id,
                isPlaying = isPlaying && currentSong?.id == song.id,
                onClick = { onSongClick(song) },
                onFavoriteToggle = { onFavoriteToggle(song) }
            )
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun SongListItem(
    song: SongItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " • ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = song.format,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            }

            Text(
                text = song.duration,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun LibraryScreen(
    songs: List<SongItem>,
    currentSong: SongItem?,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onFavoriteToggle: (SongItem) -> Unit
) {
    val categories = listOf("All", "Favorites", "Downloaded", "High-Res FLAC")
    val filteredSongs = when (selectedCategory) {
        "Favorites" -> songs.filter { it.isFavorite }
        "High-Res FLAC" -> songs.filter { it.format == "FLAC" }
        else -> songs
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your Music Library",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filteredSongs.size} tracks available offline",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (filteredSongs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks found in this category",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            items(filteredSongs) { song ->
                SongListItem(
                    song = song,
                    isCurrent = currentSong?.id == song.id,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onFavoriteToggle = { onFavoriteToggle(song) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun PlaylistScreen(
    songs: List<SongItem>,
    onPlaylistPlay: () -> Unit
) {
    val playlists = listOf(
        Triple("Favorites Collection", "${songs.count { it.isFavorite }} songs", Color(0xFFEF4444)),
        Triple("Chill Study Beats", "14 songs", Color(0xFF6366F1)),
        Triple("Workout Motivation", "22 songs", Color(0xFFF59E0B)),
        Triple("Acoustic & Classical", "8 songs", Color(0xFF10B981))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Playlists",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Personalized audio mixes & offline collections",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        items(playlists) { (title, count, color) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onPlaylistPlay),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = count,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    IconButton(onClick = onPlaylistPlay) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun SettingsScreen(
    audioQuality: String,
    onQualityChange: (String) -> Unit,
    selectedPreset: String,
    onOpenEqualizer: () -> Unit
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "App preferences, sound engine & updates",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // Equalizer Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenEqualizer),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "EQ",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio Equalizer (5-Band)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Current: $selectedPreset",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }

        // Audio Quality Section
        item {
            Text(
                text = "Audio Streaming & Download Quality",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(qualities) { quality ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onQualityChange(quality) },
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = quality, fontSize = 14.sp)
                    RadioButton(
                        selected = audioQuality == quality,
                        onClick = { onQualityChange(quality) }
                    )
                }
            }
        }

        // App Version & Update Check
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MusicHub Android Edition",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Version: v1.0.0 (Latest Release)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "You are running the latest version (v1.0.0)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun NowPlayingDialog(
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
            color = MaterialTheme.colorScheme.background
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NOW PLAYING",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = song.album,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    }
                }

                // Centered Album Artwork
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(270.dp)
                        .clip(RoundedCornerShape(24.dp))
                )

                // Track Title and Artist
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = song.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${song.artist} • ${song.format}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Progress Scrubber
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progress,
                        onValueChange = onProgressChange,
                        modifier = Modifier.fillMaxWidth()
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
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = song.duration,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onEqualizerClick) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "EQ")
                    }

                    IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Large circular play/pause button
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun EqualizerDialog(
    currentPreset: String,
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf("Bass Boost", "Vocal Boost", "Electronic", "Rock", "Flat", "Acoustic")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "5-Band Sound Equalizer", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select hardware audio profile preset:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                        color = if (currentPreset == preset) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
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
                                color = if (currentPreset == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (currentPreset == preset) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
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
                Text("Close")
            }
        }
    )
}

@Composable
fun DownloadDialog(
    onDownloadSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Download Offline Audio", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter direct legal audio stream URL (.mp3, .wav, .flac):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/audio.mp3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onDownloadSubmit(urlText)
                    }
                }
            ) {
                Text("Start Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
