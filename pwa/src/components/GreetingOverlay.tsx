'use client';

import { useState, useEffect, useRef } from 'react';
import { Phone, FileText, ChevronDown } from 'lucide-react';
import { translate, type TranslationKeys } from '@/i18n';

type Phase = 'TYPING' | 'MSG_WELCOME' | 'MSG_SERVE' | 'MSG_CHOICES' | 'DONE';

interface Props {
  doctorName?: string;
  lang?: string;
  consultationId: string;
  onChooseText: (autoMessage: string) => void;
  onChooseCall: (type: 'AUDIO' | 'VIDEO') => void;
  onDismiss: () => void;
}

export default function GreetingOverlay({
  doctorName, lang = 'en', consultationId,
  onChooseText, onChooseCall, onDismiss,
}: Props) {
  const [phase, setPhase] = useState<Phase>('TYPING');
  const [showCallDropdown, setShowCallDropdown] = useState(false);
  const dismissed = useRef(false);

  useEffect(() => {
    // Check if already dismissed for this consultation
    const key = `greeting_dismissed_${consultationId}`;
    if (localStorage.getItem(key)) {
      dismissed.current = true;
      onDismiss();
      return;
    }

    const timers = [
      setTimeout(() => setPhase('MSG_WELCOME'), 1200),
      setTimeout(() => setPhase('MSG_SERVE'), 3000),
      setTimeout(() => setPhase('MSG_CHOICES'), 4800),
    ];
    return () => timers.forEach(clearTimeout);
  }, [consultationId, onDismiss]);

  function markDismissed() {
    localStorage.setItem(`greeting_dismissed_${consultationId}`, '1');
  }

  function handleText() {
    markDismissed();
    onChooseText(translate('greeting_text_auto_message', lang));
  }

  function handleCall(type: 'AUDIO' | 'VIDEO') {
    markDismissed();
    setShowCallDropdown(false);
    onChooseCall(type);
  }

  if (dismissed.current) return null;

  const showWelcome = phase === 'MSG_WELCOME' || phase === 'MSG_SERVE' || phase === 'MSG_CHOICES';
  const showServe = phase === 'MSG_SERVE' || phase === 'MSG_CHOICES';
  const showChoices = phase === 'MSG_CHOICES';
  const showTyping = phase === 'TYPING' || (phase === 'MSG_WELCOME' && false) || (phase === 'MSG_SERVE' && false);

  const serveText = doctorName
    ? translate('greeting_here_to_serve', lang, { name: doctorName })
    : translate('greeting_here_to_serve_generic', lang);

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex flex-col justify-end p-4 pb-8">
      <div className="max-w-md mx-auto w-full space-y-3">
        {/* Typing indicator */}
        {phase === 'TYPING' && <TypingBubble />}

        {/* Welcome message */}
        {showWelcome && (
          <MessageBubble text={translate('greeting_welcome', lang)} />
        )}

        {/* Between welcome and serve — show typing briefly */}
        {phase === 'MSG_WELCOME' && <TypingBubble />}

        {/* Serve message */}
        {showServe && (
          <MessageBubble text={serveText} />
        )}

        {/* Between serve and choices — show typing briefly */}
        {phase === 'MSG_SERVE' && <TypingBubble />}

        {/* How to proceed */}
        {showChoices && (
          <>
            <MessageBubble text={translate('greeting_how_to_proceed', lang)} />

            {/* Choice buttons */}
            <div className="flex gap-3 pt-2">
              <div className="relative flex-1">
                <button
                  onClick={() => setShowCallDropdown((v) => !v)}
                  className="w-full h-[52px] rounded-[14px] bg-white text-[#2A9D8F] font-bold text-[15px] flex items-center justify-center gap-2 shadow-md hover:shadow-lg transition-shadow"
                >
                  <Phone size={18} />
                  {translate('greeting_choice_call', lang)}
                  <ChevronDown size={14} />
                </button>
                {showCallDropdown && (
                  <div className="absolute bottom-full mb-2 left-0 right-0 bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden z-10">
                    <button onClick={() => handleCall('AUDIO')}
                      className="w-full px-4 py-3 text-sm font-medium text-black hover:bg-gray-50 text-left">
                      Voice Call
                    </button>
                    <button onClick={() => handleCall('VIDEO')}
                      className="w-full px-4 py-3 text-sm font-medium text-black hover:bg-gray-50 text-left border-t border-gray-100">
                      Video Call
                    </button>
                  </div>
                )}
              </div>
              <button
                onClick={handleText}
                className="flex-1 h-[52px] rounded-[14px] bg-[#2A9D8F] text-white font-bold text-[15px] flex items-center justify-center gap-2 shadow-md hover:shadow-lg transition-shadow"
              >
                <FileText size={18} />
                {translate('greeting_choice_text', lang)}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function MessageBubble({ text }: { text: string }) {
  return (
    <div
      className="bg-[#2A9D8F] text-white text-[15px] font-medium px-4 py-2.5 rounded-2xl rounded-bl-[4px] shadow-md w-fit max-w-[85%]"
      style={{ animation: 'slideUp 300ms ease-out' }}
    >
      {text}
    </div>
  );
}

function TypingBubble() {
  const [dots, setDots] = useState('.');
  useEffect(() => {
    const interval = setInterval(() => {
      setDots((d) => (d.length >= 3 ? '.' : d + '.'));
    }, 400);
    return () => clearInterval(interval);
  }, []);

  return (
    <div
      className="bg-[#2A9D8F]/80 text-white text-[22px] font-bold px-5 py-1.5 rounded-xl w-fit"
      style={{ animation: 'fadeIn 200ms ease-out' }}
    >
      {dots}
    </div>
  );
}
