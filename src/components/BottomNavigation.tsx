import React from 'react';
import { Home, Compass, ListMusic, Settings } from 'lucide-react';
import { translations } from '../i18n/translations';
import { Language } from '../types/music';

export type NavTab = 'home' | 'library' | 'playlist' | 'settings';

interface BottomNavigationProps {
  currentTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  lang: Language;
}

export const BottomNavigation: React.FC<BottomNavigationProps> = ({
  currentTab,
  onSelectTab,
  lang,
}) => {
  const t = translations[lang];

  const tabs: { id: NavTab; label: string; icon: React.FC<{ className?: string }> }[] = [
    { id: 'home', label: t.navHome, icon: Home },
    { id: 'library', label: t.navLibrary, icon: Compass },
    { id: 'playlist', label: t.navPlaylist, icon: ListMusic },
    { id: 'settings', label: t.navSettings, icon: Settings },
  ];

  return (
    <nav className="w-full bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-t border-neutral-200/80 dark:border-neutral-800/80 px-4 py-2 z-30 transition-colors">
      <div className="flex items-center justify-around max-w-md mx-auto">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = currentTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onSelectTab(tab.id)}
              className={`flex flex-col items-center justify-center flex-1 py-1 px-2 rounded-2xl transition-all duration-200 ${
                isActive
                  ? 'text-indigo-600 dark:text-indigo-400 font-semibold'
                  : 'text-neutral-500 dark:text-neutral-400 hover:text-neutral-800 dark:hover:text-neutral-200 font-medium'
              }`}
            >
              <div
                className={`flex items-center justify-center w-12 h-7 rounded-full transition-all duration-200 ${
                  isActive
                    ? 'bg-indigo-100 dark:bg-indigo-950/80 text-indigo-600 dark:text-indigo-400'
                    : 'bg-transparent text-neutral-500 dark:text-neutral-400'
                }`}
              >
                <Icon className="w-5 h-5" />
              </div>
              <span className="text-[11px] mt-0.5 tracking-tight line-clamp-1">
                {tab.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
