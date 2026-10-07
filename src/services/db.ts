import { Song, Playlist, DownloadTask, AppSettings } from '../types/music';

const DB_NAME = 'MusicHubDB';
const DB_VERSION = 1;

class MusicHubDatabase {
  private db: IDBDatabase | null = null;
  private isInitializing: Promise<IDBDatabase> | null = null;

  async init(): Promise<IDBDatabase> {
    if (this.db) return this.db;
    if (this.isInitializing) return this.isInitializing;

    this.isInitializing = new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        
        if (!db.objectStoreNames.contains('songs')) {
          const songStore = db.createObjectStore('songs', { keyPath: 'id' });
          songStore.createIndex('title', 'title', { unique: false });
          songStore.createIndex('artist', 'artist', { unique: false });
          songStore.createIndex('album', 'album', { unique: false });
          songStore.createIndex('isFavorite', 'isFavorite', { unique: false });
          songStore.createIndex('dateAdded', 'dateAdded', { unique: false });
        }

        if (!db.objectStoreNames.contains('playlists')) {
          db.createObjectStore('playlists', { keyPath: 'id' });
        }

        if (!db.objectStoreNames.contains('downloads')) {
          db.createObjectStore('downloads', { keyPath: 'id' });
        }

        if (!db.objectStoreNames.contains('settings')) {
          db.createObjectStore('settings', { keyPath: 'key' });
        }

        if (!db.objectStoreNames.contains('audioBlobs')) {
          db.createObjectStore('audioBlobs', { keyPath: 'songId' });
        }
      };

      request.onsuccess = () => {
        this.db = request.result;
        resolve(request.result);
      };

      request.onerror = () => {
        reject(request.error);
      };
    });

    return this.isInitializing;
  }

  // --- Songs ---
  async getAllSongs(): Promise<Song[]> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('songs', 'readonly');
      const store = transaction.objectStore('songs');
      const request = store.getAll();

      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error);
    });
  }

  async getSong(id: string): Promise<Song | undefined> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('songs', 'readonly');
      const store = transaction.objectStore('songs');
      const request = store.get(id);

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async saveSong(song: Song): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('songs', 'readwrite');
      const store = transaction.objectStore('songs');
      const request = store.put(song);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async deleteSong(id: string): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(['songs', 'audioBlobs'], 'readwrite');
      transaction.objectStore('songs').delete(id);
      transaction.objectStore('audioBlobs').delete(id);

      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  }

  async saveAudioBlob(songId: string, blob: Blob): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('audioBlobs', 'readwrite');
      const store = transaction.objectStore('audioBlobs');
      const request = store.put({ songId, blob });

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async getAudioBlob(songId: string): Promise<Blob | undefined> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('audioBlobs', 'readonly');
      const store = transaction.objectStore('audioBlobs');
      const request = store.get(songId);

      request.onsuccess = () => resolve(request.result?.blob);
      request.onerror = () => reject(request.error);
    });
  }

  // --- Playlists ---
  async getAllPlaylists(): Promise<Playlist[]> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('playlists', 'readonly');
      const store = transaction.objectStore('playlists');
      const request = store.getAll();

      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error);
    });
  }

  async savePlaylist(playlist: Playlist): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('playlists', 'readwrite');
      const store = transaction.objectStore('playlists');
      const request = store.put(playlist);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async deletePlaylist(id: string): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('playlists', 'readwrite');
      const store = transaction.objectStore('playlists');
      const request = store.delete(id);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  // --- Download Tasks ---
  async getAllDownloads(): Promise<DownloadTask[]> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('downloads', 'readonly');
      const store = transaction.objectStore('downloads');
      const request = store.getAll();

      request.onsuccess = () => resolve(request.result || []);
      request.onerror = () => reject(request.error);
    });
  }

  async saveDownload(task: DownloadTask): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('downloads', 'readwrite');
      const store = transaction.objectStore('downloads');
      const request = store.put(task);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  async deleteDownload(id: string): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('downloads', 'readwrite');
      const store = transaction.objectStore('downloads');
      const request = store.delete(id);

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  // --- Settings ---
  async getSettings(): Promise<AppSettings> {
    const db = await this.init();
    return new Promise((resolve) => {
      const transaction = db.transaction('settings', 'readonly');
      const store = transaction.objectStore('settings');
      const request = store.get('app_settings');

      request.onsuccess = () => {
        if (request.result?.value) {
          resolve(request.result.value);
        } else {
          // Defaults
          resolve({
            theme: 'dark',
            language: 'km',
            defaultQuality: '320k',
            wifiOnly: false,
            resumePlayback: true,
            volumeNormalization: false,
            equalizer: {
              enabled: false,
              preset: 'Flat',
              bands: [0, 0, 0, 0, 0],
              bassBoost: 0,
            },
            storageLocation: '/storage/emulated/0/Music/MusicHub',
          });
        }
      };
      request.onerror = () => {
        resolve({
          theme: 'dark',
          language: 'km',
          defaultQuality: '320k',
          wifiOnly: false,
          resumePlayback: true,
          volumeNormalization: false,
          equalizer: {
            enabled: false,
            preset: 'Flat',
            bands: [0, 0, 0, 0, 0],
            bassBoost: 0,
          },
          storageLocation: '/storage/emulated/0/Music/MusicHub',
        });
      };
    });
  }

  async saveSettings(settings: AppSettings): Promise<void> {
    const db = await this.init();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('settings', 'readwrite');
      const store = transaction.objectStore('settings');
      const request = store.put({ key: 'app_settings', value: settings });

      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  // --- Cache & Storage Stats ---
  async getStorageUsage(): Promise<{ songCount: number; estimatedBytes: number }> {
    const songs = await this.getAllSongs();
    const totalBytes = songs.reduce((acc, song) => acc + (song.sizeBytes || 0), 0);
    return {
      songCount: songs.length,
      estimatedBytes: totalBytes,
    };
  }

  async clearTemporaryCache(): Promise<void> {
    // Clear completed download tasks that are finished
    const downloads = await this.getAllDownloads();
    const finished = downloads.filter(d => d.status === 'completed' || d.status === 'cancelled');
    for (const d of finished) {
      await this.deleteDownload(d.id);
    }
  }
}

export const musicDb = new MusicHubDatabase();
