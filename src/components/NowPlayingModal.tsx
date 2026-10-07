import React, { useState } from 'react';
import {
  ChevronDown,
  Heart,
  Shuffle,
  Repeat,
  Repeat1,
  SkipBack,
  SkipForward,
  Play,
  Pause,
  ListMusic,
  Sliders,
  Trash2,
  X,
} from 'lucide-react';
import { Song, RepeatMode } from '../types/music';

interface NowPlayingModalProps {
  isOpen: boolean;
  onClose: () => void;
  song: Song | null;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  isShuffle: boolean;
  repeatMode: RepeatMode;
  queue: Song[];
  queueIndex: number;
  onTogglePlay: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onSeek: (seconds: number) => void;
  onToggleShuffle: () => void;
  onToggleRepeat: () => void;
  onToggleFavorite: (song: Song) => void;
  onSelectQueueSong: (index: number) => void;
  onRemoveFromQueue: (index: number) => void;
  onOpenEqualizer: () => void;
}

function formatTime(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

export const NowPlayingModal: React.FC<NowPlayingModalProps> = ({
  isOpen,
  onClose,
  song,
  isPlaying,
  currentTime,
  duration,
  isShuffle,
  repeatMode,
  queue,
  queueIndex,
  onTogglePlay,
  onPrevious,
  onNext,
  onSeek,
  onToggleShuffle,
  onToggleRepeat,
  onToggleFavorite,
  onSelectQueueSong,
  onRemoveFromQueue,
  onOpenEqualizer,
}) => {
  const [showQueue, setShowQueue] = useState(false);
  const [isSeeking, setIsSeeking] = useState(false);
  const [seekValue, setSeekValue] = useState(0);

  if (!isOpen || !song) return null;

  const currentSeekTime = isSeeking ? seekValue : currentTime;
  const progressPercent = duration > 0 ? (currentSeekTime / duration) * 100 : 0;

  // Waveform bars simulation
  const waveformBars = 36;

  return (
    <div className="absolute inset-0 z-50 flex flex-col bg-neutral-900 text-white animate-in fade-in duration-200 overflow-y-auto sm:overflow-hidden select-none">
      {/* Top Header Bar */}
      <div className="flex items-center justify-between px-6 pt-4 pb-2 flex-shrink-0">
        <button
          onClick={onClose}
          className="w-9 h-9 rounded-full flex items-center justify-center text-neutral-300 hover:text-white hover:bg-white/10 active:scale-95 transition"
          aria-label="Collapse player"
        >
          <ChevronDown className="w-5 h-5" />
        </button>

        <div className="text-center">
          <span className="text-[10px] uppercase tracking-widest text-neutral-400 font-semibold">
            Now Playing
          </span>
          <p className="text-xs text-neutral-300 font-medium truncate max-w-[180px]">
            {song.album || 'MusicHub'}
          </p>
        </div>

        <button
          onClick={onOpenEqualizer}
          className="w-9 h-9 rounded-full flex items-center justify-center text-neutral-300 hover:text-white hover:bg-white/10 active:scale-95 transition"
          aria-label="Equalizer"
        >
          <Sliders className="w-4 h-4" />
        </button>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col justify-between px-6 py-2 max-w-md mx-auto w-full min-h-0">
        {/* Large Album Artwork - Responsively Sized */}
        <div className="w-52 h-52 sm:w-60 sm:h-60 max-h-[28vh] aspect-square mx-auto my-auto relative rounded-3xl overflow-hidden shadow-2xl shadow-black/60 border border-white/10 bg-neutral-800 flex-shrink-0">
          <img
            src={song.artworkUrl}
            alt={song.title}
            className={`w-full h-full object-cover transition-transform duration-700 ${
              isPlaying ? 'scale-105' : 'scale-100'
            }`}
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent pointer-events-none" />
        </div>

        {/* Title, Artist & Favorite Button */}
        <div className="pt-2 flex-shrink-0">
          <div className="flex items-center justify-between">
            <div className="min-w-0 pr-4 flex-1">
              <h2 className="text-xl font-bold text-white tracking-tight truncate">
                {song.title}
              </h2>
              <p className="text-sm text-neutral-400 font-medium truncate mt-0.5">
                {song.artist}
              </p>
            </div>
            <button
              onClick={() => onToggleFavorite(song)}
              className={`w-10 h-10 rounded-full flex items-center justify-center transition active:scale-90 ${
                song.isFavorite
                  ? 'text-rose-500 bg-rose-500/10'
                  : 'text-neutral-400 hover:text-white bg-white/5'
              }`}
              aria-label="Toggle favorite"
            >
              <Heart
                className={`w-5 h-5 ${song.isFavorite ? 'fill-current' : ''}`}
              />
            </button>
          </div>
        </div>

        {/* Waveform-inspired Progress Scrubber */}
        <div className="pt-2 flex-shrink-0">
          {/* Animated decorative waveform */}
          <div className="flex items-end justify-between h-6 mb-1.5 px-1 gap-[2px] opacity-75">
            {Array.from({ length: waveformBars }).map((_, i) => {
              const active = (i / waveformBars) * 100 <= progressPercent;
              const heightPercent = 20 + ((Math.sin(i * 0.7) + 1) * 35) + ((i % 3) * 10);
              return (
                <div
                  key={i}
                  className={`w-full rounded-full transition-all duration-150 ${
                    active ? 'bg-indigo-400' : 'bg-neutral-700'
                  } ${isPlaying ? 'animate-pulse' : ''}`}
                  style={{
                    height: `${heightPercent}%`,
                    animationDelay: `${(i % 5) * 100}ms`,
                  }}
                />
              );
            })}
          </div>

          {/* Interactive Progress Slider */}
          <div className="relative flex items-center">
            <input
              type="range"
              min={0}
              max={duration || 100}
              step={0.5}
              value={currentSeekTime}
              onMouseDown={() => setIsSeeking(true)}
              onTouchStart={() => setIsSeeking(true)}
              onChange={(e) => setSeekValue(parseFloat(e.target.value))}
              onMouseUp={() => {
                setIsSeeking(false);
                onSeek(seekValue);
              }}
              onTouchEnd={() => {
                setIsSeeking(false);
                onSeek(seekValue);
              }}
              className="w-full h-1.5 bg-neutral-700 rounded-lg appearance-none cursor-pointer accent-indigo-500"
            />
          </div>

          {/* Time stamps */}
          <div className="flex items-center justify-between text-[11px] font-semibold text-neutral-400 mt-1 px-1">
            <span>{formatTime(currentSeekTime)}</span>
            <span>{formatTime(duration)}</span>
          </div>
        </div>

        {/* Playback Controls Bar */}
        <div className="pt-2 pb-1 flex-shrink-0">
          <div className="flex items-center justify-between">
            {/* Shuffle */}
            <button
              onClick={onToggleShuffle}
              className={`w-9 h-9 rounded-full flex items-center justify-center transition active:scale-95 ${
                isShuffle ? 'text-indigo-400 bg-indigo-500/20' : 'text-neutral-400 hover:text-white'
              }`}
              aria-label="Toggle shuffle"
            >
              <Shuffle className="w-4 h-4" />
            </button>

            {/* Previous */}
            <button
              onClick={onPrevious}
              className="w-11 h-11 rounded-full flex items-center justify-center text-white hover:bg-white/10 active:scale-90 transition"
              aria-label="Previous track"
            >
              <SkipBack className="w-6 h-6" />
            </button>

            {/* Play / Pause button */}
            <button
              onClick={onTogglePlay}
              className="w-14 h-14 rounded-full bg-white text-neutral-900 flex items-center justify-center shadow-lg shadow-white/20 active:scale-95 transition"
              aria-label={isPlaying ? 'Pause' : 'Play'}
            >
              {isPlaying ? (
                <Pause className="w-6 h-6 fill-current" />
              ) : (
                <Play className="w-6 h-6 fill-current ml-0.5" />
              )}
            </button>

            {/* Next */}
            <button
              onClick={onNext}
              className="w-11 h-11 rounded-full flex items-center justify-center text-white hover:bg-white/10 active:scale-90 transition"
              aria-label="Next track"
            >
              <SkipForward className="w-6 h-6" />
            </button>

            {/* Repeat */}
            <button
              onClick={onToggleRepeat}
              className={`w-9 h-9 rounded-full flex items-center justify-center transition active:scale-95 ${
                repeatMode !== 'off'
                  ? 'text-indigo-400 bg-indigo-500/20'
                  : 'text-neutral-400 hover:text-white'
              }`}
              aria-label="Toggle repeat"
            >
              {repeatMode === 'one' ? (
                <Repeat1 className="w-4 h-4" />
              ) : (
                <Repeat className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>

        {/* Bottom Bar: Queue Drawer Toggle */}
        <div className="pb-3 pt-1 flex items-center justify-center flex-shrink-0">
          <button
            onClick={() => setShowQueue(true)}
            className="flex items-center space-x-1.5 px-4 py-1.5 rounded-full bg-white/10 hover:bg-white/15 active:scale-95 transition text-[11px] font-semibold text-neutral-300"
          >
            <ListMusic className="w-3.5 h-3.5" />
            <span>Playing Queue ({queue.length})</span>
          </button>
        </div>
      </div>

      {/* Slide-over Queue Modal */}
      {showQueue && (
        <div className="absolute inset-0 z-60 bg-black/80 backdrop-blur-md flex flex-col justify-end animate-in fade-in">
          <div className="bg-neutral-900 border-t border-neutral-800 rounded-t-3xl max-h-[80vh] flex flex-col p-6 animate-in slide-in-from-bottom duration-200">
            <div className="flex items-center justify-between pb-4 border-b border-neutral-800">
              <div className="flex items-center space-x-2">
                <ListMusic className="w-5 h-5 text-indigo-400" />
                <h3 className="text-base font-bold text-white">Queue ({queue.length})</h3>
              </div>
              <button
                onClick={() => setShowQueue(false)}
                className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-white bg-white/10"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-2 space-y-2 max-h-[60vh]">
              {queue.map((item, idx) => {
                const isCurrent = idx === queueIndex;
                return (
                  <div
                    key={`${item.id}-${idx}`}
                    onClick={() => {
                      onSelectQueueSong(idx);
                      setShowQueue(false);
                    }}
                    className={`flex items-center justify-between p-2.5 rounded-2xl cursor-pointer transition ${
                      isCurrent
                        ? 'bg-indigo-600/20 border border-indigo-500/40'
                        : 'hover:bg-white/5'
                    }`}
                  >
                    <div className="flex items-center space-x-3 min-w-0 flex-1">
                      <span className="text-xs font-bold text-neutral-500 w-5 text-center">
                        {idx + 1}
                      </span>
                      <img
                        src={item.artworkUrl}
                        alt={item.title}
                        className="w-10 h-10 rounded-xl object-cover"
                      />
                      <div className="min-w-0 flex-1">
                        <p
                          className={`text-sm font-semibold truncate ${
                            isCurrent ? 'text-indigo-400' : 'text-white'
                          }`}
                        >
                          {item.title}
                        </p>
                        <p className="text-xs text-neutral-400 truncate">
                          {item.artist}
                        </p>
                      </div>
                    </div>

                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onRemoveFromQueue(idx);
                      }}
                      className="p-2 text-neutral-500 hover:text-rose-400 transition"
                      aria-label="Remove from queue"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
