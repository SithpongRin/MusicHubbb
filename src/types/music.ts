export interface Song {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number; // in seconds
  audioUrl: string;
  audioBlob?: Blob;
  artworkUrl: string;
  artworkBlob?: Blob;
  dateAdded: number; // timestamp
  playCount: number;
  lastPlayed: number | null; // timestamp
  isFavorite: boolean;
  format: string; // 'mp3', 'flac', 'wav', 'm4a', 'aac', 'ogg'
  bitRate?: string; // '320kbps', etc.
  sizeBytes: number;
  sourceUrl?: string;
  isDownloaded: boolean;
}

export interface Playlist {
  id: string;
  title: string;
  description?: string;
  artworkUrl?: string;
  artworkBlob?: Blob;
  songIds: string[];
  createdAt: number;
  updatedAt: number;
}

export type DownloadStatus = 'pending' | 'downloading' | 'paused' | 'completed' | 'failed' | 'cancelled';

export interface DownloadTask {
  id: string;
  url: string;
  title: string;
  artist: string;
  album: string;
  artworkUrl?: string;
  quality: string;
  progress: number; // 0 to 100
  status: DownloadStatus;
  error?: string;
  totalBytes: number;
  downloadedBytes: number;
  createdAt: number;
}

export type ThemeMode = 'system' | 'light' | 'dark';
export type Language = 'en' | 'km';
export type RepeatMode = 'off' | 'all' | 'one';

export interface EqualizerSettings {
  enabled: boolean;
  preset: string;
  bands: number[]; // 5 bands in dB (-12 to +12)
  bassBoost: number; // 0 to 100
}

export interface AppSettings {
  theme: ThemeMode;
  language: Language;
  defaultQuality: '128k' | '192k' | '256k' | '320k';
  wifiOnly: boolean;
  resumePlayback: boolean;
  volumeNormalization: boolean;
  equalizer: EqualizerSettings;
  storageLocation: string;
}

export interface GitHubRelease {
  tagName: string;
  versionName: string;
  versionCode: number;
  name: string;
  body: string;
  publishedAt: string;
  apkDownloadUrl: string;
  apkSize: number;
}
