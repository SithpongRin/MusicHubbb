import React, { useState } from 'react';
import { X, Sliders, RotateCcw } from 'lucide-react';
import { EqualizerSettings } from '../types/music';

interface EqualizerModalProps {
  isOpen: boolean;
  onClose: () => void;
  settings: EqualizerSettings;
  onSave: (settings: EqualizerSettings) => void;
}

const PRESETS: Record<string, number[]> = {
  Flat: [0, 0, 0, 0, 0],
  'Bass Boost': [6, 4, 1, 0, -1],
  Vocal: [-2, 1, 4, 3, 1],
  Rock: [4, 2, -1, 2, 4],
  Pop: [2, 1, 3, 2, 1],
  Classical: [3, 2, -2, 2, 3],
  Electronic: [5, 3, 0, 2, 5],
};

const BANDS_LABEL = ['60 Hz', '230 Hz', '910 Hz', '3.6 kHz', '14 kHz'];

export const EqualizerModal: React.FC<EqualizerModalProps> = ({
  isOpen,
  onClose,
  settings,
  onSave,
}) => {
  const [enabled, setEnabled] = useState(settings.enabled);
  const [preset, setPreset] = useState(settings.preset);
  const [bands, setBands] = useState<number[]>([...settings.bands]);
  const [bassBoost, setBassBoost] = useState(settings.bassBoost);

  if (!isOpen) return null;

  const handleBandChange = (index: number, val: number) => {
    const updated = [...bands];
    updated[index] = val;
    setBands(updated);
    setPreset('Custom');
    onSave({ enabled, preset: 'Custom', bands: updated, bassBoost });
  };

  const handlePresetSelect = (presetName: string) => {
    const presetBands = PRESETS[presetName] || [0, 0, 0, 0, 0];
    setPreset(presetName);
    setBands(presetBands);
    onSave({ enabled, preset: presetName, bands: presetBands, bassBoost });
  };

  const handleToggleEnabled = () => {
    const next = !enabled;
    setEnabled(next);
    onSave({ enabled: next, preset, bands, bassBoost });
  };

  const handleBassBoostChange = (val: number) => {
    setBassBoost(val);
    onSave({ enabled, preset, bands, bassBoost: val });
  };

  const handleReset = () => {
    handlePresetSelect('Flat');
    setBassBoost(0);
  };

  return (
    <div className="fixed inset-0 z-70 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl max-w-sm w-full p-6 shadow-2xl flex flex-col max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-neutral-100 dark:border-neutral-800">
          <div className="flex items-center space-x-2">
            <Sliders className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            <h3 className="text-lg font-bold text-neutral-900 dark:text-white">
              Audio Equalizer
            </h3>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-white bg-neutral-100 dark:bg-neutral-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Master Switch */}
        <div className="flex items-center justify-between py-4">
          <span className="text-sm font-semibold text-neutral-700 dark:text-neutral-300">
            Equalizer Enabled
          </span>
          <button
            onClick={handleToggleEnabled}
            className={`w-12 h-6 flex items-center rounded-full p-1 transition-colors duration-200 ${
              enabled ? 'bg-indigo-600' : 'bg-neutral-300 dark:bg-neutral-700'
            }`}
          >
            <div
              className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform duration-200 ${
                enabled ? 'translate-x-6' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Presets Pills */}
        <div className="py-2">
          <span className="text-xs uppercase font-bold text-neutral-400 tracking-wider block mb-2">
            Presets
          </span>
          <div className="flex flex-wrap gap-2">
            {Object.keys(PRESETS).map((p) => (
              <button
                key={p}
                disabled={!enabled}
                onClick={() => handlePresetSelect(p)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  preset === p
                    ? 'bg-indigo-600 text-white'
                    : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-400 hover:bg-neutral-200 dark:hover:bg-neutral-700'
                } ${!enabled ? 'opacity-40 cursor-not-allowed' : ''}`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        {/* 5 Vertical Sliders */}
        <div className="py-6">
          <div className="flex items-end justify-between h-48 px-2">
            {bands.map((gain, idx) => (
              <div key={idx} className="flex flex-col items-center h-full justify-between">
                <span className="text-[11px] font-bold text-indigo-600 dark:text-indigo-400">
                  {gain > 0 ? `+${gain}` : gain} dB
                </span>
                <input
                  type="range"
                  min="-12"
                  max="12"
                  step="1"
                  disabled={!enabled}
                  value={gain}
                  onChange={(e) => handleBandChange(idx, parseInt(e.target.value, 10))}
                  style={{ writingMode: 'vertical-lr', direction: 'rtl' }}
                  className="h-28 w-2 accent-indigo-600 cursor-pointer disabled:opacity-40"
                />
                <span className="text-[10px] font-medium text-neutral-500 dark:text-neutral-400 mt-2">
                  {BANDS_LABEL[idx]}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Bass Boost */}
        <div className="py-4 border-t border-neutral-100 dark:border-neutral-800">
          <div className="flex justify-between items-center mb-1">
            <span className="text-xs font-bold text-neutral-700 dark:text-neutral-300">
              Bass Boost
            </span>
            <span className="text-xs font-bold text-indigo-600 dark:text-indigo-400">
              {bassBoost}%
            </span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            disabled={!enabled}
            value={bassBoost}
            onChange={(e) => handleBassBoostChange(parseInt(e.target.value, 10))}
            className="w-full h-1.5 bg-neutral-200 dark:bg-neutral-700 rounded-lg appearance-none cursor-pointer accent-indigo-600 disabled:opacity-40"
          />
        </div>

        {/* Actions */}
        <div className="flex items-center justify-between pt-4 border-t border-neutral-100 dark:border-neutral-800">
          <button
            onClick={handleReset}
            disabled={!enabled}
            className="flex items-center space-x-1 text-xs font-semibold text-neutral-500 hover:text-neutral-900 dark:hover:text-white transition disabled:opacity-40"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </button>
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs transition"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
