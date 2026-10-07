import React, { useState } from 'react';
import {
  Search,
  Play,
  Heart,
  Plus,
  DownloadCloud,
  Clock,
  Sparkles,
  TrendingUp,
  X,
} from 'lucide-react';
import { Song, Playlist, Language } from '../types/music';
import { translations } from '../i18n/translations';

interface HomeScreenProps {
  songs: Song[];
  playlists: Playlist[];
  currentSong: Song | null;
  isPlaying: boolean;
  lang: Language;
  onPlaySong: (song: Song, queue?: Song[]) => void;
  onTogglePlay: () => void;
  onToggleFavorite: (song: Song) => void;
  onOpenDownload: () => void;
  onOpenImport: () => void;
  onSelectPlaylist: (playlist: Playlist) => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  songs,
  playlists,
  currentSong,
  isPlaying,
  lang,
  onPlaySong,
  onTogglePlay,
  onToggleFavorite,
  onOpenDownload,
  onOpenImport,
  onSelectPlaylist,
}) => {
  const t = translations[lang];
  const [searchQuery, setSearchQuery] = useState('');

  // Filtering for search
  const isSearching = searchQuery.trim().length > 0;
  const filteredSongs = songs.filter((s) => {
    const q = searchQuery.toLowerCase();
    return (
      s.title.toLowerCase().includes(q) ||
      s.artist.toLowerCase().includes(q) ||
      s.album.toLowerCase().includes(q)
    );
  });

  const filteredPlaylists = playlists.filter((p) =>
    p.title.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Categorized songs
  const recentlyPlayed = [...songs]
    .filter((s) => s.lastPlayed !== null)
    .sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0))
    .slice(0, 8);

  const mostPlayed = [...songs]
    .filter((s) => s.playCount > 0)
    .sort((a, b) => b.playCount - a.playCount)
    .slice(0, 8);

  const recentlyAdded = [...songs]
    .sort((a, b) => b.dateAdded - a.dateAdded)
    .slice(0, 8);

  const favorites = songs.filter((s) => s.isFavorite);

  // Continue listening candidate
  const continueListeningSong = currentSong || recentlyPlayed[0] || songs[0];

  return (
    <div className="flex-1 overflow-y-auto px-5 py-4 pb-28">
      {/* Header and Branding */}
      <div className="flex items-center justify-between pt-1 pb-4">
        <div>
          <h1 className="text-2xl font-black text-neutral-900 dark:text-white tracking-tight">
            {t.homeTitle}
          </h1>
          <p className="text-xs font-semibold text-neutral-400 mt-0.5">
            {t.homeSubtitle}
          </p>
        </div>

        {/* Quick action shortcuts */}
        <div className="flex items-center space-x-2">
          <button
            onClick={onOpenImport}
            className="w-9 h-9 rounded-full bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-200 flex items-center justify-center hover:bg-neutral-200 dark:hover:bg-neutral-700 transition"
            aria-label="Add music"
            title="Import audio"
          >
            <Plus className="w-4 h-4" />
          </button>
          <button
            onClick={onOpenDownload}
            className="w-9 h-9 rounded-full bg-indigo-600 text-white flex items-center justify-center hover:bg-indigo-700 transition shadow-sm shadow-indigo-600/30"
            aria-label="Download music"
            title="Download permitted audio"
          >
            <DownloadCloud className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Prominent Search Bar */}
      <div className="relative my-3">
        <Search className="w-4 h-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={t.searchPlaceholder}
          className="w-full pl-10 pr-9 py-2.5 rounded-2xl bg-neutral-100 dark:bg-neutral-800/80 border border-transparent focus:border-indigo-500 text-neutral-900 dark:text-white text-xs font-semibold focus:outline-none transition"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600 dark:hover:text-neutral-200"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* If Searching, show search results */}
      {isSearching ? (
        <div className="mt-4 space-y-4">
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2">
              Songs ({filteredSongs.length})
            </h3>
            {filteredSongs.length === 0 ? (
              <p className="text-xs text-neutral-400 py-3">No matching songs.</p>
            ) : (
              <div className="space-y-1.5">
                {filteredSongs.map((song) => (
                  <div
                    key={song.id}
                    onClick={() => onPlaySong(song, filteredSongs)}
                    className="flex items-center justify-between p-2 rounded-2xl hover:bg-neutral-100 dark:hover:bg-neutral-800/60 cursor-pointer transition"
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
                          {song.artist} • {song.album}
                        </p>
                      </div>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onToggleFavorite(song);
                      }}
                      className="p-2 text-neutral-400 hover:text-rose-500"
                    >
                      <Heart
                        className={`w-4 h-4 ${
                          song.isFavorite ? 'text-rose-500 fill-current' : ''
                        }`}
                      />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {filteredPlaylists.length > 0 && (
            <div>
              <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2">
                Playlists ({filteredPlaylists.length})
              </h3>
              <div className="space-y-1.5">
                {filteredPlaylists.map((pl) => (
                  <div
                    key={pl.id}
                    onClick={() => onSelectPlaylist(pl)}
                    className="flex items-center space-x-3 p-2 rounded-2xl hover:bg-neutral-100 dark:hover:bg-neutral-800/60 cursor-pointer transition"
                  >
                    <img
                      src={pl.artworkUrl}
                      alt={pl.title}
                      className="w-10 h-10 rounded-xl object-cover"
                    />
                    <div>
                      <p className="text-xs font-bold text-neutral-900 dark:text-white">
                        {pl.title}
                      </p>
                      <p className="text-[11px] text-neutral-400">
                        {pl.songIds.length} tracks
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      ) : (
        /* Regular Home Screen Content */
        <div className="space-y-6 mt-2">
          {/* Empty State when no songs exist */}
          {songs.length === 0 ? (
            <div className="py-12 flex flex-col items-center text-center px-4">
              <div className="w-16 h-16 rounded-3xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mb-4">
                <Sparkles className="w-8 h-8" />
              </div>
              <h3 className="text-base font-bold text-neutral-900 dark:text-white">
                {t.emptyHomeTitle}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-neutral-400 max-w-xs mt-1.5 leading-relaxed">
                {t.emptyHomeDesc}
              </p>
              <div className="flex items-center space-x-3 mt-6">
                <button
                  onClick={onOpenImport}
                  className="px-4 py-2.5 rounded-2xl bg-neutral-200 dark:bg-neutral-800 text-neutral-900 dark:text-white font-bold text-xs hover:bg-neutral-300 dark:hover:bg-neutral-700 transition"
                >
                  {t.addMusicBtn}
                </button>
                <button
                  onClick={onOpenDownload}
                  className="px-4 py-2.5 rounded-2xl bg-indigo-600 text-white font-bold text-xs hover:bg-indigo-700 transition shadow-md shadow-indigo-600/20"
                >
                  {t.downloadMusicBtn}
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* Continue Listening Hero Card */}
              {continueListeningSong && (
                <div>
                  <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2.5">
                    {t.continueListening}
                  </h3>
                  <div
                    onClick={() => {
                      if (currentSong?.id === continueListeningSong.id) {
                        onTogglePlay();
                      } else {
                        onPlaySong(continueListeningSong, songs);
                      }
                    }}
                    className="p-4 rounded-3xl bg-neutral-900 text-white dark:bg-neutral-800/90 shadow-xl shadow-neutral-900/10 flex items-center justify-between cursor-pointer active:scale-[0.99] transition border border-neutral-800"
                  >
                    <div className="flex items-center space-x-3.5 min-w-0 flex-1">
                      <img
                        src={continueListeningSong.artworkUrl}
                        alt={continueListeningSong.title}
                        className="w-14 h-14 rounded-2xl object-cover flex-shrink-0 shadow-md"
                      />
                      <div className="min-w-0 flex-1">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-indigo-400">
                          Resuming
                        </span>
                        <h4 className="text-sm font-bold truncate text-white mt-0.5">
                          {continueListeningSong.title}
                        </h4>
                        <p className="text-xs text-neutral-400 truncate mt-0.5">
                          {continueListeningSong.artist}
                        </p>
                      </div>
                    </div>
                    <button
                      className="w-11 h-11 rounded-full bg-white text-neutral-900 flex items-center justify-center flex-shrink-0 ml-3 shadow-md hover:scale-105 transition"
                      aria-label="Play continue track"
                    >
                      {currentSong?.id === continueListeningSong.id && isPlaying ? (
                        <span className="w-3.5 h-3.5 bg-neutral-900 rounded-xs" />
                      ) : (
                        <Play className="w-5 h-5 fill-current ml-0.5" />
                      )}
                    </button>
                  </div>
                </div>
              )}

              {/* Favorites Carousel */}
              {favorites.length > 0 && (
                <div>
                  <div className="flex items-center justify-between mb-2.5">
                    <div className="flex items-center space-x-1.5">
                      <Heart className="w-4 h-4 text-rose-500 fill-current" />
                      <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400">
                        {t.favorites} ({favorites.length})
                      </h3>
                    </div>
                  </div>
                  <div className="flex space-x-3 overflow-x-auto pb-2 -mx-5 px-5 no-scrollbar">
                    {favorites.map((song) => (
                      <div
                        key={song.id}
                        onClick={() => onPlaySong(song, favorites)}
                        className="flex-shrink-0 w-32 cursor-pointer group"
                      >
                        <div className="w-32 h-32 rounded-2xl overflow-hidden shadow-sm bg-neutral-200 dark:bg-neutral-800 relative">
                          <img
                            src={song.artworkUrl}
                            alt={song.title}
                            className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                          />
                          <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 flex items-center justify-center transition">
                            <Play className="w-7 h-7 text-white fill-current" />
                          </div>
                        </div>
                        <p className="text-xs font-bold text-neutral-900 dark:text-white truncate mt-2">
                          {song.title}
                        </p>
                        <p className="text-[11px] text-neutral-400 truncate">
                          {song.artist}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Recently Played */}
              {recentlyPlayed.length > 0 && (
                <div>
                  <div className="flex items-center space-x-1.5 mb-2.5">
                    <Clock className="w-4 h-4 text-indigo-500" />
                    <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400">
                      {t.recentlyPlayed}
                    </h3>
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    {recentlyPlayed.slice(0, 4).map((song) => (
                      <div
                        key={song.id}
                        onClick={() => onPlaySong(song, recentlyPlayed)}
                        className="p-2.5 rounded-2xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 flex items-center space-x-2.5 cursor-pointer hover:shadow-sm transition"
                      >
                        <img
                          src={song.artworkUrl}
                          alt={song.title}
                          className="w-10 h-10 rounded-xl object-cover flex-shrink-0"
                        />
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                            {song.title}
                          </p>
                          <p className="text-[10px] text-neutral-400 truncate">
                            {song.artist}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Most Played */}
              {mostPlayed.length > 0 && (
                <div>
                  <div className="flex items-center space-x-1.5 mb-2.5">
                    <TrendingUp className="w-4 h-4 text-amber-500" />
                    <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400">
                      {t.mostPlayed}
                    </h3>
                  </div>
                  <div className="space-y-1.5">
                    {mostPlayed.slice(0, 4).map((song, idx) => (
                      <div
                        key={song.id}
                        onClick={() => onPlaySong(song, mostPlayed)}
                        className="p-2.5 rounded-2xl hover:bg-neutral-100 dark:hover:bg-neutral-800/60 flex items-center justify-between cursor-pointer transition"
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
                            <p className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                              {song.title}
                            </p>
                            <p className="text-[11px] text-neutral-400 truncate">
                              {song.artist}
                            </p>
                          </div>
                        </div>
                        <span className="text-[11px] font-semibold text-neutral-400">
                          {song.playCount} plays
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Recently Added */}
              {recentlyAdded.length > 0 && (
                <div>
                  <div className="flex items-center space-x-1.5 mb-2.5">
                    <Sparkles className="w-4 h-4 text-emerald-500" />
                    <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400">
                      {t.recentlyAdded}
                    </h3>
                  </div>
                  <div className="space-y-1.5">
                    {recentlyAdded.slice(0, 4).map((song) => (
                      <div
                        key={song.id}
                        onClick={() => onPlaySong(song, recentlyAdded)}
                        className="p-2.5 rounded-2xl hover:bg-neutral-100 dark:hover:bg-neutral-800/60 flex items-center justify-between cursor-pointer transition"
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
                        <span className="text-[10px] font-mono text-neutral-400 uppercase">
                          {song.format}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
};
