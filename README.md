# MusicHub

MusicHub is a personal, offline-first Android music player and legal audio downloader designed for private use and direct APK sharing. It operates completely offline with zero account requirements, zero advertisements, and zero tracking.

## Highlights

- Offline-First: All imported songs, playlists, favorites, and playback statistics are stored on-device in a local Room database.
- Android Media3: Powered by ExoPlayer 1.3.1 with foreground MediaSessionService for seamless background playback, lock screen controls, and Bluetooth headset button integration.
- 5-Band Equalizer: Customizable audio frequency shaping (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz), bass boost, and audio presets.
- Legal Downloader Architecture: Modular DownloadProvider framework for permitted audio streams and public domain recordings (Archive.org) without DRM circumvention.
- Direct APK Updates: Built-in update checker that queries GitHub Releases without requiring Google Play or third-party app stores.
- Modern Material 3 UI: Jetpack Compose user interface with 4 dedicated navigation destinations (Home, Library, Playlist, Settings), responsive Now Playing screen, and docking Mini Player.
- Bilingual: Full localization support for English and Khmer (Kantumruy Pro typography).

## Technology Stack

- Language: Kotlin 1.9.23 / Jetpack Compose
- Audio Playback: Android Media3 / ExoPlayer 1.3.1
- Local Database: Room 2.6.1 (SQLite) with StateFlow reactivity
- Settings: Jetpack DataStore Preferences
- Background Tasks: Foreground Service & WorkManager 2.9.0
- Image Loading: Coil 2.6.0
- Network & HTTP: OkHttp 4.12.0
- Automated Releases: GitHub Actions workflow with GitHub Releases API

## Project Architecture

The codebase follows Clean Architecture and MVVM principles:

```
app/
  src/main/
    java/com/musichub/app/
      data/
        database/          # Room Entities, DAOs, and AppDatabase
        repository/        # MusicRepository, PlaylistRepository, DownloadRepository
      domain/
        model/             # Song, Playlist, DownloadTask, AudioQuality
        downloader/        # Modular DownloadProvider interface and implementations
      player/
        MediaPlaybackService.kt  # Media3 MediaSessionService with foreground notification
        MusicPlayerController.kt # Player controller abstraction
      update/
        GitHubUpdateChecker.kt   # Semantic version comparison for GitHub Releases
        ApkInstaller.kt          # Android Intent FileProvider installer
      presentation/
        MainActivity.kt    # Jetpack Compose entry point and navigation scaffold
        screens/           # Home, Library, Playlist, Settings screens
        components/        # MiniPlayer, NowPlaying, Equalizer, DownloadModal
    res/
      xml/file_paths.xml   # FileProvider configuration for APK installs
    AndroidManifest.xml    # Permissions, services, and FileProvider declarations
```

## How to Build the Release APK

### Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK with compileSdk 34 and build-tools

### Build Command

```bash
# Clone the repository
git clone https://github.com/musichub-android/musichub.git
cd musichub

# Build the release APK
./gradlew assembleRelease
```

The compiled APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

## Automated GitHub Release Workflow

To publish a new version for friends and users:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit your changes:
   ```bash
   git commit -am "Release version 1.0.1"
   ```
3. Tag the commit with the release version:
   ```bash
   git tag -a v1.0.1 -m "Release v1.0.1"
   ```
4. Push the tag to GitHub:
   ```bash
   git push origin v1.0.1
   ```
5. The GitHub Actions workflow (`.github/workflows/release.yml`) will automatically:
   - Check out the repository
   - Run tests
   - Build the release APK
   - Create a GitHub Release
   - Upload the APK asset as `MusicHub-v1.0.1.apk`

When users open MusicHub on their devices, the app's update system will detect the new release and offer an in-app download and installation.

## Legal and DRM Policy

MusicHub is built solely for personal use with legally acquired media, direct public links, and public domain audio. The software does not decrypt, bypass, or circumvent DRM, authentication, or protected subscription services.

## License

Personal and educational use. Free of advertisements and user tracking.
