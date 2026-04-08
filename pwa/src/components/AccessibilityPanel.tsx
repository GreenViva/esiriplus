'use client';

import { useRef, useState } from 'react';
import { RotateCcw, Play, Square } from 'lucide-react';
import {
  usePreferencesStore,
  type ThemeMode,
  type FontScale,
  type Language,
} from '@/store/preferences';
import { useTranslation } from '@/hooks/useTranslation';

const THEMES: { value: ThemeMode; label: string }[] = [
  { value: 'system', label: 'Auto' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
];

const FONT_SCALES: { value: FontScale; label: string; size: string }[] = [
  { value: 'small', label: 'A', size: 'text-[13px]' },
  { value: 'normal', label: 'A', size: 'text-[16px]' },
  { value: 'large', label: 'A', size: 'text-[20px]' },
];

const LANGUAGES: { value: Language; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'sw', label: 'Kiswahili' },
  { value: 'fr', label: 'Français' },
  { value: 'es', label: 'Español' },
  { value: 'ar', label: 'العربية' },
  { value: 'hi', label: 'हिन्दी' },
];

const RINGTONES = [
  { id: 'default', label: 'Default Beep', file: null },
  { id: 'ringtone-023', label: 'Gentle Chime', file: '/sounds/universfield-ringtone-023-376906.mp3' },
  { id: 'ringtone-029', label: 'Soft Bell', file: '/sounds/universfield-ringtone-029-437512.mp3' },
  { id: 'ringtone-030', label: 'Classic Ring', file: '/sounds/universfield-ringtone-030-437513.mp3' },
  { id: 'ringtone-031', label: 'Bright Tone', file: '/sounds/universfield-ringtone-031-437514.mp3' },
  { id: 'ringtone-055', label: 'Pulse Alert', file: '/sounds/universfield-ringtone-055-494939.mp3' },
  { id: 'ringtone-087', label: 'Warm Melody', file: '/sounds/universfield-ringtone-087-496415.mp3' },
  { id: 'ringtone-088', label: 'Clear Signal', file: '/sounds/universfield-ringtone-088-496414.mp3' },
  { id: 'ringtone-089', label: 'Attention', file: '/sounds/universfield-ringtone-089-496413.mp3' },
  { id: 'ringtone-091', label: 'Calm Notify', file: '/sounds/universfield-ringtone-091-496417.mp3' },
];

/** Get the audio file path for a ringtone ID, or null for default beep */
export function getRingtoneFile(id: string | null): string | null {
  if (!id || id === 'default') return null;
  return RINGTONES.find((r) => r.id === id)?.file ?? null;
}

interface Props {
  onDismiss: () => void;
}

export function AccessibilityPanel({ onDismiss }: Props) {
  const {
    themeMode, fontScale, highContrast, reduceMotion, language,
    callSound, requestSound,
    setThemeMode, setFontScale, setHighContrast, setReduceMotion,
    setLanguage, setCallSound, setRequestSound,
  } = usePreferencesStore();

  const { t } = useTranslation();
  const panelRef = useRef<HTMLDivElement>(null);

  function handleLanguageChange(lang: Language) {
    setLanguage(lang);
    onDismiss();
  }

  return (
    <div
      ref={panelRef}
      role="dialog"
      aria-label={t('display_accessibility')}
      className="w-[280px] rounded-[20px] shadow-xl p-5 overflow-y-auto max-h-[80vh] animate-[scaleIn_250ms_ease-out]"
      style={{ background: 'var(--surface, #fff)' }}
    >
      {/* Header */}
      <h2 className="text-[16px] font-bold" style={{ color: 'var(--on-surface)' }}>
        {t('display_accessibility')}
      </h2>
      <p className="text-[13px] mb-4" style={{ color: 'var(--on-surface-variant)' }}>
        {t('customize_experience')}
      </p>

      {/* ── Theme ──────────────────────────────────────────────── */}
      <SectionLabel>{t('theme')}</SectionLabel>
      <div className="flex gap-1.5 mb-3">
        {THEMES.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => setThemeMode(value)}
            className={`flex-1 h-9 rounded-[10px] text-[12px] font-semibold transition-colors ${
              themeMode === value
                ? 'bg-[#2A9D8F] text-white'
                : 'border text-[var(--on-surface-variant)]'
            }`}
            style={themeMode !== value ? { background: 'var(--surface-container-high)', borderColor: 'var(--outline)' } : undefined}
            role="radio"
            aria-checked={themeMode === value}
          >
            {label}
          </button>
        ))}
      </div>
      <Divider />

      {/* ── Text Size ──────────────────────────────────────────── */}
      <SectionLabel>{t('text_size')}</SectionLabel>
      <div className="flex gap-1.5 mb-3">
        {FONT_SCALES.map(({ value, label, size }) => (
          <button
            key={value}
            onClick={() => setFontScale(value)}
            className={`flex-1 h-10 rounded-[10px] font-bold flex items-end justify-center pb-1.5 transition-colors ${
              fontScale === value
                ? 'bg-[#2A9D8F]/10 text-[#2A9D8F] border-2 border-[#2A9D8F]'
                : 'border text-[var(--on-surface-variant)]'
            } ${size}`}
            style={fontScale !== value ? { background: 'var(--surface-container-high)', borderColor: 'var(--outline)' } : undefined}
            role="radio"
            aria-checked={fontScale === value}
          >
            {label}
          </button>
        ))}
      </div>
      <Divider />

      {/* ── High Contrast ──────────────────────────────────────── */}
      <ToggleRow
        label={t('high_contrast')}
        subtitle={t('high_contrast_desc')}
        checked={highContrast}
        onChange={setHighContrast}
      />

      {/* ── Reduce Motion ──────────────────────────────────────── */}
      <ToggleRow
        label={t('reduce_motion')}
        subtitle={t('reduce_motion_desc')}
        checked={reduceMotion}
        onChange={setReduceMotion}
      />
      <Divider />

      {/* ── Language ────────────────────────────────────────────── */}
      <SectionLabel>{t('language')}</SectionLabel>
      <div className="grid grid-cols-3 gap-1.5 mb-3">
        {LANGUAGES.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => handleLanguageChange(value)}
            className={`h-[34px] rounded-lg text-[11px] font-medium transition-colors ${
              language === value
                ? 'bg-[#2A9D8F] text-white font-bold'
                : 'border text-[var(--on-surface-variant)]'
            }`}
            style={language !== value ? { background: 'var(--surface-container-high)', borderColor: 'var(--outline)' } : undefined}
            role="radio"
            aria-checked={language === value}
          >
            {label}
          </button>
        ))}
      </div>
      <Divider />

      {/* ── Sounds ──────────────────────────────────────────────── */}
      <SectionLabel>{t('sounds')}</SectionLabel>
      <SoundRow
        label={t('incoming_call')}
        value={callSound}
        onChange={setCallSound}
      />
      <div className="h-2" />
      <SoundRow
        label={t('consultation_request')}
        value={requestSound}
        onChange={setRequestSound}
      />
    </div>
  );
}

/* ── Sub-components ─────────────────────────────────────────────────────── */

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p
      className="text-[11px] font-bold tracking-wider mb-2"
      style={{ color: 'var(--on-surface-variant)' }}
    >
      {children}
    </p>
  );
}

function Divider() {
  return <hr className="my-3 border-0 h-px" style={{ background: 'var(--outline)' }} />;
}

function ToggleRow({
  label,
  subtitle,
  checked,
  onChange,
}: {
  label: string;
  subtitle: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between py-2">
      <div>
        <p className="text-[14px] font-medium" style={{ color: 'var(--on-surface)' }}>
          {label}
        </p>
        <p className="text-[12px]" style={{ color: 'var(--on-surface-variant)' }}>
          {subtitle}
        </p>
      </div>
      <button
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative w-11 h-6 rounded-full transition-colors ${
          checked ? 'bg-[#2A9D8F]' : 'bg-[#D1D5DB]'
        }`}
      >
        <span
          className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-sm transition-transform ${
            checked ? 'translate-x-[22px]' : 'translate-x-0.5'
          }`}
        />
      </button>
    </div>
  );
}

function SoundRow({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string | null;
  onChange: (v: string | null) => void;
}) {
  const [playing, setPlaying] = useState(false);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  function preview(ringtoneId: string) {
    // Stop any currently playing preview
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current = null;
    }

    if (playing) {
      setPlaying(false);
      return;
    }

    const file = getRingtoneFile(ringtoneId);
    if (!file) {
      // Default beep — use Web Audio
      try {
        const ctx = new AudioContext();
        if (ctx.state === 'suspended') ctx.resume();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.frequency.value = 880;
        gain.gain.value = 0.3;
        osc.start();
        osc.stop(ctx.currentTime + 0.5);
      } catch { /* no audio */ }
      return;
    }

    const audio = new Audio(file);
    audioRef.current = audio;
    setPlaying(true);
    audio.play().catch(() => setPlaying(false));
    audio.onended = () => { setPlaying(false); audioRef.current = null; };
  }

  return (
    <div
      className="rounded-[10px] border p-3"
      style={{ background: 'var(--surface-container-high)', borderColor: 'var(--outline)' }}
    >
      <p className="text-[13px] font-medium mb-1.5" style={{ color: 'var(--on-surface)' }}>
        {label}
      </p>
      <div className="flex items-center gap-2">
        <select
          value={value ?? 'default'}
          onChange={(e) => {
            const v = e.target.value === 'default' ? null : e.target.value;
            onChange(v);
            // Stop preview on change
            if (audioRef.current) { audioRef.current.pause(); audioRef.current = null; setPlaying(false); }
          }}
          className="flex-1 text-[11px] bg-transparent border border-[var(--outline)] rounded-md px-2 py-1.5 outline-none cursor-pointer"
          style={{ color: value ? '#2A9D8F' : 'var(--on-surface-variant)' }}
        >
          {RINGTONES.map((r) => (
            <option key={r.id} value={r.id}>
              {r.label}
            </option>
          ))}
        </select>
        <button
          onClick={() => preview(value ?? 'default')}
          className="w-7 h-7 rounded-full bg-[#2A9D8F]/10 flex items-center justify-center shrink-0 hover:bg-[#2A9D8F]/20 transition-colors"
          title={playing ? 'Stop' : 'Preview'}
        >
          {playing ? <Square size={12} className="text-[#2A9D8F]" /> : <Play size={12} className="text-[#2A9D8F]" />}
        </button>
        {value && (
          <button
            onClick={() => { onChange(null); if (audioRef.current) { audioRef.current.pause(); audioRef.current = null; setPlaying(false); } }}
            className="text-[11px] font-medium text-[#DC2626] flex items-center gap-1 shrink-0"
          >
            <RotateCcw size={10} /> Reset
          </button>
        )}
      </div>
    </div>
  );
}
