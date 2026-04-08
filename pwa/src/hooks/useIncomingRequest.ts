'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '@/store/auth';
import { usePreferencesStore } from '@/store/preferences';
import { useSupabase } from '@/hooks/useSupabase';
import { invokeEdgeFunction } from '@/lib/supabase';
import { getRingtoneFile } from '@/components/AccessibilityPanel';
import type { IncomingRequestState, ConsultationRequest } from '@/types';

const REQUEST_TTL_SECONDS = 60;
const AUTO_DISMISS_MS = 1500;
const EXPIRE_DISMISS_MS = 2000;

const INITIAL_STATE: IncomingRequestState = {
  requestId: null,
  patientSessionId: null,
  serviceType: null,
  serviceTier: null,
  chiefComplaint: null,
  isFollowUp: false,
  isSubstituteFollowUp: false,
  secondsRemaining: 0,
  isResponding: false,
  responseStatus: null,
  errorMessage: null,
  canRetry: false,
  consultationId: null,
  symptoms: null,
  patientAgeGroup: null,
  patientSex: null,
  patientBloodGroup: null,
  patientAllergies: null,
  patientChronicConditions: null,
};

export function useIncomingRequest() {
  const db = useSupabase();
  const session = useAuthStore((s) => s.session);
  const [state, setState] = useState<IncomingRequestState>(INITIAL_STATE);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const dismissTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const activeRequestIdRef = useRef<string | null>(null);

  const doctorId = session?.user?.role === 'doctor' ? session.user.id : null;
  const token = session?.accessToken;

  // ── Cleanup helpers ───────────────────────────���──────────────────────────

  const stopCountdown = useCallback(() => {
    if (countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
    }
  }, []);

  const dismiss = useCallback(() => {
    stopCountdown();
    if (dismissTimerRef.current) {
      clearTimeout(dismissTimerRef.current);
      dismissTimerRef.current = null;
    }
    activeRequestIdRef.current = null;
    setState(INITIAL_STATE);
  }, [stopCountdown]);

  // ── Show an incoming request ─────────────────────────────────────────────

  const showRequest = useCallback(
    (row: ConsultationRequest) => {
      // Ignore if we already have an active request
      if (activeRequestIdRef.current) return;

      // Calculate remaining seconds from server expiry
      const expiresAt = new Date(row.expires_at).getTime();
      const remaining = Math.max(0, Math.floor((expiresAt - Date.now()) / 1000));
      if (remaining <= 0) return; // already expired

      activeRequestIdRef.current = row.request_id;

      setState({
        requestId: row.request_id,
        patientSessionId: row.patient_session_id,
        serviceType: row.service_type,
        serviceTier: row.service_tier ?? 'ECONOMY',
        chiefComplaint: (row as Record<string, unknown>).chief_complaint as string ?? null,
        isFollowUp: !!(row as Record<string, unknown>).is_follow_up,
        isSubstituteFollowUp: !!(row as Record<string, unknown>).is_substitute_follow_up,
        secondsRemaining: remaining,
        isResponding: false,
        responseStatus: null,
        errorMessage: null,
        canRetry: false,
        consultationId: null,
        symptoms: row.symptoms ?? null,
        patientAgeGroup: row.patient_age_group ?? null,
        patientSex: row.patient_sex ?? null,
        patientBloodGroup: row.patient_blood_group ?? null,
        patientAllergies: row.patient_allergies ?? null,
        patientChronicConditions: row.patient_chronic_conditions ?? null,
      });

      // Start countdown
      stopCountdown();
      const currentRequestId = row.request_id;
      countdownRef.current = setInterval(() => {
        setState((prev) => {
          if (!prev.requestId) return prev;
          const next = prev.secondsRemaining - 1;
          if (next <= 0) {
            clearInterval(countdownRef.current!);
            countdownRef.current = null;
            // Call expire on server (best-effort)
            const tok = useAuthStore.getState().session?.accessToken;
            if (tok) {
              invokeEdgeFunction('handle-consultation-request', {
                action: 'expire',
                request_id: currentRequestId,
              }, tok, 'doctor').catch(() => {});
            }
            dismissTimerRef.current = setTimeout(() => {
              activeRequestIdRef.current = null;
              setState(INITIAL_STATE);
            }, EXPIRE_DISMISS_MS);
            return { ...prev, secondsRemaining: 0, responseStatus: 'expired' };
          }
          return { ...prev, secondsRemaining: next };
        });
      }, 1000);

      // Play notification sound — use selected ringtone or default beep
      try {
        const selectedSound = usePreferencesStore.getState().requestSound;
        const ringtoneFile = getRingtoneFile(selectedSound);

        if (ringtoneFile) {
          // Play selected ringtone file
          const audio = new Audio(ringtoneFile);
          audio.play().catch(() => {});
        } else {
          // Default two-tone beep
          const audioCtx = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
          if (audioCtx.state === 'suspended') audioCtx.resume();
          const osc = audioCtx.createOscillator();
          const gain = audioCtx.createGain();
          osc.connect(gain);
          gain.connect(audioCtx.destination);
          osc.frequency.value = 880;
          gain.gain.value = 0.3;
          osc.start();
          osc.stop(audioCtx.currentTime + 0.3);
          setTimeout(() => {
            const osc2 = audioCtx.createOscillator();
            const gain2 = audioCtx.createGain();
            osc2.connect(gain2);
            gain2.connect(audioCtx.destination);
            osc2.frequency.value = 1100;
            gain2.gain.value = 0.3;
            osc2.start();
            osc2.stop(audioCtx.currentTime + 0.3);
          }, 350);
        }
      } catch {
        // audio not available (no user gesture yet)
      }

      // Vibrate
      try {
        if ('vibrate' in navigator) {
          navigator.vibrate([800, 400, 800, 400, 800]);
        }
      } catch {
        // non-critical
      }
    },
    [stopCountdown]
  );

  // ── Realtime subscription ────────────────────────────────────────────────

  useEffect(() => {
    if (!db || !doctorId) return;

    const channel = db
      .channel(`doctor-requests-${doctorId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'consultation_requests',
          filter: `doctor_id=eq.${doctorId}`,
        },
        (payload) => {
          const row = payload.new as ConsultationRequest;
          if (!row?.request_id) return;

          const status = row.status?.toLowerCase();

          if (status === 'pending' && payload.eventType === 'INSERT') {
            showRequest(row);
          } else if (
            (status === 'expired' || status === 'accepted' || status === 'rejected') &&
            row.request_id === activeRequestIdRef.current
          ) {
            // Server-side status update (e.g. expired by cron, or our own action echoed back)
            // Only handle if we haven't already processed locally
            setState((prev) => {
              if (prev.responseStatus) return prev; // already resolved locally
              stopCountdown();
              dismissTimerRef.current = setTimeout(() => {
                activeRequestIdRef.current = null;
                setState(INITIAL_STATE);
              }, status === 'expired' ? EXPIRE_DISMISS_MS : AUTO_DISMISS_MS);
              return {
                ...prev,
                responseStatus: status as 'accepted' | 'rejected' | 'expired',
                consultationId: row.consultation_id ?? prev.consultationId,
              };
            });
          }
        }
      )
      .subscribe();

    return () => {
      db.removeChannel(channel);
    };
  }, [db, doctorId, showRequest, stopCountdown]);

  // ── Accept ───────────────────────────────────────────────────────────────

  const acceptRequest = useCallback(async () => {
    const requestId = state.requestId;
    if (!requestId || !token) return;

    setState((prev) => ({ ...prev, isResponding: true, errorMessage: null }));

    try {
      const result = await invokeEdgeFunction<{
        request_id: string;
        status: string;
        consultation_id?: string;
      }>('handle-consultation-request', { action: 'accept', request_id: requestId }, token, 'doctor');

      stopCountdown();

      const consultationId = result.consultation_id ?? null;

      setState((prev) => ({
        ...prev,
        isResponding: false,
        responseStatus: 'accepted',
        consultationId,
        errorMessage: null,
        canRetry: false,
      }));

      // Auto-dismiss after showing success
      dismissTimerRef.current = setTimeout(() => {
        activeRequestIdRef.current = null;
        setState(INITIAL_STATE);
      }, AUTO_DISMISS_MS);
    } catch (err) {
      stopCountdown();
      setState((prev) => ({
        ...prev,
        isResponding: false,
        errorMessage: err instanceof Error ? err.message : 'Failed to accept request',
        canRetry: true,
      }));
    }
  }, [state.requestId, token, stopCountdown]);

  // ── Reject ───────────────────────────────────────────────────────────────

  const rejectRequest = useCallback(async () => {
    const requestId = state.requestId;
    if (!requestId || !token) return;

    setState((prev) => ({ ...prev, isResponding: true, errorMessage: null }));

    try {
      await invokeEdgeFunction('handle-consultation-request', { action: 'reject', request_id: requestId }, token, 'doctor');

      stopCountdown();

      setState((prev) => ({
        ...prev,
        isResponding: false,
        responseStatus: 'rejected',
        errorMessage: null,
        canRetry: false,
      }));

      dismissTimerRef.current = setTimeout(() => {
        activeRequestIdRef.current = null;
        setState(INITIAL_STATE);
      }, AUTO_DISMISS_MS);
    } catch (err) {
      stopCountdown();
      setState((prev) => ({
        ...prev,
        isResponding: false,
        errorMessage: err instanceof Error ? err.message : 'Failed to reject request',
        canRetry: true,
      }));
    }
  }, [state.requestId, token, stopCountdown]);

  // ── Cleanup on unmount ───────────────────────────────────────────────────

  useEffect(() => {
    return () => {
      stopCountdown();
      if (dismissTimerRef.current) clearTimeout(dismissTimerRef.current);
    };
  }, [stopCountdown]);

  return {
    state,
    acceptRequest,
    rejectRequest,
    dismiss,
  };
}
