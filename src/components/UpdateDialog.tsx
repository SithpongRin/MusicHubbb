import React, { useState } from 'react';
import { X, Sparkles, Download, CheckCircle, ShieldCheck } from 'lucide-react';
import { GitHubRelease } from '../types/music';

interface UpdateDialogProps {
  isOpen: boolean;
  onClose: () => void;
  release: GitHubRelease | null;
  currentVersion: string;
}

export const UpdateDialog: React.FC<UpdateDialogProps> = ({
  isOpen,
  onClose,
  release,
  currentVersion,
}) => {
  const [downloadProgress, setDownloadProgress] = useState<number | null>(null);
  const [isInstalling, setIsInstalling] = useState(false);
  const [installSuccess, setInstallSuccess] = useState(false);

  if (!isOpen || !release) return null;

  const formatSize = (bytes: number) => {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const handleStartUpdate = () => {
    setDownloadProgress(0);
    // Simulate APK download with progress
    const interval = setInterval(() => {
      setDownloadProgress((prev) => {
        if (prev === null) return 10;
        if (prev >= 100) {
          clearInterval(interval);
          handleInstallation();
          return 100;
        }
        return prev + 15;
      });
    }, 200);
  };

  const handleInstallation = () => {
    setIsInstalling(true);
    setTimeout(() => {
      setIsInstalling(false);
      setInstallSuccess(true);
    }, 1500);
  };

  return (
    <div className="fixed inset-0 z-80 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-sm w-full p-6 shadow-2xl flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-neutral-100 dark:border-neutral-800">
          <div className="flex items-center space-x-2">
            <Sparkles className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            <h3 className="text-base font-bold text-neutral-900 dark:text-white">
              Update Available
            </h3>
          </div>
          {!downloadProgress && (
            <button
              onClick={onClose}
              className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Version Badge & Info */}
        <div className="mt-4 flex items-center justify-between">
          <div>
            <span className="text-xs text-neutral-400 font-semibold block">
              Current: v{currentVersion}
            </span>
            <span className="text-lg font-extrabold text-neutral-900 dark:text-white">
              {release.tagName}
            </span>
          </div>
          <span className="px-3 py-1 rounded-full text-xs font-bold bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800">
            {formatSize(release.apkSize)}
          </span>
        </div>

        {/* Release Notes */}
        <div className="mt-4">
          <span className="text-xs font-bold uppercase tracking-wider text-neutral-400 block mb-1.5">
            What's New:
          </span>
          <div className="p-3 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-200/80 dark:border-neutral-700/80 max-h-36 overflow-y-auto text-xs text-neutral-700 dark:text-neutral-300 whitespace-pre-line leading-relaxed font-mono">
            {release.body}
          </div>
        </div>

        {/* Data Preservation Assurance */}
        <div className="mt-3 flex items-center space-x-2 text-[11px] text-neutral-500 dark:text-neutral-400">
          <ShieldCheck className="w-4 h-4 text-emerald-500 flex-shrink-0" />
          <span>Local songs, playlists, and settings remain preserved.</span>
        </div>

        {/* Progress or Actions */}
        {downloadProgress !== null ? (
          <div className="mt-5 space-y-2">
            <div className="flex justify-between text-xs font-semibold text-neutral-600 dark:text-neutral-400">
              <span>{isInstalling ? 'Verifying APK package...' : installSuccess ? 'Installed successfully' : 'Downloading APK...'}</span>
              <span>{downloadProgress}%</span>
            </div>
            <div className="w-full h-2 bg-neutral-200 dark:bg-neutral-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-indigo-600 transition-all duration-200"
                style={{ width: `${downloadProgress}%` }}
              />
            </div>
            {installSuccess && (
              <div className="mt-3 p-2.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 text-xs font-bold flex items-center justify-center space-x-1.5">
                <CheckCircle className="w-4 h-4" />
                <span>Ready to relaunch MusicHub</span>
              </div>
            )}
            {installSuccess && (
              <button
                onClick={onClose}
                className="w-full mt-2 py-2.5 rounded-xl bg-neutral-900 dark:bg-white text-white dark:text-neutral-900 font-bold text-xs"
              >
                Close
              </button>
            )}
          </div>
        ) : (
          <div className="mt-6 flex items-center justify-end space-x-2">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-neutral-600 dark:text-neutral-400 font-semibold text-xs hover:bg-neutral-100 dark:hover:bg-neutral-800 transition"
            >
              Later
            </button>
            <button
              onClick={handleStartUpdate}
              className="px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs transition flex items-center space-x-1.5 shadow-md shadow-indigo-600/20"
            >
              <Download className="w-4 h-4" />
              <span>Download APK</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
