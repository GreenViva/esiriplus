'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import { Settings, X } from 'lucide-react';
import { AccessibilityPanel } from './AccessibilityPanel';

export default function AccessibilityFAB() {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [initialized, setInitialized] = useState(false);
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0, px: 0, py: 0 });
  const fabRef = useRef<HTMLDivElement>(null);
  const didDrag = useRef(false);

  // Initialize position on mount
  useEffect(() => {
    setPos({ x: window.innerWidth - 68, y: window.innerHeight - 68 });
    setInitialized(true);
  }, []);

  const clamp = useCallback((x: number, y: number) => {
    const size = 52;
    return {
      x: Math.max(8, Math.min(x, window.innerWidth - size - 8)),
      y: Math.max(8, Math.min(y, window.innerHeight - size - 8)),
    };
  }, []);

  const onPointerDown = useCallback((e: React.PointerEvent) => {
    dragging.current = true;
    didDrag.current = false;
    dragStart.current = { x: e.clientX, y: e.clientY, px: pos.x, py: pos.y };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  }, [pos]);

  const onPointerMove = useCallback((e: React.PointerEvent) => {
    if (!dragging.current) return;
    const dx = e.clientX - dragStart.current.x;
    const dy = e.clientY - dragStart.current.y;
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) didDrag.current = true;
    setPos(clamp(dragStart.current.px + dx, dragStart.current.py + dy));
  }, [clamp]);

  const onPointerUp = useCallback(() => {
    dragging.current = false;
  }, []);

  const handleClick = useCallback(() => {
    if (!didDrag.current) setOpen((v) => !v);
  }, []);

  if (!initialized) return null;

  return (
    <>
      {/* Scrim */}
      {open && (
        <div
          className="fixed inset-0 bg-black/30 z-[999]"
          onClick={() => setOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Panel */}
      {open && (
        <div
          className="fixed z-[1001]"
          style={{
            right: Math.max(16, window.innerWidth - pos.x),
            bottom: Math.max(80, window.innerHeight - pos.y + 16),
          }}
        >
          <AccessibilityPanel onDismiss={() => setOpen(false)} />
        </div>
      )}

      {/* FAB */}
      <div
        ref={fabRef}
        className="fixed z-[1000] select-none"
        style={{ left: pos.x, top: pos.y, touchAction: 'none' }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
      >
        {/* Directional arrows */}
        <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-[8px] text-[#2A9D8F]/50 select-none pointer-events-none">&#x25B2;</span>
        <span className="absolute -bottom-4 left-1/2 -translate-x-1/2 text-[8px] text-[#2A9D8F]/50 select-none pointer-events-none">&#x25BC;</span>
        <span className="absolute top-1/2 -left-4 -translate-y-1/2 text-[8px] text-[#2A9D8F]/50 select-none pointer-events-none">&#x25C0;</span>
        <span className="absolute top-1/2 -right-4 -translate-y-1/2 text-[8px] text-[#2A9D8F]/50 select-none pointer-events-none">&#x25B6;</span>

        {/* Button */}
        <button
          onClick={handleClick}
          className="w-[52px] h-[52px] rounded-full bg-[#2A9D8F] text-white shadow-lg hover:shadow-xl flex items-center justify-center transition-shadow"
          style={{ transform: open ? 'rotate(45deg)' : 'rotate(0deg)', transition: 'transform 250ms ease' }}
          role="button"
          aria-label="Accessibility settings"
          aria-expanded={open}
        >
          {open ? <X size={22} /> : <Settings size={22} />}
        </button>
      </div>
    </>
  );
}
