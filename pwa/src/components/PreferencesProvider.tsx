'use client';

import { useEffect } from 'react';
import { usePreferencesStore } from '@/store/preferences';
import AccessibilityFAB from './AccessibilityFAB';
import InstallPrompt from './InstallPrompt';
import PullToRefresh from './PullToRefresh';

export default function PreferencesProvider({ children }: { children: React.ReactNode }) {
  const { themeMode, fontScale, highContrast, reduceMotion, language } = usePreferencesStore();

  useEffect(() => {
    const html = document.documentElement;
    html.dataset.theme = themeMode;
    html.dataset.fontScale = fontScale;
    html.dataset.highContrast = String(highContrast);
    html.dataset.reduceMotion = String(reduceMotion);
    html.lang = language;
    html.dir = language === 'ar' ? 'rtl' : 'ltr';
  }, [themeMode, fontScale, highContrast, reduceMotion, language]);

  return (
    <PullToRefresh>
      {children}
      <AccessibilityFAB />
      <InstallPrompt />
    </PullToRefresh>
  );
}
