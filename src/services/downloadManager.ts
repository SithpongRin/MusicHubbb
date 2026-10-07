import { DownloadTask, Song } from '../types/music';
import { musicDb } from './db';

export interface MediaQualityOption {
  qualityLabel: string; // '320kbps', '256kbps', '128kbps', 'Lossless'
  format: string; // 'mp3', 'wav', 'flac', 'ogg'
  url: string;
  sizeBytes?: number;
}

export interface MediaInspectionResult {
  isValid: boolean;
  providerName: string;
  title: string;
  artist: string;
  album: string;
  artworkUrl?: string;
  duration?: number;
  qualities: MediaQualityOption[];
  errorMessage?: string;
}

export interface DownloadProvider {
  name: string;
  canHandle(url: string): boolean;
  inspect(url: string): Promise<MediaInspectionResult>;
  download(
    url: string,
    onProgress: (percent: number, downloaded: number, total: number) => void,
    signal?: AbortSignal
  ): Promise<{ blob: Blob; format: string; size: number }>;
}

/**
 * Provider for direct legal audio file links (MP3, WAV, OGG, FLAC, M4A, AAC)
 */
class DirectAudioProvider implements DownloadProvider {
  name = 'Direct Legal Audio Stream';

  canHandle(url: string): boolean {
    try {
      const parsed = new URL(url);
      const pathname = parsed.pathname.toLowerCase();
      return (
        pathname.endsWith('.mp3') ||
        pathname.endsWith('.wav') ||
        pathname.endsWith('.ogg') ||
        pathname.endsWith('.flac') ||
        pathname.endsWith('.m4a') ||
        pathname.endsWith('.aac') ||
        url.includes('audio') ||
        url.includes('music')
      );
    } catch {
      return false;
    }
  }

  async inspect(url: string): Promise<MediaInspectionResult> {
    try {
      const parsed = new URL(url);
      const filename = parsed.pathname.split('/').pop() || 'Unknown Track';
      const cleanName = decodeURIComponent(filename).replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');
      
      const ext = filename.split('.').pop()?.toLowerCase() || 'mp3';

      return {
        isValid: true,
        providerName: this.name,
        title: cleanName || 'Downloaded Audio',
        artist: 'Unknown Artist',
        album: 'Web Downloads',
        artworkUrl: '',
        duration: 0,
        qualities: [
          { qualityLabel: 'Original Quality', format: ext, url: url, sizeBytes: 0 },
        ],
      };
    } catch {
      return {
        isValid: false,
        providerName: this.name,
        title: '',
        artist: '',
        album: '',
        qualities: [],
        errorMessage: 'Invalid URL format.',
      };
    }
  }

  async download(
    url: string,
    onProgress: (percent: number, downloaded: number, total: number) => void,
    signal?: AbortSignal
  ): Promise<{ blob: Blob; format: string; size: number }> {
    const response = await fetch(url, { signal });
    if (!response.ok) {
      throw new Error(`Download failed with status ${response.status}`);
    }

    const contentLength = response.headers.get('content-length');
    const total = contentLength ? parseInt(contentLength, 10) : 0;
    
    if (!response.body) {
      const blob = await response.blob();
      return { blob, format: 'mp3', size: blob.size };
    }

    const reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let received = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value) {
        chunks.push(value);
        received += value.length;
        const percent = total > 0 ? Math.round((received / total) * 100) : 50;
        onProgress(percent, received, total || received);
      }
    }

    const contentType = response.headers.get('content-type') || 'audio/mpeg';
    const blob = new Blob(chunks as unknown as BlobPart[], { type: contentType });
    const format = contentType.includes('wav') ? 'wav' : contentType.includes('ogg') ? 'ogg' : 'mp3';
    return { blob, format, size: blob.size };
  }
}

/**
 * Provider for Internet Archive public domain & legal audio recordings
 */
class ArchiveOrgProvider implements DownloadProvider {
  name = 'Internet Archive (Public Domain)';

  canHandle(url: string): boolean {
    return url.includes('archive.org');
  }

  async inspect(url: string): Promise<MediaInspectionResult> {
    try {
      const parsed = new URL(url);
      const isDirect = parsed.pathname.endsWith('.mp3') || parsed.pathname.endsWith('.flac') || parsed.pathname.endsWith('.ogg');
      
      const segments = parsed.pathname.split('/').filter(Boolean);
      const identifier = segments[1] || 'archive_audio';

      return {
        isValid: true,
        providerName: this.name,
        title: identifier.replace(/[-_]/g, ' '),
        artist: 'Public Domain Archive',
        album: 'Internet Archive Classics',
        artworkUrl: '',
        qualities: [
          { qualityLabel: 'Standard (VBR)', format: 'mp3', url: url },
          { qualityLabel: 'High Quality (320kbps)', format: 'mp3', url: url },
          { qualityLabel: 'Lossless (FLAC)', format: 'flac', url: url },
        ],
      };
    } catch {
      return {
        isValid: false,
        providerName: this.name,
        title: '',
        artist: '',
        album: '',
        qualities: [],
        errorMessage: 'Invalid Internet Archive audio URL.',
      };
    }
  }

  async download(
    url: string,
    onProgress: (percent: number, downloaded: number, total: number) => void,
    signal?: AbortSignal
  ): Promise<{ blob: Blob; format: string; size: number }> {
    // Reuse fetch download logic
    const directProvider = new DirectAudioProvider();
    return directProvider.download(url, onProgress, signal);
  }
}

export class DownloadManager {
  private providers: DownloadProvider[] = [
    new ArchiveOrgProvider(),
    new DirectAudioProvider(),
  ];

  private activeAbortControllers: Map<string, AbortController> = new Map();
  private listeners: Set<() => void> = new Set();

  registerProvider(provider: DownloadProvider) {
    this.providers.unshift(provider);
  }

  findProvider(url: string): DownloadProvider | undefined {
    return this.providers.find(p => p.canHandle(url)) || this.providers[this.providers.length - 1]; // fallback to direct
  }

  async inspectUrl(url: string): Promise<MediaInspectionResult> {
    const trimmed = url.trim();
    if (!trimmed) {
      return {
        isValid: false,
        providerName: 'None',
        title: '',
        artist: '',
        album: '',
        qualities: [],
        errorMessage: 'Please enter a valid URL.',
      };
    }

    const provider = this.findProvider(trimmed);
    if (!provider) {
      return {
        isValid: false,
        providerName: 'None',
        title: '',
        artist: '',
        album: '',
        qualities: [],
        errorMessage: 'No supported legal download provider for this URL.',
      };
    }

    return provider.inspect(trimmed);
  }

  async startDownload(
    inspection: MediaInspectionResult,
    selectedQuality: MediaQualityOption,
    onCompleted?: (song: Song) => void
  ): Promise<DownloadTask> {
    const taskId = `dl-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
    const task: DownloadTask = {
      id: taskId,
      url: selectedQuality.url,
      title: inspection.title || 'Downloaded Song',
      artist: inspection.artist || 'Unknown Artist',
      album: inspection.album || 'Downloaded',
      artworkUrl: inspection.artworkUrl,
      quality: selectedQuality.qualityLabel,
      progress: 0,
      status: 'downloading',
      totalBytes: selectedQuality.sizeBytes || 0,
      downloadedBytes: 0,
      createdAt: Date.now(),
    };

    await musicDb.saveDownload(task);
    this.notify();

    const abortController = new AbortController();
    this.activeAbortControllers.set(taskId, abortController);

    // Execute download asynchronously
    (async () => {
      try {
        const provider = this.findProvider(task.url) || new DirectAudioProvider();

        const result = await provider.download(
          task.url,
          async (percent, downloaded, total) => {
            task.progress = percent;
            task.downloadedBytes = downloaded;
            task.totalBytes = total;
            await musicDb.saveDownload(task);
            this.notify();
          },
          abortController.signal
        );

        // Download succeeded, convert blob to local object URL and persist in Room/IndexedDB
        const audioBlobUrl = URL.createObjectURL(result.blob);
        const songId = `song-${Date.now()}`;
        
        // Generate SVG cover if none provided
        const artworkUrl = inspection.artworkUrl || this.createDefaultCover(task.title, task.artist);

        const newSong: Song = {
          id: songId,
          title: task.title,
          artist: task.artist,
          album: task.album,
          duration: 180, // Default estimate until loaded
          audioUrl: audioBlobUrl,
          audioBlob: result.blob,
          artworkUrl: artworkUrl,
          dateAdded: Date.now(),
          playCount: 0,
          lastPlayed: null,
          isFavorite: false,
          format: result.format,
          bitRate: task.quality,
          sizeBytes: result.size,
          sourceUrl: task.url,
          isDownloaded: true,
        };

        await musicDb.saveSong(newSong);
        await musicDb.saveAudioBlob(songId, result.blob);

        task.status = 'completed';
        task.progress = 100;
        await musicDb.saveDownload(task);
        this.activeAbortControllers.delete(taskId);
        this.notify();

        if (onCompleted) {
          onCompleted(newSong);
        }
      } catch (err: unknown) {
        if (abortController.signal.aborted) {
          task.status = 'cancelled';
        } else {
          task.status = 'failed';
          task.error = err instanceof Error ? err.message : 'Download failed';
        }
        await musicDb.saveDownload(task);
        this.activeAbortControllers.delete(taskId);
        this.notify();
      }
    })();

    return task;
  }

  cancelDownload(taskId: string) {
    const controller = this.activeAbortControllers.get(taskId);
    if (controller) {
      controller.abort();
      this.activeAbortControllers.delete(taskId);
    }
  }

  async retryDownload(task: DownloadTask, onCompleted?: (song: Song) => void) {
    const quality: MediaQualityOption = {
      qualityLabel: task.quality,
      format: 'mp3',
      url: task.url,
    };
    const inspection: MediaInspectionResult = {
      isValid: true,
      providerName: 'Direct Legal Audio Stream',
      title: task.title,
      artist: task.artist,
      album: task.album,
      artworkUrl: task.artworkUrl,
      qualities: [quality],
    };
    return this.startDownload(inspection, quality, onCompleted);
  }

  private createDefaultCover(title: string, artist: string): string {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300">
      <defs>
        <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#4f46e5" />
          <stop offset="100%" stop-color="#06b6d4" />
        </linearGradient>
      </defs>
      <rect width="300" height="300" rx="24" fill="url(#g)" />
      <circle cx="150" cy="130" r="45" fill="none" stroke="rgba(255,255,255,0.6)" stroke-width="4" />
      <circle cx="150" cy="130" r="18" fill="rgba(255,255,255,0.9)" />
      <text x="150" y="220" font-family="system-ui, sans-serif" font-size="16" font-weight="700" fill="#ffffff" text-anchor="middle">${title.substring(0, 20)}</text>
      <text x="150" y="245" font-family="system-ui, sans-serif" font-size="12" font-weight="500" fill="rgba(255,255,255,0.8)" text-anchor="middle">${artist.substring(0, 24)}</text>
    </svg>`;
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
  }

  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach(l => l());
  }
}

export const downloadManager = new DownloadManager();
