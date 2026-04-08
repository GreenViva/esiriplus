'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import { invokeEdgeFunction, ensureFreshToken } from '@/lib/supabase';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';

const THROTTLE_MS = 2000;
const AUTO_CLEAR_MS = 3000;
const DISPLAY_TIMEOUT_MS = 5000;
const POLL_INTERVAL_MS = 30000; // 30s fallback (realtime is primary)

interface UseTypingIndicatorOptions {
  consultationId: string;
  userId: string;
  role: 'patient' | 'doctor';
  enabled?: boolean;
}

export function useTypingIndicator({
  consultationId,
  userId,
  role,
  enabled = true,
}: UseTypingIndicatorOptions) {
  const db = useSupabase();
  const [otherPartyTyping, setOtherPartyTyping] = useState(false);
  const lastSendRef = useRef(0);
  const autoClearRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const displayTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Realtime subscription for typing indicators
  useEffect(() => {
    if (!enabled || !db || !consultationId || !userId) return;
    const channel = db
      .channel(`typing-${consultationId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'typing_indicators',
          filter: `consultation_id=eq.${consultationId}`,
        },
        (payload) => {
          const row = payload.new as { user_id: string; is_typing: boolean } | undefined;
          if (!row || row.user_id === userId) return;
          if (row.is_typing) {
            setOtherPartyTyping(true);
            if (displayTimeoutRef.current) clearTimeout(displayTimeoutRef.current);
            displayTimeoutRef.current = setTimeout(() => setOtherPartyTyping(false), DISPLAY_TIMEOUT_MS);
          } else {
            setOtherPartyTyping(false);
          }
        },
      )
      .subscribe();
    return () => { db.removeChannel(channel); };
  }, [db, consultationId, userId, enabled]);

  // Send typing indicator to server (throttled, best-effort)
  const sendTyping = useCallback(
    async (isTyping: boolean) => {
      if (!enabled || !consultationId || !userId) return;
      try {
        const token =
          role === 'patient'
            ? useAuthStore.getState().session?.accessToken
            : useAuthStore.getState().session?.accessToken;
        if (!token) return;

        if (role === 'patient') await ensureFreshToken();

        await invokeEdgeFunction(
          'handle-messages',
          {
            action: 'typing',
            consultation_id: consultationId,
            user_id: userId,
            is_typing: isTyping,
          },
          token,
          role,
        );
      } catch {
        // Best-effort — never block the chat
      }
    },
    [consultationId, userId, role, enabled],
  );

  // Called on every keystroke in the input
  const onTypingChange = useCallback(
    (isTyping: boolean) => {
      if (!enabled) return;

      if (autoClearRef.current) clearTimeout(autoClearRef.current);

      if (isTyping) {
        const now = Date.now();
        if (now - lastSendRef.current > THROTTLE_MS) {
          lastSendRef.current = now;
          sendTyping(true);
        }
        // Auto-clear after user stops typing
        autoClearRef.current = setTimeout(() => {
          sendTyping(false);
        }, AUTO_CLEAR_MS);
      } else {
        sendTyping(false);
      }
    },
    [enabled, sendTyping],
  );

  // Poll for other party's typing status
  useEffect(() => {
    if (!enabled || !consultationId || !userId) return;

    let stopped = false;

    async function pollTyping() {
      if (stopped) return;
      try {
        const token = useAuthStore.getState().session?.accessToken;
        if (!token) return;

        if (role === 'patient') await ensureFreshToken();

        const result = await invokeEdgeFunction<{
          typing_indicators?: Array<{
            user_id: string;
            is_typing: boolean;
            updated_at: string;
          }>;
        }>(
          'handle-messages',
          {
            action: 'get_typing',
            consultation_id: consultationId,
          },
          token,
          role,
        );

        if (stopped) return;

        const indicators = result?.typing_indicators ?? [];
        const otherTyping = indicators.some(
          (t) => t.user_id !== userId && t.is_typing,
        );

        if (otherTyping) {
          setOtherPartyTyping(true);
          if (displayTimeoutRef.current) clearTimeout(displayTimeoutRef.current);
          displayTimeoutRef.current = setTimeout(() => {
            setOtherPartyTyping(false);
          }, DISPLAY_TIMEOUT_MS);
        } else {
          setOtherPartyTyping(false);
        }
      } catch {
        // Best-effort
      }
    }

    pollTyping();
    const interval = setInterval(pollTyping, POLL_INTERVAL_MS);

    return () => {
      stopped = true;
      clearInterval(interval);
      if (displayTimeoutRef.current) clearTimeout(displayTimeoutRef.current);
    };
  }, [consultationId, userId, role, enabled]);

  // Cleanup: send typing=false on unmount
  useEffect(() => {
    return () => {
      if (autoClearRef.current) clearTimeout(autoClearRef.current);
      sendTyping(false);
    };
  }, [sendTyping]);

  return { otherPartyTyping, onTypingChange };
}
