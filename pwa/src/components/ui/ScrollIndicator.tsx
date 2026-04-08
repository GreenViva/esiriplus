'use client';

import { useEffect, useState, useRef } from 'react';
import { ChevronDown } from 'lucide-react';

interface ScrollIndicatorProps {
  containerRef: React.RefObject<HTMLElement | null>;
}

export function ScrollIndicator({ containerRef }: ScrollIndicatorProps) {
  const [showArrow, setShowArrow] = useState(false);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const check = () => {
      const canScroll = el.scrollHeight > el.clientHeight;
      const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 20;
      setShowArrow(canScroll && !atBottom);
    };

    check();
    el.addEventListener('scroll', check, { passive: true });
    const ro = new ResizeObserver(check);
    ro.observe(el);

    return () => {
      el.removeEventListener('scroll', check);
      ro.disconnect();
    };
  }, [containerRef]);

  if (!showArrow) return null;

  return (
    <div className="absolute bottom-3 left-1/2 -translate-x-1/2 pointer-events-none z-10">
      <div className="animate-pulse-bounce w-9 h-9 rounded-full bg-[var(--brand-teal)]/15 shadow flex items-center justify-center">
        <ChevronDown size={20} className="text-[var(--brand-teal)]" />
      </div>
    </div>
  );
}
