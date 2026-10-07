import React, { useState, useEffect } from 'react';
import { Wifi, Battery, Volume2 } from 'lucide-react';

interface StatusBarProps {
  isPlaying?: boolean;
}

export const StatusBar: React.FC<StatusBarProps> = ({ isPlaying }) => {
  const [time, setTime] = useState<string>('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTime(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="w-full flex items-center justify-between px-6 py-2 select-none text-xs font-semibold tracking-tight opacity-90 z-30">
      <div className="flex items-center space-x-2">
        <span>{time || '09:41'}</span>
        {isPlaying && (
          <span className="flex items-center text-indigo-500 animate-pulse">
            <Volume2 className="w-3.5 h-3.5" />
          </span>
        )}
      </div>

      <div className="w-20 h-4 bg-neutral-900/10 dark:bg-neutral-100/10 rounded-full mx-auto" />

      <div className="flex items-center space-x-2 text-neutral-600 dark:text-neutral-300">
        <Wifi className="w-3.5 h-3.5" />
        <span className="text-[10px] font-bold">5G</span>
        <div className="flex items-center space-x-0.5">
          <span className="text-[10px]">98%</span>
          <Battery className="w-4 h-4 fill-current" />
        </div>
      </div>
    </div>
  );
};
