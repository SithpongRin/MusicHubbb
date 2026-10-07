import { Song } from '../types/music';

// Generates a simple, pleasing audio WAV data URI for local offline playback
function createSyntheticAudioWav(toneFreqs: number[], durationSec: number = 30): string {
  const sampleRate = 22050;
  const numSamples = sampleRate * durationSec;
  const buffer = new ArrayBuffer(44 + numSamples * 2);
  const view = new DataView(buffer);

  // RIFF chunk descriptor
  const writeString = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i));
    }
  };

  writeString(0, 'RIFF');
  view.setUint32(4, 36 + numSamples * 2, true);
  writeString(8, 'WAVE');
  writeString(12, 'fmt ');
  view.setUint32(16, 16, true); // SubChunk1Size (16 for PCM)
  view.setUint16(20, 1, true); // AudioFormat (1 for PCM)
  view.setUint16(22, 1, true); // NumChannels (1 mono)
  view.setUint32(24, sampleRate, true); // SampleRate
  view.setUint32(28, sampleRate * 2, true); // ByteRate
  view.setUint16(32, 2, true); // BlockAlign
  view.setUint16(34, 16, true); // BitsPerSample
  writeString(36, 'data');
  view.setUint32(40, numSamples * 2, true);

  // Generate pleasant harmonic chord arpeggio
  const chordLength = sampleRate * 2; // 2 seconds per chord
  for (let i = 0; i < numSamples; i++) {
    const chordIndex = Math.floor(i / chordLength) % toneFreqs.length;
    const baseFreq = toneFreqs[chordIndex];
    const t = i / sampleRate;

    // Soft envelope to avoid clicking
    const chordT = (i % chordLength) / chordLength;
    const envelope = Math.sin(Math.PI * chordT);

    // Warm harmonics
    const sampleVal = (
      Math.sin(2 * Math.PI * baseFreq * t) * 0.4 +
      Math.sin(2 * Math.PI * (baseFreq * 1.5) * t) * 0.2 +
      Math.sin(2 * Math.PI * (baseFreq * 2.0) * t) * 0.15 +
      Math.sin(2 * Math.PI * (baseFreq * 0.5) * t) * 0.25
    ) * envelope;

    const int16Val = Math.max(-32768, Math.min(32767, Math.floor(sampleVal * 12000)));
    view.setInt16(44 + i * 2, int16Val, true);
  }

  const blob = new Blob([buffer], { type: 'audio/wav' });
  return URL.createObjectURL(blob);
}

// Generate artwork SVG data URIs
function createCoverSvg(gradientColors: [string, string], title: string, artist: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400">
    <defs>
      <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="${gradientColors[0]}" />
        <stop offset="100%" stop-color="${gradientColors[1]}" />
      </linearGradient>
      <radialGradient id="glow" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="rgba(255,255,255,0.18)" />
        <stop offset="100%" stop-color="rgba(0,0,0,0.3)" />
      </radialGradient>
    </defs>
    <rect width="400" height="400" rx="36" fill="url(#grad)" />
    <circle cx="200" cy="180" r="100" fill="url(#glow)" />
    <circle cx="200" cy="180" r="40" fill="none" stroke="rgba(255,255,255,0.4)" stroke-width="4" stroke-dasharray="8 6" />
    <circle cx="200" cy="180" r="16" fill="rgba(255,255,255,0.85)" />
    <path d="M190 280 L210 280 M200 280 L200 320" stroke="rgba(255,255,255,0.4)" stroke-width="3" stroke-linecap="round" />
    <text x="200" y="340" font-family="system-ui, sans-serif" font-size="20" font-weight="700" fill="#ffffff" text-anchor="middle">${title}</text>
    <text x="200" y="365" font-family="system-ui, sans-serif" font-size="14" font-weight="500" fill="rgba(255,255,255,0.75)" text-anchor="middle">${artist}</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

export function getInitialSampleSongs(): Song[] {
  const now = Date.now();

  const song1Url = createSyntheticAudioWav([220, 261.63, 329.63, 392], 42); // Am7, C, Em, G
  const song2Url = createSyntheticAudioWav([174.61, 220, 261.63, 349.23], 38); // Fmaj7
  const song3Url = createSyntheticAudioWav([246.94, 293.66, 369.99, 440], 48); // Bm7
  const song4Url = createSyntheticAudioWav([196, 246.94, 293.66, 392], 40); // Gmaj7

  return [
    {
      id: 'sample-track-1',
      title: 'Midnight Serenade',
      artist: 'Aura Studio',
      album: 'Nocturne Horizon',
      duration: 42,
      audioUrl: song1Url,
      artworkUrl: createCoverSvg(['#1e1b4b', '#4338ca'], 'Midnight Serenade', 'Aura Studio'),
      dateAdded: now - 86400000 * 3,
      playCount: 14,
      lastPlayed: now - 3600000 * 2,
      isFavorite: true,
      format: 'wav',
      bitRate: '320kbps',
      sizeBytes: 1852200,
      isDownloaded: true,
    },
    {
      id: 'sample-track-2',
      title: 'Amber Echoes',
      artist: 'Kroma Sound',
      album: 'Golden Hour Reflections',
      duration: 38,
      audioUrl: song2Url,
      artworkUrl: createCoverSvg(['#7c2d12', '#ea580c'], 'Amber Echoes', 'Kroma Sound'),
      dateAdded: now - 86400000 * 2,
      playCount: 9,
      lastPlayed: now - 3600000 * 8,
      isFavorite: true,
      format: 'wav',
      bitRate: '320kbps',
      sizeBytes: 1675800,
      isDownloaded: true,
    },
    {
      id: 'sample-track-3',
      title: 'Cyber Solitude',
      artist: 'Velvet Synth',
      album: 'Neon Memories',
      duration: 48,
      audioUrl: song3Url,
      artworkUrl: createCoverSvg(['#064e3b', '#059669'], 'Cyber Solitude', 'Velvet Synth'),
      dateAdded: now - 86400000 * 1,
      playCount: 5,
      lastPlayed: now - 86400000,
      isFavorite: false,
      format: 'wav',
      bitRate: '320kbps',
      sizeBytes: 2116800,
      isDownloaded: true,
    },
    {
      id: 'sample-track-4',
      title: 'Celestial Drift',
      artist: 'Luna Pulse',
      album: 'Orbit Chronicles',
      duration: 40,
      audioUrl: song4Url,
      artworkUrl: createCoverSvg(['#3b0764', '#9333ea'], 'Celestial Drift', 'Luna Pulse'),
      dateAdded: now,
      playCount: 2,
      lastPlayed: null,
      isFavorite: false,
      format: 'wav',
      bitRate: '320kbps',
      sizeBytes: 1764000,
      isDownloaded: true,
    },
  ];
}
