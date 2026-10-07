import React, { useState } from 'react';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  ChevronDown,
  ChevronUp,
  Sliders,
  Volume2,
} from 'lucide-react';
import { Song } from '../types/music';

interface AndroidNotificationBannerProps {
  song: Song | null;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  onTogglePlay: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onOpenPlayer: () => void;
}

export const AndroidNotificationBanner: React.FC<AndroidNotificationBannerProps> = ({
  song,
  isPlaying,
  currentTime,
  duration,
  onTogglePlay,
  onPrevious,
  onNext,
  onOpenPlayer,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  if (!song) return null;

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="w-full px-4 pt-1 pb-1">
      <div className="bg-neutral-800/95 text-white rounded-3xl p-3 border border-neutral-700/60 shadow-lg text-xs transition-all">
        {/* Android System Header */}
        <div
          className="flex items-center justify-between cursor-pointer select-none pb-1"
          onClick={() => setIsExpanded(!isExpanded)}
        >
          <div className="flex items-center space-x-2 text-neutral-400">
            <Volume2 className="w-3.5 h-3.5 text-indigo-400" />
            <span className="text-[11px] font-bold text-neutral-300">
              Media3 Foreground Playback Service
            </span>
          </div>
          <button className="text-neutral-400 hover:text-white">
            {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        </div>

        {/* Media Notification Body */}
        <div className="flex items-center space-x-3 pt-1">
          <img
            src={song.artworkUrl}
            alt={song.title}
            onClick={onOpenPlayer}
            className="w-12 h-12 rounded-2xl object-cover cursor-pointer flex-shrink-0"
          />

          <div className="min-w-0 flex-1 cursor-pointer" onClick={onOpenPlayer}>
            <p className="text-xs font-bold text-white truncate">{song.title}</p>
            <p className="text-[11px] text-neutral-400 truncate">{song.artist}</p>
          </div>

          {/* Compact Notification Action Controls */}
          <div className="flex items-center space-x-1 flex-shrink-0">
            <button
              onClick={onPrevious}
              className="p-1.5 rounded-full text-neutral-300 hover:text-white"
              aria-label="Previous"
            >
              <SkipBack className="w-4 h-4" />
            </button>
            <button
              onClick={onTogglePlay}
              className="w-8 h-8 rounded-full bg-white text-neutral-900 flex items-center justify-center hover:scale-105 transition"
              aria-label="Play/Pause"
            >
              {isPlaying ? (
                <Pause className="w-4 h-4 fill-current" />
              ) : (
                <Play className="w-4 h-4 fill-current ml-0.5" />
              )}
            </button>
            <button
              onClick={onNext}
              className="p-1.5 rounded-full text-neutral-300 hover:text-white"
              aria-label="Next"
            >
              <SkipForward className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Expanded Details and Progress */}
        {isExpanded && (
          <div className="mt-3 pt-2 border-t border-neutral-700/60">
            <div className="w-full h-1 bg-neutral-700 rounded-full overflow-hidden mb-2">
              <div
                className="h-full bg-indigo-400"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <div className="flex justify-between text-[10px] text-neutral-400 font-mono">
              <span>Android MediaSession: Active</span>
              <span>Audio Format: {song.format.toUpperCase()}</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
