import { Song, RepeatMode, EqualizerSettings } from '../types/music';

type AudioListener = () => void;

class AudioPlayerService {
  private audio: HTMLAudioElement;
  private audioContext: AudioContext | null = null;
  private sourceNode: MediaElementAudioSourceNode | null = null;
  private equalizerBands: BiquadFilterNode[] = [];
  private gainNode: GainNode | null = null;
  private isAudioGraphConnected = false;

  private currentSong: Song | null = null;
  private queue: Song[] = [];
  private queueIndex = -1;
  private isPlaying = false;
  private isShuffle = false;
  private repeatMode: RepeatMode = 'off';
  private originalQueue: Song[] = [];

  private listeners: Set<AudioListener> = new Set();
  private duration = 0;
  private currentTime = 0;

  constructor() {
    this.audio = new Audio();
    this.audio.preload = 'metadata';

    this.setupEventListeners();
  }

  private initAudioGraph() {
    if (this.audioContext) return;

    try {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.audioContext = new AudioCtx();

      // Band frequencies: 60Hz (sub-bass), 230Hz (bass), 910Hz (mid), 3600Hz (upper mid), 14000Hz (treble)
      const frequencies = [60, 230, 910, 3600, 14000];
      this.equalizerBands = frequencies.map((freq, index) => {
        const filter = this.audioContext!.createBiquadFilter();
        if (index === 0) {
          filter.type = 'lowshelf';
        } else if (index === frequencies.length - 1) {
          filter.type = 'highshelf';
        } else {
          filter.type = 'peaking';
          filter.Q.value = 1.0;
        }
        filter.frequency.value = freq;
        filter.gain.value = 0;
        return filter;
      });

      this.gainNode = this.audioContext.createGain();
      this.gainNode.gain.value = 1.0;

      // Connect HTMLAudio element to Web Audio Graph
      this.sourceNode = this.audioContext.createMediaElementSource(this.audio);

      // Chain: source -> eq[0] -> eq[1] -> eq[2] -> eq[3] -> eq[4] -> gain -> destination
      let lastNode: AudioNode = this.sourceNode;
      for (const filter of this.equalizerBands) {
        lastNode.connect(filter);
        lastNode = filter;
      }
      lastNode.connect(this.gainNode);
      this.gainNode.connect(this.audioContext.destination);

      this.isAudioGraphConnected = true;
    } catch {
      // Fallback: direct audio without Web Audio API if restricted
      this.isAudioGraphConnected = false;
    }
  }

  private setupEventListeners() {
    this.audio.addEventListener('play', () => {
      this.isPlaying = true;
      this.notifyListeners();
      this.updateMediaSession();
    });

    this.audio.addEventListener('pause', () => {
      this.isPlaying = false;
      this.notifyListeners();
    });

    this.audio.addEventListener('timeupdate', () => {
      this.currentTime = this.audio.currentTime;
      this.notifyListeners();
    });

    this.audio.addEventListener('loadedmetadata', () => {
      this.duration = this.audio.duration || (this.currentSong ? this.currentSong.duration : 0);
      this.notifyListeners();
    });

    this.audio.addEventListener('ended', () => {
      this.handleTrackEnded();
    });

    this.audio.addEventListener('error', () => {
      this.isPlaying = false;
      this.notifyListeners();
    });

    // Native MediaSession Controls for Background & Lock Screen Playback
    if ('mediaSession' in navigator) {
      navigator.mediaSession.setActionHandler('play', () => this.play());
      navigator.mediaSession.setActionHandler('pause', () => this.pause());
      navigator.mediaSession.setActionHandler('previoustrack', () => this.previous());
      navigator.mediaSession.setActionHandler('nexttrack', () => this.next());
      navigator.mediaSession.setActionHandler('seekto', (details) => {
        if (details.seekTime !== undefined) {
          this.seekTo(details.seekTime);
        }
      });
      navigator.mediaSession.setActionHandler('seekbackward', (details) => {
        const offset = details.seekOffset || 10;
        this.seekTo(Math.max(0, this.audio.currentTime - offset));
      });
      navigator.mediaSession.setActionHandler('seekforward', (details) => {
        const offset = details.seekOffset || 10;
        this.seekTo(Math.min(this.duration, this.audio.currentTime + offset));
      });
    }
  }

  private updateMediaSession() {
    if (!('mediaSession' in navigator) || !this.currentSong) return;

    navigator.mediaSession.metadata = new MediaMetadata({
      title: this.currentSong.title,
      artist: this.currentSong.artist,
      album: this.currentSong.album,
      artwork: [
        { src: this.currentSong.artworkUrl, sizes: '512x512', type: 'image/png' },
      ],
    });
  }

  private handleTrackEnded() {
    if (this.repeatMode === 'one') {
      this.seekTo(0);
      this.play();
    } else if (this.queueIndex < this.queue.length - 1) {
      this.next();
    } else if (this.repeatMode === 'all') {
      this.playTrackAtIndex(0);
    } else {
      this.isPlaying = false;
      this.notifyListeners();
    }
  }

  // Playback commands
  async playSong(song: Song, queueList?: Song[]) {
    this.initAudioGraph();
    if (this.audioContext && this.audioContext.state === 'suspended') {
      await this.audioContext.resume();
    }

    if (queueList && queueList.length > 0) {
      this.originalQueue = [...queueList];
      if (this.isShuffle) {
        this.queue = this.shuffleList(queueList, song.id);
      } else {
        this.queue = [...queueList];
      }
      this.queueIndex = this.queue.findIndex(s => s.id === song.id);
      if (this.queueIndex === -1) {
        this.queue.unshift(song);
        this.queueIndex = 0;
      }
    } else {
      this.queue = [song];
      this.originalQueue = [song];
      this.queueIndex = 0;
    }

    this.currentSong = song;
    this.audio.src = song.audioUrl;
    this.audio.load();

    try {
      await this.audio.play();
      this.isPlaying = true;
    } catch {
      this.isPlaying = false;
    }

    this.notifyListeners();
    this.updateMediaSession();
  }

  async play() {
    if (!this.currentSong && this.queue.length > 0) {
      await this.playTrackAtIndex(0);
      return;
    }

    if (this.audioContext && this.audioContext.state === 'suspended') {
      await this.audioContext.resume();
    }

    try {
      await this.audio.play();
      this.isPlaying = true;
      this.notifyListeners();
    } catch {
      // Audio playback interrupted
    }
  }

  pause() {
    this.audio.pause();
    this.isPlaying = false;
    this.notifyListeners();
  }

  togglePlayPause() {
    if (this.isPlaying) {
      this.pause();
    } else {
      this.play();
    }
  }

  async next() {
    if (this.queue.length === 0) return;

    if (this.queueIndex < this.queue.length - 1) {
      await this.playTrackAtIndex(this.queueIndex + 1);
    } else if (this.repeatMode === 'all') {
      await this.playTrackAtIndex(0);
    }
  }

  async previous() {
    if (this.queue.length === 0) return;

    // If more than 3 seconds in, restart track
    if (this.audio.currentTime > 3) {
      this.seekTo(0);
      return;
    }

    if (this.queueIndex > 0) {
      await this.playTrackAtIndex(this.queueIndex - 1);
    } else {
      this.seekTo(0);
    }
  }

  async playTrackAtIndex(index: number) {
    if (index >= 0 && index < this.queue.length) {
      const song = this.queue[index];
      this.queueIndex = index;
      this.currentSong = song;
      this.audio.src = song.audioUrl;
      this.audio.load();
      try {
        await this.audio.play();
        this.isPlaying = true;
      } catch {
        this.isPlaying = false;
      }
      this.notifyListeners();
      this.updateMediaSession();
    }
  }

  seekTo(seconds: number) {
    if (isFinite(seconds)) {
      this.audio.currentTime = Math.max(0, Math.min(seconds, this.duration || 999999));
      this.currentTime = this.audio.currentTime;
      this.notifyListeners();
    }
  }

  setVolume(volume: number) {
    this.audio.volume = Math.max(0, Math.min(1, volume));
    this.notifyListeners();
  }

  toggleShuffle() {
    this.isShuffle = !this.isShuffle;
    if (this.currentSong) {
      if (this.isShuffle) {
        this.queue = this.shuffleList(this.originalQueue, this.currentSong.id);
        this.queueIndex = 0;
      } else {
        this.queue = [...this.originalQueue];
        this.queueIndex = this.queue.findIndex(s => s.id === this.currentSong?.id);
      }
    }
    this.notifyListeners();
  }

  toggleRepeat() {
    if (this.repeatMode === 'off') {
      this.repeatMode = 'all';
    } else if (this.repeatMode === 'all') {
      this.repeatMode = 'one';
    } else {
      this.repeatMode = 'off';
    }
    this.notifyListeners();
  }

  private shuffleList(list: Song[], keepFirstId?: string): Song[] {
    const copy = [...list];
    const firstSong = keepFirstId ? copy.find(s => s.id === keepFirstId) : null;
    const remaining = keepFirstId ? copy.filter(s => s.id !== keepFirstId) : copy;

    for (let i = remaining.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [remaining[i], remaining[j]] = [remaining[j], remaining[i]];
    }

    return firstSong ? [firstSong, ...remaining] : remaining;
  }

  // Queue manipulation
  reorderQueue(fromIndex: number, toIndex: number) {
    const item = this.queue.splice(fromIndex, 1)[0];
    this.queue.splice(toIndex, 0, item);
    const cur = this.currentSong;
    if (cur) {
      this.queueIndex = this.queue.findIndex(s => s.id === cur.id);
    }
    this.notifyListeners();
  }

  removeFromQueue(index: number) {
    if (index === this.queueIndex) {
      if (this.queue.length > 1) {
        this.next();
      } else {
        this.pause();
        this.currentSong = null;
      }
    }
    this.queue.splice(index, 1);
    const cur = this.currentSong;
    if (cur) {
      this.queueIndex = this.queue.findIndex(s => s.id === cur.id);
    }
    this.notifyListeners();
  }

  clearQueue() {
    this.pause();
    this.currentSong = null;
    this.queue = [];
    this.queueIndex = -1;
    this.notifyListeners();
  }

  // Equalizer adjustments
  applyEqualizer(settings: EqualizerSettings) {
    this.initAudioGraph();
    if (!this.isAudioGraphConnected) return;

    for (let i = 0; i < this.equalizerBands.length; i++) {
      const gain = settings.enabled ? (settings.bands[i] || 0) : 0;
      this.equalizerBands[i].gain.setTargetAtTime(gain, this.audioContext!.currentTime, 0.05);
    }

    if (this.gainNode) {
      // Bass boost affects lowest band
      if (settings.enabled && settings.bassBoost > 0) {
        const extraBass = (settings.bassBoost / 100) * 6; // up to +6dB
        this.equalizerBands[0].gain.setTargetAtTime(
          (settings.bands[0] || 0) + extraBass,
          this.audioContext!.currentTime,
          0.05
        );
      }
    }
  }

  setVolumeNormalization(enabled: boolean) {
    if (this.gainNode && this.audioContext) {
      const targetGain = enabled ? 0.85 : 1.0;
      this.gainNode.gain.setTargetAtTime(targetGain, this.audioContext.currentTime, 0.05);
    }
  }

  // State Getters
  getCurrentSong() { return this.currentSong; }
  getIsPlaying() { return this.isPlaying; }
  getCurrentTime() { return this.currentTime; }
  getDuration() { return this.duration; }
  getQueue() { return this.queue; }
  getQueueIndex() { return this.queueIndex; }
  getIsShuffle() { return this.isShuffle; }
  getRepeatMode() { return this.repeatMode; }
  getVolume() { return this.audio.volume; }

  // Subscription
  subscribe(listener: AudioListener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notifyListeners() {
    this.listeners.forEach(listener => listener());
  }
}

export const audioPlayer = new AudioPlayerService();
