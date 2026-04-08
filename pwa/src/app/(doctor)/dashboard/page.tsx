'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Crown,
  MessageSquare,
  DollarSign,
  Users,
  Clock,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  CheckCircle,
  ArrowUpRight,
  Calendar,
  Wallet,
  AlertTriangle,
  XCircle,
  Phone,
  Mail,
  Stethoscope,
} from 'lucide-react';
import { Card, Badge, Button } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction, ensureFreshToken } from '@/lib/supabase';
import { useSupabase } from '@/hooks/useSupabase';
import ContactUs from '@/components/ContactUs';
import type { Consultation, DoctorProfileRow, EarningsRow } from '@/types';

// ── Helpers ─────────────────────────────────────────────────────────────────

function formatCurrency(amount: number) {
  return `TSh ${amount.toLocaleString()}`;
}

function formatTimeAgo(ts: string) {
  const diff = Date.now() - new Date(ts).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

function formatDate(ts: string) {
  return new Date(ts).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function earningTypeLabel(type: string): { label: string; pct: string; color: string } {
  switch (type) {
    case 'follow_up':
      return { label: 'Follow-up', pct: '20%', color: 'purple' };
    case 'substitute_follow_up':
      return { label: 'Substitute FU', pct: '20%', color: 'purple' };
    case 'substitute_consultation':
      return { label: 'Substitute', pct: '30%', color: 'teal' };
    default:
      return { label: 'Consultation', pct: '30%', color: 'teal' };
  }
}

// ── Consultation Timer ──────────────────────────────────────────────────────

function ConsultationTimer({ startTime, endTime }: { startTime?: string; endTime?: string }) {
  const [elapsed, setElapsed] = useState('00:00');

  useEffect(() => {
    if (!startTime) return;
    const startMs = new Date(startTime).getTime();
    const endMs = endTime ? new Date(endTime).getTime() : undefined;
    const interval = setInterval(() => {
      const now = Date.now();
      const end = endMs ?? now;
      const remaining = Math.max(0, end - now);
      const totalSecs = endMs ? Math.floor(remaining / 1000) : Math.floor((now - startMs) / 1000);
      const m = Math.floor(totalSecs / 60);
      const s = totalSecs % 60;
      setElapsed(`${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`);
    }, 1000);
    return () => clearInterval(interval);
  }, [startTime, endTime]);

  return (
    <span className="text-sm font-mono font-semibold text-[var(--brand-teal)]">
      {elapsed}
    </span>
  );
}

// ── Main ────────────────────────────────────────────────────────────────────

export default function DoctorDashboardPage() {
  const router = useRouter();
  const { session } = useAuthStore();
  const db = useSupabase();

  // Profile state
  const [profile, setProfile] = useState<DoctorProfileRow | null>(null);
  const [isOnline, setIsOnline] = useState(false);
  const [canServeAsGp, setCanServeAsGp] = useState(false);
  const [loading, setLoading] = useState(true);

  // Warning state
  const [warningExpanded, setWarningExpanded] = useState(false);
  const [warningAcknowledged, setWarningAcknowledged] = useState(false);
  const swipeStartX = useRef<number | null>(null);

  // Consultation state
  const [activeConsultations, setActiveConsultations] = useState<Consultation[]>([]);
  const [pendingRequests, setPendingRequests] = useState<Consultation[]>([]);

  // Earnings state
  const [earningsRows, setEarningsRows] = useState<EarningsRow[]>([]);
  const [earningsSummary, setEarningsSummary] = useState({
    total: 0,
    thisMonth: 0,
    lastMonth: 0,
    pendingPayout: 0,
  });

  // Stats
  const [stats, setStats] = useState({
    royalClients: 0,
    activeConsultations: 0,
    todaysEarnings: 0,
    totalPatients: 0,
    acceptanceRate: null as number | null,
  });

  const doctorId = session?.user?.id;
  const doctorName = session?.user?.fullName ?? 'Doctor';

  // ── Data loading ────────────────────────────────────────────────────────

  const loadProfile = useCallback(async () => {
    if (!db || !doctorId) return;
    const { data } = await db
      .from('doctor_profiles')
      .select('*')
      .eq('doctor_id', doctorId)
      .single();
    if (data) {
      const row = data as DoctorProfileRow;
      setProfile(row);
      setIsOnline(row.is_available);
      setCanServeAsGp(row.can_serve_as_gp ?? false);
      setWarningAcknowledged(row.warning_acknowledged ?? false);
    }
  }, [db, doctorId]);

  const loadConsultations = useCallback(async () => {
    if (!db || !doctorId) return;
    const { data: active } = await db
      .from('consultations')
      .select('*')
      .eq('doctor_id', doctorId)
      .in('status', ['active', 'awaiting_extension', 'grace_period'])
      .order('created_at', { ascending: false });
    setActiveConsultations((active as Consultation[]) ?? []);

    const { data: pending } = await db
      .from('consultations')
      .select('*')
      .eq('doctor_id', doctorId)
      .eq('status', 'pending')
      .order('created_at', { ascending: false });
    setPendingRequests((pending as Consultation[]) ?? []);

    const { count: totalPatients } = await db
      .from('consultations')
      .select('patient_session_id', { count: 'exact', head: true })
      .eq('doctor_id', doctorId);

    const { count: royalClients } = await db
      .from('consultations')
      .select('patient_session_id', { count: 'exact', head: true })
      .eq('doctor_id', doctorId)
      .eq('service_tier', 'ROYAL');

    // Acceptance rate from consultation_requests
    const { data: requestStats } = await db
      .from('consultation_requests')
      .select('status')
      .eq('doctor_id', doctorId);
    let acceptanceRate: number | null = null;
    if (requestStats && requestStats.length > 0) {
      const accepted = requestStats.filter((r: { status: string }) => r.status === 'accepted').length;
      const rejected = requestStats.filter((r: { status: string }) => r.status === 'rejected').length;
      const expired = requestStats.filter((r: { status: string }) => r.status === 'expired').length;
      const total = accepted + rejected + expired;
      if (total > 0) acceptanceRate = Math.round((accepted / total) * 100);
    }

    setStats((prev) => ({
      ...prev,
      activeConsultations: (active ?? []).length,
      totalPatients: totalPatients ?? 0,
      royalClients: royalClients ?? 0,
      acceptanceRate,
    }));
  }, [db, doctorId]);

  const loadEarnings = useCallback(async () => {
    if (!db || !doctorId) return;

    // Recent transactions (last 10)
    const { data: recent } = await db
      .from('doctor_earnings')
      .select('*')
      .eq('doctor_id', doctorId)
      .order('created_at', { ascending: false })
      .limit(10);
    const rows = (recent ?? []) as EarningsRow[];
    setEarningsRows(rows);

    // Today's earnings
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    const { data: todayRows } = await db
      .from('doctor_earnings')
      .select('amount')
      .eq('doctor_id', doctorId)
      .gte('created_at', todayStart.toISOString());
    const todaysTotal = (todayRows ?? []).reduce((sum: number, r: { amount: number }) => sum + r.amount, 0);
    setStats((prev) => ({ ...prev, todaysEarnings: todaysTotal }));

    // Total earnings
    const { data: allEarnings } = await db
      .from('doctor_earnings')
      .select('amount, status, created_at')
      .eq('doctor_id', doctorId);
    const all = allEarnings ?? [];

    const now = new Date();
    const thisMonthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const lastMonthStart = new Date(now.getFullYear(), now.getMonth() - 1, 1);

    const total = all.reduce((s: number, r: { amount: number }) => s + r.amount, 0);
    const thisMonth = all
      .filter((r: { created_at: string }) => new Date(r.created_at) >= thisMonthStart)
      .reduce((s: number, r: { amount: number }) => s + r.amount, 0);
    const lastMonth = all
      .filter((r: { created_at: string }) => {
        const d = new Date(r.created_at);
        return d >= lastMonthStart && d < thisMonthStart;
      })
      .reduce((s: number, r: { amount: number }) => s + r.amount, 0);
    const pendingPayout = all
      .filter((r: { status: string }) => r.status === 'pending')
      .reduce((s: number, r: { amount: number }) => s + r.amount, 0);

    setEarningsSummary({ total, thisMonth, lastMonth, pendingPayout });
  }, [db, doctorId]);

  // ── Initial load (waits for fresh token) ─────────────────────────────────

  useEffect(() => {
    if (!db || !doctorId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    (async () => {
      // Ensure token is valid before querying
      await ensureFreshToken();
      if (cancelled) return;
      try {
        await Promise.all([loadProfile(), loadConsultations(), loadEarnings()]);
      } catch {
        // silently fail
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [db, doctorId, loadProfile, loadConsultations, loadEarnings]);

  // ── Realtime subscriptions ──────────────────────────────────────────────

  useEffect(() => {
    if (!db || !doctorId) return;

    const channels = [
      db.channel(`doctor-profile-${doctorId}`).on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'doctor_profiles', filter: `doctor_id=eq.${doctorId}` },
        () => { loadProfile(); }
      ),
      db.channel(`doctor-consultations-${doctorId}`).on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'consultations', filter: `doctor_id=eq.${doctorId}` },
        () => { loadConsultations(); }
      ),
      db.channel(`doctor-earnings-${doctorId}`).on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'doctor_earnings', filter: `doctor_id=eq.${doctorId}` },
        () => { loadEarnings(); }
      ),
    ];

    channels.forEach((ch) => ch.subscribe());

    return () => {
      channels.forEach((ch) => db.removeChannel(ch));
    };
  }, [db, doctorId, loadProfile, loadConsultations, loadEarnings]);

  // ── Actions ─────────────────────────────────────────────────────────────

  async function toggleOnline() {
    if (!db || !doctorId) return;
    const newStatus = !isOnline;
    setIsOnline(newStatus);
    try {
      const { error } = await db.from('doctor_profiles').update({
        is_available: newStatus,
        updated_at: new Date().toISOString(),
      }).eq('doctor_id', doctorId);
      if (error) throw error;

      const currentToken = useAuthStore.getState().session?.accessToken;
      if (currentToken) {
        invokeEdgeFunction('log-doctor-online', {
          action: newStatus ? 'online' : 'offline',
        }, currentToken, 'doctor').catch(() => {});
      }
    } catch {
      setIsOnline(!newStatus);
    }
  }

  async function toggleGpServing() {
    if (!db || !doctorId) return;
    const newVal = !canServeAsGp;
    setCanServeAsGp(newVal);
    try {
      const { error } = await db.from('doctor_profiles').update({
        can_serve_as_gp: newVal,
        updated_at: new Date().toISOString(),
      }).eq('doctor_id', doctorId);
      if (error) throw error;
    } catch {
      setCanServeAsGp(!newVal);
    }
  }

  async function acknowledgeWarning() {
    if (!db || !doctorId) return;
    setWarningAcknowledged(true);
    try {
      await db.from('doctor_profiles').update({
        warning_acknowledged: true,
        updated_at: new Date().toISOString(),
      }).eq('doctor_id', doctorId);
    } catch {
      setWarningAcknowledged(false);
    }
  }

  // ── Swipe handlers for warning ──────────────────────────────────────────

  function handleSwipeStart(e: React.TouchEvent) {
    swipeStartX.current = e.touches[0].clientX;
  }
  function handleSwipeEnd(e: React.TouchEvent) {
    if (swipeStartX.current === null || warningAcknowledged) return;
    const diff = e.changedTouches[0].clientX - swipeStartX.current;
    if (Math.abs(diff) > 80) {
      acknowledgeWarning();
    }
    swipeStartX.current = null;
  }

  // ── Derived state ───────────────────────────────────────────────────────

  const isSuspended = profile?.suspended_until
    ? new Date(profile.suspended_until) > new Date()
    : false;
  const isVerified = profile?.is_verified ?? false;
  const hasRejection = !!profile?.rejection_reason;
  const isPendingReview = !isVerified && !hasRejection;
  const isSpecialist = profile?.specialty === 'specialist';
  const warningCount = profile?.warning_count ?? 0;
  const warningMessage = profile?.warning_message;
  const warningAt = profile?.warning_at;

  // Find the most recent active consultation to resume
  const activeConsultationToResume = activeConsultations[0] ?? null;

  const statCards = [
    { label: 'Royal Clients', value: stats.royalClients, icon: Crown, color: 'var(--royal-purple)', bg: 'bg-[var(--royal-purple)]/10', href: '/royal-clients' },
    { label: "Today's Earnings", value: formatCurrency(stats.todaysEarnings), icon: DollarSign, color: 'var(--royal-gold)', bg: 'bg-[var(--royal-gold)]/10', href: '/earnings' },
    { label: 'Active Consults', value: stats.activeConsultations, icon: MessageSquare, color: 'var(--brand-teal)', bg: 'bg-[var(--brand-teal)]/10', href: '/consultations' },
    { label: 'Total Patients', value: stats.totalPatients, icon: Users, color: 'var(--success-green)', bg: 'bg-[var(--success-green)]/10', href: null },
    {
      label: 'Acceptance Rate',
      value: stats.acceptanceRate !== null ? `${stats.acceptanceRate}%` : '\u2014',
      icon: CheckCircle,
      color: stats.acceptanceRate !== null && stats.acceptanceRate < 75 ? '#DC2626' : 'var(--brand-teal)',
      bg: stats.acceptanceRate !== null && stats.acceptanceRate < 75 ? 'bg-[#FEE2E2]' : 'bg-[#D1FAE5]',
      href: null,
    },
  ];

  // ── Render ──────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="px-4 lg:px-8 py-6 max-w-4xl mx-auto space-y-5">
      {/* ── Top bar ────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-[var(--subtitle-grey)]">Welcome back</p>
          <h1 className="text-xl font-bold text-black">Dr. {doctorName}</h1>
        </div>

        {/* Status chip (mutually exclusive) */}
        <div>
          {isSuspended ? (
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold bg-red-100 text-[var(--error-red)] border border-red-200">
              <XCircle size={14} />
              Suspended
            </span>
          ) : isVerified ? (
            <button
              onClick={toggleOnline}
              className={`flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold transition-all ${
                isOnline
                  ? 'bg-[var(--success-green)] text-white shadow-lg shadow-[var(--success-green)]/30'
                  : 'bg-gray-200 text-gray-600'
              }`}
            >
              <div
                className={`w-2.5 h-2.5 rounded-full ${
                  isOnline ? 'bg-white animate-pulse' : 'bg-gray-400'
                }`}
              />
              {isOnline ? 'Online' : 'Offline'}
            </button>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold bg-orange-100 text-orange-700 border border-orange-200">
              <Clock size={14} />
              Under Review
            </span>
          )}
        </div>
      </div>

      {/* ── Specialist GP toggle ───────────────────────────────────────── */}
      {isSpecialist && isVerified && !isSuspended && (
        <div className={`flex items-center gap-3 px-4 py-3 rounded-xl border transition-colors ${
          canServeAsGp
            ? 'bg-[#EFF6FF] border-[#2563EB]/40'
            : 'bg-white border-[var(--card-border)]'
        }`}>
          <div className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 text-[11px] font-bold text-white ${
            canServeAsGp ? 'bg-[#2563EB]' : 'bg-[#9CA3AF]'
          }`}>
            GP
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-black">Serve as General Practitioner</p>
            <p className="text-[11px]" style={{ color: 'var(--on-surface-variant)' }}>
              {canServeAsGp ? 'Visible on GP listings \u2014 Paid at GP rates' : 'Toggle to appear on GP patient listings'}
            </p>
          </div>
          <button
            onClick={toggleGpServing}
            className={`relative w-11 h-6 rounded-full transition-colors shrink-0 ${
              canServeAsGp ? 'bg-[#2563EB]' : 'bg-[#D1D5DB]'
            }`}
          >
            <div
              className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${
                canServeAsGp ? 'translate-x-[22px]' : 'translate-x-0.5'
              }`}
            />
          </button>
        </div>
      )}

      {/* ── Status banners ─────────────────────────────────────────────── */}
      {isVerified && !isSuspended && (
        <div className="flex items-center gap-2 px-4 py-2.5 bg-[var(--brand-teal)]/10 border border-[var(--brand-teal)]/20 rounded-xl">
          <CheckCircle size={16} className="text-[var(--brand-teal)]" />
          <span className="text-sm font-medium text-[var(--brand-teal)]">Verified Doctor</span>
        </div>
      )}

      {hasRejection && (
        <div className="px-4 py-3 bg-[#FEF2F2] border border-[#FCA5A5] rounded-xl">
          <div className="flex items-center gap-2 mb-1">
            <XCircle size={16} className="text-[var(--error-red)]" />
            <span className="text-sm font-bold text-[var(--error-red)]">Application Rejected</span>
          </div>
          <p className="text-xs text-[var(--error-red)]/80 ml-6">{profile?.rejection_reason}</p>
          <p className="text-xs text-[var(--error-red)] font-medium ml-6 mt-1">
            Please resubmit your credentials.
          </p>
        </div>
      )}

      {isPendingReview && !isSuspended && (
        <div className="flex items-center gap-2 px-4 py-2.5 bg-orange-50 border border-orange-200 rounded-xl">
          <Clock size={16} className="text-orange-600" />
          <span className="text-sm font-medium text-orange-700">
            Your profile is under review. You will be notified once verified.
          </span>
        </div>
      )}

      {isSuspended && (
        <div className="px-4 py-3 bg-[#FEF2F2] border border-[#FCA5A5] rounded-xl">
          <div className="flex items-center gap-2 mb-1">
            <XCircle size={16} className="text-[var(--error-red)]" />
            <span className="text-sm font-bold text-[var(--error-red)]">Account Suspended</span>
          </div>
          {profile?.suspension_reason && (
            <p className="text-xs text-[var(--error-red)]/80 ml-6">{profile.suspension_reason}</p>
          )}
          <p className="text-xs text-[var(--error-red)] font-medium ml-6 mt-1">
            Suspended until {new Date(profile!.suspended_until!).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
          </p>
        </div>
      )}

      {/* ── Warning badge ──────────────────────────────────────────────── */}
      {warningMessage && warningCount > 0 && (
        <div
          className={`rounded-xl border overflow-hidden transition-colors ${
            warningAcknowledged
              ? 'border-gray-300 bg-gray-100'
              : 'border-[#FCD34D] bg-[#FEF3C7]'
          }`}
          style={!warningAcknowledged ? { animation: 'heartbeat 1.2s ease-in-out infinite' } : undefined}
          onTouchStart={handleSwipeStart}
          onTouchEnd={handleSwipeEnd}
        >
          <button
            onClick={() => setWarningExpanded(!warningExpanded)}
            className="w-full flex items-center justify-between px-4 py-3"
          >
            <div className="flex items-center gap-3">
              <div className="relative">
                <AlertTriangle
                  size={20}
                  className={warningAcknowledged ? 'text-gray-500' : 'text-[#B45309]'}
                />
                <span className={`absolute -top-1.5 -right-2 w-4 h-4 rounded-full text-white text-[10px] font-bold flex items-center justify-center ${
                  warningAcknowledged ? 'bg-gray-400' : 'bg-red-500'
                }`}>
                  {warningCount}
                </span>
              </div>
              <div className="text-left">
                <p className={`text-sm font-bold ${warningAcknowledged ? 'text-gray-600' : 'text-[#B45309]'}`}>
                  Warnings ({warningCount})
                </p>
                {!warningExpanded && (
                  <p className={`text-xs ${warningAcknowledged ? 'text-gray-500' : 'text-[#92400E]'}`}>
                    {warningAcknowledged ? 'Tap to view details' : 'Swipe to acknowledge'}
                  </p>
                )}
              </div>
            </div>
            {warningExpanded ? (
              <ChevronUp size={20} className={warningAcknowledged ? 'text-gray-500' : 'text-[#B45309]'} />
            ) : (
              <ChevronDown size={20} className={warningAcknowledged ? 'text-gray-500' : 'text-[#B45309]'} />
            )}
          </button>
          {warningExpanded && (
            <div className="px-4 pb-4">
              <div className={`rounded-lg border p-4 ${
                warningAcknowledged ? 'border-gray-300 bg-white' : 'border-[#FCD34D] bg-white'
              }`}>
                <p className={`text-sm font-bold ${warningAcknowledged ? 'text-gray-600' : 'text-[#B45309]'}`}>
                  From Administration
                </p>
                {warningAt && (
                  <p className={`text-xs mt-1 ${warningAcknowledged ? 'text-gray-500' : 'text-[#92400E]'}`}>
                    {new Date(warningAt).toLocaleDateString('en-US', {
                      month: 'short', day: 'numeric', year: 'numeric',
                    })}{' '}at{' '}
                    {new Date(warningAt).toLocaleTimeString('en-US', {
                      hour: '2-digit', minute: '2-digit',
                    })}
                  </p>
                )}
                <p className={`text-sm mt-3 leading-relaxed ${warningAcknowledged ? 'text-gray-600' : 'text-[#92400E]'}`}>
                  {warningMessage}
                </p>
              </div>
              {!warningAcknowledged && (
                <button
                  onClick={acknowledgeWarning}
                  className="mt-3 w-full text-center text-xs font-semibold text-[#B45309] py-2 rounded-lg border border-[#FCD34D] hover:bg-[#FCD34D]/20 transition-colors"
                >
                  Acknowledge Warning
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── Stats grid ─────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 gap-3">
        {statCards.map(({ label, value, icon: Icon, color, bg, href }) => (
          <Card
            key={label}
            className="!p-3.5"
            onClick={href ? () => router.push(href) : undefined}
          >
            <div className="flex items-start justify-between mb-2">
              <div className={`w-9 h-9 rounded-xl ${bg} flex items-center justify-center`}>
                <Icon size={18} style={{ color }} />
              </div>
              {href && <ChevronRight size={14} className="text-gray-300 mt-1" />}
            </div>
            <p className="text-xl font-bold text-black">{value}</p>
            <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">{label}</p>
          </Card>
        ))}
      </div>

      {/* ── Today's availability ───────────────────────────────────────── */}
      <Card>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center">
              <Calendar size={18} className="text-[var(--brand-teal)]" />
            </div>
            <div>
              <p className="text-sm font-bold text-black">Today&apos;s Availability</p>
              <p className="text-xs text-[var(--subtitle-grey)]">
                {isOnline ? 'You are currently online' : 'You are offline'}
              </p>
            </div>
          </div>
          <button
            onClick={() => router.push('/availability')}
            className="text-xs font-semibold text-[var(--brand-teal)] px-3 py-1.5 bg-[var(--brand-teal)]/10 rounded-lg hover:bg-[var(--brand-teal)]/20 transition-colors"
          >
            Set Availability
          </button>
        </div>
      </Card>

      {/* ── Active consultations ───────────────────────────────────────── */}
      {activeConsultations.length > 0 && (
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-bold text-black">Active Consultations</h2>
            <button
              onClick={() => router.push('/consultations')}
              className="text-xs font-medium text-[var(--brand-teal)] flex items-center gap-0.5"
            >
              View all <ChevronRight size={14} />
            </button>
          </div>
          <div className="space-y-3">
            {activeConsultations.slice(0, 3).map((c) => (
              <Card
                key={c.consultation_id}
                onClick={() => router.push(`/doc-consultation/${c.consultation_id}`)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
                      <MessageSquare size={18} className="text-[var(--brand-teal)]" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-black">{c.service_type}</p>
                      <div className="flex items-center gap-2 mt-0.5">
                        <Badge variant={c.service_tier === 'ROYAL' ? 'purple' : 'teal'}>
                          {c.service_tier}
                        </Badge>
                        <span className="text-xs text-[var(--subtitle-grey)]">
                          {formatCurrency(c.consultation_fee)}
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="text-right">
                    <ConsultationTimer
                      startTime={c.session_start_time}
                      endTime={c.scheduled_end_at}
                    />
                    <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">
                      <Clock size={10} className="inline mr-0.5" />
                      {c.session_duration_minutes}min
                    </p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* ── Pending requests ───────────────────────────────────────────── */}
      {pendingRequests.length > 0 && (
        <section>
          <h2 className="text-base font-bold text-black mb-3">Pending Requests</h2>
          <div className="space-y-3">
            {pendingRequests.slice(0, 5).map((c) => (
              <Card
                key={c.consultation_id}
                onClick={() => router.push(`/doc-consultation/${c.consultation_id}`)}
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-semibold text-black">{c.service_type}</p>
                    <div className="flex items-center gap-2 mt-1">
                      <Badge variant={c.service_tier === 'ROYAL' ? 'purple' : 'teal'}>
                        {c.service_tier}
                      </Badge>
                      <span className="text-xs text-[var(--subtitle-grey)]">
                        {formatTimeAgo(c.created_at)}
                      </span>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold text-black">{formatCurrency(c.consultation_fee)}</p>
                    <Badge variant="gold">Pending</Badge>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* ── Earnings section ───────────────────────────────────────────── */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-bold text-black">Earnings</h2>
          <button
            onClick={() => router.push('/earnings')}
            className="text-xs font-medium text-[var(--brand-teal)] flex items-center gap-0.5"
          >
            View all <ChevronRight size={14} />
          </button>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 gap-3 mb-3">
          <Card className="!p-3">
            <p className="text-xs text-[var(--subtitle-grey)]">Total Earnings</p>
            <p className="text-lg font-bold text-black">{formatCurrency(earningsSummary.total)}</p>
          </Card>
          <Card className="!p-3">
            <p className="text-xs text-[var(--subtitle-grey)]">This Month</p>
            <p className="text-lg font-bold text-black">{formatCurrency(earningsSummary.thisMonth)}</p>
          </Card>
          <Card className="!p-3">
            <p className="text-xs text-[var(--subtitle-grey)]">Last Month</p>
            <p className="text-lg font-bold text-black">{formatCurrency(earningsSummary.lastMonth)}</p>
          </Card>
          <Card className="!p-3">
            <p className="text-xs text-[var(--subtitle-grey)]">Pending Payout</p>
            <p className="text-lg font-bold text-[var(--royal-gold)]">{formatCurrency(earningsSummary.pendingPayout)}</p>
          </Card>
        </div>

        {/* Recent transactions */}
        {earningsRows.length > 0 && (
          <Card padding={false}>
            <div className="px-4 py-2.5 border-b border-[var(--card-border)]">
              <p className="text-xs font-semibold text-[var(--subtitle-grey)] uppercase tracking-wide">
                Recent Transactions
              </p>
            </div>
            <div className="divide-y divide-[var(--card-border)]">
              {earningsRows.map((tx, idx) => {
                const meta = earningTypeLabel(tx.earning_type);
                return (
                  <div key={tx.earning_id ?? tx.id ?? idx} className="flex items-center justify-between px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-[var(--success-green)]/10 flex items-center justify-center">
                        <ArrowUpRight size={16} className="text-[var(--success-green)]" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-medium text-black">{meta.label}</p>
                          <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                            meta.color === 'purple'
                              ? 'bg-[var(--royal-purple)]/10 text-[var(--royal-purple)]'
                              : 'bg-[var(--brand-teal)]/10 text-[var(--brand-teal)]'
                          }`}>
                            {meta.pct}
                          </span>
                        </div>
                        <p className="text-xs text-[var(--subtitle-grey)]">{formatDate(tx.created_at)}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-bold text-[var(--success-green)]">
                        +{formatCurrency(tx.amount)}
                      </p>
                      <span className={`text-[10px] font-bold ${
                        tx.status === 'paid' ? 'text-[var(--success-green)]' : 'text-[var(--royal-gold)]'
                      }`}>
                        {tx.status === 'paid' ? 'Paid' : 'Pending'}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        )}
      </section>

      {/* ── Contact us ─────────────────────────────────────────────────── */}
      <section>
        <h2 className="text-base font-bold text-black mb-3">Contact Us</h2>
        <Card>
          <div className="space-y-3">
            <a href="tel:+255663582994" className="flex items-center gap-3 text-sm text-black hover:text-[var(--brand-teal)]">
              <div className="w-8 h-8 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
                <Phone size={14} className="text-[var(--brand-teal)]" />
              </div>
              +255 663 582 994
            </a>
            <a href="mailto:support@esiri.africa" className="flex items-center gap-3 text-sm text-black hover:text-[var(--brand-teal)]">
              <div className="w-8 h-8 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
                <Mail size={14} className="text-[var(--brand-teal)]" />
              </div>
              support@esiri.africa
            </a>
          </div>
        </Card>
      </section>

      {/* ── Empty state ────────────────────────────────────────────────── */}
      {activeConsultations.length === 0 && pendingRequests.length === 0 && (
        <div className="text-center py-12">
          <div className="w-16 h-16 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center mx-auto mb-4">
            <MessageSquare size={28} className="text-[var(--brand-teal)]" />
          </div>
          <h3 className="text-base font-bold text-black mb-1">No active consultations</h3>
          <p className="text-sm text-[var(--subtitle-grey)]">
            {isOnline
              ? 'Waiting for patient requests...'
              : 'Go online to start receiving consultation requests'}
          </p>
          {!isOnline && isVerified && !isSuspended && (
            <Button className="mt-4" onClick={toggleOnline}>
              Go Online
            </Button>
          )}
        </div>
      )}

      <ContactUs />

      {/* ── Resume chat FAB ────────────────────────────────────────────── */}
      {activeConsultationToResume && (
        <button
          onClick={() => router.push(`/doc-consultation/${activeConsultationToResume.consultation_id}`)}
          className="fixed bottom-24 right-5 lg:bottom-8 lg:right-8 w-14 h-14 rounded-full bg-[var(--brand-teal)] text-white shadow-xl shadow-[var(--brand-teal)]/40 flex items-center justify-center z-30 animate-pulse hover:scale-105 transition-transform"
          title="Resume active consultation"
        >
          <MessageSquare size={24} />
        </button>
      )}
    </div>
  );
}
