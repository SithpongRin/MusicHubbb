import React, { useState } from 'react';
import { X, Image, Save } from 'lucide-react';
import { Song } from '../types/music';

interface MetadataEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  song: Song | null;
  onSave: (updatedSong: Song) => void;
}

export const MetadataEditorModal: React.FC<MetadataEditorModalProps> = ({
  isOpen,
  onClose,
  song,
  onSave,
}) => {
  const [title, setTitle] = useState(song?.title || '');
  const [artist, setArtist] = useState(song?.artist || '');
  const [album, setAlbum] = useState(song?.album || '');
  const [artworkUrl, setArtworkUrl] = useState(song?.artworkUrl || '');
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    if (song) {
      setTitle(song.title);
      setArtist(song.artist);
      setAlbum(song.album);
      setArtworkUrl(song.artworkUrl);
    }
  }, [song]);

  if (!isOpen || !song) return null;

  const handleImageUpload = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const file = files[0];
    const reader = new FileReader();
    reader.onload = (e) => {
      if (e.target?.result) {
        setArtworkUrl(e.target.result as string);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleSave = () => {
    const updated: Song = {
      ...song,
      title: title.trim() || song.title,
      artist: artist.trim() || song.artist,
      album: album.trim() || song.album,
      artworkUrl: artworkUrl || song.artworkUrl,
    };
    onSave(updated);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-70 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-sm w-full p-6 shadow-2xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-neutral-100 dark:border-neutral-800">
          <h3 className="text-base font-bold text-neutral-900 dark:text-white">
            Edit Track Metadata
          </h3>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Artwork Preview and Change */}
        <div className="mt-4 flex flex-col items-center">
          <div className="relative w-28 h-28 rounded-2xl overflow-hidden shadow-md bg-neutral-100 dark:bg-neutral-800 border border-neutral-200 dark:border-neutral-700">
            <img src={artworkUrl} alt={title} className="w-full h-full object-cover" />
            <button
              onClick={() => fileInputRef.current?.click()}
              className="absolute inset-0 bg-black/40 hover:bg-black/60 flex flex-col items-center justify-center text-white opacity-90 transition cursor-pointer"
            >
              <Image className="w-6 h-6 mb-1" />
              <span className="text-[10px] font-bold">Change Cover</span>
            </button>
          </div>
          <input
            type="file"
            ref={fileInputRef}
            accept="image/*"
            className="hidden"
            onChange={(e) => handleImageUpload(e.target.files)}
          />
        </div>

        {/* Fields */}
        <div className="mt-4 space-y-3">
          <div>
            <label className="text-xs font-bold text-neutral-500 uppercase tracking-wider block mb-1">
              Song Title
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl border border-neutral-200 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-neutral-900 dark:text-white text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="text-xs font-bold text-neutral-500 uppercase tracking-wider block mb-1">
              Artist
            </label>
            <input
              type="text"
              value={artist}
              onChange={(e) => setArtist(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl border border-neutral-200 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-neutral-900 dark:text-white text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="text-xs font-bold text-neutral-500 uppercase tracking-wider block mb-1">
              Album
            </label>
            <input
              type="text"
              value={album}
              onChange={(e) => setAlbum(e.target.value)}
              className="w-full px-3.5 py-2 rounded-xl border border-neutral-200 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-neutral-900 dark:text-white text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>

        {/* Action Buttons */}
        <div className="mt-6 flex items-center justify-end space-x-2">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-neutral-600 dark:text-neutral-400 font-semibold text-xs hover:bg-neutral-100 dark:hover:bg-neutral-800 transition"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs transition flex items-center space-x-1.5 shadow-md shadow-indigo-600/20"
          >
            <Save className="w-3.5 h-3.5" />
            <span>Save</span>
          </button>
        </div>
      </div>
    </div>
  );
};
