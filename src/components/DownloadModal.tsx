import React, { useState } from 'react';
import {
  X,
  DownloadCloud,
  CheckCircle,
  AlertCircle,
  Search,
  RefreshCw,
  Trash2,
  ShieldCheck,
  Music,
} from 'lucide-react';
import { DownloadTask, Song } from '../types/music';
import { downloadManager, MediaInspectionResult, MediaQualityOption } from '../services/downloadManager';

interface DownloadModalProps {
  isOpen: boolean;
  onClose: () => void;
  downloads: DownloadTask[];
  onSongDownloaded: (song: Song) => void;
}

const SAMPLE_LEGAL_URLS = [
  {
    name: 'Internet Archive - Chopin Nocturne in E-Flat Major (Public Domain)',
    url: 'https://ia800504.us.archive.org/11/items/freepd_Chopin_Nocturne_in_E_Flat_Major_Op._9_No._2/Chopin_Nocturne_in_E_Flat_Major_Op._9_No._2.mp3',
  },
  {
    name: 'Internet Archive - Moonlight Sonata Beethoven (Public Domain)',
    url: 'https://ia800301.us.archive.org/15/items/MoonlightSonata_855/Beethoven-MoonlightSonata.mp3',
  },
  {
    name: 'Internet Archive - Bach Prelude in C Major (Public Domain)',
    url: 'https://ia800201.us.archive.org/30/items/J.s.Bach-PreludeInCMajor/Bach_Prelude_in_C.mp3',
  },
];

export const DownloadModal: React.FC<DownloadModalProps> = ({
  isOpen,
  onClose,
  downloads,
  onSongDownloaded,
}) => {
  const [url, setUrl] = useState('');
  const [isInspecting, setIsInspecting] = useState(false);
  const [inspectionResult, setInspectionResult] = useState<MediaInspectionResult | null>(null);
  const [selectedQuality, setSelectedQuality] = useState<MediaQualityOption | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleInspect = async (overrideUrl?: string) => {
    const targetUrl = overrideUrl || url;
    if (!targetUrl.trim()) return;

    setIsInspecting(true);
    setStatusMessage(null);
    try {
      const result = await downloadManager.inspectUrl(targetUrl);
      setInspectionResult(result);
      if (result.isValid && result.qualities.length > 0) {
        setSelectedQuality(result.qualities[0]);
      }
    } catch {
      setStatusMessage('Unable to inspect URL. Check network connection.');
    } finally {
      setIsInspecting(false);
    }
  };

  const handleStartDownload = async () => {
    if (!inspectionResult || !selectedQuality) return;

    try {
      setStatusMessage('Download initiated...');
      await downloadManager.startDownload(inspectionResult, selectedQuality, (newSong) => {
        onSongDownloaded(newSong);
        setStatusMessage(`Successfully downloaded: ${newSong.title}`);
      });
      // Clear input
      setUrl('');
      setInspectionResult(null);
      setSelectedQuality(null);
    } catch {
      setStatusMessage('Error starting download.');
    }
  };

  const handleCancel = (taskId: string) => {
    downloadManager.cancelDownload(taskId);
  };

  const handleRetry = (task: DownloadTask) => {
    downloadManager.retryDownload(task, (newSong) => {
      onSongDownloaded(newSong);
    });
  };

  return (
    <div className="fixed inset-0 z-70 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-lg w-full p-6 shadow-2xl flex flex-col max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-neutral-100 dark:border-neutral-800">
          <div className="flex items-center space-x-2">
            <DownloadCloud className="w-6 h-6 text-indigo-600 dark:text-indigo-400" />
            <h3 className="text-lg font-bold text-neutral-900 dark:text-white">
              Music Downloader
            </h3>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Legal Notice */}
        <div className="mt-4 p-3 rounded-2xl bg-indigo-50 dark:bg-indigo-950/40 border border-indigo-100 dark:border-indigo-900/50 flex items-start space-x-2.5">
          <ShieldCheck className="w-5 h-5 text-indigo-600 dark:text-indigo-400 flex-shrink-0 mt-0.5" />
          <p className="text-xs text-indigo-900 dark:text-indigo-200 leading-relaxed font-medium">
            Permitted and Public Domain audio sources only. DRM circumvention and subscription protected streams are strictly prohibited.
          </p>
        </div>

        {/* Input Form */}
        <div className="mt-4 space-y-3">
          <div>
            <label className="text-xs font-bold text-neutral-600 dark:text-neutral-400 block mb-1">
              Audio Source URL
            </label>
            <div className="flex items-center space-x-2">
              <input
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://... (Direct audio or archive.org)"
                className="flex-1 px-4 py-2.5 rounded-xl border border-neutral-200 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-neutral-900 dark:text-white text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <button
                onClick={() => handleInspect()}
                disabled={isInspecting || !url.trim()}
                className="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs transition flex items-center space-x-1.5 disabled:opacity-50"
              >
                {isInspecting ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <Search className="w-4 h-4" />
                )}
                <span>Inspect</span>
              </button>
            </div>
          </div>

          {/* Quick Legal Samples for Testing */}
          <div>
            <span className="text-[11px] font-semibold text-neutral-400 block mb-1.5">
              Or select legal Public Domain source:
            </span>
            <div className="space-y-1.5">
              {SAMPLE_LEGAL_URLS.map((sample, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    setUrl(sample.url);
                    handleInspect(sample.url);
                  }}
                  className="w-full text-left p-2 rounded-xl bg-neutral-100 dark:bg-neutral-800/60 hover:bg-neutral-200 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300 text-xs font-medium truncate flex items-center space-x-2 transition"
                >
                  <Music className="w-3.5 h-3.5 text-indigo-500 flex-shrink-0" />
                  <span className="truncate">{sample.name}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Media Inspection Result */}
        {inspectionResult && inspectionResult.isValid && (
          <div className="mt-4 p-4 rounded-2xl bg-neutral-50 dark:bg-neutral-800/80 border border-neutral-200 dark:border-neutral-700 animate-in fade-in">
            <div className="flex items-start justify-between">
              <div>
                <h4 className="text-sm font-bold text-neutral-900 dark:text-white">
                  {inspectionResult.title}
                </h4>
                <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-0.5">
                  {inspectionResult.artist} • {inspectionResult.providerName}
                </p>
              </div>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 dark:bg-emerald-950 text-emerald-600 dark:text-emerald-400">
                Verified
              </span>
            </div>

            {/* Quality Selection */}
            <div className="mt-3">
              <span className="text-xs font-bold text-neutral-600 dark:text-neutral-400 block mb-1.5">
                Select Audio Quality:
              </span>
              <div className="grid grid-cols-2 gap-2">
                {inspectionResult.qualities.map((q, qIdx) => (
                  <button
                    key={qIdx}
                    onClick={() => setSelectedQuality(q)}
                    className={`p-2 rounded-xl text-xs font-semibold text-left border transition ${
                      selectedQuality?.qualityLabel === q.qualityLabel
                        ? 'border-indigo-600 bg-indigo-50/50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400'
                        : 'border-neutral-200 dark:border-neutral-700 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-700/50'
                    }`}
                  >
                    <div>{q.qualityLabel}</div>
                    <div className="text-[10px] text-neutral-400 uppercase font-mono mt-0.5">
                      {q.format}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            <button
              onClick={handleStartDownload}
              className="w-full mt-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs transition flex items-center justify-center space-x-1.5 shadow-md shadow-indigo-600/20"
            >
              <DownloadCloud className="w-4 h-4" />
              <span>Download and Save to Library</span>
            </button>
          </div>
        )}

        {inspectionResult && !inspectionResult.isValid && (
          <div className="mt-4 p-3 rounded-xl bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900/50 flex items-center space-x-2 text-rose-600 dark:text-rose-400 text-xs font-semibold">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{inspectionResult.errorMessage || 'Inspection failed.'}</span>
          </div>
        )}

        {statusMessage && (
          <div className="mt-3 text-xs text-center font-medium text-indigo-600 dark:text-indigo-400">
            {statusMessage}
          </div>
        )}

        {/* Active & Recent Downloads List */}
        <div className="mt-6 pt-4 border-t border-neutral-100 dark:border-neutral-800">
          <h4 className="text-xs font-bold uppercase tracking-wider text-neutral-400 mb-3">
            Download Tasks ({downloads.length})
          </h4>

          {downloads.length === 0 ? (
            <p className="text-xs text-neutral-500 dark:text-neutral-400 text-center py-4">
              No recent downloads.
            </p>
          ) : (
            <div className="space-y-2.5 max-h-48 overflow-y-auto">
              {downloads.map((task) => (
                <div
                  key={task.id}
                  className="p-3 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-200/80 dark:border-neutral-700/80 flex items-center justify-between"
                >
                  <div className="min-w-0 flex-1 pr-3">
                    <p className="text-xs font-bold text-neutral-900 dark:text-white truncate">
                      {task.title}
                    </p>
                    <div className="flex items-center space-x-2 text-[11px] text-neutral-500 dark:text-neutral-400 mt-1">
                      <span>{task.quality}</span>
                      <span>•</span>
                      <span className="capitalize">{task.status}</span>
                      {task.status === 'downloading' && (
                        <span>({task.progress}%)</span>
                      )}
                    </div>

                    {task.status === 'downloading' && (
                      <div className="w-full h-1 bg-neutral-200 dark:bg-neutral-700 rounded-full mt-2 overflow-hidden">
                        <div
                          className="h-full bg-indigo-600 transition-all duration-300"
                          style={{ width: `${task.progress}%` }}
                        />
                      </div>
                    )}
                  </div>

                  <div className="flex items-center space-x-1 flex-shrink-0">
                    {task.status === 'completed' && (
                      <CheckCircle className="w-5 h-5 text-emerald-500" />
                    )}
                    {task.status === 'downloading' && (
                      <button
                        onClick={() => handleCancel(task.id)}
                        className="p-1.5 rounded-lg text-neutral-400 hover:text-rose-500 transition"
                        title="Cancel"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                    {(task.status === 'failed' || task.status === 'cancelled') && (
                      <button
                        onClick={() => handleRetry(task)}
                        className="p-1.5 rounded-lg text-neutral-400 hover:text-indigo-500 transition"
                        title="Retry"
                      >
                        <RefreshCw className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
