'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type ThemeMode = 'system' | 'light' | 'dark';
export type FontScale = 'small' | 'normal' | 'large';
export type Language = 'en' | 'sw' | 'fr' | 'es' | 'ar' | 'hi';

interface PreferencesState {
  themeMode: ThemeMode;
  fontScale: FontScale;
  highContrast: boolean;
  reduceMotion: boolean;
  language: Language;
  callSound: string | null;
  requestSound: string | null;
  setThemeMode: (mode: ThemeMode) => void;
  setFontScale: (scale: FontScale) => void;
  setHighContrast: (enabled: boolean) => void;
  setReduceMotion: (enabled: boolean) => void;
  setLanguage: (lang: Language) => void;
  setCallSound: (sound: string | null) => void;
  setRequestSound: (sound: string | null) => void;
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      themeMode: 'light',
      fontScale: 'normal',
      highContrast: false,
      reduceMotion: false,
      language: 'en',
      callSound: null,
      requestSound: null,
      setThemeMode: (mode) => set({ themeMode: mode }),
      setFontScale: (scale) => set({ fontScale: scale }),
      setHighContrast: (enabled) => set({ highContrast: enabled }),
      setReduceMotion: (enabled) => set({ reduceMotion: enabled }),
      setLanguage: (lang) => set({ language: lang }),
      setCallSound: (sound) => set({ callSound: sound }),
      setRequestSound: (sound) => set({ requestSound: sound }),
    }),
    {
      name: 'esiri-preferences',
    },
  ),
);
