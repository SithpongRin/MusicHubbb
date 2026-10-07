import React, { useState } from 'react';
import {
  MoreVertical,
  Plus,
  DownloadCloud,
  Disc,
  User,
  Heart,
  Music2,
  FolderDown,
} from 'lucide-react';
import { Song, Language } from '../types/music';
import { translations } from '../i18n/translations';

type LibraryTab = 'all' | 'downloaded' | 'albums' | 'artists' | 'favorites';

interface LibraryScreenProps {
  songs: Song[];
  currentSong: Song | null;
  isPlaying: boolean;
  lang: Language;
  onPlaySong: (song: Song, queue?: Song[]) => void;
  onOpenOptions: (song: Song) => void;
  onOpenImport: () => void;
  onOpenDownload: () => void;
}

function formatDuration(sec: number): string {
  if (isNaN(sec) || sec <= 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export const LibraryScreen: React.FC<LibraryScreenProps> = ({
  songs,
  currentSong,
  isPlaying,
  lang,
  onPlaySong,
  onOpenOptions,
  onOpenImport,
  onOpenDownload,
}) => {
  const t = translations[lang];
  const [activeTab, setActiveTab] = useState<LibraryTab>('all');
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null);

  // Filtered views
  const downloadedSongs = songs.filter((s) => s.isDownloaded);
  const favoriteSongs = songs.filter((s) => s.isFavorite);

  // Group by Album
  const albumsMap = songs.reduce<Record<string, Song[]>>((acc, song) => {
    const albumName = song.album || 'Unknown Album';
    if (!acc[albumName]) acc[albumName] = [];
    acc[albumName].push(song);
    return acc;
  }, {});

  // Group by Artist
  const artistsMap = songs.reduce<Record<string, Song[]>>((acc, song) => {
    const artistName = song.artist || 'Unknown Artist';
    if (!acc[artistName]) acc[artistName] = [];
    acc[artistName].push(song);
    return acc;
  }, {});

  const currentSongsList =
    activeTab === 'all'
      ? songs
      : activeTab === 'downloaded'
      ? downloadedSongs
      : activeTab === 'favorites'
      ? favoriteSongs
      : [];

  return (
    <div className="flex-1 overflow-y-auto px-5 py-4 pb-28">
      {/* Header and Action Buttons */}
      <div className="flex items-center justify-between pt-1 pb-4">
        <div>
          <h1 className="text-2xl font-black text-neutral-900 dark:text-white tracking-tight">
            {t.libraryTitle}
          </h1>
          <p className="text-xs font-semibold text-neutral-400 mt-0.5">
            {songs.length} tracks in collection
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={onOpenImport}
            className="px-3 py-1.5 rounded-xl bg-neutral-100 dark:bg-neutral-800 text-neutral-800 dark:text-neutral-200 text-xs font-bold flex items-center space-x-1.5 hover:bg-neutral-200 dark:hover:bg-neutral-700 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Import</span>
          </button>
          <button
            onClick={onOpenDownload}
            className="px-3 py-1.5 rounded-xl bg-indigo-600 text-white text-xs font-bold flex items-center space-x-1.5 hover:bg-indigo-700 transition shadow-sm shadow-indigo-600/30"
          >
            <DownloadCloud className="w-3.5 h-3.5" />
            <span>Download</span>
          </button>
        </div>
      </div>

      {/* Tabs Bar */}
      <div className="flex space-x-2 overflow-x-auto pb-2 -mx-5 px-5 no-scrollbar mb-3">
        {[
          { id: 'all', label: t.allSongs, icon: Music2 },
          { id: 'downloaded', label: t.downloadedSongs, icon: FolderDown },
          { id: 'albums', label: t.albums, icon: Disc },
          { id: 'artists', label: t.artists, icon: User },
          { id: 'favorites', label: t.libraryFavorites, icon: Heart },
        ].map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => {
                setActiveTab(tab.id as LibraryTab);
                setSelectedGroup(null);
              }}
              className={`flex items-center space-x-1.5 px-3.5 py-1.5 rounded-full text-xs font-bold transition whitespace-nowrap ${
                isActive
                  ? 'bg-neutral-900 dark:bg-white text-white dark:text-neutral-900'
                  : 'bg-neutral-100 dark:bg-neutral-800/80 text-neutral-600 dark:text-neutral-400 hover:bg-neutral-200 dark:hover:bg-neutral-700'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Content depending on Active Tab */}
      {(activeTab === 'all' || activeTab === 'downloaded' || activeTab === 'favorites') && (
        <div className="space-y-1 mt-2">
          {currentSongsList.length === 0 ? (
            <div className="py-14 text-center">
              <p className="text-sm font-bold text-neutral-700 dark:text-neutral-300">
                {t.emptyLibraryTitle}
              </p>
              <p className="text-xs text-neutral-400 max-w-xs mx-auto mt-1">
                {t.emptyLibraryDesc}
              </p>
            </div>
          ) : (
            currentSongsList.map((song) => {
              const isCurrent = currentSong?.id === song.id;
              return (
                <div
                  key={song.id}
                  onClick={() => onPlaySong(song, currentSongsList)}
                  className={`flex items-center justify-between p-2.5 rounded-2xl cursor-pointer transition ${
                    isCurrent
                      ? 'bg-indigo-50/70 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-900/60'
                      : 'hover:bg-neutral-100 dark:hover:bg-neutral-800/60 border border-transparent'
                  }`}
                >
                  <div className="flex items-center space-x-3 min-w-0 flex-1">
                    <div className="relative w-11 h-11 rounded-xl overflow-hidden shadow-xs flex-shrink-0 bg-neutral-200 dark:bg-neutral-700">
                      <img
                        src={song.artworkUrl}
                        alt={song.title}
                        className="w-full h-full object-cover"
                      />
                      {isCurrent && isPlaying && (
                        <div className="absolute inset-0 bg-indigo-900/40 flex items-center justify-center">
                          <div className="flex items-end space-x-0.5 h-3">
                            <span className="w-0.5 h-full bg-white rounded-full animate-bounce" />
                            <span className="w-0.5 h-2 bg-white rounded-full animate-bounce delay-100" />
                            <span className="w-0.5 h-2.5 bg-white rounded-full animate-bounce delay-75" />
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="min-w-0 flex-1 pr-2">
                      <p
                        className={`text-xs font-bold truncate ${
                          isCurrent
                            ? 'text-indigo-600 dark:text-indigo-400'
                            : 'text-neutral-900 dark:text-white'
                        }`}
                      >
                        {song.title}
                      </p>
                      <p className="text-[11px] text-neutral-400 truncate mt-0.5">
                        {song.artist} • {song.album}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center space-x-2 flex-shrink-0">
                    <span className="text-[11px] font-mono text-neutral-400">
                      {formatDuration(song.duration)}
                    </span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onOpenOptions(song);
                      }}
                      className="p-1.5 rounded-xl text-neutral-400 hover:text-neutral-700 dark:hover:text-white hover:bg-neutral-200/50 dark:hover:bg-neutral-700/50 transition"
                      aria-label="More options"
                    >
                      <MoreVertical className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {/* Albums View */}
      {activeTab === 'albums' && (
        <div className="mt-2">
          {selectedGroup ? (
            <div>
              <div className="flex items-center justify-between mb-3">
                <div>
                  <h3 className="text-sm font-bold text-neutral-900 dark:text-white">
                    {selectedGroup}
                  </h3>
                  <span className="text-xs text-neutral-400">
                    {albumsMap[selectedGroup]?.length || 0} songs
                  </span>
                </div>
                <button
                  onClick={() => setSelectedGroup(null)}
                  className="text-xs font-bold text-indigo-500"
                >
                  All Albums
                </button>
              </div>

              <div className="space-y-1">
                {albumsMap[selectedGroup]?.map((song) => (
                  <div
                    key={song.id}
                    onClick={() => onPlaySong(song, albumsMap[selectedGroup])}
                    className="flex items-center justify-between p-2.5 rounded-2xl hover:bg-neutral-100 dark:hover:bg-neutral-800/60 cursor-pointer"
                  >
                    <div className="flex items-center space-x-3 min-w-0 flex-1">
                      <img
                        src={song.artworkUrl}
                        alt={song.title}
                        className="w-10 h-10 rounded-xl object-cover"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                          {song.title}
                        </p>
                        <p className="text-[11px] text-neutral-400 truncate">
                          {song.artist}
                        </p>
                      </div>
                    </div>
                    <span className="text-[11px] font-mono text-neutral-400">
                      {formatDuration(song.duration)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {Object.entries(albumsMap).map(([albumName, albumSongs]) => {
                const cover = albumSongs[0]?.artworkUrl;
                return (
                  <div
                    key={albumName}
                    onClick={() => setSelectedGroup(albumName)}
                    className="p-3 rounded-2xl bg-white dark:bg-neutral-800/50 border border-neutral-200/60 dark:border-neutral-700/60 cursor-pointer hover:shadow-sm transition group"
                  >
                    <div className="w-full aspect-square rounded-xl overflow-hidden bg-neutral-200 dark:bg-neutral-700 mb-2">
                      <img
                        src={cover}
                        alt={albumName}
                        className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                      />
                    </div>
                    <p className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                      {albumName}
                    </p>
                    <p className="text-[11px] text-neutral-400 truncate">
                      {albumSongs.length} songs
                    </p>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Artists View */}
      {activeTab === 'artists' && (
        <div className="mt-2">
          {selectedGroup ? (
            <div>
              <div className="flex items-center justify-between mb-3">
                <div>
                  <h3 className="text-sm font-bold text-neutral-900 dark:text-white">
                    {selectedGroup}
                  </h3>
                  <span className="text-xs text-neutral-400">
                    {artistsMap[selectedGroup]?.length || 0} songs
                  </span>
                </div>
                <button
                  onClick={() => setSelectedGroup(null)}
                  className="text-xs font-bold text-indigo-500"
                >
                  All Artists
                </button>
              </div>

              <div className="space-y-1">
                {artistsMap[selectedGroup]?.map((song) => (
                  <div
                    key={song.id}
                    onClick={() => onPlaySong(song, artistsMap[selectedGroup])}
                    className="flex items-center justify-between p-2.5 rounded-2xl hover:bg-neutral-100 dark:hover:bg-neutral-800/60 cursor-pointer"
                  >
                    <div className="flex items-center space-x-3 min-w-0 flex-1">
                      <img
                        src={song.artworkUrl}
                        alt={song.title}
                        className="w-10 h-10 rounded-xl object-cover"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                          {song.title}
                        </p>
                        <p className="text-[11px] text-neutral-400 truncate">
                          {song.album}
                        </p>
                      </div>
                    </div>
                    <span className="text-[11px] font-mono text-neutral-400">
                      {formatDuration(song.duration)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              {Object.entries(artistsMap).map(([artistName, artistSongs]) => {
                const cover = artistSongs[0]?.artworkUrl;
                return (
                  <div
                    key={artistName}
                    onClick={() => setSelectedGroup(artistName)}
                    className="flex items-center space-x-3.5 p-3 rounded-2xl bg-white dark:bg-neutral-800/50 border border-neutral-200/60 dark:border-neutral-700/60 cursor-pointer hover:shadow-sm transition"
                  >
                    <img
                      src={cover}
                      alt={artistName}
                      className="w-12 h-12 rounded-full object-cover"
                    />
                    <div>
                      <p className="text-xs font-bold text-neutral-900 dark:text-white">
                        {artistName}
                      </p>
                      <p className="text-[11px] text-neutral-400">
                        {artistSongs.length} tracks
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
