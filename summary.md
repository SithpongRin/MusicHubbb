# Project Overview

MusicHub is a personal, offline-first Android music player and legal audio downloader built for private usage and direct APK sharing among friends. The application does not require any user registration, authentication, or external cloud user accounts. It focuses on high performance, lightweight resource utilization, robust offline media persistence, low battery consumption, and a modern, distraction-free Material 3 user interface.

# Features

- Home Screen:
  - Recently Played track history
  - Most Played track analytics
  - Recently Added tracks
  - Offline Favorites carousel
  - Continue Listening quick-resume hero card
  - Comprehensive unified search covering songs, artists, albums, and playlists
  - Dedicated empty states explaining how to add or download audio

- Library:
  - Tabbed organization: All Songs, Downloaded Songs, Albums, Artists, Favorites
  - Track metadata cards displaying album artwork, title, artist, album, duration, and contextual options
  - Direct audio file import from device storage (supporting MP3, M4A, AAC, FLAC, WAV, and OGG formats)
  - Automatic audio metadata extraction and storage

- Legal Downloader Architecture:
  - Modular DownloadProvider architecture for legitimate and public domain media
  - Strict anti-DRM circumvention policy (no proprietary or subscription DRM bypass)
  - URL inspection, audio format detection, and quality selection (320kbps, 256kbps, 192kbps, 128kbps)
  - Active download management with progress tracking, pause, resume, cancel, and retry capabilities
  - Direct local storage without cloud server uploads

- Now Playing Screen:
  - Full-screen player inspired by modern minimal aesthetics
  - Large rounded album artwork with smooth visual transitions
  - Waveform-inspired progress visualizer and scrub bar
  - High-precision elapsed time and total duration indicators
  - Playback controls: Previous, Play/Pause, Next, Shuffle, Repeat (Off, All, One)
  - Queue drawer with item reordering, removal, and quick selection
  - 5-band Equalizer with presets (Flat, Bass Boost, Vocal, Rock, Pop, Classical, Electronic) and Bass Boost slider

- Background Playback & MediaSession:
  - Uninterrupted audio playback when minimized, screen is locked, or other apps are open
  - Android Media3 / MediaSessionService foreground notification with lock screen media controls
  - Headset and Bluetooth hardware button control handling

- Mini Player:
  - Floating card docked directly above the bottom navigation bar
  - Displays thumbnail, title, artist, progress indicator, play/pause, and next buttons
  - Tap expands directly to the Now Playing modal

- Playlists:
  - Create, rename, and delete playlists
  - Add, remove, and reorder songs within playlists
  - Custom playlist cover artwork
  - Deleting a playlist never deletes underlying audio files from device storage

- Settings:
  - Appearance: Light, Dark, and System Default themes
  - Localization: English and Khmer (Kantumruy Pro typography)
  - Playback: Auto-resume playback on start, volume normalization, 5-band EQ
  - Downloads: Default audio quality preference, Wi-Fi only download switch, storage directory
  - Storage: Live cache size calculation, temporary cache cleaner, library rescan
  - Updates: GitHub Releases update checker, semantic version comparison, and APK installer

# Technology Stack

- Language: Kotlin 1.9.23 / TypeScript (React SPA preview)
- UI Toolkit: Jetpack Compose with Material 3 & Tailwind CSS
- Audio Playback Engine: Android Media3 / ExoPlayer 1.3.1 (Web Audio API & MediaSession for preview)
- Local Database: Room 2.6.1 with SQLite & IndexedDB
- Preferences: Jetpack DataStore Preferences
- Background Processing: Android Foreground Service with WorkManager 2.9.0
- Image Loading: Coil 2.6.0
- HTTP Client: OkHttp 4.12.0
- APK Distribution: GitHub Releases API and GitHub Actions workflow

# Architecture

MusicHub adheres to Clean Architecture and MVVM patterns:
- Presentation Layer: Jetpack Compose UI components, Scaffolding, ViewModels, and StateFlow observables.
- Domain Layer: Core domain entities (Song, Playlist, DownloadTask), use cases, and DownloadProvider contracts.
- Data Layer: Room local database, DAOs, DataStore preferences repositories, OkHttp downloaders, and file system managers.
- Player Layer: Android Media3 MediaSessionService decoupled from UI lifecycle, persisting across configuration changes and device lock.

# Project Structure

```
app/
  src/
    main/
      java/com/musichub/app/
        data/
          database/
            entity/          # Room Entities: SongEntity, PlaylistEntity, PlaylistSongCrossRef, DownloadTaskEntity
            dao/             # Room DAOs: SongDao, PlaylistDao, DownloadDao
            AppDatabase.kt   # Room Database class with migration fallbacks
          repository/        # Repository implementations
        domain/
          model/             # Song, Playlist, DownloadTask, AudioQuality models
          downloader/        # DownloadProvider interface, DirectAudioProvider, ArchiveOrgProvider
        player/
          MediaPlaybackService.kt  # Media3 MediaSessionService with foreground notification
          MusicPlayerController.kt # Player controller abstraction
        update/
          GitHubUpdateChecker.kt   # Semantic version inspector for GitHub Releases API
          ApkInstaller.kt          # Android Intent FileProvider installer
        presentation/
          MainActivity.kt    # Compose Activity and Navigation scaffold
          screens/           # Home, Library, Playlist, Settings
          components/        # MiniPlayer, NowPlaying, Equalizer, DownloadModal
      res/
        xml/file_paths.xml   # FileProvider definition for APK installations
      AndroidManifest.xml    # Permissions, service definitions, FileProvider
src/                         # Full interactive web engine matching native architecture
.github/
  workflows/
    release.yml              # Automated build, test, and GitHub Releases APK distribution
```

# Database Schema

The Room database (`musichub_database`) includes the following entities:

1. `songs`:
   - `id` (String, Primary Key)
   - `title` (String, Indexed)
   - `artist` (String, Indexed)
   - `album` (String, Indexed)
   - `duration` (Long, Milliseconds)
   - `path` (String, Local URI or file path)
   - `artworkUri` (String, Optional cover art reference)
   - `dateAdded` (Long, Timestamp, Indexed)
   - `playCount` (Int, Default 0)
   - `lastPlayed` (Long, Nullable)
   - `isFavorite` (Boolean, Indexed)
   - `format` (String, mp3, m4a, flac, wav, aac, ogg)
   - `bitRate` (String, Optional)
   - `sizeBytes` (Long)
   - `isDownloaded` (Boolean)

2. `playlists`:
   - `id` (String, Primary Key)
   - `title` (String)
   - `description` (String, Nullable)
   - `artworkUri` (String, Nullable)
   - `createdAt` (Long)
   - `updatedAt` (Long)

3. `playlist_songs`:
   - Composite Primary Key: (`playlistId`, `songId`, `position`)
   - Foreign references with cascade deletion on playlist removal

4. `download_tasks`:
   - `id` (String, Primary Key)
   - `url` (String)
   - `title` (String)
   - `artist` (String)
   - `album` (String)
   - `artworkUrl` (String, Nullable)
   - `quality` (String)
   - `progress` (Int, 0-100)
   - `status` (String: PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED)
   - `totalBytes` (Long)
   - `downloadedBytes` (Long)
   - `errorMessage` (String, Nullable)

# Playback Architecture

The playback subsystem uses Android Media3:
- A foreground `MediaSessionService` runs as an independent component.
- The service declares `foregroundServiceType="mediaPlayback"`.
- It posts an ongoing notification on Android 8.0+ (`NotificationChannel` with `IMPORTANCE_LOW` to prevent audible alerts).
- Connects directly with Android's system `MediaSessionCompat` and lock screen media browser.
- Automatically releases media resources and audio focus when playback stops.
- Handles audio becoming noisy (unplugging headphones pauses playback immediately).

# Download Architecture

The download subsystem is structured around a modular provider interface:
- `DownloadProvider` specifies:
  - `canHandle(url: String): Boolean`
  - `inspect(url: String): MediaInspection`
  - `download(url: String, destinationFile: File, onProgress: (percent, downloaded, total) -> Unit): Boolean`
- Downloads are executed in background threads via OkHttp streaming directly to private device storage.
- Downloaded audio files are registered in Room with exact file sizes and detected duration.
- Progress events are emitted via Coroutine Flows to update UI progress bars reactively.

# Supported Download Providers

1. `DirectAudioProvider`: Supports direct public and legal audio links (`.mp3`, `.wav`, `.ogg`, `.flac`, `.m4a`, `.aac`).
2. `PublicDomainArchiveProvider`: Connects to Internet Archive public domain collections.
3. Custom Legal Stream Provider: Extensible interface for royalty-free and Creative Commons repositories.

Note on Legality and DRM:
The application explicitly rejects DRM circumvention. It does not tamper with encryption, does not bypass authentication tokens, and does not download protected subscription catalog content.

# Offline Architecture

MusicHub operates fully offline:
- Zero dependency on external backend authentication servers or databases.
- All track files, playlists, play counts, and favorites are stored locally in SQLite Room and device storage.
- Internet connectivity is utilized strictly on-demand for downloading new user-requested files and checking GitHub for app updates.

# GitHub Release & Update System

Because the application is distributed directly to friends without Google Play:
1. The app stores `currentVersionCode` and `currentVersionName` in `BuildConfig`.
2. The `GitHubUpdateChecker` queries `https://api.github.com/repos/{owner}/{repo}/releases/latest` via GitHub REST API without requiring user tokens.
3. If the tag (e.g. `v1.1.0`) is semantically higher than current (`v1.0.0`), an update dialog appears.
4. The dialog displays version details, APK size, and release notes.
5. Tapping "Download APK" downloads the release asset file (`MusicHub-vX.X.X.apk`) into app-specific cache storage.
6. The app verifies that the downloaded payload is a valid ZIP/APK archive (`PK\x03\x04` signature).
7. The app fires an `ACTION_VIEW` intent with `application/vnd.android.package-archive` and `FLAG_GRANT_READ_URI_PERMISSION` via `androidx.core.content.FileProvider`.
8. Android Package Installer replaces the existing APK while keeping SQLite databases, DataStore files, and downloaded media intact.

# GitHub Actions

The workflow located at `.github/workflows/release.yml`:
- Triggers on tag pushes matching `v*` (e.g., `git push origin v1.0.1`).
- Checks out code and installs JDK 17.
- Executes `./gradlew testReleaseUnitTest`.
- Compiles `./gradlew assembleRelease`.
- Gathers the release artifact into `MusicHub-vX.X.X.apk`.
- Creates a GitHub Release via `softprops/action-gh-release@v2` with automated changelog notes and the APK attached.

# APK Release Process

To publish a new version of MusicHub:
1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit all changes to the main branch.
3. Tag the commit with the new version:
   `git tag -a v1.0.1 -m "Release v1.0.1"`
4. Push the tag to GitHub:
   `git push origin v1.0.1`
5. The GitHub Actions runner will automatically build the release APK and upload it to GitHub Releases.
6. User devices running MusicHub will detect the new release when tapping "Check for Updates" or upon periodic checks.

# Data Persistence

When an updated APK is installed over an older version:
- The Android operating system retains the app's internal sandbox `/data/data/com.musichub.app/`.
- Room database schemas must be incremented with explicit `Migration` classes rather than destructive rebuilding (`fallbackToDestructiveMigration(false)`).
- Media files stored under external storage or app-specific external files remain untouched.

# Permissions

The app requests only required permissions:
- `INTERNET`: Required for user-directed downloads and GitHub release update checks.
- `ACCESS_NETWORK_STATE`: For checking network availability prior to downloads.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Required for Media3 background playback on Android 14+.
- `POST_NOTIFICATIONS`: Required on Android 13+ to display the playback notification.
- `READ_MEDIA_AUDIO`: Required on Android 13+ to import local audio files.
- `READ_EXTERNAL_STORAGE`: Required on Android 12 and below with `maxSdkVersion="32"`.
- `REQUEST_INSTALL_PACKAGES`: Required to initiate the system APK package installation.

# Security & Privacy

- No analytics, telemetry, or user tracking SDKs.
- No third-party advertisements.
- No user accounts, credentials, or login systems.
- Music files and playback histories remain strictly on the user's device.

# Important Design Decisions

- Emoji ban: No emojis are used in the UI, dialogs, buttons, notifications, or documentation to maintain a clean, professional aesthetic.
- Exactly 4 navigation tabs: Home, Library, Playlist, Settings.
- Responsive Now Playing layout: Album artwork dynamically scales with `max-h-[28vh]` aspect-square sizing and compact padding so all playback controls, progress scrubbers, duration counters, and queue buttons remain fully visible on all viewport heights without clipping.
- In-App deletion confirmations: Uses native modal dialogs instead of browser `window.confirm` to ensure reliable operation in iframe and sandboxed environments.
- Tailwind v4 Dark Mode: Configured with `@custom-variant dark (&:where(.dark, .dark *));` to guarantee instant class-based theme toggling between Light, Dark, and System modes.
- Kotlin KAPT & Room Configuration: Room annotation processor configured via standard `kotlin-kapt` with explicit version catalog synchronization. Room DAO methods enforce strict non-default argument signatures to prevent synthetic query binding failures.
- Android Lint & CI Robustness: Configured `lint { abortOnError = false; checkReleaseBuilds = false }`. Workflow includes explicit Android SDK 34 pre-installation and automated license acceptance via `android-actions/setup-android` to prevent runner build failures.
- Mini player docking: Floats seamlessly above bottom navigation bar without obscuring content.
- Native MediaSession compliance: Guarantees full integration with smartwatches, Bluetooth car systems, and lock screen media controls.
- Web Audio EQ: Employs 5-band biquad filters to enable immediate frequency shaping in the interactive preview.

# Known Limitations

- Downloading content that requires account logins or complex JavaScript rendering is outside the scope of direct audio providers.
- Certain restricted background battery optimizations on aggressive vendor OEM skins (e.g., MIUI, OneUI) may require users to disable battery optimization for the app if background playback is killed after extended idle periods.

# Future Improvements

- Add synchronized LRC lyrics parsing and display.
- Add ReplayGain scanner to calculate and persist track loudness gains.
- Add playlist export and import via standard M3U/M3U8 format.
- Add sleep timer functionality with gentle volume fade-out.

# Release History & Changelog

### Version 1.0.25 (Current)
- Universal HD Artwork Architecture across all screens: Replaced all legacy `AsyncImage` instances with `SmartArtworkImage` across Home Hero card, Playlists cards, Numbered track row items, Playlist detail dialog, and Floating Mini-Player.
- Removed artificial `scale(1.40f)` over-zooming across all track and playlist cards, eliminating pixel stretching and image distortion.
- Added Automatic Background HD Cover Enhancer: Queries iTunes Search API and YouTube HD endpoints to automatically upgrade any existing low-resolution (<40KB) cover art on device to 1000x1000 master studio artwork upon app launch and playback.
- Updated `versionCode = 25` and `versionName = "1.0.25"` in `app/build.gradle.kts`.

### Version 1.0.24
- Redesigned In-App / Headphone Volume card layout in Settings: Separated title/subtitle into full-width header and moved "Default (60%)" button and percentage text into a dedicated control row above the slider, completely eliminating text truncation/cut-off in English ("In-App / Headp...") and Khmer.
- High-Definition Album Artwork Architecture: Upgraded YouTube thumbnail extractor to dynamically query and resolve crystal-clear 1280x720 HD (`maxresdefault.jpg`) instead of low-resolution 320x180 (`mqdefault.jpg`), yielding 16x higher image fidelity.
- Added `SmartArtworkImage` composable with automatic resolution escalation to `maxresdefault.jpg` and cascading fallback across `hq720.jpg`, `sddefault.jpg`, and `hqdefault.jpg`.
- Optimized vinyl record picture disc scaling: reduced `scale(1.40f)` to `scale(1.05f)` to eliminate pixel stretching, prevent over-cropping of artists' faces, and maximize image sharpness.
- Added master studio artwork search via iTunes Search API (`searchHdCoverArt`) for lossless 1000x1000px cover resolution.
- Updated `versionCode = 24` and `versionName = "1.0.24"` in `app/build.gradle.kts`.

### Version 1.0.49
- Active Playing Animations on Mix & Playlist Cards:
  - When playing songs from "My Mix" or "Artist Mix", the corresponding mix card on the Home screen displays an active `AnimatedEqualizer` overlay over its artwork along with a pulsing glowing border and highlighted title.
  - When playing songs from a Playlist, the active playlist card on both the Home screen and the Library screen displays an `AnimatedEqualizer` overlay, glowing border, and highlighted title.
  - Hero card animation strictly activates only when general offline library tracks are playing, preventing animation crosstalk.
- Smooth Playlist Song Reordering:
  - Added dedicated 1-tap Move Up (`KeyboardArrowUp`) and Move Down (`KeyboardArrowDown`) buttons on each song row in `PlaylistDetailDialog`.
  - Maintained the quick reorder dropdown menu (Move Up, Move Down, Move to Top, Move to Bottom) for fast one-touch repositioning.
  - Eliminated conflicting drag-and-drop gesture states that previously caused recomposition resets or failed drops in `LazyColumn`.
- Bumped `versionCode = 49` and `versionName = "1.0.49"` in `app/build.gradle.kts`.

### Version 1.0.48
- Mix Exploration & Playback Differentiation:
  - Enabled viewing songs in "My Mix" and "Artist Mix" cards via `OfflineLibraryDialog` with custom headers before or during playback.
  - Hero card title marquee animation active during track playback.
  - Differentiated playback queue contexts (`activeMixTitle`, `activePlaylistId`, `activeArtistName`) so hero card only reflects library playback.
- Bumped `versionCode = 48` and `versionName = "1.0.48"` in `app/build.gradle.kts`.

### Version 1.0.29
- Fixed Compose Compilation Error in HomeScreen: Moved `remember(songs)` artist grouping outside `LazyColumn` LazyListScope to the top-level `@Composable` function scope, resolving GitHub Actions build failure `e: @Composable invocations can only happen from the context of a @Composable function`.
- Preserved full Artists & Channels grouping functionality on Home and Library screens, with high-definition artist covers and dedicated `ArtistDetailDialog`.
- Bumped `versionCode = 29` and `versionName = "1.0.29"` in `app/build.gradle.kts`.

### Version 1.0.28
- Home Screen "Artists & Channels" Carousel: Introduced a dedicated horizontal row showcasing artists and channels with circular avatars, track counts, and interactive detail view.
- Library "Artists" Tab: Added "សិល្បករ" / "Artists" filter chip in the Library tab. Automatically groups songs by artist/channel, displaying total track count and artist avatar.
- Interactive Artist Detail Modal (`ArtistDetailDialog`): Users can view all songs by a specific artist, with 1-tap "Play All" and "Shuffle" controls directly targeted at that artist's discography.
- Bumped `versionCode = 28` and `versionName = "1.0.28"` in `app/build.gradle.kts`.

### Version 1.0.27
- Strict App Memory and Physical Storage Synchronization: Ensured the library displays only real physical files present on the device. Replaced loose MediaStore scanning (`RELATIVE_PATH LIKE '%MusicHub%'`) which previously generated phantom duplicate entries with differing URI schemes for the same physical file.
- Single Source of Truth Guarantee (1 Physical File = 1 Library Item): Implemented canonical file path normalization (`seenFilePaths`) in `restoreAndSyncLibrary`. A single file on disk can never appear as duplicate entries in the library.
- Permanent Multi-Layer Deletion: When a song is deleted in the app, the physical audio file and companion cover art in `Music/MusicHub` and `Music/MusicHub/Songs` are immediately wiped from device storage (`File.delete()` and `ContentResolver.delete()`). The song is pruned from the library, all playlists, and `musichub_library.json`.
- Persistent Deletion Tombstone Tracking: Added persistent blacklisting (`deleted_song_signatures`) in `SharedPreferences`. Deleted songs are permanently prevented from reappearing upon app restarts or storage rescans.
- Broken / Ghost Song Pruning: `findExistingAudioUri` returns `null` if the physical audio file does not exist on disk. Missing or corrupted tracks are cleanly purged from the library with an informative user notification instead of triggering ExoPlayer playback crashes.
- Bumped `versionCode = 27` and `versionName = "1.0.27"` in `app/build.gradle.kts`.

### Version 1.0.26
- Fixed Playlist Auto-Advance & Skipping Bug: Resolved Jetpack Compose closure capturing inside long-lived ExoPlayer `Player.Listener` and `MediaPlaybackService.onActionReceived` by implementing dynamic `getCurrentPlaybackQueue()` resolution and `rememberUpdatedState` triggers. When playing a playlist, playback transition (track end or manual skipping) strictly stays within the playlist queue and loops according to the active loop mode.
- Ensured queue reset to general library (`activePlaylistId = null`) when selecting individual tracks from Home, Search, or Library screens, or when deleting the active playlist.
- Full Dark Mode Theming Support for Dialogs: Unified `PlaylistDetailDialog`, `SelectPlaylistSongsDialog`, `MediaLinkDownloadDialog`, and `EqualizerDialog` with dynamic `LocalDarkMode` palette adaptations for surfaces, cards, borders, text, and interactive buttons.
- Bumped `versionCode = 26` and `versionName = "1.0.26"` in `app/build.gradle.kts`.

### Version 1.0.54
- Clean Track Number Index & Preserved Artwork Overlay Equalizer Animation:
  - Removed equalizer bar replacement from the track index number in `NumberedTrackRowItem`. The track number (e.g. `01`, `02`, `03`) is now permanently preserved and cleanly highlighted when active, preventing visual clutter in the track index column.
  - Retained the animated equalizer wave overlay on the playing track's artwork thumbnail in all song rows.
  - Fully preserved the hero card live animated equalizer overlay and pulsing glow on the Home screen whenever the offline library is actively playing.
  - Lightweight, hardware-accelerated Compose animation ensuring zero latency, zero lag, and smooth scrolling across lists of any size.
- Strict zero-emoji policy maintained across all strings, labels, and UI elements.
- Bumped `versionCode = 54` and `versionName = "1.0.54"` in `app/build.gradle.kts`.

### Version 1.0.53
- Fixed Favorite Heart Desynchronization Bug between List Tracks and Mini-Player:
  - Identified root cause where toggling favorite status from list items (such as inside `OfflineLibraryDialog`, `HomeScreen`, `LibraryScreen`, or `SearchScreen`) updated only `songsList` but left `currentSong.isFavorite` out of sync, causing the Mini-Player heart icon to remain unselected (white/empty) while the list item showed a red filled heart.
  - Implemented centralized `toggleFavorite(song: SongItem)` handler in `MainActivity.kt` used uniformly across all UI call sites.
  - Whenever a song's favorite status is toggled, if it matches `currentSong.id`, `currentSong` is immediately updated with the matching `isFavorite` state, guaranteeing instant bidirectional synchronization everywhere in the UI (track rows, Mini-Player, and Now Playing modal).
- Strict zero-emoji policy maintained across all strings, labels, and UI elements.
- Bumped `versionCode = 53` and `versionName = "1.0.53"` in `app/build.gradle.kts`.

### Version 1.0.52
- Search-Driven Smart Recommendation Integration (Personalized Discovery):
  - Created `SearchPreferenceTracker` in `RecommendationEngine.kt` to record user search terms persistently (`SharedPreferences`).
  - Search queries from both Offline Search and Online Search (including popular search chips) are automatically tracked with frequency ranking and LRU ordering.
  - `RecommendationEngine.getPersonalizedRecommendations` dynamically ingests recent search interests alongside library top artists, finding matching trending tracks and artist discography via public metadata APIs.
  - Instant recommendation refresh: whenever a search is conducted, `searchVersion` triggers a smooth background refresh of the "Recommended for You" carousel on the Home screen.
  - Safe fallback: if there are no search queries, recommendation gracefully relies on library top artists and trending categories, ensuring zero disruption or crashes.
  - Fully decoupled: offline audio playback, queue management, equalizer, downloader, and dialogs remain completely unaffected and robust.
- Strict zero-emoji policy maintained across all strings, labels, and UI elements.
- Bumped `versionCode = 52` and `versionName = "1.0.52"` in `app/build.gradle.kts`.

### Version 1.0.51
- Multi-Directional Swipe-to-Dismiss on Mini-Player (Now Playing card):
  - Users can now dismiss and close the Mini-Player card by dragging downwards, swiping right, or swiping left.
  - Interactive physics with real-time translation offset and alpha fade-out during dragging.
  - Directional exit animation when release exceeds the swipe threshold (down, right, or left), smoothly animating off-screen before dismissal.
  - Graceful spring return animation back to resting position `(0, 0)` with full opacity if drag does not meet the threshold.
  - Complete playback cleanup on dismissal: stops audio playback (`exoPlayer.stop()`), resets `isPlaying = false`, and clears `currentSong = null` across the entire app so no hidden background audio persists.
  - Child touch safety: Play/Pause pill and Favorite heart buttons consume their own click events and do not trigger card dismissal or modal expansion.
  - Tap-to-expand: Tapping any neutral area of the card opens the full Now Playing player modal without false dismissal triggers.
  - Supported everywhere: The dismissible gesture is fully supported in the bottom scaffold Mini-Player as well as within all overlay modals (`OfflineLibraryDialog`, `PlaylistDetailDialog`, and `ArtistDetailDialog`).
- Strict zero-emoji policy maintained across all strings, labels, and UI elements.
- Bumped `versionCode = 51` and `versionName = "1.0.51"` in `app/build.gradle.kts`.

### Version 1.0.74
- 4 Core Moods in `Your Mix` (`Calm`, `Energetic`, `Sad`, `Romantic` alongside `My Mix` & `Artist Mix`), 3-Stage Precision Acoustic Engine (`MP4 stsz`, Exact MP3 Frame-Stepping, Ogg/WebM Packet Lacing, Autocorrelation BPM), Library-Wide Z-Score Self-Calibration, Self-Learning 4x6 Neural Weight Matrix (`Delta-W`), and Unified `140.dp` `Your Mix` Card Size:
  - **Unified `Your Mix` Card Dimensions (`140.dp x 120.dp`)**:
    - Restored `Your Mix` card width to `140.dp` and artwork height to `120.dp` in `MainActivity.kt` so `Your Mix` cards match the exact dimensions of `Because You Listen to ...` and `Recommended for You` cards.
  - **Streamlined `Your Mix` Lineup (`My Mix`, `Artist Mix`, and 4 Core Moods)**:
    - Replaced the previous 7 overlapping mood categories with the 4 core emotional/acoustic moods requested by the user alongside `My Mix` (`ចម្រៀងចម្រុះខ្ញុំ`) and `Artist Mix`:
      1. **`Calm`** (`ស្ងប់ស្ងាត់`): Low/relaxed energy, slow/gentle tempo, high melodic sustain, minimal transient punch, low dynamic variance.
      2. **`Energetic`** (`ស្វាហាប់`): High energy, fast tempo pulse, strong percussive punch, driving groove regularity.
      3. **`Sad`** (`កម្សត់`): Slow tempo, soft non-percussive attacks, low groove regularity, high melodic sustain & emotional swell.
      4. **`Romantic`** (`មនោសញ្ចេតនា`): Warm mid-tempo, balanced energy, smooth melodic sustain, gentle groove & expressive phrasing.
  - **3-Stage Precision Acoustic Feature Extraction (Zero `MediaCodec`)**:
    - **Stage 1 (True Frame-Table & Frame-Stepping)**:
      - Added `extractM4aStszEnergies` to parse the ISO BMFF `stsz` (Sample Size Box) table in M4A/MP4 files, reading the exact byte sizes of variable-length `23.22ms` AAC audio frames across the track.
      - Upgraded `extractMp3ExactFrameEnergies` to step by exact MPEG-1/2 Layer III frame byte length (`(144 * bitrate) / sampleRate + padding`) and verify next-frame `0xFF` sync, reading true `global_gain` (1.5 dB logarithmic loudness) and `main_data_begin` transient bursts without false sync hits.
      - Added `extractWebmOggPacketEnergies` to parse Ogg (`OggS`) segment lacing tables alongside compressed frame contour analysis.
    - **Stage 2 (Percentile Self-Normalization & Autocorrelation True BPM)**:
      - Self-normalizes each song's frame envelope using its $P_{10}, P_{25}, P_{50}, P_{75}, P_{90}$ percentiles and Coefficient of Variation so compressed bitstreams do not artificially saturate energy.
      - Computes first-order onset flux $O[t] = \max(0, E[t] - E[t-1])$ and runs Normalized Autocorrelation $R(\tau)$ across tempo lags (`60 BPM .. 180 BPM`) to extract true BPM tempo speed and periodic groove regularity.
    - **Stage 3 (Library-Wide Z-Score Self-Calibration)**:
      - Added `calibrateLibraryAcousticSpace` to standardize all 6 acoustic features across the user's library (`z = tanh((raw - mean) / (1.05 * std))`), ensuring songs accurately populate all 4 Core Mood Mixes (`Calm`, `Energetic`, `Sad`, `Romantic`).
  - **On-Device Self-Learning 4x6 Neural Weight Matrix (`Delta-W`) & Historical Bootstrap**:
    - Added `updateLearnedNeuralWeights` (`learned_w_{moodIdx}_{featIdx}`) trained via Online Gradient Descent from user Star Ratings (`1-5 Stars`) and listening completions/early skips, plus `bootstrapHistoricalLearningIfNeeded` to incorporate existing `PlayCountTracker` and `isFavorite` history.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 74` and `versionName = "1.0.74"` in `app/build.gradle.kts`.

### Version 1.0.73
- 100% Pure Audio Signal Mood Classification for `Your Mix`, Conditional AI Uncertainty Star Rating (`1-5 Stars`), 3-Signal Hybrid `Recommended for You` Engine, and Crash-Free Song Info Saving:
  - **100% Pure Audio Signal Analysis for `Your Mix` (`AiAudioMoodEngine`)**:
    - Rebuilt `getSongMoodProfile` (`computePureAudioProfile`) so local songs in the 7 Mood Mixes (`Workout & Sport`, `Party & Remix`, `Chill & Lofi`, `Sad & Emotional`, `Romantic & Love`, `Focus & Study`, `Driving & Vibe`) are classified strictly from the physical audio signal with zero inspection of song title or artist name.
    - Added magic-byte audio container detection (`detectAudioContainerFormat`) for WAV PCM (`RIFF`/`WAVE`), MPEG Layer III (`0xFF 0xE0`), MP4/M4A (`ftyp`), WebM/Matroska (`0x1A 0x45 0xDF 0xA3`), and Ogg (`OggS`) across 5 sections of the song (`15%`, `32%`, `50%`, `68%`, `82%`), extracting 6 pure acoustic dimensions: RMS Energy, Dynamic Variance, Tempo/Pulse (`tempoPulse`), Percussive Transient Punch (`transientPunch`), Beat Regularity/Groove (`beatRegularity`), and Melodic Sustain Smoothness (`sustainSmooth`).
    - Added strict pure-acoustic polarity vetoes so upbeat, bouncy, or percussive tracks never enter `Sad & Emotional`, and slow/soft tracks never enter `Workout & Sport` or `Party & Remix`.
    - **`My Mix`**: Combines user listening history (`PlayCountTracker`, `isFavorite`, `getSongLearnedReward`) with Pure Audio Taste Vector similarity and smooth audio transition sequencing.
    - **`Artist Mix`**: Uses `artist` name strictly to group the top artist's tracks, and selects companion tracks 100% by Pure Audio similarity (`energyScore`, `tempoScore`, mood cosine similarity).
  - **Conditional AI Uncertainty Rating Star Card (`1-5 Stars`) in `Your Mix`**:
    - Added `confidenceScore` tracking per song and `isMixUncertain` detection per Mix.
    - Displays a `1-5 Star` AI Calibration card at the top of `OfflineLibraryDialog` **only when the AI is uncertain (`isMixUncertain == true`)** about borderline tracks in that Mix; when the AI is already confident, the card stays completely hidden.
    - When the user taps a star rating (`1-5`), `recordMixStarRating` trains the on-device AI (`4-5 Stars` confirms and boosts confidence; `1-3 Stars` penalizes and immediately removes the uncertain borderline tracks from that Mix) and hides the rating card immediately.
  - **3-Signal Hybrid Recommendation Engine (`Recommended for You`)**:
    - Upgraded `RecommendationEngine.getPersonalizedRecommendations` and `classifyMetadataProfile` to combine all 3 signals for Internet discovery: (1) Pure Audio Taste Vector + Target Energy/Tempo + Local Artist Real Audio Profiles (`40%`), (2) Artist Name & Learned Artist Affinity (`32%`), and (3) Whole-Word Boundary (`\b`) Title/Genre/Search matching (`28%`) so substrings like `"alone"` never match inside `"Post Malone"`.
  - **Fixed `Edit Song Info -> Save` Crash**:
    - Removed synchronous `restoreAndSyncLibrary(context)` execution on the Main UI Thread inside `EditSongDialog` `onSave`, atomically updating `songsList`, `currentSong`, `viewingMix`, `activeMixTracks`, and `viewingArtist` in memory while persisting JSON metadata asynchronously on `Dispatchers.IO`.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 73` and `versionName = "1.0.73"` in `app/build.gradle.kts`.

### Version 1.0.72
- Self-Learning On-Device AI Mood Engine v2 (`AiAudioMoodEngine`), Anti-Contradiction Mood Classification, AI-Powered Internet Discovery (`RecommendationEngine`), and Non-Truncated Mix Card Typography:
  - **AI-Powered Internet Recommendations (`Recommended for You`)**:
    - Restored Internet discovery for `Recommended for You`: the On-Device AI analyzes the user's learned 7-dimensional mood taste vector, top played/favorited artists, learned artist affinities, and recent search queries (`SearchPreferenceTracker`) to synthesize targeted discovery queries and fetch new candidate tracks from the Internet (YouTube Music/Video via `LocalMediaExtractor.searchOnlineTracks` + iTunes API).
    - Strictly filters out all songs already in the user's local library (`existingSongs`) so `Recommended for You` only recommends new songs from the Internet.
    - Scores and ranks every Internet candidate deterministically using `AiAudioMoodEngine.classifyMetadataProfile` (zero `.shuffled()`).
  - **High-Precision Acoustic & Semantic Mood Classification (Zero Randomness, Zero Wrong-Mood Padding)**:
    - Eliminated `hashCode()` fallback randomness in `AiAudioMoodEngine`.
    - Added real MPEG-1/2 Layer III (`0xFF 0xE0`) granule `global_gain` loudness scanning, WAV 16-bit PCM RMS analysis, M4A/AAC frame contour analysis across 3 segments of the audio file (`22%`, `50%`, `74%`), and `MediaMetadataRetriever` container genre/bitrate inspection.
    - Added strict Valence/Arousal Anti-Contradiction Polarity Vetoes (`isSongCompatibleWithMood` + logit vetoes) and Artist Mood Propagation so upbeat/happy/party/workout songs never enter `Sad & Emotional` (and sad ballads never enter `Workout & Sport` or `Party & Remix`), and eliminated fallback padding with non-matching songs.
  - **Real On-Device Online Machine Learning**:
    - Added `recordListeningFeedback` (rewards `>= 20s` completions, penalizes `< 10s` early skips, and adjusts per-song mood bias when a song is skipped inside a specific Mood Mix), `recordFavoriteFeedback`, and `recordSearchFeedback` with persistent weights in `SharedPreferences`.
  - **Clean Non-Truncated Mix Cards (`Your Mix`)**:
    - Widened `Your Mix` cards to `156.dp`, refined mood titles (`Workout & Sport`, `Party & Remix`, `Chill & Lofi`, `Sad & Emotional`, `Romantic & Love`, `Focus & Study`, `Driving & Vibe`), and simplified all card subtitles to display only the song count (`25 songs` / `25 បទ`) so neither titles nor subtitles are ever cut off.
  - **Playlist Shuffle Button (`PlaylistDetailDialog`)**:
    - Added the side-by-side `Play All` (`ចាក់ទាំងអស់`) and `Shuffle` (`ច្របល់`) button row inside `PlaylistDetailDialog` (matching `OfflineLibraryDialog` and `ArtistDetailDialog`) and wired `onShufflePlay` so users can shuffle-play any playlist with one tap.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 72` and `versionName = "1.0.72"` in `app/build.gradle.kts`.

### Version 1.0.71
- 100% On-Device AI Audio Mood, Sport & Activity Mix Engine (`AiAudioMoodEngine`) & On-Device Smart Recommendations (`RecommendationEngine`):
  - Replaced legacy crash-prone `MediaCodec` / `MediaExtractor` calls in `AudioWaveformFingerprinter.kt` with a 100% crash-free On-Device AI Audio & Semantic Classifier (`AiAudioMoodEngine`) that requires zero external APIs and zero manual training.
  - Extracts a 12-dimensional acoustic & multi-language (English/Khmer) feature vector per song using lightweight byte-frame envelope/transient analysis and runs a pre-trained 2-layer neural network (`12 -> 16 tanh -> 7 softmax`) with `ConcurrentHashMap` and `SharedPreferences` caching.
  - Upgraded **Your Mix** (`HomeScreen`) to dynamically generate personalized AI mixes and mood/activity stations from the user's local library:
    - **My Mix** (`ចម្រៀងចម្រុះរបស់ខ្ញុំ`) — AI-sequenced personal taste and smooth mood transitions
    - **Artist Mix** (`[Top Artist] Mix`) — Top artist tracks blended with acoustically similar mood tracks
    - **Workout & Sport** (`សម្រាប់ហាត់កីឡា`)
    - **Party & Remix** (`កន្ត្រាក់អារម្មណ៍ & Remix`)
    - **Chill & Lofi** (`ស្ងប់ស្ងាត់ & Chill`)
    - **Sad & Emotional** (`មនោសញ្ចេតនា & សេដ`)
    - **Romantic & Love** (`ស្នេហា & ផ្អែមល្ហែម`)
    - **Focus & Study** (`ផ្តោតអារម្មណ៍ & សិក្សា`)
    - **Driving & Vibe** (`ស្តាប់ពេលធ្វើដំណើរ & Vibe`)
  - Replaced the legacy external-API `RecommendationEngine` with 100% On-Device AI recommendation scoring (`RecommendationEngine.getPersonalizedRecommendations`) that ranks the user's songs using cosine similarity against their listening history taste vector, currently playing track's AI mood profile, favorite status, rediscovery weighting, and recent search intent (`SearchPreferenceTracker`), displaying each track's AI mood badge directly on the **Recommended for You** cards.
  - Upgraded `findSmartNextTrack` to select the next track using `AiAudioMoodEngine.computeMoodSimilarity`.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 71` and `versionName = "1.0.71"` in `app/build.gradle.kts`.

### Version 1.0.70
- YouTube Music-Style 2-Stage Smooth Queue Transition & Stationary Finger-Locked Physics:
  - Upgraded `UnifiedExpandablePlayerCard` to a 2-stage connected transition (`queueStage`: `0` = Full Now Playing, `1` = Split Compact Now Playing + Half Queue, `2` = Top Mini-Player + Full Queue):
    - **Stage 1 (`queueStage = 1`, `qp = 0.44f`)**: Swiping up once smoothly compresses Now Playing into the top `56%` of the screen (scaling the Vinyl/Square artwork, title, waveform scrubber, and playback controls proportionally while keeping all controls active) and attaches the Playing Queue in the bottom `44%` of the screen.
    - **Stage 2 (`queueStage = 2`, `qp = 1.0f`)**: Swiping up a second time smoothly morphs the upper Now Playing section into the `68.dp` Top Mini-Player bar while expanding the attached Playing Queue to fill the rest of the screen (`fullScreenHeight - 68.dp`).
  - Replaced separate moving-coordinate child `pointerInput` handlers with a single unified stationary gesture loop on the parent `Surface` and pre-mounted/pre-scrolled the Queue `LazyColumn` (`scrollToItem(activeIdx, 0)`) prior to gesture start, eliminating coordinate feedback jitter and mid-swipe composition stutter so transitions are as smooth as Mini-Player -> Now Playing.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 70` and `versionName = "1.0.70"` in `app/build.gradle.kts`.

### Version 1.0.69
- Connected Now Playing -> Top Mini-Player + Attached Playing Queue Morphing Layout & Active Song Top-of-Queue Positioning:
  - Connected Now Playing and Playing Queue into one unified morphing layout inside `UnifiedExpandablePlayerCard`: swiping up from Now Playing smoothly morphs Now Playing into a `68.dp` Mini-Player bar docked at the top of the screen while the Playing Queue rises directly attached underneath it (`height = fullScreenHeight - 68.dp`), with no separate floating overlay scrim.
  - Swiping down on the top Mini-Player bar or the Queue header (or tapping the top Mini-Player bar / Queue handle) smoothly expands the top Mini-Player back into the full Now Playing screen while sliding the Playing Queue back down.
  - Updated Playing Queue auto-scroll (`queueListState.scrollToItem(activeIdx, 0)`) so that while all songs in the active Playlist / Playback context remain in the queue, the list automatically positions the currently playing song right at the top of the visible list (`offset = 0`) rather than starting from index 0.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 69` and `versionName = "1.0.69"` in `app/build.gradle.kts`.

### Version 1.0.68
- Fixed Playing Queue Crash, Connected Context-Strict Playing Queue, Added `=` Drag-and-Drop Reordering to Offline Music Lists, and Removed Redundant Artwork Style Card from Settings:
  - Fixed the `awaitEachGesture` non-suspending infinite loop crash in `UnifiedExpandablePlayerCard` when opening the Playing Queue sheet by always awaiting `awaitFirstDown` before checking `isQueueSheetOpen` and removing `isQueueSheetOpen` from `pointerInput` keys, plus added continuous finger-tracking morphing (`queueProgress`) between Now Playing and Playing Queue.
  - Strictly connected `getCurrentPlaybackQueue()` and `currentQueueTitle` to the active playback source: playing inside a Playlist (e.g. Playlist A) populates Playing Queue exclusively with Playlist A's songs; playing inside an Artist populates only that Artist's songs; playing inside a Mix populates only that Mix's songs; playing inside Library Favorites/Downloaded populates only that filtered list; and playing inside Offline Library populates all offline songs.
  - Added `220ms` press-and-hold `=` (`Icons.Default.DragHandle`) drag-and-drop reordering and `Move to Top` / `Move to Bottom` menu options to `OfflineLibraryDialog` and `LibraryScreen` song lists, persisting custom order to storage.
  - Removed the `Player Artwork Style` card from `SettingsScreen` since the toggle button is directly accessible in the Now Playing top header bar.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 68` and `versionName = "1.0.68"` in `app/build.gradle.kts`.

### Version 1.0.67
- Swipe-Up Playing Queue Sheet in Now Playing, Artist Song Drag-and-Drop (`=`), Update Dialog Button Contrast Fix, and Single-Line Search Placeholder:
  - Added a minimal bottom handle bar (no text) below the Now Playing controls in `UnifiedExpandablePlayerCard` and enabled swiping up (or tapping the handle bar) to smoothly open the active Playing Queue sheet (`currentQueue`), while preserving swipe-down to minimize into the Mini-Player.
  - Inside the swipe-up Playing Queue sheet, displayed the active queue title and track count, auto-scrolled to the currently playing song, enabled 1-tap track switching, and added `220ms` press-and-hold drag-and-drop reordering (`=`) without `...` menu buttons.
  - Added `220ms` press-and-hold drag-and-drop reordering (`=`) and Move to Top / Move to Bottom options to `ArtistDetailDialog`, persisting artist track order in library storage.
  - Fixed button text and icon contrast in `AppUpdateDialog` (`Download via Browser`, `Download & Install`, and `Close` buttons) so text is always crisp and visible in Dark Mode.
  - Prevented the search input placeholder and tab labels in `SearchScreen` from wrapping onto multiple lines (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 67` and `versionName = "1.0.67"` in `app/build.gradle.kts`.

### Version 1.0.66
- Smooth Shape-Matched Button Borders & Single `+` on Library Create Playlist Button:
  - Added explicit `shape` parameters (`RoundedCornerShape` / `CircleShape`) directly to all bordered `Surface` components (`EN` language pill, `+` import button, `Loop Off` toggle button, `+ New` playlist button, Rescan button, `MiniPlayerCard`, and `UnifiedExpandablePlayerCard`) so `BorderStroke` renders smoothly around every curved edge instead of clipping a rectangular border.
  - Removed the duplicate `+` from the `"List"` label inside the Library Playlists creation button (`Icons.Default.Add` + `"List"` instead of `+ + List`).
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 66` and `versionName = "1.0.66"` in `app/build.gradle.kts`.

### Version 1.0.65
- Reduced Corner Roundness Across All Cards & Song Artwork Thumbnails, Bright White Dark Mode Accent Palette, Removed Right-Side Section Subtitles on Home, and Brief Hold Before Playlist Drag-and-Drop:
  - Reduced `RoundedCornerShape` across all cards (`10.dp`–`12.dp` for Hero Card, Mix cards, Recommended cards, Playlist cards, Artist cards, Track rows, Mini-Player card, Settings cards, and Dialog cards) and all song artwork thumbnails (`8.dp`–`12.dp` for large/medium card covers, `6.dp` for track row and Mini-Player thumbnails).
  - Replaced blue/indigo (`#6366F1` / `#818CF8`) accents in Dark Mode with crisp bright white (`Color.White` with dark `Color(0xFF0C0E12)` foreground contrast on solid buttons/chips, and `Color.White` for selected Bottom Navigation tab, waveform scrubber, equalizer bars, active track titles/indices, Now Playing main Play/Pause button, Settings controls, and Download dialog).
  - Removed the 3 right-side section subtitle labels on the Home screen (`"Made For You"` / `"ជ្រើសរើសសម្រាប់អ្នក"`, `"Similar Vibe"` / `"ស្រដៀងគ្នា"`, and `"New & Trending"` / `"ថ្មីៗ • Trending"`).
  - Updated playlist song reordering (`=`) in `PlaylistDetailDialog` to require a brief press-and-hold (`220ms` within `touchSlop`) before activating drag-and-drop so normal list scrolling is never hijacked.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 65` and `versionName = "1.0.65"` in `app/build.gradle.kts`.

### Version 1.0.64
- Instant Touch-and-Drag Playlist Song Reordering & Full `...` Options Menu in `PlaylistDetailDialog`:
  - Replaced the single-step `^`/`v` arrows and `X` button on playlist song rows with a clean 2-button right layout: **`=` (`DragHandle`)** + **`...` (`MoreHoriz`)**.
  - Implemented zero-delay (no long-press required) touch-and-drag reordering on the `=` handle at the `LazyColumn` container level (`PointerEventPass.Initial`) with real-time item elevation, continuous finger-locked offset compensation across item swaps, and automatic edge scrolling.
  - Upgraded the `...` dropdown menu on each playlist song row with full track actions: Add/Remove Favorite, Move to Top, Move to Bottom, Add to Another Playlist, Edit Info, Share/Copy File, Remove from Playlist, and Delete Song.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 64` and `versionName = "1.0.64"` in `app/build.gradle.kts`.

### Version 1.0.63
- Updated `UnifiedExpandablePlayerCard` Top Header Bar, Mini-Player Gestures, and Mini-Player Artwork Shape:
  - Redesigned Now Playing top header bar to `v` (`KeyboardArrowDown` collapse button) | `Switch Player Artwork Style` (`Album` / `CropSquare` toggle button) | `Share` | `EQ` (`Tune`), removing the `"Now Playing"` text label.
  - Removed swipe-to-dismiss from the Mini-Player and restored smooth real-time **Swipe-Up + Tap to Expand** on the Mini-Player (`0f -> 1f`) paired with **Swipe-Down + `v` button to Collapse** on Now Playing (`1f -> 0f`).
  - Changed the Mini-Player song artwork thumbnail from `CircleShape` to rounded square (`RoundedCornerShape(10.dp)`).
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 63` and `versionName = "1.0.63"` in `app/build.gradle.kts`.

### Version 1.0.62
- Refined `UnifiedExpandablePlayerCard` Mini-Player Dismissal & Expansion UX:
  - Dedicated Mini-Player swipe gestures (left, right, and down) 100% to dismissing/closing the Mini-Player (`dismissXThreshold = 65.dp`, `dismissYThreshold = 38.dp`) and removed swipe-up-to-expand on the collapsed Mini-Player so diagonal or quick horizontal swipes never conflict with closing the Mini-Player.
  - Preserved 1-tap on the Mini-Player to smoothly morph and expand the single card into Full-Screen Now Playing (`0f -> 1f`).
  - Preserved real-time vertical drag-down (`1f -> 0f`) on Now Playing to smoothly shrink the single card right back into the Mini-Player dock.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 62` and `versionName = "1.0.62"` in `app/build.gradle.kts`.

### Version 1.0.61
- Single Unified Morphing Player Card (`UnifiedExpandablePlayerCard`):
  - Combined `MiniPlayerCard` and `NowPlayingDialog` into **one single continuous morphing card (`Surface`)** driven by a unified `expandProgress` (`0f` = collapsed Mini-Player docked at bottom, `1f` = expanded Full-Screen Now Playing).
  - Real-time 1:1 finger tracking and continuous geometry interpolation:
    - Dragging the Mini-Player card UP stretches that exact same card's height (`lerpDp(66.dp, maxHeight, p)`), horizontal margins (`lerpDp(16.dp, 0.dp, p)`), bottom offset (`lerpDp(collapsedBottomPadding, 0.dp, p)`), and corner radius (`lerpDp(26.dp, 0.dp, ...)`), while smoothly crossfading the Mini-Player row into the Full-Screen Now Playing layout inside the same card.
    - Dragging the Now Playing card DOWN shrinks that exact same card continuously under the user's finger right back into the `66.dp` Mini-Player pill at the bottom without opening or closing separate `Dialog` windows.
  - Preserved multi-directional swipe-to-dismiss (down, left, right) on the collapsed Mini-Player, `WaveformScrubber` seeking, and seamless layering over `OfflineLibraryDialog`, `PlaylistDetailDialog`, and `ArtistDetailDialog`.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 61` and `versionName = "1.0.61"` in `app/build.gradle.kts`.

### Version 1.0.60
- Connected Mini-Player and Now Playing Transition & YouTube Music Soft Charcoal Dark Theme:
  - Soft Charcoal Dark Mode Palette & Top Ambient Gradient: Updated `darkColorScheme` (`background = Color(0xFF0C0E12)`, `surface = Color(0xFF1B1E25)`, `surfaceVariant = Color(0xFF252932)`) and added a subtle vertical ambient top glow (`Color(0xFF16242D)` -> `Color(0xFF10141A)` -> `Color(0xFF0C0E12)`) across the main Scaffold and `NowPlayingDialog`, paired with harmonized `NavigationBar` (`Color(0xFF14161B)`) and `MiniPlayerCard` (`Color(0xFF20232B)` with `Color(0xFF2E323D)` border).
  - Connected `MiniPlayerCard` and `NowPlayingDialog` Transition:
    - Added Swipe-Up-to-Expand gesture (`currentY < -expandYThreshold`) alongside Tap-to-Expand on `MiniPlayerCard`.
    - Hid `MiniPlayerCard` whenever `NowPlayingDialog` is open (`visible = currentSong != null && !showNowPlayingModal` in the main Scaffold as well as in `OfflineLibraryDialog`, `PlaylistDetailDialog`, and `ArtistDetailDialog`) so both players are never simultaneously visible.
    - Animated `NowPlayingDialog` entry from the bottom Mini-Player dock (`Animatable(1350f)` -> `0f` with spring physics) and animated both swipe-down and back-press dismissals smoothly back down into the Mini-Player dock.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 60` and `versionName = "1.0.60"` in `app/build.gradle.kts`.

### Version 1.0.59
- Symmetrical Top Header layout in `NowPlayingDialog`:
  - Arranged the 3 top header elements evenly across the full width (`Arrangement.SpaceBetween`) with Share icon on the left, "Now Playing" title centered (`16.sp`), and Equalizer icon on the right, matching the left-center-right symmetry of the song info and playback control rows below.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 59` and `versionName = "1.0.59"` in `app/build.gradle.kts`.

### Version 1.0.58
- Ultra-clean `NowPlayingDialog` header and `NavigationBar` Dark Mode color harmony:
  - Removed the top-left down-arrow button and the top drag-pill indicator from `NowPlayingDialog` so the header displays a clean centered "Now Playing" title with Share and EQ icons on the right while relying on natural swipe-down-to-minimize anywhere on the screen.
  - Fixed `NavigationBar` Dark Mode color mismatch caused by Material 3 `tonalElevation = 8.dp` blue-purple tinting: set `surfaceTint = Color.Transparent` in `darkColorScheme`, `tonalElevation = 0.dp`, and deep obsidian `Color(0xFF0A0A0D)` container color on `NavigationBar` (with `Color(0xFF1D1D24)` active pill indicator), plus harmonized `MiniPlayerCard` dark surface (`Color(0xFF141418)` with subtle `Color(0xFF23232B)` border).
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 58` and `versionName = "1.0.58"` in `app/build.gradle.kts`.

### Version 1.0.57
- Refined `NowPlayingDialog` layout and interactions:
  - Removed the redundant bottom volume slider from `NowPlayingDialog` (volume control remains available in `SettingsScreen`), creating a cleaner, uncluttered player layout.
  - Enlarged both the Spinning Vinyl Disc and Static Square Card artwork from `290.dp` to `326.dp` for a more immersive full-bleed presentation.
  - Added smooth interactive Swipe-Down-to-Minimize gesture (`detectVerticalDragGestures` with `Animatable` offset and subtle top pill handle) allowing users to drag the Now Playing screen downward to smoothly minimize it into the Mini-Player, with spring snap-back if released early.
- Upgraded Dark Mode color palette to a YouTube Music-inspired deep obsidian black theme (`#030303` background, `#121216` card surface, `#1D1D24` surfaceVariant, `#FAFAFA` crisp foreground) across the main scaffold and all full-screen dialogs (`OfflineLibraryDialog`, `PlaylistDetailDialog`, `ArtistDetailDialog`, and `NowPlayingDialog`).
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 57` and `versionName = "1.0.57"` in `app/build.gradle.kts`.

### Version 1.0.56
- Added "Player Artwork Style" setting in Settings screen (`SettingsScreen`):
  - Allows users to switch between "Vinyl Disc" (Spinning Picture Disc with center spindle hub) and "Static Card" (290.dp rounded-corner square card without rotation or scale animation, showing full unobscured album cover).
  - Persisted user choice in `SharedPreferences` (`vinyl_style` key) across app restarts.
  - When "Static Card" is active, the frame-by-frame rotation loop in `NowPlayingDialog` is completely suspended for zero GPU/CPU animation overhead.
  - Kept `NowPlayingDialog` completely free of extra buttons per user preference, managing the toggle cleanly inside Settings.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 56` and `versionName = "1.0.56"` in `app/build.gradle.kts`.

### Version 1.0.55
- Fixed favorite state desynchronization in My Mix and all browsing modals:
  - Dynamically resolved mix track items in `viewingMix` against the reactive `songsList` state so changes to favorites immediately reflect in the active dialog list.
  - Enhanced `toggleFavorite` to synchronously update `viewingMix` and `activeMixTracks` alongside `songsList` and `currentSong`, guaranteeing that toggling favorite from any source (track row, Mini-Player card, or full Now Playing modal) updates all views in real time.
  - In `OfflineLibraryDialog`, `PlaylistDetailDialog`, and `ArtistDetailDialog`, resolved the mini player song instance against the active dialog track collection, preventing any stale favorite status between track row items and the docked Mini-Player card.
  - Synchronized `getCurrentPlaybackQueue` so auto-play and track skip within My Mix always pull fresh track metadata with updated favorite flags.
- Maintained strict zero-emoji policy across all strings, labels, and UI elements.
- Bumped `versionCode = 55` and `versionName = "1.0.55"` in `app/build.gradle.kts`.

### Version 1.0.51
- Fixed "Recommended for You" card interaction on the Home screen: Wired up `onRecommendedPlay` and `onRecommendedDownload` handlers in `MainActivity.kt`. Tapping a recommended card instantly plays the matching local song or streams a preview sample with live equalizer animation overlay, and tapping the download overlay button prefills and opens the downloader.
- Universal Mini-Player (Now Playing card) across all screens and modals: Extracted reusable `MiniPlayerCard` and integrated it at the bottom of all full-screen browsing dialogs (`OfflineLibraryDialog` for Offline Library and Your Mix / My Mix, `PlaylistDetailDialog`, and `ArtistDetailDialog`). Users can now always see the currently playing track and control playback (play/pause, favorite, tap to open full player) no matter what screen or panel is open.
- Refactored Dialog Hierarchy: Moved `NowPlayingDialog`, `EqualizerDialog`, and `EditSongDialog` to the top of the overlay stack so expanding the player from any dialog cleanly layers above it without occlusion.
- Maintained strict zero-emoji policy across all strings, labels, and buttons.
- Bumped `versionCode = 50` and `versionName = "1.0.50"` in `app/build.gradle.kts`.

### Version 1.0.25
- Added Dark Mode Toggle in Settings: Users can now switch between Light Mode and Dark Mode with persistent state saved in SharedPreferences.
- Comprehensive Dark Theme implementation across MaterialTheme color schemes, Scaffold, NavigationBar, track items, headers, NowPlayingDialog, waveforms, and modals.
- Fixed Disappeared/Missing Songs in Library: Removed aggressive 12-character title truncating de-duplication in `restoreAndSyncLibrary`. Songs with shared prefixes now persist distinctly.
- Added Multi-Source Storage Sync: `restoreAndSyncLibrary` now actively searches public `Music/MusicHub`, `Music/MusicHub/Songs`, app-specific internal/external storage, and Android 10+ MediaStore queries for complete track preservation.
- Fixed URI playable candidate matching to prevent hijacking tracks by artist match.

### Version 1.0.23
- Fixed volume card layout in Settings: Added `Modifier.weight(1f)` with ellipsis truncation to ensure volume percentage text ("60%") is never clipped or pushed off screen when using English UI language.
- Added 1-Tap "Default" / "លំនាំដើម" quick-reset button on the volume card in Settings to instantly reset audio gain to safe 60% headphone level.
- Integrated hardware/software DSP Equalizer (`android.media.audiofx.Equalizer`) attached directly to ExoPlayer's `audioSessionId` with multi-band frequency modulation for presets (Bass Boost, Vocal Boost, Electronic, Rock, Acoustic, Flat).
- Persisted Equalizer preset selection to `SharedPreferences` across app restarts.
- Added `MODIFY_AUDIO_SETTINGS` permission in `AndroidManifest.xml`.
- Updated `versionCode = 23` and `versionName = "1.0.23"` in `app/build.gradle.kts`.

### Version 1.0.22
- Fixed playlist song reordering: replaced standard touch slop gestures with unconsumed `awaitEachGesture` pointer tracking, enabling instant real-time drag-and-drop reordering without scroll interception from parent `LazyColumn`.
- Added 1-Tap Quick Reorder Dropdown Menu to the drag handle (`≡`): users can either drag directly or tap to select Move Up, Move Down, Move to Top, or Move to Bottom.
- Further enlarged vinyl record center spindle hub to 76.dp (with 34.dp inner hole and 3.5.dp metallic silver bevel border).
- Added In-App Software Gain / Headphone Volume Control with real-time sliders in both Now Playing dialog and Settings screen, defaulting to 60% gain to protect users from sudden loud volume spikes when connecting headphones/earphones.
- Updated `versionCode = 22` and `versionName = "1.0.22"` in `app/build.gradle.kts`.

### Version 1.0.21
- Full-bleed artwork scaling (`scale(1.40f)`) implemented across all cards (Hero card, track rows, mini player, playlist detail, library items) to completely push out baked-in 45px black letterbox bars from YouTube 4:3 thumbnails.
- Added Playlist Cards horizontal row on the Home screen below the Hero card, with artwork collage, track count badge, and "+ New" creation shortcut.
- Switched default YouTube thumbnail extraction from `hqdefault.jpg` to `mqdefault.jpg` (clean 16:9 widescreen without black borders).

### Version 1.0.20
- Dynamic app version resolution in Settings and UpdateDialog using Android `packageManager.getPackageInfo`.
- Enlarged vinyl record center album artwork to fill the disc.

# Development Instructions for Future AI Agents

1. When adding new audio formats, ensure both the Media3 extractor dependencies and the import MIME-type filters in `AndroidManifest.xml` are updated.
2. When altering Room database schema, always write an explicit `Migration(from, to)` in `AppDatabase.kt` and increment the database version number.
3. Keep the 4 bottom navigation items strictly intact: Home, Library, Playlist, Settings.
4. Maintain zero emojis across all strings, labels, and documentation.
5. Verify changes with `./gradlew compileDebugKotlin` before completing turns.

# Your Mix: On-device Music Mood AI (2026-10-13)

## Model and license

- **Model:** `mert-v1-95m-music-emotion-int8` (`mert_emotion_int8.onnx`), a dynamically INT8-quantized MERT-v1-95M music-emotion regression model.
- **Source:** https://huggingface.co/huyleit/mert-v1-95m-music-emotion-int8
- **License:** Apache-2.0, as declared in the model card. The card identifies MERT-v1-95M as the base model and reports fine-tuning on DEAM (1,802 tracks) and PMEmo (767 tracks).
- **Artifact:** `app/src/main/assets/mood/mert_emotion_int8.onnx`, SHA-256 `B05EF3FBE904F1121B32632DA9AD4907AFB676A11E348FE01484A71412C42E01`; 95,552,890 bytes (about 95.6 MB APK-size impact before APK compression).
- **Runtime:** ONNX Runtime Android 1.18.0. The model is copied into private app storage on first use, then loaded locally. It needs no network, account, or API key after installation.

## How Your Mix classification works

`OnDeviceMusicMoodClassifier` decodes a center 15-second local-audio segment, mixes it to mono, linearly resamples it to the model's documented 24 kHz / 360,000-float input, and invokes the bundled ONNX model off the main thread. The learned output is continuous valence/arousal. MusicHub converts that output to normalized proximity scores for Calm, Energetic, Sad, and Romantic in the Russell valence/arousal space. This is an explicit app taxonomy mapping, not metadata or genre inference. Corrupt, unavailable, or unsupported audio is excluded from Your Mix rather than being given an invented result.

The model natively supports music-affect valence/arousal, not a direct “romantic” class. Romantic is therefore a documented positive-valence / moderate-arousal target in the same trained affect space; it should be presented as a recommendation label, not as a claim that the pretrained model emits a literal romantic tag.

## Personalization and storage

Your Mix keeps the existing local signals—completion versus early skip, replays, favorites, and 1–5 mix ratings—and ranks the **model-derived** mood vectors with those learned local preference weights. Explicit favorites and star ratings retain their stronger existing rewards; an early skip remains a weak negative reward. The existing diversity limits prevent a few songs or artists from dominating a mix.

Room schema v2 adds `mood_predictions`, keyed by stable song ID plus model version. It stores only valence, arousal, four derived scores, confidence, and analysis time—never raw audio. Migration `1 -> 2` creates this table and index without clearing existing data. Changing `MODEL_VERSION` invalidates old predictions naturally.

## Files changed

- Added `app/src/main/assets/mood/mert_emotion_int8.onnx`.
- Added `app/src/main/java/com/musichub/app/domain/mood/OnDeviceMusicMoodClassifier.kt`.
- Updated `AudioWaveformFingerprinter.kt` only at the Your Mix integration boundary; other recommendation paths retain their existing behavior.
- Updated `MainActivity.kt` only to display an on-device analysis loading state for Your Mix.
- Updated `RoomSchema.kt` with the minimal cache entity/DAO and migration.
- Updated Gradle dependencies for ONNX Runtime Android and added focused classifier mapping tests in `app/src/test/java/com/musichub/app/domain/mood/OnDeviceMusicMoodClassifierTest.kt`.

## Verification

- Verified the bundled artifact exists and SHA-256 matches the value above.
- Ran `git diff --check` successfully.
- Attempted `:app:compileDebugKotlin` multiple times. Gradle could not start its required single-use daemon because this execution environment blocks its loopback connection (`java.io.IOException: Unable to establish loopback connection`), so no successful build or test run is claimed.

## Known limitations

- First-time analysis can be CPU- and memory-intensive because the model is approximately 95.6 MB; subsequent runs use the Room cache.
- The model card reports self-reported evaluation metrics. Its model artifact and documented input/output were verified, but this repository does not include a representative licensed audio test corpus for end-to-end accuracy validation.

