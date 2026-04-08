'use client';

import { useState, useEffect, useRef } from 'react';
import { Download, X, Smartphone } from 'lucide-react';
import { useTranslation } from '@/hooks/useTranslation';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export default function InstallPrompt() {
  const [show, setShow] = useState(false);
  const [isIos, setIsIos] = useState(false);
  const deferredPrompt = useRef<BeforeInstallPromptEvent | null>(null);

  useEffect(() => {
    // Don't show if already installed (standalone mode)
    if (window.matchMedia('(display-mode: standalone)').matches) return;

    // Check if dismissed recently (within 7 days)
    const dismissed = localStorage.getItem('install-prompt-dismissed');
    if (dismissed && Date.now() - Number(dismissed) < 7 * 24 * 60 * 60 * 1000) return;

    // iOS detection — Safari doesn't fire beforeinstallprompt
    const ua = navigator.userAgent;
    const iosDevice = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    const isSafari = /Safari/.test(ua) && !/Chrome|CriOS|FxiOS/.test(ua);

    if (iosDevice && isSafari) {
      setIsIos(true);
      // Show after a short delay so the page loads first
      const timer = setTimeout(() => setShow(true), 3000);
      return () => clearTimeout(timer);
    }

    // Chrome/Edge/etc — listen for beforeinstallprompt
    function handlePrompt(e: Event) {
      e.preventDefault();
      deferredPrompt.current = e as BeforeInstallPromptEvent;
      // Show after a short delay
      setTimeout(() => setShow(true), 2000);
    }

    window.addEventListener('beforeinstallprompt', handlePrompt);
    return () => window.removeEventListener('beforeinstallprompt', handlePrompt);
  }, []);

  async function handleInstall() {
    if (deferredPrompt.current) {
      await deferredPrompt.current.prompt();
      const result = await deferredPrompt.current.userChoice;
      if (result.outcome === 'accepted') {
        setShow(false);
      }
      deferredPrompt.current = null;
    }
  }

  function handleDismiss() {
    setShow(false);
    localStorage.setItem('install-prompt-dismissed', String(Date.now()));
  }

  const { t } = useTranslation();

  if (!show) return null;

  return (
    <div className="fixed bottom-20 left-4 right-4 z-[998] flex justify-center animate-[slideUp_300ms_ease-out] lg:left-auto lg:right-6 lg:max-w-sm">
      <div
        className="w-full rounded-2xl shadow-2xl border p-4"
        style={{ background: 'var(--surface, #fff)', borderColor: 'var(--card-border, #E5E7EB)' }}
      >
        {/* Close button */}
        <button
          onClick={handleDismiss}
          className="absolute top-3 right-3 p-1 rounded-full hover:bg-gray-100"
        >
          <X size={16} className="text-gray-400" />
        </button>

        <div className="flex items-start gap-3">
          {/* Icon */}
          <div className="w-12 h-12 rounded-xl bg-[#2A9D8F]/10 flex items-center justify-center shrink-0">
            <Smartphone size={24} className="text-[#2A9D8F]" />
          </div>

          <div className="flex-1 min-w-0">
            <h3 className="text-[15px] font-bold text-black">{t('install_app')}</h3>
            <p className="text-[12px] text-[var(--subtitle-grey)] mt-0.5">
              {isIos ? t('install_ios') : t('install_desc')}
            </p>

            {!isIos && (
              <button
                onClick={handleInstall}
                className="mt-3 w-full h-10 rounded-xl bg-[#2A9D8F] text-white text-[13px] font-semibold flex items-center justify-center gap-2 hover:bg-[#238377] transition-colors"
              >
                <Download size={16} />
                {t('install_button')}
              </button>
            )}

            {isIos && (
              <div className="mt-3 flex items-center gap-2 bg-[#2A9D8F]/5 rounded-lg px-3 py-2">
                <span className="text-[18px]">&#x2191;</span>
                <p className="text-[11px] text-[#2A9D8F] font-medium">
                  Tap <strong>Share</strong> then <strong>&quot;Add to Home Screen&quot;</strong>
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
