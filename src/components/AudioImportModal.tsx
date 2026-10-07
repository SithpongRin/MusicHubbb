import React, { useState, useRef } from 'react';
import { X, Upload, Music, Check, HardDrive } from 'lucide-react';
import { Song } from '../types/music';
import { musicDb } from '../services/db';

interface AudioImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSongsImported: (newSongs: Song[]) => void;
}

export const AudioImportModal: React.FC<AudioImportModalProps> = ({
  isOpen,
  onClose,
  onSongsImported,
}) => {
  const [isProcessing, setIsProcessing] = useState(false);
  const [importedList, setImportedList] = useState<Song[]>([]);
  const [dragActive, setDragActive] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;

    setIsProcessing(true);
    const newSongs: Song[] = [];

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const ext = file.name.split('.').pop()?.toLowerCase() || 'mp3';
      const cleanTitle = file.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');

      // Measure duration with temporary audio element
      const objectUrl = URL.createObjectURL(file);
      const duration = await new Promise<number>((resolve) => {
        const audio = new Audio();
        audio.src = objectUrl;
        audio.onloadedmetadata = () => {
          resolve(Math.round(audio.duration) || 180);
        };
        audio.onerror = () => {
          resolve(180);
        };
      });

      // Generate colorful gradient cover for local file
      const colors: [string, string][] = [
        ['#312e81', '#6366f1'],
        ['#1e3a8a', '#3b82f6'],
        ['#14532d', '#22c55e'],
        ['#701a75', '#d946ef'],
        ['#7c2d12', '#f97316'],
      ];
      const selectedColor = colors[i % colors.length];
      const artworkSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300">
        <defs>
          <linearGradient id="g${i}" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="${selectedColor[0]}" />
            <stop offset="100%" stop-color="${selectedColor[1]}" />
          </linearGradient>
        </defs>
        <rect width="300" height="300" rx="30" fill="url(#g${i})" />
        <circle cx="150" cy="140" r="45" fill="none" stroke="rgba(255,255,255,0.7)" stroke-width="4" />
        <circle cx="150" cy="140" r="18" fill="rgba(255,255,255,0.9)" />
        <text x="150" y="235" font-family="system-ui, sans-serif" font-size="15" font-weight="700" fill="#ffffff" text-anchor="middle">${cleanTitle.substring(0, 22)}</text>
      </svg>`;
      const artworkUrl = `data:image/svg+xml;utf8,${encodeURIComponent(artworkSvg)}`;

      const songId = `imported-${Date.now()}-${i}`;
      const song: Song = {
        id: songId,
        title: cleanTitle,
        artist: 'Local Artist',
        album: 'Device Storage',
        duration,
        audioUrl: objectUrl,
        audioBlob: file,
        artworkUrl,
        dateAdded: Date.now(),
        playCount: 0,
        lastPlayed: null,
        isFavorite: false,
        format: ext,
        bitRate: '320kbps',
        sizeBytes: file.size,
        isDownloaded: true,
      };

      await musicDb.saveSong(song);
      await musicDb.saveAudioBlob(songId, file);
      newSongs.push(song);
    }

    setImportedList((prev) => [...prev, ...newSongs]);
    onSongsImported(newSongs);
    setIsProcessing(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragActive(false);
    if (e.dataTransfer.files) {
      handleFiles(e.dataTransfer.files);
    }
  };

  return (
    <div className="fixed inset-0 z-70 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-md w-full p-6 shadow-2xl flex flex-col max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-neutral-100 dark:border-neutral-800">
          <div className="flex items-center space-x-2">
            <HardDrive className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            <h3 className="text-lg font-bold text-neutral-900 dark:text-white">
              Import Local Audio
            </h3>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Drop Zone */}
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragActive(true);
          }}
          onDragLeave={() => setDragActive(false)}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
          className={`mt-4 border-2 border-dashed rounded-3xl p-8 flex flex-col items-center justify-center text-center cursor-pointer transition ${
            dragActive
              ? 'border-indigo-600 bg-indigo-50/50 dark:bg-indigo-950/30'
              : 'border-neutral-300 dark:border-neutral-700 hover:border-indigo-500 bg-neutral-50 dark:bg-neutral-800/40'
          }`}
        >
          <input
            type="file"
            ref={fileInputRef}
            multiple
            accept=".mp3,.m4a,.aac,.flac,.wav,.ogg,audio/*"
            className="hidden"
            onChange={(e) => handleFiles(e.target.files)}
          />

          <div className="w-14 h-14 rounded-2xl bg-indigo-100 dark:bg-indigo-950 flex items-center justify-center text-indigo-600 dark:text-indigo-400 mb-3">
            <Upload className="w-7 h-7" />
          </div>

          <p className="text-sm font-bold text-neutral-900 dark:text-white">
            Choose Audio Files from Device
          </p>
          <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-1">
            Tap or drag & drop files here
          </p>
          <p className="text-[11px] font-mono text-neutral-400 dark:text-neutral-500 mt-3">
            Supported: MP3, M4A, FLAC, WAV, AAC, OGG
          </p>
        </div>

        {isProcessing && (
          <div className="mt-4 p-3 rounded-xl bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400 text-xs font-semibold flex items-center justify-center space-x-2 animate-pulse">
            <Music className="w-4 h-4" />
            <span>Scanning and importing audio files...</span>
          </div>
        )}

        {/* Imported List */}
        {importedList.length > 0 && (
          <div className="mt-4">
            <h4 className="text-xs font-bold text-neutral-500 uppercase tracking-wider mb-2">
              Imported ({importedList.length})
            </h4>
            <div className="space-y-1.5 max-h-40 overflow-y-auto">
              {importedList.map((song) => (
                <div
                  key={song.id}
                  className="flex items-center justify-between p-2 rounded-xl bg-neutral-100 dark:bg-neutral-800 text-xs"
                >
                  <div className="flex items-center space-x-2 truncate">
                    <Check className="w-4 h-4 text-emerald-500 flex-shrink-0" />
                    <span className="font-semibold text-neutral-900 dark:text-white truncate">
                      {song.title}
                    </span>
                  </div>
                  <span className="text-[10px] text-neutral-400 uppercase font-mono ml-2">
                    {song.format}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-6 flex justify-end">
          <button
            onClick={onClose}
            className="px-6 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs transition"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
