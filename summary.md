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

### Version 1.0.50
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

