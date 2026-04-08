'use client';

import { useState, useEffect, useRef, useCallback, use } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  Send,
  Paperclip,
  Phone,
  Video,
  Clock,
  Star,
  X,
  AlertTriangle,
  CheckCircle2,
  MessageSquare,
  Loader2,
  FileText,
  Image as ImageIcon,
  Camera,
} from 'lucide-react';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction, ensureFreshToken, getStorageClient } from '@/lib/supabase';
import { useSupabase } from '@/hooks/useSupabase';
import { useTypingIndicator } from '@/hooks/useTypingIndicator';
import { usePreferencesStore } from '@/store/preferences';
import GreetingOverlay from '@/components/GreetingOverlay';
import type { Message, Consultation, ConsultationStatus } from '@/types';

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatTimer(seconds: number): string {
  const m = Math.floor(Math.abs(seconds) / 60);
  const s = Math.abs(seconds) % 60;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
}

function formatWaitTime(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s > 0 ? `${m}m ${s}s` : `${m}m`;
}

function formatMessageTime(ts: number | string): string {
  const d = new Date(ts);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function isSameMinute(a: number | string, b: number | string): boolean {
  const da = new Date(a);
  const db = new Date(b);
  return (
    da.getHours() === db.getHours() &&
    da.getMinutes() === db.getMinutes()
  );
}

// ── Component ────────────────────────────────────────────────────────────────

export default function ConsultationChatPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: consultationId } = use(params);
  const router = useRouter();
  const { session } = useAuthStore();
  const userId = session?.user?.id;

  // Core state
  const [messages, setMessages] = useState<Message[]>([]);
  const [consultation, setConsultation] = useState<Consultation | null>(null);
  const [loading, setLoading] = useState(true);
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);

  // Timer state
  const [remainingTime, setRemainingTime] = useState<number | null>(null);
  const [waitTime, setWaitTime] = useState(0);

  // Rating state
  const [rating, setRating] = useState(0);
  const [ratingHover, setRatingHover] = useState(0);
  const [ratingComment, setRatingComment] = useState('');
  const [ratingSubmitted, setRatingSubmitted] = useState(false);
  const [submittingRating, setSubmittingRating] = useState(false);

  // Greeting overlay
  const [showGreeting, setShowGreeting] = useState(false);
  const greetingLang = usePreferencesStore((s) => s.language);

  // Image lightbox
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  // Connection state
  const db = useSupabase();
  const [connectionStatus, setConnectionStatus] = useState<'connected' | 'reconnecting' | 'offline'>('connected');

  // Typing indicator (initialized after status vars below)

  // Refs
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const waitStartRef = useRef<number>(Date.now());

  const status = consultation?.status;

  const isActive = status === 'active' || status === 'in_progress';
  const isPending = status === 'pending';
  const isAwaitingExtension = status === 'awaiting_extension';
  const isGracePeriod = status === 'grace_period';
  const isCompleted = status === 'completed';
  const isCancelled = status === 'cancelled' || status === 'expired';
  const canChat = isActive || isGracePeriod;
  const canCall = isActive;

  // Typing indicator
  const patientSessionId = useAuthStore.getState().patientSession?.sessionId ?? '';
  const { otherPartyTyping, onTypingChange } = useTypingIndicator({
    consultationId,
    userId: patientSessionId,
    role: 'patient',
    enabled: canChat,
  });

  // ── Realtime messages ────────────────────────────────────────────────────────

  useEffect(() => {
    if (!consultationId || !db) return;
    const channel = db
      .channel(`patient-msgs-${consultationId}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'messages',
          filter: `consultation_id=eq.${consultationId}`,
        },
        (payload) => {
          const msg = payload.new as Message;
          if (msg?.message_id) {
            setMessages((prev) => {
              if (prev.some((m) => m.message_id === msg.message_id)) return prev;
              return [...prev, msg];
            });
          }
        },
      )
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') setConnectionStatus('connected');
      });
    return () => { db.removeChannel(channel); };
  }, [consultationId, db]);

  // ── Scroll to bottom ────────────────────────────────────────────────────────

  const scrollToBottom = useCallback((smooth = true) => {
    messagesEndRef.current?.scrollIntoView({
      behavior: smooth ? 'smooth' : 'instant',
    });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  // ── Load consultation + messages ────────────────────────────────────────────

  const loadData = useCallback(async () => {
    try {
      const currentToken = useAuthStore.getState().session?.accessToken;

      // Load consultation and messages via edge functions (RLS blocks anon reads)
      const [consultResult, msgResult] = await Promise.all([
        invokeEdgeFunction<Consultation>(
          'manage-consultation',
          { action: 'sync', consultation_id: consultationId },
          currentToken ?? undefined,
          'patient',
        ).catch(() => null),
        invokeEdgeFunction<Message[]>(
          'handle-messages',
          { action: 'get', consultation_id: consultationId, include_parent: true },
          currentToken ?? undefined,
          'patient',
        ).catch(() => null),
      ]);

      if (consultResult?.consultation_id) {
        setConsultation(consultResult as Consultation);
      }

      if (Array.isArray(msgResult)) {
        setMessages(msgResult.map((m) => ({
          ...m,
          is_from_previous_session: m.is_from_previous_session ?? m.consultation_id !== consultationId,
        })));
        // Show greeting on new consultations with no messages (not follow-ups)
        const currentMsgs = msgResult.filter((m) => m.consultation_id === consultationId);
        const isFollowUp = !!(consultResult as Record<string, unknown>)?.parent_consultation_id;
        if (currentMsgs.length === 0 && !isFollowUp) {
          setShowGreeting(true);
        }
      }
    } finally {
      setLoading(false);
    }
  }, [consultationId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Status polling merged into the message poller above

  // ── Wait timer (pending) ────────────────────────────────────────────────────

  useEffect(() => {
    if (!isPending) return;
    waitStartRef.current = Date.now();
    const interval = setInterval(() => {
      setWaitTime(Math.floor((Date.now() - waitStartRef.current) / 1000));
    }, 1000);
    return () => clearInterval(interval);
  }, [isPending]);

  // ── Consultation countdown timer ────────────────────────────────────────────

  useEffect(() => {
    if (!consultation) return;

    const endAtStr = isGracePeriod
      ? consultation.grace_period_end_at
      : consultation.scheduled_end_at;
    const endAt = endAtStr ? new Date(endAtStr).getTime() : null;

    if (!endAtStr || !endAt) {
      setRemainingTime(null);
      return;
    }

    function tick() {
      const now = Date.now();
      const remaining = Math.max(0, Math.floor((endAt! - now) / 1000));
      setRemainingTime(remaining);
    }

    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [consultation, isGracePeriod]);

  // ── Smart polling with backoff, visibility pause, and token refresh ────────

  const pollCountRef = useRef(0);
  const consecutiveFailsRef = useRef(0);
  const isVisibleRef = useRef(true);
  const isOnlineRef = useRef(typeof navigator !== 'undefined' ? navigator.onLine : true);

  useEffect(() => {
    if (isCompleted && !canChat && !isPending && !isAwaitingExtension) return;

    let timeoutId: ReturnType<typeof setTimeout> | null = null;
    let stopped = false;

    // ── Visibility change: pause polling when tab is hidden ───────────
    function handleVisibility() {
      isVisibleRef.current = document.visibilityState === 'visible';
      // Resume immediately when tab becomes visible
      if (isVisibleRef.current && !stopped) {
        consecutiveFailsRef.current = 0;
        schedulePoll(0);
      }
    }

    // ── Online/offline detection ──────────────────────────────────────
    function handleOnline() {
      isOnlineRef.current = true;
      setConnectionStatus('reconnecting');
      consecutiveFailsRef.current = 0;
      schedulePoll(0);
    }
    function handleOffline() {
      isOnlineRef.current = false;
      setConnectionStatus('offline');
    }

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    // ── Adaptive interval based on consecutive failures ───────────────
    function getInterval(): number {
      const fails = consecutiveFailsRef.current;
      if (fails === 0) return 5_000;   // 5s normal
      if (fails <= 2) return 10_000;   // 10s after 1-2 fails
      if (fails <= 5) return 20_000;   // 20s after 3-5 fails
      return 30_000;                   // 30s cap
    }

    function schedulePoll(delayMs?: number) {
      if (stopped) return;
      if (timeoutId) clearTimeout(timeoutId);
      timeoutId = setTimeout(poll, delayMs ?? getInterval());
    }

    async function poll() {
      if (stopped) return;
      // Skip if tab hidden or offline
      if (!isVisibleRef.current || !isOnlineRef.current) {
        schedulePoll();
        return;
      }

      try {
        // Ensure token is still valid; refresh if near expiry
        await ensureFreshToken();

        const currentToken = useAuthStore.getState().session?.accessToken;
        if (!currentToken) { schedulePoll(); return; }

        const msgResult = await invokeEdgeFunction<Message[]>(
          'handle-messages',
          { action: 'get', consultation_id: consultationId },
          currentToken,
          'patient',
        );

        if (Array.isArray(msgResult)) {
          setMessages((prev) => {
            const realMsgs = msgResult;
            const tempMsgs = prev.filter((m) => m.message_id.startsWith('temp-'));
            const remainingTemps = tempMsgs.filter(
              (t) => !realMsgs.some((r) => r.message_text === t.message_text && r.sender_type === t.sender_type)
            );
            return [...realMsgs, ...remainingTemps];
          });
        }

        // Success — reset backoff
        consecutiveFailsRef.current = 0;
        setConnectionStatus('connected');

        // Refresh consultation status every 3rd poll
        pollCountRef.current++;
        if (pollCountRef.current % 3 === 0 || isPending || isAwaitingExtension) {
          const consultResult = await invokeEdgeFunction<Consultation>(
            'manage-consultation',
            { action: 'sync', consultation_id: consultationId },
            currentToken,
            'patient',
          ).catch(() => null);

          if (consultResult?.consultation_id) {
            setConsultation(consultResult as Consultation);
          }
        }
      } catch {
        consecutiveFailsRef.current++;
        if (consecutiveFailsRef.current >= 2) {
          setConnectionStatus(isOnlineRef.current ? 'reconnecting' : 'offline');
        }
      }

      schedulePoll();
    }

    // Poll immediately on mount
    poll();

    return () => {
      stopped = true;
      if (timeoutId) clearTimeout(timeoutId);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [consultationId, canChat, isPending, isAwaitingExtension, isCompleted]);

  // ── Send text message ───────────────────────────────────────────────────────

  async function handleSend() {
    const text = inputText.trim();
    if (!text || sending || !canChat) return;

    setSending(true);
    setInputText('');

    // Optimistic add
    const patientSessionId = useAuthStore.getState().patientSession?.sessionId;
    const optimistic: Message = {
      message_id: `temp-${Date.now()}`,
      consultation_id: consultationId,
      sender_id: patientSessionId ?? userId!,
      sender_type: 'patient',
      message_text: text,
      message_type: 'text',
      created_at: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimistic]);

    try {
      await ensureFreshToken();
      const currentToken = useAuthStore.getState().session?.accessToken;
      await invokeEdgeFunction('handle-messages', {
        action: 'send',
        consultation_id: consultationId,
        sender_id: patientSessionId ?? userId,
        sender_type: 'patient',
        message_text: text,
        message_type: 'text',
      }, currentToken ?? undefined);
    } catch {
      // Remove optimistic on fail, restore input
      setMessages((prev) =>
        prev.filter((m) => m.message_id !== optimistic.message_id),
      );
      setInputText(text);
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }

  // ── Send attachment (image or PDF) ───────────────────────────────────────────

  const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
  const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif', 'application/pdf'];
  const [attachError, setAttachError] = useState<string | null>(null);
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const docInputRef = useRef<HTMLInputElement>(null);

  async function handleAttachment(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !canChat) return;
    setAttachError(null);

    // Validate type
    if (!ALLOWED_TYPES.includes(file.type)) {
      setAttachError('Only images and PDFs are supported');
      if (imageInputRef.current) imageInputRef.current.value = '';
      if (docInputRef.current) docInputRef.current.value = '';
      return;
    }

    // Validate size
    if (file.size > MAX_FILE_SIZE) {
      setAttachError('File must be under 10 MB');
      if (imageInputRef.current) imageInputRef.current.value = '';
      if (docInputRef.current) docInputRef.current.value = '';
      return;
    }

    const isImage = file.type.startsWith('image/');
    const messageType = isImage ? 'image' : 'document';
    const ext = file.name.split('.').pop() || (isImage ? 'jpg' : 'pdf');
    const storagePath = `${consultationId}/${crypto.randomUUID()}.${ext}`;

    setSending(true);
    try {
      await ensureFreshToken();
      const currentToken = useAuthStore.getState().session?.accessToken;
      if (!currentToken) throw new Error('No token');

      // Use authenticated client for storage (RLS requires "authenticated" role)
      const storageClient = getStorageClient(currentToken);
      const { error: uploadError } = await storageClient.storage
        .from('message-attachments')
        .upload(storagePath, file, { contentType: file.type, upsert: true });
      if (uploadError) throw uploadError;

      const { data: urlData } = storageClient.storage
        .from('message-attachments')
        .getPublicUrl(storagePath);

      const patientSessionId = useAuthStore.getState().patientSession?.sessionId;
      await invokeEdgeFunction('handle-messages', {
        action: 'send',
        consultation_id: consultationId,
        sender_id: patientSessionId ?? userId,
        sender_type: 'patient',
        message_text: file.name,
        message_type: messageType,
        attachment_url: urlData.publicUrl,
      }, currentToken, 'patient');
    } catch {
      setAttachError('Failed to send file. Please try again.');
    } finally {
      setSending(false);
      if (imageInputRef.current) imageInputRef.current.value = '';
      if (docInputRef.current) docInputRef.current.value = '';
      setShowAttachMenu(false);
    }
  }

  // ── Cancel consultation ─────────────────────────────────────────────────────

  async function handleCancel() {
    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      await invokeEdgeFunction('handle-messages', {
        action: 'cancel_consultation',
        consultation_id: consultationId,
      }, currentToken ?? undefined);
    } catch {
      // Silently fail
    }
    router.push('/home');
  }

  // ── Rate doctor ─────────────────────────────────────────────────────────────

  async function handleSubmitRating() {
    if (!rating || submittingRating) return;

    setSubmittingRating(true);
    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      await invokeEdgeFunction(
        'rate-doctor',
        {
          consultation_id: consultationId,
          rating,
          comment: ratingComment.trim() || null,
        },
        currentToken ?? undefined,
        'patient',
      );
    } catch (err) {
      console.warn('[Rating] Submit failed:', err);
    }
    setRatingSubmitted(true);
    setSubmittingRating(false);
  }

  // ── Follow-up days remaining ────────────────────────────────────────────────

  const followUpDaysLeft = consultation?.follow_up_expiry
    ? Math.max(
        0,
        Math.ceil(
          (new Date(consultation.follow_up_expiry).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
        ),
      )
    : 0;

  // ── Loading state ───────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-dvh bg-gray-50">
        <Loader2 size={32} className="text-[var(--brand-teal)] animate-spin" />
        <p className="mt-3 text-sm text-black">Loading consultation...</p>
      </div>
    );
  }

  // ════════════════════════════════════════════════════════════════════════════
  //  STATE 1: Waiting for Doctor (pending)
  // ════════════════════════════════════════════════════════════════════════════

  if (isPending) {
    return (
      <div className="flex flex-col h-dvh bg-gray-50">
        {/* Header */}
        <div className="bg-white border-b border-gray-200 px-4 py-3 shrink-0">
          <div className="flex items-center gap-3">
            <button
              onClick={() => router.push('/home')}
              className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors"
            >
              <ArrowLeft size={22} className="text-black" />
            </button>
            <h1 className="text-base font-bold text-black">Consultation</h1>
          </div>
        </div>

        {/* Connection status banner */}
        {connectionStatus !== 'connected' && (
          <div
            className={`px-4 py-2 flex items-center justify-center gap-2 shrink-0 ${
              connectionStatus === 'offline'
                ? 'bg-red-50 border-b border-red-200'
                : 'bg-amber-50 border-b border-amber-200'
            }`}
          >
            {connectionStatus === 'offline' ? (
              <>
                <AlertTriangle size={13} className="text-red-500 shrink-0" />
                <span className="text-xs font-semibold text-red-600">No internet connection</span>
              </>
            ) : (
              <>
                <Loader2 size={13} className="text-amber-600 animate-spin shrink-0" />
                <span className="text-xs font-semibold text-amber-700">Reconnecting...</span>
              </>
            )}
          </div>
        )}

        {/* Waiting content */}
        <div className="flex-1 flex flex-col items-center justify-center px-6">
          {/* Pulsing rings */}
          <div className="relative w-28 h-28 mb-8">
            <div className="absolute inset-0 rounded-full bg-[var(--brand-teal)]/10 animate-ping" />
            <div
              className="absolute inset-3 rounded-full bg-[var(--brand-teal)]/20"
              style={{ animation: 'ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite 0.3s' }}
            />
            <div className="absolute inset-6 rounded-full bg-[var(--brand-teal)]/30 flex items-center justify-center">
              <MessageSquare size={28} className="text-[var(--brand-teal)]" />
            </div>
          </div>

          <h2 className="text-lg font-bold text-black mb-2">
            Waiting for a doctor...
          </h2>
          <p className="text-sm text-black text-center mb-6 max-w-xs">
            A doctor will accept your consultation shortly. Please stay on this page.
          </p>

          {/* Wait timer */}
          <div className="flex items-center gap-2 px-4 py-2.5 bg-white rounded-2xl border border-gray-200 shadow-sm mb-8">
            <Clock size={16} className="text-[var(--brand-teal)]" />
            <span className="text-sm font-semibold text-black tabular-nums">
              {formatWaitTime(waitTime)}
            </span>
            <span className="text-sm text-black">elapsed</span>
          </div>

          {/* Cancel */}
          <button
            onClick={handleCancel}
            className="px-6 py-2.5 text-sm font-semibold text-red-600 bg-red-50 rounded-full hover:bg-red-100 transition-colors"
          >
            Cancel Request
          </button>
        </div>

        <div className="h-[env(safe-area-inset-bottom)]" />
      </div>
    );
  }

  // ════════════════════════════════════════════════════════════════════════════
  //  STATE 3: Awaiting Extension
  // ════════════════════════════════════════════════════════════════════════════

  if (isAwaitingExtension) {
    return (
      <div className="flex flex-col h-dvh bg-gray-50">
        {/* Header */}
        <ChatHeader
          router={router}
          remainingTime={0}
          canCall={false}
          consultationId={consultationId}
          isGracePeriod={false}
        />

        {/* Messages (read-only) */}
        <div
          ref={messagesContainerRef}
          className="flex-1 overflow-y-auto px-4 py-4"
        >
          <MessageList
            messages={messages}
            userId={userId}
            onImageTap={setLightboxUrl}
          />
          {otherPartyTyping && <TypingBubble />}
          <div ref={messagesEndRef} />
        </div>

        {/* Time's up overlay */}
        <div className="absolute inset-0 bg-black/40 flex items-center justify-center z-30">
          <div className="bg-white rounded-3xl p-8 mx-6 text-center shadow-2xl animate-[scaleIn_0.3s_ease-out]">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-amber-50 flex items-center justify-center">
              <AlertTriangle size={28} className="text-amber-500" />
            </div>
            <h3 className="text-xl font-bold text-black mb-2">Time's Up!</h3>
            <p className="text-sm text-black mb-4">
              Waiting for the doctor to extend the session...
            </p>
            <div className="flex items-center justify-center gap-2">
              <Loader2
                size={16}
                className="text-[var(--brand-teal)] animate-spin"
              />
              <span className="text-sm text-black font-medium">
                Waiting for extension...
              </span>
            </div>
          </div>
        </div>

        <DisabledInputBar />
      </div>
    );
  }

  // ════════════════════════════════════════════════════════════════════════════
  //  STATE 5: Completed → Mandatory Rating (full-screen, no dismiss)
  // ════════════════════════════════════════════════════════════════════════════

  if (isCompleted) {
    // After rating is submitted, redirect to home after a short delay
    const handleRatingDone = () => {
      router.replace('/home');
      // Fallback if Next.js router doesn't navigate
      setTimeout(() => { window.location.href = '/home'; }, 500);
    };

    return (
      <CompulsoryRatingScreen
        rating={rating}
        ratingHover={ratingHover}
        ratingComment={ratingComment}
        ratingSubmitted={ratingSubmitted}
        submittingRating={submittingRating}
        setRating={setRating}
        setRatingHover={setRatingHover}
        setRatingComment={setRatingComment}
        onSubmit={handleSubmitRating}
        onDone={handleRatingDone}
      />
    );
  }

  // ════════════════════════════════════════════════════════════════════════════
  //  STATE 6: Cancelled
  // ════════════════════════════════════════════════════════════════════════════

  if (isCancelled) {
    return (
      <div className="flex flex-col h-dvh bg-gray-50">
        <ChatHeader
          router={router}
          remainingTime={null}
          canCall={false}
          consultationId={consultationId}
          isGracePeriod={false}
        />

        <div className="bg-red-50 border-b border-red-100 px-4 py-2.5 flex items-center justify-center gap-2 shrink-0">
          <X size={14} className="text-red-500" />
          <span className="text-xs font-semibold text-red-600">
            Consultation Cancelled
          </span>
        </div>

        <div
          ref={messagesContainerRef}
          className="flex-1 overflow-y-auto px-4 py-4"
        >
          <MessageList
            messages={messages}
            userId={userId}
            onImageTap={setLightboxUrl}
          />
          {otherPartyTyping && <TypingBubble />}
          <div ref={messagesEndRef} />
        </div>

        <div className="bg-white border-t border-gray-200 px-4 py-3 shrink-0">
          <button
            onClick={() => router.replace('/home')}
            className="w-full h-11 rounded-full bg-gray-100 text-black text-sm font-semibold hover:bg-gray-200 transition-colors"
          >
            Back to Home
          </button>
          <div className="h-[env(safe-area-inset-bottom)]" />
        </div>

        {lightboxUrl && (
          <ImageLightbox url={lightboxUrl} onClose={() => setLightboxUrl(null)} />
        )}
      </div>
    );
  }

  // ════════════════════════════════════════════════════════════════════════════
  //  STATE 2 + 4: Active Chat / Grace Period
  // ════════════════════════════════════════════════════════════════════════════

  // Greeting handlers
  function handleGreetingText(autoMessage: string) {
    setShowGreeting(false);
    setInputText(autoMessage);
    // Auto-send after a tick
    setTimeout(() => {
      const fakeEvent = { trim: () => autoMessage } as unknown;
      void (async () => {
        const patientSessionId = useAuthStore.getState().patientSession?.sessionId;
        try {
          await ensureFreshToken();
          const currentToken = useAuthStore.getState().session?.accessToken;
          const optimistic: Message = {
            message_id: `temp-${Date.now()}`,
            consultation_id: consultationId,
            sender_id: patientSessionId ?? userId!,
            sender_type: 'patient',
            message_text: autoMessage,
            message_type: 'text',
            created_at: new Date().toISOString(),
          };
          setMessages((prev) => [...prev, optimistic]);
          setInputText('');
          await invokeEdgeFunction('handle-messages', {
            action: 'send',
            consultation_id: consultationId,
            sender_id: patientSessionId ?? userId,
            sender_type: 'patient',
            message_text: autoMessage,
            message_type: 'text',
          }, currentToken ?? undefined, 'patient');
        } catch { /* silent */ }
      })();
    }, 100);
  }

  function handleGreetingCall(type: 'AUDIO' | 'VIDEO') {
    setShowGreeting(false);
    router.push(`/video-call/${consultationId}?type=${type}`);
  }

  return (
    <div className="flex flex-col h-dvh bg-gray-50">
      {/* Greeting overlay */}
      {showGreeting && (
        <GreetingOverlay
          consultationId={consultationId}
          doctorName={undefined}
          lang={greetingLang}
          onChooseText={handleGreetingText}
          onChooseCall={handleGreetingCall}
          onDismiss={() => setShowGreeting(false)}
        />
      )}

      <ChatHeader
        router={router}
        remainingTime={remainingTime}
        canCall={canCall}
        consultationId={consultationId}
        isGracePeriod={isGracePeriod}
      />

      {/* Grace period warning */}
      {isGracePeriod && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 flex items-center gap-2 shrink-0 animate-[slideDown_0.3s_ease-out]">
          <AlertTriangle size={14} className="text-amber-600 shrink-0" />
          <span className="text-xs font-semibold text-amber-700">
            Grace period &mdash; session ending soon
          </span>
        </div>
      )}

      {/* Connection status banner */}
      {connectionStatus !== 'connected' && (
        <div
          className={`px-4 py-2 flex items-center justify-center gap-2 shrink-0 animate-[slideDown_0.3s_ease-out] ${
            connectionStatus === 'offline'
              ? 'bg-red-50 border-b border-red-200'
              : 'bg-amber-50 border-b border-amber-200'
          }`}
        >
          {connectionStatus === 'offline' ? (
            <>
              <AlertTriangle size={13} className="text-red-500 shrink-0" />
              <span className="text-xs font-semibold text-red-600">
                No internet connection
              </span>
            </>
          ) : (
            <>
              <Loader2 size={13} className="text-amber-600 animate-spin shrink-0" />
              <span className="text-xs font-semibold text-amber-700">
                Reconnecting...
              </span>
            </>
          )}
        </div>
      )}

      {/* Messages */}
      <div
        ref={messagesContainerRef}
        className="flex-1 overflow-y-auto px-4 py-4"
      >
        {messages.length === 0 && (
          <div className="text-center py-16">
            <div className="w-14 h-14 mx-auto mb-4 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
              <MessageSquare
                size={24}
                className="text-[var(--brand-teal)]"
              />
            </div>
            <p className="text-sm font-medium text-black">
              Start the conversation
            </p>
            <p className="text-xs text-black mt-1">
              Describe your symptoms to the doctor
            </p>
          </div>
        )}

        <MessageList
          messages={messages}
          userId={userId}
          onImageTap={setLightboxUrl}
        />
        {otherPartyTyping && <TypingBubble />}
        <div ref={messagesEndRef} />
      </div>

      {/* Attachment error toast */}
      {attachError && (
        <div className="absolute bottom-20 left-4 right-4 z-20 animate-[slideUp_0.2s_ease-out]">
          <div className="bg-red-600 text-white text-xs font-semibold px-4 py-2.5 rounded-xl shadow-lg flex items-center justify-between">
            <span>{attachError}</span>
            <button onClick={() => setAttachError(null)} className="ml-3 p-0.5">
              <X size={14} />
            </button>
          </div>
        </div>
      )}

      {/* Attachment picker menu */}
      {showAttachMenu && (
        <div className="bg-white border-t border-gray-100 px-4 py-3 shrink-0 animate-[slideUp_0.2s_ease-out]">
          <div className="flex gap-6 justify-center">
            <button
              onClick={() => { imageInputRef.current?.click(); }}
              className="flex flex-col items-center gap-1.5"
            >
              <div className="w-12 h-12 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
                <ImageIcon size={22} className="text-[var(--brand-teal)]" />
              </div>
              <span className="text-[11px] font-medium text-black">Photo</span>
            </button>
            <button
              onClick={() => { if ('capture' in HTMLInputElement.prototype) { imageInputRef.current?.setAttribute('capture', 'environment'); imageInputRef.current?.click(); imageInputRef.current?.removeAttribute('capture'); } else { imageInputRef.current?.click(); } }}
              className="flex flex-col items-center gap-1.5"
            >
              <div className="w-12 h-12 rounded-full bg-blue-50 flex items-center justify-center">
                <Camera size={22} className="text-blue-500" />
              </div>
              <span className="text-[11px] font-medium text-black">Camera</span>
            </button>
            <button
              onClick={() => { docInputRef.current?.click(); }}
              className="flex flex-col items-center gap-1.5"
            >
              <div className="w-12 h-12 rounded-full bg-amber-50 flex items-center justify-center">
                <FileText size={22} className="text-amber-600" />
              </div>
              <span className="text-[11px] font-medium text-black">Document</span>
            </button>
          </div>
        </div>
      )}

      {/* Hidden file inputs */}
      <input type="file" ref={imageInputRef} accept="image/*" className="hidden" onChange={handleAttachment} />
      <input type="file" ref={docInputRef} accept="application/pdf" className="hidden" onChange={handleAttachment} />

      {/* Input bar */}
      <div className="bg-white border-t border-gray-200 px-3 py-2.5 shrink-0">
        <div className="flex items-end gap-2">
          <button
            onClick={() => setShowAttachMenu((v) => !v)}
            className={`p-2 rounded-full transition-colors shrink-0 mb-0.5 ${
              showAttachMenu ? 'bg-[var(--brand-teal)]/10' : 'hover:bg-gray-100'
            }`}
            disabled={!canChat || sending}
          >
            <Paperclip
              size={20}
              className={canChat ? (showAttachMenu ? 'text-[var(--brand-teal)]' : 'text-gray-500') : 'text-gray-300'}
            />
          </button>

          <input
            ref={inputRef}
            type="text"
            placeholder="Type a message..."
            value={inputText}
            onChange={(e) => { setInputText(e.target.value); onTypingChange(e.target.value.length > 0); if (showAttachMenu) setShowAttachMenu(false); }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            disabled={!canChat}
            className="flex-1 h-10 px-4 text-sm text-black placeholder-gray-400 bg-gray-100 rounded-full border-0 focus:outline-none focus:ring-2 focus:ring-[var(--brand-teal)]/30 transition-all"
          />

          <button
            onClick={handleSend}
            disabled={!inputText.trim() || sending || !canChat}
            className="p-2.5 rounded-full bg-[var(--brand-teal)] text-white disabled:opacity-30 hover:bg-[#238377] active:scale-95 transition-all shrink-0 mb-0.5"
          >
            {sending ? (
              <Loader2 size={18} className="animate-spin" />
            ) : (
              <Send size={18} />
            )}
          </button>
        </div>
        <div className="h-[env(safe-area-inset-bottom)]" />
      </div>

      {/* Lightbox */}
      {lightboxUrl && (
        <ImageLightbox url={lightboxUrl} onClose={() => setLightboxUrl(null)} />
      )}
    </div>
  );
}

// ══════════════════════════════════════════════════════════════════════════════
//  Sub-components
// ══════════════════════════════════════════════════════════════════════════════

// ── Chat Header ──────────────────────────────────────────────────────────────

function ChatHeader({
  router,
  remainingTime,
  canCall,
  consultationId,
  isGracePeriod,
}: {
  router: ReturnType<typeof useRouter>;
  remainingTime: number | null;
  canCall: boolean;
  consultationId: string;
  isGracePeriod: boolean;
}) {
  const timerColor =
    remainingTime !== null && remainingTime <= 60
      ? 'text-red-600 bg-red-50'
      : remainingTime !== null && remainingTime <= 180
        ? 'text-amber-600 bg-amber-50'
        : isGracePeriod
          ? 'text-amber-600 bg-amber-50'
          : 'text-[var(--brand-teal)] bg-[var(--brand-teal)]/10';

  return (
    <div className="bg-white border-b border-gray-200 px-4 py-3 shrink-0 shadow-sm">
      <div className="flex items-center gap-3">
        {/* Back */}
        <button
          onClick={() => router.push('/home')}
          className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors"
        >
          <ArrowLeft size={22} className="text-black" />
        </button>

        {/* Title */}
        <div className="flex-1 min-w-0">
          <h1 className="text-base font-bold text-black">Consultation</h1>
        </div>

        {/* Timer pill */}
        {remainingTime !== null && (
          <div
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold tabular-nums ${timerColor}`}
          >
            <Clock size={13} />
            {formatTimer(remainingTime)}
          </div>
        )}

        {/* Call buttons */}
        <div className="flex items-center gap-0.5">
          <button
            onClick={() =>
              router.push(`/video-call/${consultationId}?type=AUDIO`)
            }
            className="p-2 rounded-full hover:bg-gray-100 transition-colors disabled:opacity-30"
            disabled={!canCall}
          >
            <Phone
              size={18}
              className={
                canCall ? 'text-[var(--brand-teal)]' : 'text-gray-300'
              }
            />
          </button>
          <button
            onClick={() =>
              router.push(`/video-call/${consultationId}?type=VIDEO`)
            }
            className="p-2 rounded-full hover:bg-gray-100 transition-colors disabled:opacity-30"
            disabled={!canCall}
          >
            <Video
              size={18}
              className={
                canCall ? 'text-[var(--brand-teal)]' : 'text-gray-300'
              }
            />
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Message List ─────────────────────────────────────────────────────────────

function TypingBubble() {
  return (
    <div className="flex justify-start px-1 py-1">
      <div className="bg-gray-100 rounded-2xl rounded-bl-md px-4 py-2.5 flex items-center gap-1">
        <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
        <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
        <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
      </div>
    </div>
  );
}

function MessageList({
  messages,
  userId,
  onImageTap,
}: {
  messages: Message[];
  userId: string | undefined;
  onImageTap: (url: string) => void;
}) {
  return (
    <div className="space-y-1">
      {messages.map((msg, i) => {
        const isPatient = msg.sender_type === 'patient';
        const isSystem = msg.sender_type === 'system';
        const isPrevSession = msg.is_from_previous_session;
        const isImage = msg.message_type === 'image';
        const isDocument = msg.message_type === 'document';
        const hasAttachment = isImage || isDocument;
        const prev = messages[i - 1];
        const next = messages[i + 1];

        // Show timestamp separator when messages are > 5 min apart
        const showDateSep =
          !prev ||
          new Date(msg.created_at).getTime() -
            new Date(prev.created_at).getTime() >
            5 * 60 * 1000;

        // Group consecutive messages from same sender
        const isFirstInGroup = !prev || prev.sender_id !== msg.sender_id || showDateSep;
        const isLastInGroup = !next || next.sender_id !== msg.sender_id;

        // Show time below last message in group
        const showTime =
          isLastInGroup ||
          !next ||
          !isSameMinute(msg.created_at, next.created_at);

        // Resolve the attachment URL (may be in attachment_url or message_text for older messages)
        const attachUrl = msg.attachment_url || (hasAttachment ? msg.message_text : null);

        // Previous/current session separators
        const showPrevSessionSep = isPrevSession && i === 0;
        const showCurrentSessionSep = !isPrevSession && prev?.is_from_previous_session;

        return (
          <div key={msg.message_id} className={isPrevSession ? 'opacity-55' : ''}>
            {showPrevSessionSep && (
              <div className="flex items-center gap-3 my-4">
                <div className="flex-1 border-t border-dashed border-gray-300" />
                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Previous session</span>
                <div className="flex-1 border-t border-dashed border-gray-300" />
              </div>
            )}
            {showCurrentSessionSep && (
              <div className="flex items-center gap-3 my-4">
                <div className="flex-1 border-t border-dashed border-gray-300" />
                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Current session</span>
                <div className="flex-1 border-t border-dashed border-gray-300" />
              </div>
            )}
            {isSystem ? (
              <div className="flex justify-center my-2">
                <span className="text-xs text-gray-400 italic bg-gray-100 px-3 py-1.5 rounded-full">
                  {msg.message_text}
                </span>
              </div>
            ) : (
            <>
            {/* Date / time separator */}
            {showDateSep && (
              <div className="flex justify-center py-3">
                <span className="text-[10px] text-gray-400 bg-white px-3 py-1 rounded-full shadow-sm border border-gray-100">
                  {new Date(msg.created_at).toLocaleDateString([], {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            )}

            <div
              className={`flex ${isPatient ? 'justify-end' : 'justify-start'} ${
                isFirstInGroup ? 'mt-3' : 'mt-0.5'
              }`}
              style={{
                animation: 'messageIn 0.25s ease-out',
              }}
            >
              <div
                className={`max-w-[78%] relative ${
                  isPatient
                    ? `bg-[var(--brand-teal)] text-white ${
                        isLastInGroup ? 'rounded-2xl rounded-br-md' : 'rounded-2xl'
                      }`
                    : `bg-white text-black border border-gray-100 shadow-sm ${
                        isLastInGroup ? 'rounded-2xl rounded-bl-md' : 'rounded-2xl'
                      }`
                } ${isImage ? 'p-1.5' : 'px-3.5 py-2'}`}
              >
                {isImage && attachUrl ? (
                  <button
                    onClick={() => onImageTap(attachUrl)}
                    className="block"
                  >
                    <img
                      src={attachUrl}
                      alt="Attachment"
                      className="rounded-xl max-w-full max-h-52 object-cover"
                      loading="lazy"
                    />
                  </button>
                ) : isDocument && attachUrl ? (
                  <a
                    href={`https://docs.google.com/gview?embedded=true&url=${encodeURIComponent(attachUrl)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2.5 no-underline"
                  >
                    <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                      isPatient ? 'bg-white/15' : 'bg-gray-100'
                    }`}>
                      <FileText size={20} className={isPatient ? 'text-white' : 'text-[var(--brand-teal)]'} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className={`text-[13px] font-medium truncate ${isPatient ? 'text-white' : 'text-black'}`}>
                        {msg.attachment_url ? msg.message_text : 'Document'}
                      </p>
                      <p className={`text-[11px] ${isPatient ? 'text-white/60' : 'text-gray-400'}`}>
                        Tap to view
                      </p>
                    </div>
                  </a>
                ) : (
                  <p className="text-[14.5px] leading-relaxed whitespace-pre-wrap break-words">
                    {msg.message_text}
                  </p>
                )}

                {showTime && (
                  <p
                    className={`text-[10px] mt-1 ${
                      isImage ? 'px-2 pb-1' : ''
                    } ${
                      isPatient
                        ? 'text-white/60 text-right'
                        : 'text-gray-400 text-right'
                    }`}
                  >
                    {formatMessageTime(msg.created_at)}
                  </p>
                )}
              </div>
            </div>
            </>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ── Disabled Input Bar (for read-only states) ────────────────────────────────

function DisabledInputBar() {
  return (
    <div className="bg-white border-t border-gray-200 px-4 py-3 shrink-0">
      <div className="flex items-center justify-center h-10">
        <span className="text-sm text-gray-400">Chat is not available</span>
      </div>
      <div className="h-[env(safe-area-inset-bottom)]" />
    </div>
  );
}

// ── Compulsory Rating Screen (full-page, no dismiss) ────────────────────────

const RATING_LABELS = ['', 'Poor', 'Fair', 'Good', 'Very Good', 'Excellent'];

function CompulsoryRatingScreen({
  rating,
  ratingHover,
  ratingComment,
  ratingSubmitted,
  submittingRating,
  setRating,
  setRatingHover,
  setRatingComment,
  onSubmit,
  onDone,
}: {
  rating: number;
  ratingHover: number;
  ratingComment: string;
  ratingSubmitted: boolean;
  submittingRating: boolean;
  setRating: (r: number) => void;
  setRatingHover: (r: number) => void;
  setRatingComment: (c: string) => void;
  onSubmit: () => void;
  onDone: () => void;
}) {
  const [commentError, setCommentError] = useState('');
  const activeRating = ratingHover || rating;
  const needsComment = rating > 0 && rating <= 3;
  const canSubmit = rating > 0 && (!needsComment || ratingComment.trim().length > 0) && !submittingRating;

  // No auto-redirect — patient taps the button manually

  function handleSubmit() {
    if (needsComment && !ratingComment.trim()) {
      setCommentError('Please tell us what happened');
      return;
    }
    setCommentError('');
    onSubmit();
  }

  // ── Success state ──────────────────────────────────────────────────────
  if (ratingSubmitted) {
    return (
      <div className="flex flex-col items-center justify-center h-dvh bg-gray-50 px-6 animate-[fadeIn_0.3s_ease-out]">
        <div className="w-20 h-20 rounded-full bg-green-50 flex items-center justify-center mb-6 animate-[scaleIn_0.4s_ease-out]">
          <CheckCircle2 size={40} className="text-green-500" />
        </div>
        <h2 className="text-xl font-bold text-black mb-2">Thank You!</h2>
        <p className="text-sm text-black text-center mb-6">
          Your feedback helps us improve our service.
        </p>
        <button
          onClick={onDone}
          className="flex items-center gap-2 px-6 py-3 bg-[var(--brand-teal)] text-white rounded-xl text-sm font-semibold hover:opacity-90 active:scale-[0.97] transition-all"
        >
          <ArrowLeft size={18} />
          Back to Dashboard
        </button>
      </div>
    );
  }

  // ── Rating form (full-page) ────────────────────────────────────────────
  return (
    <div className="flex flex-col h-dvh bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-4 py-3 shrink-0 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-amber-50 flex items-center justify-center">
            <Star size={18} className="text-amber-500" />
          </div>
          <div>
            <h1 className="text-base font-bold text-black">Consultation Ended</h1>
            <p className="text-xs text-gray-500">Please rate your experience</p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col items-center justify-center px-6">
        {/* Title */}
        <h2 className="text-lg font-bold text-black mb-2 text-center">
          How would you rate this consultation?
        </h2>
        <p className="text-sm text-gray-500 mb-8 text-center">
          Your feedback helps us improve service quality
        </p>

        {/* Stars */}
        <div className="flex justify-center gap-3 mb-3">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              onClick={() => { setRating(n); setCommentError(''); }}
              onMouseEnter={() => setRatingHover(n)}
              onMouseLeave={() => setRatingHover(0)}
              className="p-1 transition-transform hover:scale-110 active:scale-90"
            >
              <Star
                size={40}
                className={
                  n <= activeRating
                    ? 'fill-amber-400 text-amber-400 drop-shadow-sm'
                    : 'text-gray-200'
                }
              />
            </button>
          ))}
        </div>

        {/* Rating label */}
        <p className={`text-sm font-semibold mb-8 h-5 transition-all ${
          activeRating ? (activeRating <= 2 ? 'text-red-500' : activeRating <= 3 ? 'text-amber-500' : 'text-green-500') : 'text-transparent'
        }`}>
          {RATING_LABELS[activeRating] || ''}
        </p>

        {/* Comment */}
        <div className="w-full max-w-sm">
          <textarea
            value={ratingComment}
            onChange={(e) => { setRatingComment(e.target.value); if (commentError) setCommentError(''); }}
            placeholder={needsComment ? 'Please tell us what happened...' : 'Share your feedback (optional)...'}
            rows={3}
            className={`w-full px-4 py-3 text-sm text-black placeholder-gray-400 bg-white rounded-2xl border focus:outline-none focus:ring-2 focus:ring-[var(--brand-teal)]/30 resize-none transition-all ${
              commentError ? 'border-red-300' : 'border-gray-200'
            }`}
          />
          {commentError && (
            <p className="text-xs text-red-500 mt-1.5 ml-1">{commentError}</p>
          )}
          {needsComment && !commentError && (
            <p className="text-xs text-amber-500 mt-1.5 ml-1">Comment required for low ratings</p>
          )}
        </div>
      </div>

      {/* Submit button (fixed at bottom) */}
      <div className="bg-white border-t border-gray-200 px-6 py-4 shrink-0">
        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          className="w-full h-12 rounded-full bg-[var(--brand-teal)] text-white text-sm font-semibold disabled:opacity-40 hover:bg-[#238377] transition-colors flex items-center justify-center gap-2"
        >
          {submittingRating ? (
            <Loader2 size={18} className="animate-spin" />
          ) : (
            'Submit Rating'
          )}
        </button>
        <div className="h-[env(safe-area-inset-bottom)]" />
      </div>
    </div>
  );
}

// ── Image Lightbox ───────────────────────────────────────────────────────────

function ImageLightbox({
  url,
  onClose,
}: {
  url: string;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 bg-black/90 flex items-center justify-center z-50 animate-[fadeIn_0.2s_ease-out]"
      onClick={onClose}
    >
      <button
        onClick={onClose}
        className="absolute top-4 right-4 p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors z-10"
      >
        <X size={24} className="text-white" />
      </button>
      <img
        src={url}
        alt="Full size"
        className="max-w-[95vw] max-h-[90vh] object-contain rounded-lg animate-[scaleIn_0.3s_ease-out]"
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}
