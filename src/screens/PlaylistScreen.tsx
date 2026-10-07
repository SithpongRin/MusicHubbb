import React, { useState } from 'react';
import {
  Plus,
  Play,
  Shuffle,
  Trash2,
  Edit2,
  ChevronLeft,
  Music,
  ArrowUp,
  ArrowDown,
  X,
} from 'lucide-react';
import { Playlist, Song, Language } from '../types/music';
import { translations } from '../i18n/translations';

interface PlaylistScreenProps {
  playlists: Playlist[];
  allSongs: Song[];
  currentSong: Song | null;
  isPlaying: boolean;
  lang: Language;
  onPlaySong: (song: Song, queue?: Song[]) => void;
  onCreatePlaylist: () => void;
  onEditPlaylist: (playlist: Playlist) => void;
  onDeletePlaylist: (id: string) => void;
  onUpdatePlaylistSongs: (playlistId: string, songIds: string[]) => void;
}

export const PlaylistScreen: React.FC<PlaylistScreenProps> = ({
  playlists,
  allSongs,
  currentSong,
  isPlaying,
  lang,
  onPlaySong,
  onCreatePlaylist,
  onEditPlaylist,
  onDeletePlaylist,
  onUpdatePlaylistSongs,
}) => {
  const t = translations[lang];
  const [selectedPlaylist, setSelectedPlaylist] = useState<Playlist | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  // Active playlist resolution
  const activePlaylistData = selectedPlaylist
    ? playlists.find((p) => p.id === selectedPlaylist.id) || selectedPlaylist
    : null;

  const playlistSongs = activePlaylistData
    ? activePlaylistData.songIds
        .map((id) => allSongs.find((s) => s.id === id))
        .filter((s): s is Song => Boolean(s))
    : [];

  const handlePlayAll = (shuffle: boolean = false) => {
    if (playlistSongs.length === 0) return;
    if (shuffle) {
      const shuffled = [...playlistSongs].sort(() => Math.random() - 0.5);
      onPlaySong(shuffled[0], shuffled);
    } else {
      onPlaySong(playlistSongs[0], playlistSongs);
    }
  };

  const handleMoveSong = (index: number, direction: 'up' | 'down') => {
    if (!activePlaylistData) return;
    const ids = [...activePlaylistData.songIds];
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= ids.length) return;

    const temp = ids[index];
    ids[index] = ids[targetIndex];
    ids[targetIndex] = temp;

    onUpdatePlaylistSongs(activePlaylistData.id, ids);
  };

  const handleRemoveSong = (songId: string) => {
    if (!activePlaylistData) return;
    const ids = activePlaylistData.songIds.filter((id) => id !== songId);
    onUpdatePlaylistSongs(activePlaylistData.id, ids);
  };

  return (
    <div className="flex-1 overflow-y-auto px-5 py-4 pb-28">
      {/* If Inside Playlist Details */}
      {activePlaylistData ? (
        <div>
          {/* Back button & actions */}
          <div className="flex items-center justify-between pb-4">
            <button
              onClick={() => setSelectedPlaylist(null)}
              className="flex items-center space-x-1.5 text-xs font-bold text-neutral-600 dark:text-neutral-400 hover:text-neutral-900 dark:hover:text-white"
            >
              <ChevronLeft className="w-4 h-4" />
              <span>Back to Playlists</span>
            </button>

            <div className="flex items-center space-x-1">
              <button
                onClick={() => onEditPlaylist(activePlaylistData)}
                className="p-2 rounded-xl text-neutral-500 hover:text-neutral-900 dark:hover:text-white hover:bg-neutral-100 dark:hover:bg-neutral-800"
                title="Edit playlist details"
              >
                <Edit2 className="w-4 h-4" />
              </button>
              <button
                onClick={() => setShowDeleteConfirm(true)}
                className="p-2 rounded-xl text-neutral-500 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/40"
                title="Delete playlist"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Delete Playlist In-App Confirmation Dialog */}
          {showDeleteConfirm && (
            <div className="mb-4 p-4 rounded-3xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/60 text-center animate-in fade-in">
              <h4 className="text-sm font-bold text-rose-900 dark:text-rose-200">
                {t.deletePlaylist}?
              </h4>
              <p className="text-xs text-rose-700 dark:text-rose-300 mt-1 max-w-xs mx-auto">
                {t.deletePlaylistConfirm}
              </p>
              <div className="flex items-center justify-center space-x-2 mt-3">
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className="px-4 py-1.5 rounded-xl bg-white dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300 font-semibold text-xs border border-neutral-200 dark:border-neutral-700"
                >
                  Cancel
                </button>
                <button
                  onClick={() => {
                    onDeletePlaylist(activePlaylistData.id);
                    setSelectedPlaylist(null);
                    setShowDeleteConfirm(false);
                  }}
                  className="px-4 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs shadow-md shadow-rose-600/20"
                >
                  {t.delete}
                </button>
              </div>
            </div>
          )}

          {/* Playlist Hero Info */}
          <div className="flex items-center space-x-4 p-4 rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 mb-5 shadow-xs">
            <img
              src={activePlaylistData.artworkUrl}
              alt={activePlaylistData.title}
              className="w-24 h-24 rounded-2xl object-cover shadow-sm bg-indigo-600"
            />
            <div className="min-w-0 flex-1">
              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-500">
                Custom Playlist
              </span>
              <h2 className="text-lg font-black text-neutral-900 dark:text-white truncate mt-0.5">
                {activePlaylistData.title}
              </h2>
              {activePlaylistData.description && (
                <p className="text-xs text-neutral-400 truncate mt-0.5">
                  {activePlaylistData.description}
                </p>
              )}
              <p className="text-xs font-semibold text-neutral-500 mt-1">
                {playlistSongs.length} tracks
              </p>
            </div>
          </div>

          {/* Action Play / Shuffle buttons */}
          <div className="flex items-center space-x-3 mb-5">
            <button
              onClick={() => handlePlayAll(false)}
              disabled={playlistSongs.length === 0}
              className="flex-1 py-2.5 rounded-2xl bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white font-bold text-xs flex items-center justify-center space-x-1.5 shadow-md shadow-indigo-600/20 transition"
            >
              <Play className="w-4 h-4 fill-current ml-0.5" />
              <span>{t.playAll}</span>
            </button>
            <button
              onClick={() => handlePlayAll(true)}
              disabled={playlistSongs.length === 0}
              className="flex-1 py-2.5 rounded-2xl bg-neutral-100 dark:bg-neutral-800 hover:bg-neutral-200 dark:hover:bg-neutral-700 disabled:opacity-40 text-neutral-900 dark:text-white font-bold text-xs flex items-center justify-center space-x-1.5 transition"
            >
              <Shuffle className="w-4 h-4" />
              <span>{t.shuffleAll}</span>
            </button>
          </div>

          {/* Track Listing */}
          <div className="space-y-1.5">
            {playlistSongs.length === 0 ? (
              <div className="py-12 text-center text-neutral-400 text-xs">
                No songs in this playlist yet. Edit playlist to add songs.
              </div>
            ) : (
              playlistSongs.map((song, idx) => {
                const isCurrent = currentSong?.id === song.id;
                return (
                  <div
                    key={`${song.id}-${idx}`}
                    onClick={() => onPlaySong(song, playlistSongs)}
                    className={`flex items-center justify-between p-2.5 rounded-2xl cursor-pointer transition ${
                      isCurrent
                        ? 'bg-indigo-50/70 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-900/60'
                        : 'hover:bg-neutral-100 dark:hover:bg-neutral-800/60'
                    }`}
                  >
                    <div className="flex items-center space-x-3 min-w-0 flex-1">
                      <span className="text-xs font-extrabold text-neutral-400 w-4 text-center">
                        {idx + 1}
                      </span>
                      <img
                        src={song.artworkUrl}
                        alt={song.title}
                        className="w-10 h-10 rounded-xl object-cover"
                      />
                      <div className="min-w-0 flex-1">
                        <p
                          className={`text-xs font-bold truncate ${
                            isCurrent
                              ? 'text-indigo-600 dark:text-indigo-400'
                              : 'text-neutral-900 dark:text-white'
                          }`}
                        >
                          {song.title}
                        </p>
                        <p className="text-[11px] text-neutral-400 truncate">
                          {song.artist}
                        </p>
                      </div>
                    </div>

                    <div
                      className="flex items-center space-x-1 flex-shrink-0"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        onClick={() => handleMoveSong(idx, 'up')}
                        disabled={idx === 0}
                        className="p-1 text-neutral-400 hover:text-neutral-700 dark:hover:text-white disabled:opacity-20"
                        title="Move up"
                      >
                        <ArrowUp className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => handleMoveSong(idx, 'down')}
                        disabled={idx === playlistSongs.length - 1}
                        className="p-1 text-neutral-400 hover:text-neutral-700 dark:hover:text-white disabled:opacity-20"
                        title="Move down"
                      >
                        <ArrowDown className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => handleRemoveSong(song.id)}
                        className="p-1 text-neutral-400 hover:text-rose-500"
                        title="Remove from playlist"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      ) : (
        /* Playlists Directory Grid */
        <div>
          {/* Header */}
          <div className="flex items-center justify-between pt-1 pb-4">
            <div>
              <h1 className="text-2xl font-black text-neutral-900 dark:text-white tracking-tight">
                {t.playlistsTitle}
              </h1>
              <p className="text-xs font-semibold text-neutral-400 mt-0.5">
                {playlists.length} playlists
              </p>
            </div>

            <button
              onClick={onCreatePlaylist}
              className="px-3.5 py-2 rounded-xl bg-indigo-600 text-white text-xs font-bold flex items-center space-x-1.5 hover:bg-indigo-700 transition shadow-sm shadow-indigo-600/30"
            >
              <Plus className="w-4 h-4" />
              <span>{t.createPlaylist}</span>
            </button>
          </div>

          {playlists.length === 0 ? (
            <div className="py-16 text-center">
              <div className="w-14 h-14 rounded-2xl bg-neutral-100 dark:bg-neutral-800 flex items-center justify-center text-neutral-400 mx-auto mb-3">
                <Music className="w-7 h-7" />
              </div>
              <p className="text-sm font-bold text-neutral-800 dark:text-neutral-200">
                {t.emptyPlaylistsTitle}
              </p>
              <p className="text-xs text-neutral-400 max-w-xs mx-auto mt-1">
                {t.emptyPlaylistsDesc}
              </p>
              <button
                onClick={onCreatePlaylist}
                className="mt-4 px-4 py-2 rounded-xl bg-indigo-600 text-white text-xs font-bold"
              >
                {t.createPlaylist}
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3.5 mt-2">
              {playlists.map((pl) => (
                <div
                  key={pl.id}
                  onClick={() => setSelectedPlaylist(pl)}
                  className="p-3 rounded-2xl bg-white dark:bg-neutral-800/50 border border-neutral-200/60 dark:border-neutral-700/60 cursor-pointer hover:shadow-md transition group"
                >
                  <div className="w-full aspect-square rounded-xl overflow-hidden bg-indigo-950 mb-2.5 relative">
                    <img
                      src={pl.artworkUrl}
                      alt={pl.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                    />
                    <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 flex items-center justify-center transition">
                      <Play className="w-8 h-8 text-white fill-current" />
                    </div>
                  </div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                    {pl.title}
                  </h4>
                  <p className="text-[11px] text-neutral-400 truncate mt-0.5">
                    {pl.songIds.length} tracks
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
