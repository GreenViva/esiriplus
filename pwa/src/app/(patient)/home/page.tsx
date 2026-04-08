'use client';

import { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import {
  Stethoscope,
  FileText,
  CalendarPlus,
  User,
  ChevronRight,
  Activity,
  Clock,
  Loader2,
  CheckCircle2,
  XCircle,
  TimerOff,
  RefreshCw,
} from 'lucide-react';
import { Button, Card, Badge } from '@/components/ui';
import ContactUs from '@/components/ContactUs';
import { useAuthStore } from '@/store/auth';
import { supabase, invokeEdgeFunction, getAuthClient } from '@/lib/supabase';
import type { Consultation } from '@/types';

export default function PatientHomePage() {
  const router = useRouter();
  const { session, patientSession } = useAuthStore();
  const [activeConsultations, setActiveConsultations] = useState<Consultation[]>([]);
  const [ongoingConsultations, setOngoingConsultations] = useState<Consultation[]>([]);
  const [recentReports, setRecentReports] = useState<unknown[]>([]);
  const [loading, setLoading] = useState(true);
  const [unreadReportCount, setUnreadReportCount] = useState(0);

  // Ongoing consultations sheet
  const [showOngoingSheet, setShowOngoingSheet] = useState(false);

  // Follow-up request state
  const [followUpTarget, setFollowUpTarget] = useState<Consultation | null>(null);
  const [followUpState, setFollowUpState] = useState<'idle' | 'confirm' | 'requesting' | 'waiting' | 'accepted' | 'rejected' | 'expired' | 'error'>('idle');
  const [followUpCountdown, setFollowUpCountdown] = useState(60);
  const [followUpConsultationId, setFollowUpConsultationId] = useState<string | null>(null);
  const [followUpError, setFollowUpError] = useState('');
  const followUpPollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const followUpCountdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const userName = session?.user?.fullName ?? 'Patient';
  const patientId = session?.user?.id;
  const sessionId = patientSession?.sessionId;

  useEffect(() => {
    if (!sessionId) {
      setLoading(false);
      return;
    }

    async function loadDashboard() {
      try {
        const token = useAuthStore.getState().session?.accessToken;
        const db = token ? getAuthClient(token, 'patient') : supabase;

        const [consultRes, reportRes] = await Promise.all([
          db.from('consultations')
            .select('*')
            .eq('patient_session_id', sessionId)
            .in('status', ['active', 'pending', 'awaiting_extension', 'grace_period', 'completed'])
            .order('created_at', { ascending: false })
            .limit(20),
          db.from('consultation_reports')
            .select('*')
            .eq('patient_session_id', sessionId)
            .order('created_at', { ascending: false })
            .limit(3),
        ]);

        if (consultRes.error) {
          console.warn('[Dashboard] consultations query failed:', consultRes.error.message);
        }

        const consultations = consultRes.data ?? [];
        const now = Date.now();
        const active = consultations.filter((c: Record<string, unknown>) => {
          const status = c.status as string;
          return ['active', 'in_progress', 'pending', 'awaiting_extension', 'grace_period'].includes(status);
        });
        const ongoing = consultations.filter((c: Record<string, unknown>) => {
          const status = c.status as string;
          if (status === 'completed' && c.follow_up_expiry) {
            return new Date(c.follow_up_expiry as string).getTime() > now;
          }
          return false;
        });
        setActiveConsultations(active as Consultation[]);
        setOngoingConsultations(ongoing as Consultation[]);

        if (reportRes.data) {
          setRecentReports(reportRes.data);
        }

        // Count unread reports — fetch ALL report IDs (not just 3)
        try {
          const { data: allReports } = await db.from('consultation_reports')
            .select('report_id')
            .eq('patient_session_id', sessionId);
          if (allReports && allReports.length > 0) {
            const viewedRaw = localStorage.getItem('esiri_viewed_reports');
            const viewed: string[] = viewedRaw ? JSON.parse(viewedRaw) : [];
            const allIds = allReports.map((r: { report_id: string }) => r.report_id);
            const unread = allIds.filter((id: string) => !viewed.includes(id));
            setUnreadReportCount(unread.length);
          }
        } catch { /* best-effort */ }
      } catch {
        // Silently handle - dashboard is non-critical
      } finally {
        setLoading(false);
      }
    }

    loadDashboard();
  }, [sessionId]);

  const quickActions = [
    {
      label: 'Book Appointment',
      icon: CalendarPlus,
      href: '/book-appointment',
      color: 'bg-[var(--brand-teal)]/10 text-[var(--brand-teal)]',
    },
    {
      label: 'My Reports',
      icon: FileText,
      href: '/reports',
      color: 'bg-[var(--royal-purple)]/10 text-[var(--royal-purple)]',
    },
    {
      label: 'Profile',
      icon: User,
      href: '/profile',
      color: 'bg-[var(--royal-gold)]/10 text-[var(--royal-gold)]',
    },
  ];

  // ── Follow-up request flow ──────────────────────────────────────────────

  function stopFollowUpPolling() {
    if (followUpPollRef.current) { clearInterval(followUpPollRef.current); followUpPollRef.current = null; }
    if (followUpCountdownRef.current) { clearInterval(followUpCountdownRef.current); followUpCountdownRef.current = null; }
  }

  function dismissFollowUp() {
    stopFollowUpPolling();
    setFollowUpTarget(null);
    setFollowUpState('idle');
    setFollowUpError('');
    setFollowUpConsultationId(null);
  }

  async function handleFollowUpRequest(c: Consultation) {
    const token = useAuthStore.getState().session?.accessToken;
    if (!token) { setFollowUpError('Session expired'); return; }

    setFollowUpState('requesting');
    setFollowUpError('');

    try {
      const result = await invokeEdgeFunction<{ request_id?: string }>(
        'handle-consultation-request',
        {
          action: 'create',
          doctor_id: c.doctor_id,
          service_type: c.service_type,
          service_tier: c.service_tier ?? 'ECONOMY',
          consultation_type: 'chat',
          chief_complaint: 'Follow-up consultation',
          is_follow_up: true,
          parent_consultation_id: c.consultation_id,
        },
        token,
        'patient',
      );

      const rId = result?.request_id;
      if (!rId) throw new Error('No request ID');

      setFollowUpState('waiting');
      setFollowUpCountdown(60);

      followUpCountdownRef.current = setInterval(() => {
        setFollowUpCountdown((prev) => {
          if (prev <= 1) { stopFollowUpPolling(); setFollowUpState('expired'); return 0; }
          return prev - 1;
        });
      }, 1000);

      followUpPollRef.current = setInterval(async () => {
        try {
          const freshToken = useAuthStore.getState().session?.accessToken;
          if (!freshToken) { stopFollowUpPolling(); setFollowUpState('error'); return; }
          const statusResult = await invokeEdgeFunction<{ status?: string; consultation_id?: string }>(
            'handle-consultation-request',
            { action: 'status', request_id: rId },
            freshToken,
            'patient',
          );
          if (statusResult?.status === 'accepted' && statusResult.consultation_id) {
            stopFollowUpPolling();
            setFollowUpConsultationId(statusResult.consultation_id);
            setFollowUpState('accepted');
          } else if (statusResult?.status === 'rejected') {
            stopFollowUpPolling();
            setFollowUpState('rejected');
          } else if (statusResult?.status === 'expired') {
            stopFollowUpPolling();
            setFollowUpState('expired');
          }
        } catch { /* keep polling */ }
      }, 2000);
    } catch (err) {
      setFollowUpState('error');
      setFollowUpError(err instanceof Error ? err.message : 'Failed to request follow-up');
    }
  }

  // Auto-navigate to new consultation after accepted
  useEffect(() => {
    if (followUpState === 'accepted' && followUpConsultationId) {
      const timer = setTimeout(() => {
        router.push(`/consultation/${followUpConsultationId}`);
        dismissFollowUp();
      }, 1500);
      return () => clearTimeout(timer);
    }
  }, [followUpState, followUpConsultationId, router]);

  // Cleanup on unmount
  useEffect(() => () => stopFollowUpPolling(), []);

  function statusBadge(c: Consultation) {
    // Completed but in follow-up window = follow-up mode
    if (c.status === 'completed' && c.follow_up_expiry && new Date(c.follow_up_expiry).getTime() > Date.now()) {
      const daysLeft = Math.ceil((new Date(c.follow_up_expiry).getTime() - Date.now()) / (24 * 60 * 60 * 1000));
      return <Badge variant="purple">{'\u2605'} Follow-up · {daysLeft}d left</Badge>;
    }
    switch (c.status) {
      case 'active':
      case 'in_progress':
        return <Badge variant="green">Active</Badge>;
      case 'awaiting_extension':
      case 'grace_period':
        return <Badge variant="gold">Extending</Badge>;
      case 'pending':
        return <Badge variant="gold">Pending</Badge>;
      default:
        return <Badge variant="gray">{c.status}</Badge>;
    }
  }

  return (
    <div className="min-h-dvh">
      {/* Header */}
      <div className="bg-gradient-to-br from-[var(--brand-teal)] to-[#1A7A6E] px-5 pt-12 pb-8 rounded-b-3xl">
        <p className="text-white/70 text-sm">Welcome back</p>
        <h1 className="text-white text-2xl font-bold mt-1">{userName}</h1>
        {patientSession?.sessionId && (
          <p className="text-white/60 text-xs mt-1 font-mono">
            ID: {patientSession.sessionId.slice(0, 8)}...
          </p>
        )}
      </div>

      <div className="px-5 -mt-4 space-y-5 pb-8">
        {/* Start Consultation CTA — attention-grabbing animated card */}
        <style>{`
          @keyframes ctaGlow {
            0%, 100% { box-shadow: 0 0 0 0 rgba(42,157,143,0.4); }
            50% { box-shadow: 0 0 20px 6px rgba(42,157,143,0.15); }
          }
          @keyframes ctaSlideIn {
            0% { opacity: 0; transform: translateY(16px) scale(0.97); }
            100% { opacity: 1; transform: translateY(0) scale(1); }
          }
          @keyframes ctaHeartbeat {
            0%, 100% { transform: scale(1); }
            14% { transform: scale(1.12); }
            28% { transform: scale(1); }
            42% { transform: scale(1.08); }
            56% { transform: scale(1); }
          }
          @keyframes ctaShimmer {
            0% { transform: translateX(-100%); }
            100% { transform: translateX(200%); }
          }
          @keyframes ctaNudge {
            0%, 100% { transform: translateX(0); }
            50% { transform: translateX(5px); }
          }
        `}</style>
        <button
          onClick={() => router.push('/tier-selection')}
          className="relative w-full overflow-hidden rounded-2xl bg-gradient-to-r from-[#2A9D8F] via-[#35B4A5] to-[#2A9D8F] p-[1px]"
          style={{ animation: 'ctaSlideIn 0.6s ease-out, ctaGlow 3s ease-in-out 1s infinite' }}
        >
          {/* Shimmer overlay */}
          <div
            className="absolute inset-0 z-10 pointer-events-none"
            style={{
              background: 'linear-gradient(105deg, transparent 40%, rgba(255,255,255,0.25) 50%, transparent 60%)',
              animation: 'ctaShimmer 4s ease-in-out 2s infinite',
            }}
          />
          <div className="relative z-0 flex items-center gap-4 bg-white rounded-[15px] p-4 group">
            {/* Animated icon */}
            <div
              className="w-14 h-14 rounded-2xl bg-gradient-to-br from-[#2A9D8F] to-[#1A7A6E] flex items-center justify-center shrink-0 shadow-lg shadow-[#2A9D8F]/25"
              style={{ animation: 'ctaHeartbeat 3s ease-in-out 1.5s infinite' }}
            >
              <Stethoscope size={26} className="text-white" />
            </div>
            <div className="flex-1 text-left">
              <p className="text-black font-bold text-[16px] leading-tight">Start Consultation</p>
              <p className="text-[var(--subtitle-grey)] text-[13px] mt-0.5">
                Connect with a doctor now
              </p>
            </div>
            <div
              className="w-9 h-9 rounded-full bg-[#2A9D8F]/10 flex items-center justify-center shrink-0 group-hover:bg-[#2A9D8F]/20 transition-colors"
              style={{ animation: 'ctaNudge 2s ease-in-out 2s infinite' }}
            >
              <ChevronRight size={20} className="text-[#2A9D8F]" />
            </div>
          </div>
        </button>

        {/* Quick Actions */}
        <div className="grid grid-cols-3 gap-3">
          {quickActions.map((action) => (
            <button
              key={action.href}
              onClick={() => router.push(action.href)}
              className="relative flex flex-col items-center gap-2 p-4 bg-white rounded-2xl border border-[var(--card-border)] hover:shadow-md transition-shadow active:scale-[0.98]"
            >
              <div
                className={`w-10 h-10 rounded-xl flex items-center justify-center ${action.color}`}
              >
                <action.icon size={20} />
              </div>
              {action.href === '/reports' && unreadReportCount > 0 && (
                <span className="absolute top-2 right-2 w-5 h-5 bg-red-500 rounded-full flex items-center justify-center text-[10px] font-bold text-white">
                  {unreadReportCount > 9 ? '9+' : unreadReportCount}
                </span>
              )}
              <span className="text-xs font-semibold text-black text-center leading-tight">
                {action.label}
              </span>
            </button>
          ))}
        </div>

        {/* Ongoing Consultations Button Badge */}
        {!loading && ongoingConsultations.length > 0 && (
          <button
            onClick={() => setShowOngoingSheet(true)}
            className="w-full flex items-center gap-4 p-4 bg-[var(--royal-purple)]/5 border border-[var(--royal-purple)]/30 rounded-2xl hover:shadow-md transition-shadow active:scale-[0.99]"
          >
            <div className="w-11 h-11 rounded-full bg-[var(--royal-purple)]/15 flex items-center justify-center shrink-0">
              <RefreshCw size={22} className="text-[var(--royal-purple)]" />
            </div>
            <div className="flex-1 text-left">
              <p className="text-[15px] font-bold text-black">Ongoing Consultations</p>
              <p className="text-xs text-[var(--subtitle-grey)]">
                {ongoingConsultations.length} follow-up{ongoingConsultations.length > 1 ? 's' : ''} available
              </p>
            </div>
            <div className="w-6 h-6 rounded-full bg-[var(--royal-purple)] flex items-center justify-center shrink-0">
              <span className="text-white text-xs font-bold">{ongoingConsultations.length}</span>
            </div>
            <ChevronRight size={18} className="text-[var(--royal-purple)]" />
          </button>
        )}

        {/* Active Session Recovery — only shows when there are unfinished consultations */}
        {!loading && activeConsultations.length > 0 && (
          <div className="space-y-2">
            {activeConsultations.map((c) => {
              const isRoyal = c.service_tier === 'ROYAL';
              const isPending = c.status === 'pending';
              return (
                <button
                  key={c.consultation_id}
                  onClick={() => router.push(`/consultation/${c.consultation_id}`)}
                  className={`w-full flex items-center gap-3 p-4 rounded-2xl border transition-shadow hover:shadow-md active:scale-[0.99] ${
                    isPending
                      ? 'bg-amber-50 border-amber-200'
                      : 'bg-green-50 border-green-200'
                  }`}
                >
                  <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${
                    isPending ? 'bg-amber-100' : 'bg-green-100 animate-pulse'
                  }`}>
                    <Activity size={20} className={isPending ? 'text-amber-600' : 'text-green-600'} />
                  </div>
                  <div className="flex-1 text-left">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-bold text-black">
                        {isPending ? 'Waiting for doctor' : 'Session in progress'}
                      </p>
                      {statusBadge(c)}
                    </div>
                    <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">
                      {isRoyal ? '\u2605 Royal' : 'Economy'} &middot; {c.service_type?.replace('_', ' ')} &middot; Tap to resume
                    </p>
                  </div>
                  <ChevronRight size={18} className={isPending ? 'text-amber-400' : 'text-green-400'} />
                </button>
              );
            })}
          </div>
        )}

        <ContactUs />
      </div>

      {/* Pulsing resume chat FAB */}
      {activeConsultations.length > 0 && (
        <button
          onClick={() => router.push(`/consultation/${activeConsultations[0].consultation_id}`)}
          className="fixed bottom-24 right-5 z-40 w-14 h-14 rounded-full bg-[var(--brand-teal)] text-white shadow-lg flex items-center justify-center animate-pulse hover:scale-110 transition-transform"
          title="Resume consultation"
        >
          <Stethoscope size={24} />
        </button>
      )}
      {/* Ongoing consultations sheet */}
      {showOngoingSheet && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-end justify-center" onClick={() => setShowOngoingSheet(false)}>
          <div
            className="bg-white rounded-t-3xl w-full max-w-lg max-h-[80vh] overflow-y-auto animate-[slideUp_0.3s_ease-out]"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="sticky top-0 bg-white px-5 pt-5 pb-3 border-b border-gray-100">
              <div className="w-10 h-1 bg-gray-200 rounded-full mx-auto mb-4" />
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold text-black">Ongoing Consultations</h2>
                <button onClick={() => setShowOngoingSheet(false)} className="p-1 rounded-lg hover:bg-gray-100">
                  <XCircle size={20} className="text-gray-400" />
                </button>
              </div>
              <p className="text-xs text-[var(--subtitle-grey)] mt-1">Tap to request a follow-up with the same doctor</p>
            </div>
            <div className="px-5 py-4 space-y-3">
              {ongoingConsultations.map((c) => {
                const isRoyal = c.service_tier === 'ROYAL';
                const daysLeft = c.follow_up_expiry
                  ? Math.ceil((new Date(c.follow_up_expiry).getTime() - Date.now()) / (24 * 60 * 60 * 1000))
                  : 0;
                return (
                  <Card
                    key={c.consultation_id}
                    className={isRoyal ? '!border-[var(--royal-purple)]/30' : ''}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-sm font-bold text-black">
                            {isRoyal ? '\u2605 Royal' : 'Economy'} · {c.service_type?.replace('_', ' ')}
                          </p>
                        </div>
                        <div className="flex items-center gap-2 mt-1">
                          <Badge variant="purple">{'\u2605'} {daysLeft}d left</Badge>
                          <span className="text-xs text-[var(--subtitle-grey)]">
                            {isRoyal ? 'Unlimited follow-ups' : '1 free follow-up'}
                          </span>
                        </div>
                      </div>
                    </div>
                    <button
                      onClick={() => {
                        setShowOngoingSheet(false);
                        setFollowUpTarget(c);
                        setFollowUpState('confirm');
                      }}
                      className="w-full flex items-center justify-center gap-2 py-2.5 bg-[var(--royal-purple)]/10 text-[var(--royal-purple)] rounded-xl text-sm font-semibold hover:bg-[var(--royal-purple)]/20 transition-colors active:scale-[0.98]"
                    >
                      <RefreshCw size={16} />
                      Request Follow-up
                    </button>
                  </Card>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Follow-up request modal */}
      {followUpTarget && followUpState !== 'idle' && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-6">
          <div className="bg-white rounded-3xl p-6 w-full max-w-sm shadow-xl animate-[scaleIn_0.2s_ease-out]">
            {followUpState === 'confirm' && (
              <>
                <div className="w-14 h-14 rounded-2xl bg-[var(--royal-purple)]/10 flex items-center justify-center mx-auto mb-4">
                  <RefreshCw size={28} className="text-[var(--royal-purple)]" />
                </div>
                <h3 className="text-lg font-bold text-black text-center">Request Follow-up?</h3>
                <p className="text-sm text-gray-500 text-center mt-2">
                  This will send a follow-up request to the same doctor.
                  {followUpTarget.service_tier !== 'ROYAL' && ' Economy tier allows 1 free follow-up.'}
                </p>
                <div className="flex gap-3 mt-6">
                  <button onClick={dismissFollowUp} className="flex-1 py-3 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 hover:bg-gray-50">Cancel</button>
                  <button onClick={() => handleFollowUpRequest(followUpTarget)} className="flex-1 py-3 rounded-xl bg-[var(--royal-purple)] text-white text-sm font-semibold hover:opacity-90">Confirm</button>
                </div>
              </>
            )}
            {followUpState === 'requesting' && (
              <div className="flex flex-col items-center py-4">
                <Loader2 size={36} className="text-[var(--royal-purple)] animate-spin mb-3" />
                <p className="text-sm font-semibold text-black">Sending request...</p>
              </div>
            )}
            {followUpState === 'waiting' && (
              <div className="flex flex-col items-center py-4">
                <div className="w-16 h-16 rounded-full border-4 border-[var(--royal-purple)]/20 flex items-center justify-center mb-3">
                  <span className="text-2xl font-bold text-[var(--royal-purple)]">{followUpCountdown}</span>
                </div>
                <p className="text-sm font-semibold text-black">Waiting for doctor...</p>
                <p className="text-xs text-gray-400 mt-1">Request expires in {followUpCountdown}s</p>
              </div>
            )}
            {followUpState === 'accepted' && (
              <div className="flex flex-col items-center py-4">
                <CheckCircle2 size={48} className="text-green-500 mb-3" />
                <p className="text-sm font-bold text-black">Follow-up accepted!</p>
                <p className="text-xs text-gray-400 mt-1">Redirecting to consultation...</p>
              </div>
            )}
            {followUpState === 'rejected' && (
              <div className="flex flex-col items-center py-4">
                <XCircle size={48} className="text-red-400 mb-3" />
                <p className="text-sm font-bold text-black">Doctor declined</p>
                <p className="text-xs text-gray-400 mt-1">The doctor is unavailable right now</p>
                <button onClick={dismissFollowUp} className="mt-4 px-6 py-2.5 rounded-xl bg-gray-100 text-sm font-semibold text-gray-700">OK</button>
              </div>
            )}
            {followUpState === 'expired' && (
              <div className="flex flex-col items-center py-4">
                <TimerOff size={48} className="text-amber-400 mb-3" />
                <p className="text-sm font-bold text-black">Request expired</p>
                <p className="text-xs text-gray-400 mt-1">Doctor didn&apos;t respond in time</p>
                <button onClick={dismissFollowUp} className="mt-4 px-6 py-2.5 rounded-xl bg-gray-100 text-sm font-semibold text-gray-700">OK</button>
              </div>
            )}
            {followUpState === 'error' && (
              <div className="flex flex-col items-center py-4">
                <XCircle size={48} className="text-red-400 mb-3" />
                <p className="text-sm font-bold text-black">Request failed</p>
                <p className="text-xs text-red-400 mt-1">{followUpError}</p>
                <button onClick={dismissFollowUp} className="mt-4 px-6 py-2.5 rounded-xl bg-gray-100 text-sm font-semibold text-gray-700">OK</button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
