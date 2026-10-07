import React, { useState } from 'react';
import { X, Image, Save, ListPlus } from 'lucide-react';
import { Playlist, Song } from '../types/music';

interface PlaylistModalProps {
  isOpen: boolean;
  onClose: () => void;
  playlistToEdit?: Playlist | null;
  availableSongs: Song[];
  onSave: (playlist: Playlist) => void;
}

export const PlaylistModal: React.FC<PlaylistModalProps> = ({
  isOpen,
  onClose,
  playlistToEdit,
  availableSongs,
  onSave,
}) => {
  const [title, setTitle] = useState(playlistToEdit?.title || '');
  const [description, setDescription] = useState(playlistToEdit?.description || '');
  const [artworkUrl, setArtworkUrl] = useState(playlistToEdit?.artworkUrl || '');
  const [selectedSongIds, setSelectedSongIds] = useState<string[]>(
    playlistToEdit?.songIds || []
  );
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    if (playlistToEdit) {
      setTitle(playlistToEdit.title);
      setDescription(playlistToEdit.description || '');
      setArtworkUrl(playlistToEdit.artworkUrl || '');
      setSelectedSongIds(playlistToEdit.songIds || []);
    } else {
      setTitle('');
      setDescription('');
      // default gradient playlist cover
      const defaultSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300">
        <rect width="300" height="300" rx="32" fill="#4f46e5" />
        <circle cx="150" cy="150" r="50" fill="none" stroke="#ffffff" stroke-width="6" />
        <path d="M140 125 L170 150 L140 175 Z" fill="#ffffff" />
      </svg>`;
      setArtworkUrl(`data:image/svg+xml;utf8,${encodeURIComponent(defaultSvg)}`);
      setSelectedSongIds([]);
    }
  }, [playlistToEdit, isOpen]);

  if (!isOpen) return null;

  const handleArtworkUpload = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      if (e.target?.result) {
        setArtworkUrl(e.target.result as string);
      }
    };
    reader.readAsDataURL(files[0]);
  };

  const toggleSongSelection = (id: string) => {
    if (selectedSongIds.includes(id)) {
      setSelectedSongIds(selectedSongIds.filter((s) => s !== id));
    } else {
      setSelectedSongIds([...selectedSongIds, id]);
    }
  };

  const handleSave = () => {
    if (!title.trim()) return;

    const playlist: Playlist = {
      id: playlistToEdit?.id || `pl-${Date.now()}`,
      title: title.trim(),
      description: description.trim(),
      artworkUrl: artworkUrl,
      songIds: selectedSongIds,
      createdAt: playlistToEdit?.createdAt || Date.now(),
      updatedAt: Date.now(),
    };

    onSave(playlist);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-70 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-md w-full p-6 shadow-2xl flex flex-col max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-neutral-100 dark:border-neutral-800">
          <div className="flex items-center space-x-2">
            <ListPlus className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            <h3 className="text-base font-bold text-neutral-900 dark:text-white">
              {playlistToEdit ? 'Edit Playlist' : 'Create New Playlist'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Artwork & Details */}
        <div className="mt-4 flex items-center space-x-4">
          <div className="relative w-20 h-20 rounded-2xl overflow-hidden shadow-sm bg-neutral-100 dark:bg-neutral-800 border border-neutral-200 dark:border-neutral-700 flex-shrink-0">
            <img src={artworkUrl} alt={title} className="w-full h-full object-cover" />
            <button
              onClick={() => fileInputRef.current?.click()}
              className="absolute inset-0 bg-black/40 hover:bg-black/60 flex items-center justify-center text-white opacity-80 transition cursor-pointer"
            >
              <Image className="w-5 h-5" />
            </button>
          </div>
          <input
            type="file"
            ref={fileInputRef}
            accept="image/*"
            className="hidden"
            onChange={(e) => handleArtworkUpload(e.target.files)}
          />

          <div className="flex-1 space-y-2">
            <input
              type="text"
              placeholder="Playlist Title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl border border-neutral-200 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-neutral-900 dark:text-white text-xs font-bold focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <input
              type="text"
              placeholder="Description (optional)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3.5 py-1.5 rounded-xl border border-neutral-200 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-neutral-900 dark:text-white text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>

        {/* Song Selection */}
        <div className="mt-5">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-neutral-500 uppercase tracking-wider">
              Add Tracks ({selectedSongIds.length} selected)
            </span>
          </div>

          <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
            {availableSongs.length === 0 ? (
              <p className="text-xs text-neutral-400 py-3 text-center">
                No songs in library. Import songs first.
              </p>
            ) : (
              availableSongs.map((song) => {
                const isSelected = selectedSongIds.includes(song.id);
                return (
                  <div
                    key={song.id}
                    onClick={() => toggleSongSelection(song.id)}
                    className={`flex items-center justify-between p-2 rounded-xl cursor-pointer transition text-xs ${
                      isSelected
                        ? 'bg-indigo-50 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300 font-semibold'
                        : 'hover:bg-neutral-100 dark:hover:bg-neutral-800/60 text-neutral-700 dark:text-neutral-300'
                    }`}
                  >
                    <div className="flex items-center space-x-2.5 truncate flex-1">
                      <img
                        src={song.artworkUrl}
                        alt={song.title}
                        className="w-8 h-8 rounded-lg object-cover"
                      />
                      <div className="truncate">
                        <p className="truncate font-semibold">{song.title}</p>
                        <p className="text-[10px] text-neutral-400 truncate">{song.artist}</p>
                      </div>
                    </div>
                    <div
                      className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                        isSelected
                          ? 'border-indigo-600 bg-indigo-600 text-white'
                          : 'border-neutral-300 dark:border-neutral-600'
                      }`}
                    >
                      {isSelected && <div className="w-1.5 h-1.5 bg-white rounded-full" />}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Buttons */}
        <div className="mt-6 flex items-center justify-end space-x-2">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-neutral-600 dark:text-neutral-400 font-semibold text-xs hover:bg-neutral-100 dark:hover:bg-neutral-800 transition"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={!title.trim()}
            className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-bold text-xs transition flex items-center space-x-1.5 shadow-md shadow-indigo-600/20"
          >
            <Save className="w-3.5 h-3.5" />
            <span>Save Playlist</span>
          </button>
        </div>
      </div>
    </div>
  );
};
