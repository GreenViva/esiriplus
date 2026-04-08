'use client';

import { useState, useRef, useCallback, useEffect } from 'react';

const THRESHOLD = 80;
const MAX_PULL = 130;
const RESISTANCE = 0.4;

export default function PullToRefresh({ children }: { children: React.ReactNode }) {
  const [pullDistance, setPullDistance] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const startY = useRef(0);
  const pulling = useRef(false);

  const isAtTop = useCallback(() => {
    return window.scrollY <= 0;
  }, []);

  const onTouchStart = useCallback((e: TouchEvent) => {
    if (refreshing) return;
    if (!isAtTop()) return;
    startY.current = e.touches[0].clientY;
    pulling.current = true;
  }, [refreshing, isAtTop]);

  const onTouchMove = useCallback((e: TouchEvent) => {
    if (!pulling.current || refreshing) return;
    const dy = e.touches[0].clientY - startY.current;
    if (dy < 0) {
      pulling.current = false;
      setPullDistance(0);
      return;
    }
    const distance = Math.min(MAX_PULL, dy * RESISTANCE);
    setPullDistance(distance);
  }, [refreshing]);

  const onTouchEnd = useCallback(() => {
    if (!pulling.current) return;
    pulling.current = false;

    if (pullDistance >= THRESHOLD) {
      setRefreshing(true);
      setPullDistance(THRESHOLD);
      window.location.reload();
    } else {
      setPullDistance(0);
    }
  }, [pullDistance]);

  useEffect(() => {
    document.addEventListener('touchstart', onTouchStart, { passive: true });
    document.addEventListener('touchmove', onTouchMove, { passive: true });
    document.addEventListener('touchend', onTouchEnd, { passive: true });
    return () => {
      document.removeEventListener('touchstart', onTouchStart);
      document.removeEventListener('touchmove', onTouchMove);
      document.removeEventListener('touchend', onTouchEnd);
    };
  }, [onTouchStart, onTouchMove, onTouchEnd]);

  return (
    <>
      {/* Pull indicator — rendered as a fixed overlay, outside content flow */}
      {pullDistance > 0 && (
        <div
          className="fixed top-0 left-0 right-0 z-[9999] flex justify-center pointer-events-none"
          style={{
            height: `${pullDistance}px`,
            transition: pulling.current ? 'none' : 'height 300ms ease-out',
          }}
        >
          <div
            className="flex items-center justify-center mt-auto mb-3"
            style={{
              opacity: Math.min(1, pullDistance / THRESHOLD),
              transform: `rotate(${(pullDistance / THRESHOLD) * 360}deg)`,
              transition: pulling.current ? 'none' : 'transform 300ms ease-out',
            }}
          >
            <div
              className={`w-8 h-8 rounded-full border-[3px] border-[#2A9D8F] border-t-transparent ${
                refreshing ? 'animate-spin' : ''
              }`}
            />
          </div>
        </div>
      )}

      {/* Children rendered without any wrapper transform — fixed positioning preserved */}
      {children}
    </>
  );
}
