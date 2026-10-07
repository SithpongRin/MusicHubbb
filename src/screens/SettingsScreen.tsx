import React, { useState } from 'react';
import {
  Moon,
  Sun,
  Globe,
  Sliders,
  HardDrive,
  RefreshCw,
  Trash2,
  Sparkles,
  Github,
  Info,
  Check,
  Shield,
  Volume2,
} from 'lucide-react';
import { AppSettings, Language, ThemeMode } from '../types/music';
import { translations } from '../i18n/translations';
import { CURRENT_VERSION_NAME } from '../services/githubUpdateService';

interface SettingsScreenProps {
  settings: AppSettings;
  storageUsage: { songCount: number; estimatedBytes: number };
  onUpdateSettings: (newSettings: Partial<AppSettings>) => void;
  onClearCache: () => void;
  onRescanLibrary: () => void;
  onOpenEqualizer: () => void;
  onCheckUpdates: () => void;
  isCheckingUpdate: boolean;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  settings,
  storageUsage,
  onUpdateSettings,
  onClearCache,
  onRescanLibrary,
  onOpenEqualizer,
  onCheckUpdates,
  isCheckingUpdate,
}) => {
  const t = translations[settings.language];
  const [showAboutModal, setShowAboutModal] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const formatBytes = (bytes: number) => {
    if (!bytes) return '0 MB';
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const handleClearCacheClick = () => {
    onClearCache();
    setStatusMessage('Temporary cache cleaned.');
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleRescanClick = () => {
    onRescanLibrary();
    setStatusMessage('Library scan complete.');
    setTimeout(() => setStatusMessage(null), 3000);
  };

  return (
    <div className="flex-1 overflow-y-auto px-5 py-4 pb-28">
      {/* Header */}
      <div className="pt-1 pb-4">
        <h1 className="text-2xl font-black text-neutral-900 dark:text-white tracking-tight">
          {t.settingsTitle}
        </h1>
        <p className="text-xs font-semibold text-neutral-400 mt-0.5">
          App preferences & updates
        </p>
      </div>

      {statusMessage && (
        <div className="mb-4 p-3 rounded-2xl bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 text-xs font-bold flex items-center space-x-2">
          <Check className="w-4 h-4 text-emerald-500" />
          <span>{statusMessage}</span>
        </div>
      )}

      <div className="space-y-6">
        {/* Appearance Section */}
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-2.5">
            {t.appearanceSection}
          </span>
          <div className="p-3.5 rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                {settings.theme === 'dark' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
              </div>
              <span className="text-xs font-bold text-neutral-900 dark:text-white">
                {t.theme}
              </span>
            </div>

            <div className="flex bg-neutral-100 dark:bg-neutral-700/60 p-1 rounded-2xl">
              {(['light', 'dark', 'system'] as ThemeMode[]).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  onClick={() => onUpdateSettings({ theme: mode })}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-bold capitalize transition-all cursor-pointer ${
                    settings.theme === mode
                      ? 'bg-indigo-600 text-white shadow-sm'
                      : 'text-neutral-600 dark:text-neutral-400 hover:text-neutral-900 dark:hover:text-white'
                  }`}
                >
                  {mode}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Language Section */}
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-2.5">
            {t.languageSection}
          </span>
          <div className="p-3.5 rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                <Globe className="w-4 h-4" />
              </div>
              <span className="text-xs font-bold text-neutral-900 dark:text-white">
                {t.language}
              </span>
            </div>

            <div className="flex bg-neutral-100 dark:bg-neutral-700/60 p-1 rounded-2xl">
              {(['en', 'km'] as Language[]).map((lng) => (
                <button
                  key={lng}
                  type="button"
                  onClick={() => onUpdateSettings({ language: lng })}
                  className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all cursor-pointer ${
                    settings.language === lng
                      ? 'bg-indigo-600 text-white shadow-sm'
                      : 'text-neutral-600 dark:text-neutral-400 hover:text-neutral-900 dark:hover:text-white'
                  }`}
                >
                  {lng === 'en' ? 'English' : 'ភាសាខ្មែរ'}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Audio & Playback Section */}
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-2.5">
            {t.playbackSection}
          </span>
          <div className="rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 divide-y divide-neutral-100 dark:divide-neutral-700/40 overflow-hidden">
            {/* Equalizer trigger */}
            <div
              onClick={onOpenEqualizer}
              className="p-3.5 flex items-center justify-between cursor-pointer hover:bg-neutral-50 dark:hover:bg-neutral-700/20 transition"
            >
              <div className="flex items-center space-x-3">
                <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                  <Sliders className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                    {t.equalizer}
                  </h4>
                  <p className="text-[11px] text-neutral-400">
                    {settings.equalizer.enabled ? `Preset: ${settings.equalizer.preset}` : 'Disabled'}
                  </p>
                </div>
              </div>
              <span className="text-xs font-bold text-indigo-600 dark:text-indigo-400">
                Configure
              </span>
            </div>

            {/* Resume playback toggle */}
            <div className="p-3.5 flex items-center justify-between">
              <div>
                <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                  {t.resumePlayback}
                </h4>
                <p className="text-[11px] text-neutral-400">
                  {t.resumePlaybackDesc}
                </p>
              </div>
              <button
                onClick={() => onUpdateSettings({ resumePlayback: !settings.resumePlayback })}
                className={`w-11 h-6 flex items-center rounded-full p-1 transition-colors ${
                  settings.resumePlayback ? 'bg-indigo-600' : 'bg-neutral-300 dark:bg-neutral-700'
                }`}
              >
                <div
                  className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform ${
                    settings.resumePlayback ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            {/* Volume Normalization */}
            <div className="p-3.5 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                  <Volume2 className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                    {t.volumeNormalization}
                  </h4>
                  <p className="text-[11px] text-neutral-400">
                    {t.volumeNormalizationDesc}
                  </p>
                </div>
              </div>
              <button
                onClick={() => onUpdateSettings({ volumeNormalization: !settings.volumeNormalization })}
                className={`w-11 h-6 flex items-center rounded-full p-1 transition-colors ${
                  settings.volumeNormalization ? 'bg-indigo-600' : 'bg-neutral-300 dark:bg-neutral-700'
                }`}
              >
                <div
                  className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform ${
                    settings.volumeNormalization ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
          </div>
        </div>

        {/* Downloads Configuration */}
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-2.5">
            {t.downloadSection}
          </span>
          <div className="rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 divide-y divide-neutral-100 dark:divide-neutral-700/40 overflow-hidden">
            <div className="p-3.5 flex items-center justify-between">
              <span className="text-xs font-bold text-neutral-900 dark:text-white">
                {t.defaultQuality}
              </span>
              <select
                value={settings.defaultQuality}
                onChange={(e) => onUpdateSettings({ defaultQuality: e.target.value as '128k' | '192k' | '256k' | '320k' })}
                className="bg-neutral-100 dark:bg-neutral-700/80 px-2.5 py-1 rounded-xl text-xs font-bold text-neutral-900 dark:text-white outline-none"
              >
                <option value="320k">320 kbps (High)</option>
                <option value="256k">256 kbps (Medium)</option>
                <option value="192k">192 kbps (Standard)</option>
                <option value="128k">128 kbps (Compact)</option>
              </select>
            </div>

            <div className="p-3.5 flex items-center justify-between">
              <div>
                <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                  {t.wifiOnly}
                </h4>
              </div>
              <button
                onClick={() => onUpdateSettings({ wifiOnly: !settings.wifiOnly })}
                className={`w-11 h-6 flex items-center rounded-full p-1 transition-colors ${
                  settings.wifiOnly ? 'bg-indigo-600' : 'bg-neutral-300 dark:bg-neutral-700'
                }`}
              >
                <div
                  className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform ${
                    settings.wifiOnly ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="p-3.5">
              <span className="text-[11px] text-neutral-400 font-bold block mb-1">
                Storage Directory:
              </span>
              <p className="text-xs font-mono text-neutral-700 dark:text-neutral-300 truncate bg-neutral-100 dark:bg-neutral-700/50 p-2 rounded-xl">
                {settings.storageLocation}
              </p>
            </div>
          </div>
        </div>

        {/* Storage Management */}
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-2.5">
            {t.storageSection}
          </span>
          <div className="rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 divide-y divide-neutral-100 dark:divide-neutral-700/40 overflow-hidden">
            <div className="p-3.5 flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                  <HardDrive className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                    {t.storageUsage}
                  </h4>
                  <p className="text-[11px] text-neutral-400">
                    {storageUsage.songCount} songs stored locally
                  </p>
                </div>
              </div>
              <span className="text-xs font-mono font-bold text-neutral-900 dark:text-white">
                {formatBytes(storageUsage.estimatedBytes)}
              </span>
            </div>

            <div
              onClick={handleClearCacheClick}
              className="p-3.5 flex items-center justify-between cursor-pointer hover:bg-neutral-50 dark:hover:bg-neutral-700/20 transition"
            >
              <div className="flex items-center space-x-3">
                <Trash2 className="w-4 h-4 text-neutral-400" />
                <div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                    {t.clearCache}
                  </h4>
                  <p className="text-[11px] text-neutral-400">{t.clearCacheDesc}</p>
                </div>
              </div>
              <span className="text-xs font-bold text-neutral-500">Clean</span>
            </div>

            <div
              onClick={handleRescanClick}
              className="p-3.5 flex items-center justify-between cursor-pointer hover:bg-neutral-50 dark:hover:bg-neutral-700/20 transition"
            >
              <div className="flex items-center space-x-3">
                <RefreshCw className="w-4 h-4 text-neutral-400" />
                <div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                    {t.rescanLibrary}
                  </h4>
                  <p className="text-[11px] text-neutral-400">{t.rescanLibraryDesc}</p>
                </div>
              </div>
              <span className="text-xs font-bold text-neutral-500">Rescan</span>
            </div>
          </div>
        </div>

        {/* Updates & About Section */}
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-2.5">
            {t.aboutSection}
          </span>
          <div className="rounded-3xl bg-white dark:bg-neutral-800/60 border border-neutral-200/60 dark:border-neutral-700/60 divide-y divide-neutral-100 dark:divide-neutral-700/40 overflow-hidden">
            {/* Check for updates */}
            <div
              onClick={onCheckUpdates}
              className="p-3.5 flex items-center justify-between cursor-pointer hover:bg-neutral-50 dark:hover:bg-neutral-700/20 transition"
            >
              <div className="flex items-center space-x-3">
                <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="text-xs font-bold text-neutral-900 dark:text-white">
                    {t.checkForUpdates}
                  </h4>
                  <p className="text-[11px] text-neutral-400">
                    GitHub Releases channel
                  </p>
                </div>
              </div>
              <span className="text-xs font-bold text-indigo-600 dark:text-indigo-400 flex items-center space-x-1">
                {isCheckingUpdate ? (
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <span>Check</span>
                )}
              </span>
            </div>

            {/* Version Info */}
            <div className="p-3.5 flex items-center justify-between">
              <span className="text-xs font-bold text-neutral-900 dark:text-white">
                {t.appVersion}
              </span>
              <span className="text-xs font-mono font-bold text-neutral-500">
                v{CURRENT_VERSION_NAME}
              </span>
            </div>

            {/* GitHub info */}
            <div className="p-3.5 flex items-center justify-between">
              <div className="flex items-center space-x-2 text-neutral-700 dark:text-neutral-300">
                <Github className="w-4 h-4" />
                <span className="text-xs font-bold">GitHub Source & Releases</span>
              </div>
              <span className="text-[11px] font-mono text-neutral-400">
                musichub-android
              </span>
            </div>

            {/* About Modal trigger */}
            <div
              onClick={() => setShowAboutModal(true)}
              className="p-3.5 flex items-center justify-between cursor-pointer hover:bg-neutral-50 dark:hover:bg-neutral-700/20 transition"
            >
              <div className="flex items-center space-x-2 text-neutral-700 dark:text-neutral-300">
                <Info className="w-4 h-4" />
                <span className="text-xs font-bold">{t.aboutApp}</span>
              </div>
              <span className="text-xs text-neutral-400 font-bold">Details</span>
            </div>
          </div>
        </div>
      </div>

      {/* About Modal */}
      {showAboutModal && (
        <div className="fixed inset-0 z-80 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-sm w-full p-6 shadow-2xl flex flex-col">
            <div className="w-12 h-12 rounded-2xl bg-indigo-600 text-white flex items-center justify-center mb-3 shadow-md shadow-indigo-600/30">
              <Shield className="w-6 h-6" />
            </div>
            <h3 className="text-lg font-black text-neutral-900 dark:text-white">
              MusicHub
            </h3>
            <p className="text-xs text-neutral-400 font-mono mt-0.5">
              Version {CURRENT_VERSION_NAME} (Personal Release)
            </p>
            <p className="text-xs text-neutral-600 dark:text-neutral-300 mt-3 leading-relaxed">
              Fast, offline-first personal Android music player and legal audio downloader.
              Built with Kotlin, Jetpack Compose, Material 3, Android Media3 ExoPlayer, and Room local database.
            </p>
            <div className="mt-4 p-3 rounded-2xl bg-neutral-100 dark:bg-neutral-800 text-[11px] space-y-1.5 font-medium text-neutral-600 dark:text-neutral-400">
              <p>• Zero account or login requirements</p>
              <p>• Zero tracking or analytics</p>
              <p>• Complete offline playback and storage</p>
              <p>• Distributed directly via GitHub Releases APK</p>
            </div>
            <button
              onClick={() => setShowAboutModal(false)}
              className="w-full mt-5 py-2.5 rounded-xl bg-neutral-900 dark:bg-white text-white dark:text-neutral-900 font-bold text-xs"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
