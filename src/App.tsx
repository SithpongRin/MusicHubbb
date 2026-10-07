import React, { useState, useEffect, useCallback } from 'react';
import { Song, Playlist, DownloadTask, AppSettings, GitHubRelease } from './types/music';
import { musicDb } from './services/db';
import { audioPlayer } from './services/audioPlayer';
import { downloadManager } from './services/downloadManager';
import { getInitialSampleSongs } from './services/sampleData';
import { checkGitHubRelease, CURRENT_VERSION_NAME } from './services/githubUpdateService';

// Components
import { StatusBar } from './components/StatusBar';
import { BottomNavigation, NavTab } from './components/BottomNavigation';
import { MiniPlayer } from './components/MiniPlayer';
import { NowPlayingModal } from './components/NowPlayingModal';
import { EqualizerModal } from './components/EqualizerModal';
import { DownloadModal } from './components/DownloadModal';
import { AudioImportModal } from './components/AudioImportModal';
import { PlaylistModal } from './components/PlaylistModal';
import { MetadataEditorModal } from './components/MetadataEditorModal';
import { SongOptionsMenu } from './components/SongOptionsMenu';
import { UpdateDialog } from './components/UpdateDialog';
import { AndroidNotificationBanner } from './components/AndroidNotificationBanner';

// Screens
import { HomeScreen } from './screens/HomeScreen';
import { LibraryScreen } from './screens/LibraryScreen';
import { PlaylistScreen } from './screens/PlaylistScreen';
import { SettingsScreen } from './screens/SettingsScreen';

export default function App() {
  // Navigation State
  const [currentTab, setCurrentTab] = useState<NavTab>('home');

  // Database Data States
  const [songs, setSongs] = useState<Song[]>([]);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [downloads, setDownloads] = useState<DownloadTask[]>([]);
  const [storageUsage, setStorageUsage] = useState({ songCount: 0, estimatedBytes: 0 });
  const [settings, setSettings] = useState<AppSettings>({
    theme: 'dark',
    language: 'km',
    defaultQuality: '320k',
    wifiOnly: false,
    resumePlayback: true,
    volumeNormalization: false,
    equalizer: {
      enabled: false,
      preset: 'Flat',
      bands: [0, 0, 0, 0, 0],
      bassBoost: 0,
    },
    storageLocation: '/storage/emulated/0/Music/MusicHub',
  });

  // Audio Playback State from AudioPlayerService
  const [currentSong, setCurrentSong] = useState<Song | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [queue, setQueue] = useState<Song[]>([]);
  const [queueIndex, setQueueIndex] = useState(-1);
  const [isShuffle, setIsShuffle] = useState(false);
  const [repeatMode, setRepeatMode] = useState<'off' | 'all' | 'one'>('off');

  // Modals visibility
  const [isNowPlayingOpen, setIsNowPlayingOpen] = useState(false);
  const [isEqualizerOpen, setIsEqualizerOpen] = useState(false);
  const [isDownloadOpen, setIsDownloadOpen] = useState(false);
  const [isImportOpen, setIsImportOpen] = useState(false);
  const [isPlaylistModalOpen, setIsPlaylistModalOpen] = useState(false);
  const [playlistToEdit, setPlaylistToEdit] = useState<Playlist | null>(null);
  const [isMetadataEditorOpen, setIsMetadataEditorOpen] = useState(false);
  const [isSongOptionsOpen, setIsSongOptionsOpen] = useState(false);
  const [selectedSongForOptions, setSelectedSongForOptions] = useState<Song | null>(null);
  
  // Updates State
  const [isUpdateDialogOpen, setIsUpdateDialogOpen] = useState(false);
  const [availableRelease, setAvailableRelease] = useState<GitHubRelease | null>(null);
  const [isCheckingUpdate, setIsCheckingUpdate] = useState(false);

  // Toast Notification State
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  // Sync player state from service
  const syncPlayerState = useCallback(() => {
    setCurrentSong(audioPlayer.getCurrentSong());
    setIsPlaying(audioPlayer.getIsPlaying());
    setCurrentTime(audioPlayer.getCurrentTime());
    setDuration(audioPlayer.getDuration());
    setQueue(audioPlayer.getQueue());
    setQueueIndex(audioPlayer.getQueueIndex());
    setIsShuffle(audioPlayer.getIsShuffle());
    setRepeatMode(audioPlayer.getRepeatMode());
  }, []);

  // Initialize DB and Seed data
  useEffect(() => {
    const initData = async () => {
      try {
        const loadedSettings = await musicDb.getSettings();
        setSettings(loadedSettings);

        let loadedSongs = await musicDb.getAllSongs();
        if (loadedSongs.length === 0) {
          // Seed with offline sample tracks
          const samples = getInitialSampleSongs();
          for (const s of samples) {
            await musicDb.saveSong(s);
          }
          loadedSongs = samples;

          // Seed default sample playlist
          const samplePlaylist: Playlist = {
            id: 'pl-favorites',
            title: 'Favorites & Chill',
            description: 'My selected offline tracks',
            artworkUrl: samples[0]?.artworkUrl,
            songIds: samples.slice(0, 3).map((s) => s.id),
            createdAt: Date.now(),
            updatedAt: Date.now(),
          };
          await musicDb.savePlaylist(samplePlaylist);
        }

        setSongs(loadedSongs);

        const loadedPlaylists = await musicDb.getAllPlaylists();
        setPlaylists(loadedPlaylists);

        const loadedDownloads = await musicDb.getAllDownloads();
        setDownloads(loadedDownloads);

        const stats = await musicDb.getStorageUsage();
        setStorageUsage(stats);

        // Resume playback if enabled and track exists
        if (loadedSettings.resumePlayback && loadedSongs.length > 0) {
          const lastPlayed = [...loadedSongs].sort(
            (a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0)
          )[0];
          if (lastPlayed) {
            // Load without auto-playing immediately to respect user
            setCurrentSong(lastPlayed);
          }
        }
      } catch {
        // Initialization fallback
      }
    };

    initData();

    // Subscribe to audio player events
    const unsubscribeAudio = audioPlayer.subscribe(syncPlayerState);
    const unsubscribeDownloader = downloadManager.subscribe(async () => {
      const dls = await musicDb.getAllDownloads();
      setDownloads(dls);
    });

    return () => {
      unsubscribeAudio();
      unsubscribeDownloader();
    };
  }, [syncPlayerState]);

  // Apply Theme & Language to DOM
  useEffect(() => {
    const root = document.documentElement;
    const isDark =
      settings.theme === 'dark' ||
      (settings.theme === 'system' &&
        window.matchMedia('(prefers-color-scheme: dark)').matches);

    if (isDark) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }

    if (settings.language === 'km') {
      root.classList.add('font-khmer');
    } else {
      root.classList.remove('font-khmer');
    }
  }, [settings.theme, settings.language]);

  // Playback Handlers
  const handlePlaySong = async (song: Song, queueList?: Song[]) => {
    // Update play count and last played in DB
    const updated: Song = {
      ...song,
      playCount: (song.playCount || 0) + 1,
      lastPlayed: Date.now(),
    };
    await musicDb.saveSong(updated);

    // Update in memory
    setSongs((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));

    audioPlayer.playSong(song, queueList || songs);
  };

  const handleTogglePlay = () => {
    audioPlayer.togglePlayPause();
  };

  const handlePrevious = () => {
    audioPlayer.previous();
  };

  const handleNext = () => {
    audioPlayer.next();
  };

  const handleSeek = (seconds: number) => {
    audioPlayer.seekTo(seconds);
  };

  const handleToggleShuffle = () => {
    audioPlayer.toggleShuffle();
  };

  const handleToggleRepeat = () => {
    audioPlayer.toggleRepeat();
  };

  const handleToggleFavorite = async (song: Song) => {
    const updated: Song = {
      ...song,
      isFavorite: !song.isFavorite,
    };
    await musicDb.saveSong(updated);
    setSongs((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
    if (currentSong?.id === song.id) {
      setCurrentSong(updated);
    }
  };

  // Queue actions
  const handleSelectQueueSong = (idx: number) => {
    audioPlayer.playTrackAtIndex(idx);
  };

  const handleRemoveFromQueue = (idx: number) => {
    audioPlayer.removeFromQueue(idx);
  };

  // Options Menu actions
  const handlePlayNext = (song: Song) => {
    const q = audioPlayer.getQueue();
    const idx = audioPlayer.getQueueIndex();
    const newQueue = [...q];
    newQueue.splice(idx + 1, 0, song);
    setQueue(newQueue);
  };

  const handleAddToPlaylist = async (song: Song, playlistId: string) => {
    const pl = playlists.find((p) => p.id === playlistId);
    if (!pl) return;
    if (!pl.songIds.includes(song.id)) {
      const updated: Playlist = {
        ...pl,
        songIds: [...pl.songIds, song.id],
        updatedAt: Date.now(),
      };
      await musicDb.savePlaylist(updated);
      setPlaylists((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    }
  };

  const handleDeleteSong = async (song: Song) => {
    await musicDb.deleteSong(song.id);
    setSongs((prev) => prev.filter((s) => s.id !== song.id));
    // Remove from playlists too
    for (const pl of playlists) {
      if (pl.songIds.includes(song.id)) {
        const updatedPl: Playlist = {
          ...pl,
          songIds: pl.songIds.filter((id) => id !== song.id),
        };
        await musicDb.savePlaylist(updatedPl);
      }
    }
    const updatedPlaylists = await musicDb.getAllPlaylists();
    setPlaylists(updatedPlaylists);

    // If deleting currently playing track
    if (currentSong?.id === song.id) {
      audioPlayer.next();
    }
    const stats = await musicDb.getStorageUsage();
    setStorageUsage(stats);
    showToast('Track deleted from storage');
  };

  // Playlists management
  const handleSavePlaylist = async (playlist: Playlist) => {
    await musicDb.savePlaylist(playlist);
    setPlaylists((prev) => {
      const exists = prev.some((p) => p.id === playlist.id);
      if (exists) {
        return prev.map((p) => (p.id === playlist.id ? playlist : p));
      }
      return [playlist, ...prev];
    });
    showToast('Playlist saved');
  };

  const handleDeletePlaylist = async (playlistId: string) => {
    await musicDb.deletePlaylist(playlistId);
    setPlaylists((prev) => prev.filter((p) => p.id !== playlistId));
    showToast('Playlist deleted');
  };

  const handleUpdatePlaylistSongs = async (playlistId: string, songIds: string[]) => {
    const pl = playlists.find((p) => p.id === playlistId);
    if (!pl) return;
    const updated: Playlist = { ...pl, songIds, updatedAt: Date.now() };
    await musicDb.savePlaylist(updated);
    setPlaylists((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
  };

  // Metadata editor
  const handleSaveMetadata = async (updatedSong: Song) => {
    await musicDb.saveSong(updatedSong);
    setSongs((prev) => prev.map((s) => (s.id === updatedSong.id ? updatedSong : s)));
    if (currentSong?.id === updatedSong.id) {
      setCurrentSong(updatedSong);
    }
    showToast('Track details saved');
  };

  // Settings & Equalizer
  const handleUpdateSettings = async (partial: Partial<AppSettings>) => {
    const updated = { ...settings, ...partial };
    setSettings(updated);
    await musicDb.saveSettings(updated);
    if (partial.equalizer) {
      audioPlayer.applyEqualizer(partial.equalizer);
    }
    if (partial.volumeNormalization !== undefined) {
      audioPlayer.setVolumeNormalization(partial.volumeNormalization);
    }
    if (partial.theme) {
      showToast(`Theme set to ${partial.theme}`);
    }
  };

  const handleClearCache = async () => {
    await musicDb.clearTemporaryCache();
    const stats = await musicDb.getStorageUsage();
    setStorageUsage(stats);
    showToast('Temporary cache cleared');
  };

  const handleRescanLibrary = async () => {
    const loadedSongs = await musicDb.getAllSongs();
    setSongs(loadedSongs);
    const stats = await musicDb.getStorageUsage();
    setStorageUsage(stats);
    showToast('Library rescan complete');
  };

  // Check GitHub Updates
  const handleCheckUpdates = async () => {
    setIsCheckingUpdate(true);
    try {
      const result = await checkGitHubRelease();
      if (result.hasUpdate && result.release) {
        setAvailableRelease(result.release);
        setIsUpdateDialogOpen(true);
      } else {
        showToast(`MusicHub v${CURRENT_VERSION_NAME} is up to date`);
      }
    } catch {
      showToast('Could not check updates');
    } finally {
      setIsCheckingUpdate(false);
    }
  };

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  const isDark =
    settings.theme === 'dark' ||
    (settings.theme === 'system' &&
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches);

  return (
    <div className={`w-full min-h-screen ${isDark ? 'dark bg-black' : 'bg-neutral-100'} flex items-center justify-center sm:p-4 select-none transition-colors duration-200`}>
      {/* Mobile Device Frame for realistic Android feel */}
      <div className={`w-full max-w-md h-[100dvh] sm:h-[844px] ${isDark ? 'dark bg-neutral-900 text-white border-neutral-800' : 'bg-white text-neutral-900 border-neutral-200'} sm:rounded-[44px] shadow-2xl overflow-hidden flex flex-col relative border transition-colors duration-200`}>
        
        {/* In-app Toast Banner */}
        {toastMessage && (
          <div className="absolute top-10 left-4 right-4 z-50 flex justify-center pointer-events-none animate-in fade-in slide-in-from-top-2 duration-200">
            <div className="px-4 py-2 rounded-2xl bg-neutral-900/90 dark:bg-white/95 text-white dark:text-neutral-900 text-xs font-bold shadow-lg backdrop-blur-md">
              {toastMessage}
            </div>
          </div>
        )}
        
        {/* Status Bar */}
        <StatusBar isPlaying={isPlaying} />

        {/* Media3 Foreground Notification Banner */}
        {currentSong && (
          <AndroidNotificationBanner
            song={currentSong}
            isPlaying={isPlaying}
            currentTime={currentTime}
            duration={duration}
            onTogglePlay={handleTogglePlay}
            onPrevious={handlePrevious}
            onNext={handleNext}
            onOpenPlayer={() => setIsNowPlayingOpen(true)}
          />
        )}

        {/* Tab Screens */}
        <div className="flex-1 flex flex-col min-h-0 relative overflow-hidden">
          {currentTab === 'home' && (
            <HomeScreen
              songs={songs}
              playlists={playlists}
              currentSong={currentSong}
              isPlaying={isPlaying}
              lang={settings.language}
              onPlaySong={handlePlaySong}
              onTogglePlay={handleTogglePlay}
              onToggleFavorite={handleToggleFavorite}
              onOpenDownload={() => setIsDownloadOpen(true)}
              onOpenImport={() => setIsImportOpen(true)}
              onSelectPlaylist={(pl) => {
                setPlaylistToEdit(pl);
                setCurrentTab('playlist');
              }}
            />
          )}

          {currentTab === 'library' && (
            <LibraryScreen
              songs={songs}
              currentSong={currentSong}
              isPlaying={isPlaying}
              lang={settings.language}
              onPlaySong={handlePlaySong}
              onOpenOptions={(s) => {
                setSelectedSongForOptions(s);
                setIsSongOptionsOpen(true);
              }}
              onOpenImport={() => setIsImportOpen(true)}
              onOpenDownload={() => setIsDownloadOpen(true)}
            />
          )}

          {currentTab === 'playlist' && (
            <PlaylistScreen
              playlists={playlists}
              allSongs={songs}
              currentSong={currentSong}
              isPlaying={isPlaying}
              lang={settings.language}
              onPlaySong={handlePlaySong}
              onCreatePlaylist={() => {
                setPlaylistToEdit(null);
                setIsPlaylistModalOpen(true);
              }}
              onEditPlaylist={(pl) => {
                setPlaylistToEdit(pl);
                setIsPlaylistModalOpen(true);
              }}
              onDeletePlaylist={handleDeletePlaylist}
              onUpdatePlaylistSongs={handleUpdatePlaylistSongs}
            />
          )}

          {currentTab === 'settings' && (
            <SettingsScreen
              settings={settings}
              storageUsage={storageUsage}
              onUpdateSettings={handleUpdateSettings}
              onClearCache={handleClearCache}
              onRescanLibrary={handleRescanLibrary}
              onOpenEqualizer={() => setIsEqualizerOpen(true)}
              onCheckUpdates={handleCheckUpdates}
              isCheckingUpdate={isCheckingUpdate}
            />
          )}
        </div>

        {/* Mini Player (Above Bottom Navigation when active) */}
        <MiniPlayer
          song={currentSong}
          isPlaying={isPlaying}
          progressPercent={progressPercent}
          onTogglePlay={handleTogglePlay}
          onNext={handleNext}
          onOpenNowPlaying={() => setIsNowPlayingOpen(true)}
        />

        {/* Exactly 4 Tabs Bottom Navigation */}
        <BottomNavigation
          currentTab={currentTab}
          onSelectTab={setCurrentTab}
          lang={settings.language}
        />

        {/* Android Gesture Bar */}
        <div className="w-full pb-1 pt-0.5 flex justify-center bg-white dark:bg-neutral-900 pointer-events-none">
          <div className="w-32 h-1 bg-neutral-400 dark:bg-neutral-600 rounded-full" />
        </div>

        {/* Modals & Overlays */}
        <NowPlayingModal
          isOpen={isNowPlayingOpen}
          onClose={() => setIsNowPlayingOpen(false)}
          song={currentSong}
          isPlaying={isPlaying}
          currentTime={currentTime}
          duration={duration}
          isShuffle={isShuffle}
          repeatMode={repeatMode}
          queue={queue}
          queueIndex={queueIndex}
          onTogglePlay={handleTogglePlay}
          onPrevious={handlePrevious}
          onNext={handleNext}
          onSeek={handleSeek}
          onToggleShuffle={handleToggleShuffle}
          onToggleRepeat={handleToggleRepeat}
          onToggleFavorite={handleToggleFavorite}
          onSelectQueueSong={handleSelectQueueSong}
          onRemoveFromQueue={handleRemoveFromQueue}
          onOpenEqualizer={() => setIsEqualizerOpen(true)}
        />

        <EqualizerModal
          isOpen={isEqualizerOpen}
          onClose={() => setIsEqualizerOpen(false)}
          settings={settings.equalizer}
          onSave={(eq) => handleUpdateSettings({ equalizer: eq })}
        />

        <DownloadModal
          isOpen={isDownloadOpen}
          onClose={() => setIsDownloadOpen(false)}
          downloads={downloads}
          onSongDownloaded={(newSong) => {
            setSongs((prev) => [newSong, ...prev]);
            handlePlaySong(newSong);
          }}
        />

        <AudioImportModal
          isOpen={isImportOpen}
          onClose={() => setIsImportOpen(false)}
          onSongsImported={(newSongs) => {
            setSongs((prev) => [...newSongs, ...prev]);
          }}
        />

        <PlaylistModal
          isOpen={isPlaylistModalOpen}
          onClose={() => {
            setIsPlaylistModalOpen(false);
            setPlaylistToEdit(null);
          }}
          playlistToEdit={playlistToEdit}
          availableSongs={songs}
          onSave={handleSavePlaylist}
        />

        <MetadataEditorModal
          isOpen={isMetadataEditorOpen}
          onClose={() => {
            setIsMetadataEditorOpen(false);
            setSelectedSongForOptions(null);
          }}
          song={selectedSongForOptions}
          onSave={handleSaveMetadata}
        />

        <SongOptionsMenu
          isOpen={isSongOptionsOpen}
          onClose={() => {
            setIsSongOptionsOpen(false);
            setSelectedSongForOptions(null);
          }}
          song={selectedSongForOptions}
          playlists={playlists}
          onPlayNext={handlePlayNext}
          onAddToPlaylist={handleAddToPlaylist}
          onToggleFavorite={handleToggleFavorite}
          onEditMetadata={(s) => {
            setSelectedSongForOptions(s);
            setIsSongOptionsOpen(false);
            setIsMetadataEditorOpen(true);
          }}
          onDeleteSong={handleDeleteSong}
        />

        <UpdateDialog
          isOpen={isUpdateDialogOpen}
          onClose={() => setIsUpdateDialogOpen(false)}
          release={availableRelease}
          currentVersion={CURRENT_VERSION_NAME}
        />
      </div>
    </div>
  );
}
