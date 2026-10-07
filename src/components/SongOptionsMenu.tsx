import React from 'react';
import {
  X,
  Play,
  ListPlus,
  Heart,
  Edit3,
  Trash2,
  Info,
} from 'lucide-react';
import { Song, Playlist } from '../types/music';

interface SongOptionsMenuProps {
  isOpen: boolean;
  onClose: () => void;
  song: Song | null;
  playlists: Playlist[];
  onPlayNext: (song: Song) => void;
  onAddToPlaylist: (song: Song, playlistId: string) => void;
  onToggleFavorite: (song: Song) => void;
  onEditMetadata: (song: Song) => void;
  onDeleteSong: (song: Song) => void;
}

export const SongOptionsMenu: React.FC<SongOptionsMenuProps> = ({
  isOpen,
  onClose,
  song,
  playlists,
  onPlayNext,
  onAddToPlaylist,
  onToggleFavorite,
  onEditMetadata,
  onDeleteSong,
}) => {
  const [showPlaylistPicker, setShowPlaylistPicker] = React.useState(false);
  const [showDetails, setShowDetails] = React.useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = React.useState(false);

  React.useEffect(() => {
    if (!isOpen) {
      setShowPlaylistPicker(false);
      setShowDetails(false);
      setShowDeleteConfirm(false);
    }
  }, [isOpen]);

  if (!isOpen || !song) return null;

  const formatSize = (bytes: number) => {
    if (!bytes) return 'Unknown';
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const secs = Math.floor(sec % 60);
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  return (
    <div className="fixed inset-0 z-70 bg-black/60 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border-t sm:border border-neutral-200 dark:border-neutral-800 rounded-t-3xl sm:rounded-3xl max-w-sm w-full p-6 shadow-2xl flex flex-col animate-in slide-in-from-bottom duration-200">
        {/* Header */}
        <div className="flex items-center space-x-3 pb-4 border-b border-neutral-100 dark:border-neutral-800">
          <img
            src={song.artworkUrl}
            alt={song.title}
            className="w-12 h-12 rounded-xl object-cover bg-neutral-200 dark:bg-neutral-800"
          />
          <div className="min-w-0 flex-1">
            <h4 className="text-sm font-bold text-neutral-900 dark:text-white truncate">
              {song.title}
            </h4>
            <p className="text-xs text-neutral-500 dark:text-neutral-400 truncate">
              {song.artist}
            </p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Normal Options */}
        {!showPlaylistPicker && !showDetails && !showDeleteConfirm && (
          <div className="py-2 space-y-1">
            <button
              onClick={() => {
                onPlayNext(song);
                onClose();
              }}
              className="w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-neutral-700 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition text-sm font-semibold"
            >
              <Play className="w-4 h-4 text-indigo-500" />
              <span>Play Next in Queue</span>
            </button>

            <button
              onClick={() => setShowPlaylistPicker(true)}
              className="w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-neutral-700 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition text-sm font-semibold"
            >
              <ListPlus className="w-4 h-4 text-indigo-500" />
              <span>Add to Playlist</span>
            </button>

            <button
              onClick={() => {
                onToggleFavorite(song);
                onClose();
              }}
              className="w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-neutral-700 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition text-sm font-semibold"
            >
              <Heart
                className={`w-4 h-4 ${
                  song.isFavorite ? 'text-rose-500 fill-current' : 'text-neutral-400'
                }`}
              />
              <span>{song.isFavorite ? 'Remove from Favorites' : 'Add to Favorites'}</span>
            </button>

            <button
              onClick={() => {
                onEditMetadata(song);
                onClose();
              }}
              className="w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-neutral-700 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition text-sm font-semibold"
            >
              <Edit3 className="w-4 h-4 text-neutral-500" />
              <span>Edit Details</span>
            </button>

            <button
              onClick={() => setShowDetails(true)}
              className="w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-neutral-700 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition text-sm font-semibold"
            >
              <Info className="w-4 h-4 text-neutral-500" />
              <span>Track Info</span>
            </button>

            <button
              onClick={() => setShowDeleteConfirm(true)}
              className="w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition text-sm font-semibold"
            >
              <Trash2 className="w-4 h-4" />
              <span>Delete from Device</span>
            </button>
          </div>
        )}

        {/* Delete Confirmation Subview */}
        {showDeleteConfirm && (
          <div className="py-4 text-center">
            <div className="w-12 h-12 rounded-2xl bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 flex items-center justify-center mx-auto mb-3">
              <Trash2 className="w-6 h-6" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-white">
              Delete Track?
            </h4>
            <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-1 max-w-xs mx-auto">
              Are you sure you want to permanently delete "{song.title}" from local device storage?
            </p>
            <div className="flex items-center space-x-2 mt-5">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="flex-1 py-2.5 rounded-xl bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300 font-semibold text-xs hover:bg-neutral-200 dark:hover:bg-neutral-700 transition"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onDeleteSong(song);
                  onClose();
                }}
                className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs transition shadow-md shadow-rose-600/20"
              >
                Delete
              </button>
            </div>
          </div>
        )}

        {/* Playlist Picker Subview */}
        {showPlaylistPicker && (
          <div className="py-2">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs uppercase font-bold text-neutral-400">
                Choose Playlist
              </span>
              <button
                onClick={() => setShowPlaylistPicker(false)}
                className="text-xs text-indigo-500 font-semibold"
              >
                Back
              </button>
            </div>
            {playlists.length === 0 ? (
              <p className="text-xs text-neutral-500 py-3 text-center">
                No playlists created yet.
              </p>
            ) : (
              <div className="space-y-1 max-h-48 overflow-y-auto">
                {playlists.map((pl) => (
                  <button
                    key={pl.id}
                    onClick={() => {
                      onAddToPlaylist(song, pl.id);
                      onClose();
                    }}
                    className="w-full text-left px-3 py-2 rounded-xl text-sm font-semibold text-neutral-800 dark:text-neutral-200 hover:bg-neutral-100 dark:hover:bg-neutral-800 truncate"
                  >
                    {pl.title}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Track Info Subview */}
        {showDetails && (
          <div className="py-2">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs uppercase font-bold text-neutral-400">
                Track Specifications
              </span>
              <button
                onClick={() => setShowDetails(false)}
                className="text-xs text-indigo-500 font-semibold"
              >
                Back
              </button>
            </div>
            <div className="space-y-2 text-xs bg-neutral-50 dark:bg-neutral-800/60 p-3 rounded-2xl border border-neutral-100 dark:border-neutral-800">
              <div className="flex justify-between">
                <span className="text-neutral-400 font-medium">Format:</span>
                <span className="font-bold text-neutral-800 dark:text-neutral-200 uppercase">
                  {song.format}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-neutral-400 font-medium">Duration:</span>
                <span className="font-bold text-neutral-800 dark:text-neutral-200">
                  {formatDuration(song.duration)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-neutral-400 font-medium">Bitrate:</span>
                <span className="font-bold text-neutral-800 dark:text-neutral-200">
                  {song.bitRate || '320kbps'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-neutral-400 font-medium">File Size:</span>
                <span className="font-bold text-neutral-800 dark:text-neutral-200">
                  {formatSize(song.sizeBytes)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-neutral-400 font-medium">Plays:</span>
                <span className="font-bold text-neutral-800 dark:text-neutral-200">
                  {song.playCount}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-neutral-400 font-medium">Storage:</span>
                <span className="font-bold text-emerald-500">
                  Offline Ready
                </span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
