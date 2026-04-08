'use client';

import { useCallback } from 'react';
import { usePreferencesStore } from '@/store/preferences';
import { translate, type TranslationKeys } from '@/i18n';

export function useTranslation() {
  const lang = usePreferencesStore((s) => s.language);

  const t = useCallback(
    (key: TranslationKeys, vars?: Record<string, string | number>) => translate(key, lang, vars),
    [lang],
  );

  return { t, lang };
}
