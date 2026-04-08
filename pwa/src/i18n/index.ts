import en, { type TranslationKeys } from './en';
import sw from './sw';
import fr from './fr';
import es from './es';
import ar from './ar';
import hi from './hi';

const translations: Record<string, Record<string, string>> = { en, sw, fr, es, ar, hi };

/**
 * Get a translated string. Supports {name}, {count} placeholder substitution.
 */
export function translate(key: TranslationKeys, lang: string, vars?: Record<string, string | number>): string {
  const dict = translations[lang] ?? translations.en;
  let str = dict[key] ?? translations.en[key] ?? key;
  if (vars) {
    Object.entries(vars).forEach(([k, v]) => {
      str = str.replace(`{${k}}`, String(v));
    });
  }
  return str;
}

export type { TranslationKeys };
