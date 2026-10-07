import React from 'react';
import { Play, Pause, SkipForward } from 'lucide-react';
import { Song } from '../types/music';

interface MiniPlayerProps {
  song: Song | null;
  isPlaying: boolean;
  progressPercent: number;
  onTogglePlay: (e: React.MouseEvent) => void;
  onNext: (e: React.MouseEvent) => void;
  onOpenNowPlaying: () => void;
}

export const MiniPlayer: React.FC<MiniPlayerProps> = ({
  song,
  isPlaying,
  progressPercent,
  onTogglePlay,
  onNext,
  onOpenNowPlaying,
}) => {
  if (!song) return null;

  return (
    <div className="px-3 pb-2 z-20">
      <div
        onClick={onOpenNowPlaying}
        className="w-full bg-white/90 dark:bg-neutral-800/90 backdrop-blur-xl border border-neutral-200/60 dark:border-neutral-700/60 shadow-lg shadow-neutral-900/5 rounded-2xl p-2.5 flex items-center justify-between cursor-pointer active:scale-[0.99] transition-all relative overflow-hidden group"
      >
        {/* Subtle bottom progress bar */}
        <div className="absolute bottom-0 left-0 right-0 h-1 bg-neutral-200/50 dark:bg-neutral-700/50">
          <div
            className="h-full bg-indigo-600 dark:bg-indigo-400 transition-all duration-300"
            style={{ width: `${Math.min(100, Math.max(0, progressPercent))}%` }}
          />
        </div>

        {/* Thumbnail & Title/Artist */}
        <div className="flex items-center space-x-3 min-w-0 pr-2 flex-1">
          <div className="relative w-11 h-11 rounded-xl overflow-hidden shadow-sm flex-shrink-0 bg-neutral-200 dark:bg-neutral-700">
            <img
              src={song.artworkUrl}
              alt={song.title}
              className={`w-full h-full object-cover transition-transform duration-500 ${
                isPlaying ? 'scale-105' : 'scale-100'
              }`}
            />
          </div>
          <div className="min-w-0 flex-1">
            <h4 className="text-sm font-bold text-neutral-900 dark:text-neutral-100 truncate">
              {song.title}
            </h4>
            <p className="text-xs text-neutral-500 dark:text-neutral-400 truncate">
              {song.artist}
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center space-x-1 flex-shrink-0" onClick={(e) => e.stopPropagation()}>
          <button
            onClick={onTogglePlay}
            aria-label={isPlaying ? 'Pause' : 'Play'}
            className="w-10 h-10 rounded-full flex items-center justify-center text-neutral-900 dark:text-white hover:bg-neutral-100 dark:hover:bg-neutral-700 transition"
          >
            {isPlaying ? (
              <Pause className="w-5 h-5 fill-current" />
            ) : (
              <Play className="w-5 h-5 fill-current ml-0.5" />
            )}
          </button>
          <button
            onClick={onNext}
            aria-label="Next track"
            className="w-10 h-10 rounded-full flex items-center justify-center text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-700 transition"
          >
            <SkipForward className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
};
