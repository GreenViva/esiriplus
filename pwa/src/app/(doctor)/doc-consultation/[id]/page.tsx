'use client';

import { useState, useEffect, useRef, use } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  Phone,
  Video,
  FileText,
  Send,
  User,
  MapPin,
  AlertTriangle,
  Image as ImageIcon,
  Paperclip,
  Timer,
  Crown,
  ChevronDown,
  XCircle,
  WifiOff,
} from 'lucide-react';
import { Badge, Button } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction, ensureFreshToken } from '@/lib/supabase';
import { useSupabase } from '@/hooks/useSupabase';
import { useTypingIndicator } from '@/hooks/useTypingIndicator';
import type { Consultation, Message, PatientSession } from '@/types';

function formatCurrency(amount: number) {
  return `TSh ${amount.toLocaleString()}`;
}

function formatTime(ts: string) {
  return new Date(ts).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}

function formatTimeSeparator(ts: string) {
  const d = new Date(ts);
  const today = new Date();
  if (d.toDateString() === today.toDateString()) return `Today ${formatTime(ts)}`;
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) + ' ' + formatTime(ts);
}

function shouldShowSeparator(prev: Message | undefined, current: Message): boolean {
  if (!prev) return true;
  return new Date(current.created_at).getTime() - new Date(prev.created_at).getTime() > 5 * 60 * 1000;
}

function getTimerStyle(status: string, remainingMs: number) {
  if (status === 'grace_period') return { bg: 'bg-[#F59E0B]/10', text: 'text-[#F59E0B]' };
  if (status === 'awaiting_extension') return { bg: 'bg-[#F59E0B]/10', text: 'text-[#F59E0B]' };
  if (remainingMs <= 60_000) return { bg: 'bg-[#DC2626]/10', text: 'text-[#DC2626]' };
  if (remainingMs <= 180_000) return { bg: 'bg-[#F59E0B]/10', text: 'text-[#F59E0B]' };
  return { bg: 'bg-[var(--brand-teal)]/10', text: 'text-[var(--brand-teal)]' };
}

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL!;

export default function ConsultationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const { session } = useAuthStore();
  const db = useSupabase();
  const [consultation, setConsultation] = useState<Consultation | null>(null);
  const [patient, setPatient] = useState<PatientSession | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [timeRemaining, setTimeRemaining] = useState('');
  const [remainingMs, setRemainingMs] = useState(0);
  const [showEndConfirm, setShowEndConfirm] = useState(false);
  const [ending, setEnding] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [showCallDropdown, setShowCallDropdown] = useState(false);
  const [realtimeConnected, setRealtimeConnected] = useState(true);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const doctorId = session?.user?.id;

  const isConsultationActive = consultation?.status === 'active' || consultation?.status === 'awaiting_extension' || consultation?.status === 'grace_period';
  const { otherPartyTyping, onTypingChange } = useTypingIndicator({
    consultationId: id,
    userId: doctorId ?? '',
    role: 'doctor',
    enabled: isConsultationActive,
  });

  useEffect(() => { loadConsultation(); }, [id]);
  useEffect(() => { chatEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);
  useEffect(() => {
    if (!showCallDropdown) return;
    const close = () => setShowCallDropdown(false);
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, [showCallDropdown]);

  // ── Timer ───────────────────────────────────────────────────────────────

  useEffect(() => {
    const endAt = consultation?.status === 'grace_period'
      ? consultation?.grace_period_end_at
      : consultation?.scheduled_end_at;
    if (!endAt) return;
    const interval = setInterval(() => {
      const remaining = Math.max(0, new Date(endAt).getTime() - Date.now());
      setRemainingMs(remaining);
      if (consultation?.status === 'awaiting_extension') {
        setTimeRemaining('Awaiting extension');
      } else {
        const mins = Math.floor(remaining / 60000);
        const secs = Math.floor((remaining % 60000) / 1000);
        setTimeRemaining(`${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [consultation?.scheduled_end_at, consultation?.grace_period_end_at, consultation?.status]);

  // ── Realtime messages ───────────────────────────────────────────────────

  useEffect(() => {
    if (!id || !db) return;
    const channel = db
      .channel(`consultation-msgs-${id}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'messages',
          filter: `consultation_id=eq.${id}`,
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
        setRealtimeConnected(status === 'SUBSCRIBED');
      });
    return () => { db.removeChannel(channel); };
  }, [id, db]);

  // ── Realtime consultation status ────────────────────────────────────────

  useEffect(() => {
    if (!id || !db) return;
    const channel = db
      .channel(`consultation-status-${id}`)
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'consultations', filter: `consultation_id=eq.${id}` },
        (payload) => {
          const row = payload.new as Consultation;
          if (row) setConsultation(row);
        }
      )
      .subscribe();
    return () => { db.removeChannel(channel); };
  }, [id, db]);

  // ── Data loading ────────────────────────────────────────────────────────

  async function loadConsultation() {
    try {
      await ensureFreshToken();
      const currentToken = useAuthStore.getState().session?.accessToken;

      // Sync consultation state from manage-consultation
      const syncData = await invokeEdgeFunction<{
        consultation_id: string;
        doctor_id: string;
        status: string;
        service_type: string;
        service_tier: string;
        consultation_fee: number;
        scheduled_end_at: string;
        extension_count: number;
        grace_period_end_at?: string;
        original_duration_minutes: number;
        session_start_time: string;
        server_time: string;
      }>('manage-consultation', { action: 'sync', consultation_id: id }, currentToken ?? undefined, 'doctor');

      if (syncData) {
        setConsultation((prev) => ({
          ...(prev ?? {} as Consultation),
          consultation_id: syncData.consultation_id,
          doctor_id: syncData.doctor_id,
          status: syncData.status as Consultation['status'],
          service_type: syncData.service_type,
          service_tier: syncData.service_tier as Consultation['service_tier'],
          consultation_fee: syncData.consultation_fee,
          scheduled_end_at: syncData.scheduled_end_at,
          extension_count: syncData.extension_count,
          grace_period_end_at: syncData.grace_period_end_at,
          session_start_time: syncData.session_start_time,
          session_duration_minutes: syncData.original_duration_minutes,
        } as Consultation));
      }

      // Fetch consultation full row from DB for extra fields (parent, follow_up_expiry, etc.)
      if (db) {
        const { data: fullRow } = await db
          .from('consultations')
          .select('*')
          .eq('consultation_id', id)
          .single();
        if (fullRow) setConsultation(fullRow as Consultation);

        // Fetch patient info from consultation_requests (doctor has RLS access)
        if (fullRow?.patient_session_id) {
          const { data: req } = await db
            .from('consultation_requests')
            .select('patient_session_id, patient_age_group, patient_sex, patient_blood_group, patient_allergies, patient_chronic_conditions, symptoms')
            .eq('consultation_id', id)
            .single();
          if (req) {
            setPatient({
              sessionId: req.patient_session_id,
              ageGroup: req.patient_age_group,
              sex: req.patient_sex,
              region: null,
              bloodType: req.patient_blood_group,
              allergies: req.patient_allergies ? [req.patient_allergies] : [],
              chronicConditions: req.patient_chronic_conditions ? [req.patient_chronic_conditions] : [],
            });
          }
        }
      }

      // Fetch messages via handle-messages
      const msgs = await invokeEdgeFunction<Message[]>(
        'handle-messages',
        { action: 'get', consultation_id: id, include_parent: true },
        currentToken ?? undefined,
        'doctor'
      );
      if (Array.isArray(msgs)) {
        setMessages(msgs.map((m) => ({
          ...m,
          is_from_previous_session: m.is_from_previous_session ?? m.consultation_id !== id,
        })));
      }
    } catch {
      // empty
    } finally {
      setLoading(false);
    }
  }

  // ── Send text message ───────────────────────────────────────────────────

  async function sendMessage() {
    if (!newMessage.trim() || sending) return;
    setSending(true);
    const optimistic: Message = {
      message_id: `temp-${Date.now()}`,
      consultation_id: id,
      sender_id: doctorId!,
      sender_type: 'doctor',
      message_text: newMessage.trim(),
      message_type: 'text',
      created_at: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimistic]);
    setNewMessage('');
    onTypingChange(false);

    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      await invokeEdgeFunction('handle-messages', {
        action: 'send',
        consultation_id: id,
        sender_type: 'doctor',
        sender_id: doctorId,
        message_text: optimistic.message_text,
        message_type: 'text',
      }, currentToken ?? undefined, 'doctor');
    } catch {
      setMessages((prev) => prev.filter((m) => m.message_id !== optimistic.message_id));
    } finally {
      setSending(false);
    }
  }

  // ── Attachment upload ───────────────────────────────────────────────────

  async function handleFileUpload(file: File, type: 'image' | 'document') {
    if (!file || uploading) return;
    if (file.size > 10 * 1024 * 1024) { alert('File must be under 10 MB'); return; }

    setUploading(true);
    setShowAttachMenu(false);
    try {
      await ensureFreshToken();
      const currentToken = useAuthStore.getState().session?.accessToken;
      if (!currentToken) throw new Error('No token');

      const ext = file.name.split('.').pop() ?? 'bin';
      const path = `${id}/${crypto.randomUUID()}.${ext}`;

      const { getStorageClient } = await import('@/lib/supabase');
      const storage = getStorageClient(currentToken);
      const { error: uploadError } = await storage.storage
        .from('message-attachments')
        .upload(path, file, { contentType: file.type });
      if (uploadError) throw uploadError;

      const publicUrl = `${SUPABASE_URL}/storage/v1/object/public/message-attachments/${path}`;
      const msgType = type === 'image' ? 'image' : 'document';
      const optimistic: Message = {
        message_id: `temp-${Date.now()}`,
        consultation_id: id,
        sender_id: doctorId!,
        sender_type: 'doctor',
        message_text: file.name,
        message_type: msgType,
        attachment_url: publicUrl,
        created_at: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, optimistic]);

      await invokeEdgeFunction('handle-messages', {
        action: 'send',
        consultation_id: id,
        sender_type: 'doctor',
        sender_id: doctorId,
        message_text: file.name,
        message_type: msgType,
        attachment_url: publicUrl,
      }, currentToken, 'doctor');
    } catch {
      // silently fail
    } finally {
      setUploading(false);
    }
  }

  // ── Session controls ────────────────────────────────────────────────────

  async function requestExtension() {
    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      await invokeEdgeFunction('manage-consultation', {
        action: 'request_extension',
        consultation_id: id,
      }, currentToken ?? undefined, 'doctor');
      loadConsultation();
    } catch { /* empty */ }
  }

  async function endSession() {
    setEnding(true);
    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      await invokeEdgeFunction('manage-consultation', { action: 'end', consultation_id: id }, currentToken ?? undefined, 'doctor');
      setShowEndConfirm(false);
      router.push(`/doc-report/${id}`);
    } catch {
      setEnding(false);
    }
  }

  // ── Message rendering ───────────────────────────────────────────────────

  function renderMessage(msg: Message) {
    if (msg.message_type === 'image' && msg.attachment_url) {
      return (
        <img
          src={msg.attachment_url}
          alt={msg.message_text}
          className="max-w-[240px] rounded-lg cursor-pointer"
          onClick={() => window.open(msg.attachment_url, '_blank')}
        />
      );
    }
    if (msg.message_type === 'document' && msg.attachment_url) {
      return (
        <a href={msg.attachment_url} target="_blank" rel="noopener noreferrer"
          className="flex items-center gap-2 text-sm underline">
          <FileText size={16} /> {msg.message_text || 'Document'}
        </a>
      );
    }
    return <p className="text-sm whitespace-pre-wrap break-words">{msg.message_text}</p>;
  }

  // ── Loading / not found ─────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!consultation) {
    return (
      <div className="px-4 py-6 text-center">
        <p className="text-sm text-[var(--subtitle-grey)]">Consultation not found</p>
        <Button variant="ghost" onClick={() => router.back()} className="mt-4">Go Back</Button>
      </div>
    );
  }

  const isActive = ['active', 'awaiting_extension', 'grace_period'].includes(consultation.status);
  const isFollowUp = !!consultation.parent_consultation_id;
  const timerStyle = getTimerStyle(consultation.status, remainingMs);
  const canSendMessages = consultation.status === 'active' || consultation.status === 'grace_period';

  return (
    <div className="flex flex-col h-[calc(100dvh-3.5rem)] lg:h-dvh max-w-4xl mx-auto">
      {/* ── Header ──────────────────────────────────────────────────── */}
      <div className="bg-white border-b border-[var(--card-border)] px-4 py-3 shrink-0">
        <div className="flex items-center gap-3 mb-2">
          <button
            onClick={() => {
              if (isActive) { if (confirm('Leave active consultation?')) router.back(); }
              else router.back();
            }}
            className="p-1 rounded-lg hover:bg-gray-100 lg:hidden"
          >
            <ArrowLeft size={20} className="text-black" />
          </button>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="text-base font-bold text-black truncate">Consultation</h1>
              <Badge variant={consultation.service_tier === 'ROYAL' ? 'purple' : 'teal'}>
                {consultation.service_tier}
              </Badge>
            </div>
          </div>

          {isActive && timeRemaining && (
            <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full ${timerStyle.bg}`}>
              <Timer size={14} className={timerStyle.text} />
              <span className={`text-sm font-mono font-bold ${timerStyle.text}`}>{timeRemaining}</span>
            </div>
          )}

          {isActive && (
            <div className="flex gap-1.5">
              {/* Call dropdown */}
              <div className="relative">
                <button onClick={() => setShowCallDropdown((v) => !v)} className="flex items-center gap-0.5 p-2 rounded-lg hover:bg-gray-100">
                  <Phone size={18} className="text-[var(--brand-teal)]" />
                  <ChevronDown size={12} className="text-[var(--brand-teal)]" />
                </button>
                {showCallDropdown && (
                  <div className="absolute right-0 top-full mt-1 w-40 bg-white rounded-xl shadow-lg border border-[var(--card-border)] z-50 overflow-hidden">
                    <button onClick={() => { setShowCallDropdown(false); router.push(`/doc-video-call/${id}?type=AUDIO`); }}
                      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 hover:bg-gray-50 text-left">
                      <Phone size={16} className="text-[var(--success-green)]" />
                      <span className="text-sm font-medium text-black">Voice Call</span>
                    </button>
                    <button onClick={() => { setShowCallDropdown(false); router.push(`/doc-video-call/${id}?type=VIDEO`); }}
                      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 hover:bg-gray-50 text-left border-t border-gray-100">
                      <Video size={16} className="text-[var(--brand-teal)]" />
                      <span className="text-sm font-medium text-black">Video Call</span>
                    </button>
                  </div>
                )}
              </div>
              {/* Write Report */}
              <button onClick={() => router.push(`/doc-report/${id}`)} className="p-2 rounded-lg hover:bg-gray-100">
                <FileText size={18} className="text-[var(--brand-teal)]" />
              </button>
              {/* End Session */}
              <button onClick={() => setShowEndConfirm(true)} className="p-2 rounded-lg hover:bg-gray-100">
                <XCircle size={18} className="text-[var(--error-red)]" />
              </button>
            </div>
          )}
        </div>

        {isFollowUp && (
          <div className={`flex items-center gap-2 px-3 py-2 rounded-lg mb-2 ${
            consultation.service_tier === 'ROYAL'
              ? 'bg-[var(--royal-purple)]/10 text-[var(--royal-purple)]'
              : 'bg-[var(--brand-teal)]/10 text-[var(--brand-teal)]'
          }`}>
            {consultation.service_tier === 'ROYAL' && <Crown size={14} />}
            <span className="text-xs font-semibold">
              {consultation.service_tier === 'ROYAL' ? 'Royal Follow-up' : 'Follow-up Consultation'}
              {consultation.follow_up_expiry && (
                <> &middot; {Math.max(0, Math.ceil((new Date(consultation.follow_up_expiry).getTime() - Date.now()) / 86400000))}d left</>
              )}
            </span>
          </div>
        )}

        {patient && (
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--subtitle-grey)]">
            {patient.ageGroup && (
              <span className="flex items-center gap-1"><User size={12} /> {patient.ageGroup} {patient.sex && `/ ${patient.sex}`}</span>
            )}
            {patient.region && (
              <span className="flex items-center gap-1"><MapPin size={12} /> {patient.region}</span>
            )}
            {patient.allergies.length > 0 && (
              <span className="flex items-center gap-1 text-[var(--error-red)]">
                <AlertTriangle size={12} /> Allergies: {patient.allergies.join(', ')}
              </span>
            )}
          </div>
        )}

        {isActive && (
          <div className="mt-3">
            <button onClick={requestExtension}
              className="w-full text-xs font-semibold text-[var(--brand-teal)] py-2 rounded-lg border border-[var(--brand-teal)]/20 hover:bg-[var(--brand-teal)]/5 transition-colors">
              + Extend Session
            </button>
          </div>
        )}
      </div>

      {/* ── Connection banner ─────────────────────────────────────── */}
      {!realtimeConnected && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 flex items-center gap-2 shrink-0">
          <WifiOff size={14} className="text-amber-600" />
          <span className="text-xs font-medium text-amber-700">Connection lost — messages may be delayed</span>
        </div>
      )}

      {/* ── Chat messages ──────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-1 bg-gray-50">
        {messages.length === 0 && (
          <div className="text-center py-8">
            <p className="text-sm text-[var(--subtitle-grey)]">No messages yet. Start the conversation.</p>
          </div>
        )}
        {messages.map((msg, idx) => {
          const isDoctor = msg.sender_type === 'doctor';
          const isSystem = msg.sender_type === 'system';
          const isPrevSession = msg.is_from_previous_session;
          const prevMsg = messages[idx - 1];
          const showSep = shouldShowSeparator(prevMsg, msg);
          const showPrevSep = isPrevSession && prevMsg && !prevMsg.is_from_previous_session === false && idx > 0
            && !messages.slice(0, idx).some((m) => m.is_from_previous_session);
          const showCurrentSep = !isPrevSession && prevMsg?.is_from_previous_session;
          return (
            <div key={msg.message_id} className={isPrevSession ? 'opacity-55' : ''}>
              {(idx === 0 && isPrevSession) && (
                <div className="flex items-center gap-3 my-4">
                  <div className="flex-1 border-t border-dashed border-gray-300" />
                  <span className="text-[10px] font-semibold text-[var(--subtitle-grey)] uppercase tracking-wider">Previous session</span>
                  <div className="flex-1 border-t border-dashed border-gray-300" />
                </div>
              )}
              {showCurrentSep && (
                <div className="flex items-center gap-3 my-4">
                  <div className="flex-1 border-t border-dashed border-gray-300" />
                  <span className="text-[10px] font-semibold text-[var(--subtitle-grey)] uppercase tracking-wider">Current session</span>
                  <div className="flex-1 border-t border-dashed border-gray-300" />
                </div>
              )}
              {showSep && (
                <div className="flex justify-center my-3">
                  <span className="text-[10px] text-[var(--subtitle-grey)] bg-gray-200 px-3 py-1 rounded-full">
                    {formatTimeSeparator(msg.created_at)}
                  </span>
                </div>
              )}
              {isSystem ? (
                <div className="flex justify-center mb-1">
                  <span className="text-xs text-[var(--subtitle-grey)] italic bg-gray-100 px-3 py-1.5 rounded-full">
                    {msg.message_text}
                  </span>
                </div>
              ) : (
                <div className={`flex ${isDoctor ? 'justify-end' : 'justify-start'} mb-1`}>
                  <div className={`max-w-[80%] px-3.5 py-2.5 rounded-2xl ${
                    isDoctor
                      ? 'bg-[var(--brand-teal)] text-white rounded-br-md'
                      : 'bg-white border border-[var(--card-border)] text-black rounded-bl-md'
                  }`}>
                    {renderMessage(msg)}
                    <p className={`text-[10px] mt-1 text-right ${isDoctor ? 'text-white/70' : 'text-[var(--subtitle-grey)]'}`}>
                      {formatTime(msg.created_at)}
                    </p>
                  </div>
                </div>
              )}
            </div>
          );
        })}
        {otherPartyTyping && (
          <div className="flex justify-start px-1 py-1">
            <div className="bg-gray-100 rounded-2xl rounded-bl-md px-4 py-2.5 flex items-center gap-1">
              <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
          </div>
        )}
        <div ref={chatEndRef} />
      </div>

      {/* ── Message input ──────────────────────────────────────────── */}
      {canSendMessages && (
        <div className="bg-white border-t border-[var(--card-border)] px-4 py-3 shrink-0">
          <div className="flex items-end gap-2">
            <div className="relative">
              <button onClick={() => setShowAttachMenu(!showAttachMenu)}
                className="p-2.5 rounded-full hover:bg-gray-100 text-[var(--subtitle-grey)]" disabled={uploading}>
                <Paperclip size={20} />
              </button>
              {showAttachMenu && (
                <div className="absolute bottom-12 left-0 bg-white border border-[var(--card-border)] rounded-xl shadow-lg p-2 w-40 z-10">
                  <button onClick={() => imageInputRef.current?.click()}
                    className="flex items-center gap-2 w-full px-3 py-2 text-sm text-black hover:bg-gray-50 rounded-lg">
                    <ImageIcon size={16} className="text-[var(--brand-teal)]" /> Photo
                  </button>
                  <button onClick={() => fileInputRef.current?.click()}
                    className="flex items-center gap-2 w-full px-3 py-2 text-sm text-black hover:bg-gray-50 rounded-lg">
                    <FileText size={16} className="text-[var(--royal-purple)]" /> Document
                  </button>
                </div>
              )}
            </div>
            <div className="flex-1">
              <textarea
                value={newMessage}
                onChange={(e) => { setNewMessage(e.target.value); onTypingChange(e.target.value.length > 0); }}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } }}
                placeholder={uploading ? 'Uploading...' : 'Type a message...'}
                rows={1} disabled={uploading}
                className="w-full px-4 py-2.5 text-sm text-black placeholder-gray-400 border border-[var(--card-border)] rounded-2xl resize-none focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)] max-h-24"
              />
            </div>
            <button onClick={sendMessage}
              disabled={!newMessage.trim() || sending || uploading}
              className="w-10 h-10 rounded-full bg-[var(--brand-teal)] flex items-center justify-center text-white disabled:opacity-50 shrink-0 hover:bg-[#238377] transition-colors">
              <Send size={18} />
            </button>
          </div>
          <input ref={imageInputRef} type="file" accept="image/jpeg,image/png,image/webp,image/heic" className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFileUpload(f, 'image'); e.target.value = ''; }} />
          <input ref={fileInputRef} type="file" accept=".pdf,.doc,.docx" className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFileUpload(f, 'document'); e.target.value = ''; }} />
        </div>
      )}

      {consultation.status === 'awaiting_extension' && (
        <div className="bg-[#F59E0B]/10 border-t border-[#F59E0B]/20 px-4 py-3 text-center shrink-0">
          <p className="text-sm font-medium text-[#92400E]">Session time expired — awaiting patient extension payment</p>
        </div>
      )}

      {consultation.status === 'completed' && (
        <div className="bg-white border-t border-[var(--card-border)] px-4 py-4 text-center shrink-0">
          <p className="text-sm text-[var(--subtitle-grey)] mb-2">This consultation has ended</p>
          <Button onClick={() => router.push(`/doc-report/${id}`)}>
            <FileText size={14} className="mr-1.5" /> Write Report
          </Button>
        </div>
      )}

      {/* ── End Session Confirmation ───────────────────────────────── */}
      {showEndConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6">
          <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">
            <h3 className="text-lg font-bold text-black mb-2">End Consultation?</h3>
            <p className="text-sm text-[var(--subtitle-grey)] mb-6">
              This will end the session. You will need to write a report before you can take new consultations.
            </p>
            <div className="flex gap-3">
              <button onClick={() => setShowEndConfirm(false)} disabled={ending}
                className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black hover:bg-gray-50">
                Cancel
              </button>
              <button onClick={endSession} disabled={ending}
                className="flex-1 h-11 rounded-xl bg-[var(--error-red)] text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50">
                {ending ? 'Ending...' : 'End Session'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
