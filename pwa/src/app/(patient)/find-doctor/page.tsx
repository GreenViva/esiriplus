'use client';

import { useState, useEffect, useCallback, useRef, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  Star,
  Clock,
  Languages,
  Search,
  BadgeCheck,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Stethoscope,
  X,
  CalendarPlus,
  MessageSquarePlus,
  Users,
  XCircle,
  TimerOff,
} from 'lucide-react';
import { useAuthStore } from '@/store/auth';
import { supabase, invokeEdgeFunction } from '@/lib/supabase';
import type { DoctorProfile } from '@/types';

// ── Cosmic theme tokens ──────────────────────────────────────────────────────
const cosmic = {
  bg: '#0A1628',
  surface: 'rgba(255,255,255,0.06)',
  surfaceHover: 'rgba(255,255,255,0.10)',
  border: 'rgba(255,255,255,0.08)',
  accent: '#4DD0E1',
  accentDim: 'rgba(77,208,225,0.15)',
  textPrimary: '#F0F4FF',
  textSecondary: '#B0BEC5',
  amber: '#F59E0B',
  green: '#22C55E',
  red: '#EF4444',
} as const;

type StatusFilter = 'all' | 'online' | 'offline';

type RequestState =
  | 'idle'
  | 'requesting'
  | 'waiting'
  | 'accepted'
  | 'rejected'
  | 'expired'
  | 'error';

const REQUEST_TIMEOUT_SECONDS = 60;
const POLL_INTERVAL_MS = 2000;

export default function FindDoctorPage() {
  return (
    <Suspense>
      <FindDoctorContent />
    </Suspense>
  );
}

function FindDoctorContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const tier = searchParams.get('tier') ?? 'ECONOMY';
  const service = searchParams.get('service') ?? '';
  const price = searchParams.get('price') ?? '';
  const duration = searchParams.get('duration') ?? '';

  const token = useAuthStore((s) => s.session?.accessToken);
  const patientSession = useAuthStore((s) => s.patientSession);
  const hasHydrated = useAuthStore((s) => s._hasHydrated);

  // ── State ────────────────────────────────────────────────────────────────
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [selectedDoctor, setSelectedDoctor] = useState<DoctorProfile | null>(null);
  const [requestState, setRequestState] = useState<RequestState>('idle');
  const [requestError, setRequestError] = useState('');
  const [consultationId, setConsultationId] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(REQUEST_TIMEOUT_SECONDS);
  const [chiefComplaint, setChiefComplaint] = useState('');
  const [showComplaintInput, setShowComplaintInput] = useState(false);
  const [requestId, setRequestId] = useState<string | null>(null);

  // Refs for cleanup of polling / timers
  const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const countdownIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Map service category to specialty name (matching Android's categoryToSpecialty)
  const specialty = (service ?? '').toLowerCase();

  // ── Load doctors via edge function (same as Android) ────────────────────
  const loadDoctors = useCallback(async () => {
    if (!specialty) return;
    setLoading(true);
    try {
      const result = await invokeEdgeFunction<{ doctors: Record<string, unknown>[] }>(
        'list-doctors',
        { specialty },
      );

      const mapped: DoctorProfile[] = (result.doctors ?? []).map((d) => ({
        doctorId: (d.doctor_id ?? '') as string,
        fullName: (d.full_name ?? '') as string,
        email: (d.email ?? '') as string,
        phone: (d.phone ?? '') as string,
        specialty: (d.specialty ?? '') as string,
        licenseNumber: (d.license_number ?? '') as string,
        yearsExperience: (d.years_experience ?? 0) as number,
        bio: (d.bio ?? '') as string,
        isVerified: (d.is_verified ?? false) as boolean,
        isAvailable: (d.is_available ?? false) as boolean,
        isOnline: (d.is_available ?? false) as boolean,
        rating: (d.average_rating ?? 0) as number,
        totalConsultations: (d.total_ratings ?? 0) as number,
        profilePhotoUrl: (d.profile_photo_url ?? undefined) as string | undefined,
        languages: (d.languages ?? []) as string[],
        services: (d.services ?? []) as string[],
        country: (d.country ?? '') as string,
      }));

      setDoctors(mapped);
    } catch (err) {
      console.error('Failed to load doctors:', err);
    } finally {
      setLoading(false);
    }
  }, [specialty]);

  useEffect(() => {
    loadDoctors();
  }, [loadDoctors]);

  // ── Realtime doctor online status subscription ──────────────────────────
  useEffect(() => {
    const channel = supabase
      .channel('doctor-status')
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'doctor_profiles' },
        (payload) => {
          const updated = payload.new as Record<string, unknown>;
          const docId = updated.doctor_id as string | undefined;
          if (!docId) return;
          setDoctors((prev) =>
            prev.map((d) =>
              d.doctorId === docId
                ? {
                    ...d,
                    isOnline: (updated.is_available ?? d.isOnline) as boolean,
                    isAvailable: (updated.is_available ?? d.isAvailable) as boolean,
                  }
                : d,
            ),
          );
        },
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, []);

  // ── Cleanup polling/timers on unmount ───────────────────────────────────
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
      if (countdownIntervalRef.current) clearInterval(countdownIntervalRef.current);
    };
  }, []);

  // ── Navigate after accepted ─────────────────────────────────────────────
  useEffect(() => {
    if (requestState !== 'accepted' || !consultationId) return;
    const timer = setTimeout(() => {
      router.push(`/consultation/${consultationId}`);
    }, 2000);
    return () => clearTimeout(timer);
  }, [requestState, consultationId, router]);

  // ── Filter doctors ───────────────────────────────────────────────────────
  const filteredDoctors = doctors.filter((d) => {
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      !q ||
      d.fullName.toLowerCase().includes(q) ||
      d.specialty.toLowerCase().includes(q);

    const matchesStatus =
      statusFilter === 'all' ||
      (statusFilter === 'online' && d.isOnline) ||
      (statusFilter === 'offline' && !d.isOnline);

    return matchesSearch && matchesStatus;
  });

  // ── Stop polling helper ─────────────────────────────────────────────────
  function stopPolling() {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
    if (countdownIntervalRef.current) {
      clearInterval(countdownIntervalRef.current);
      countdownIntervalRef.current = null;
    }
  }

  // ── Cancel request ──────────────────────────────────────────────────────
  function cancelRequest() {
    stopPolling();
    setRequestState('idle');
    setRequestId(null);
    setConsultationId(null);
  }

  // ── Request consultation ─────────────────────────────────────────────────
  function initiateRequest(doctor: DoctorProfile) {
    setSelectedDoctor(doctor);
    setShowComplaintInput(true);
    setChiefComplaint('');
  }

  async function handleRequestConsultation() {
    if (!selectedDoctor) return;
    const currentToken = useAuthStore.getState().session?.accessToken;
    if (!currentToken) {
      setRequestError('Session expired. Please go back and log in again.');
      return;
    }
    const currentPatientSession = useAuthStore.getState().patientSession;

    setShowComplaintInput(false);
    setRequestState('requesting');
    setRequestError('');
    setConsultationId(null);
    setRequestId(null);

    try {
      console.log('[FindDoctor] Creating consultation request:', {
        specialty,
        doctor: selectedDoctor.doctorId,
        hasToken: !!currentToken,
      });

      // Step 1: Create the consultation request via handle-consultation-request
      const createResult = await invokeEdgeFunction<{
        request_id?: string;
        status?: string;
        expires_at?: string;
        doctor_name?: string;
      }>(
        'handle-consultation-request',
        {
          action: 'create',
          doctor_id: selectedDoctor.doctorId,
          service_type: specialty,
          service_tier: tier,
          chief_complaint: chiefComplaint.trim() || undefined,
          patient_age_group: currentPatientSession?.ageGroup || undefined,
          patient_sex: currentPatientSession?.sex || undefined,
        },
        currentToken,
        'patient',
      );

      const rId = createResult?.request_id;
      if (!rId) {
        throw new Error('No request ID returned');
      }

      setRequestId(rId);
      setRequestState('waiting');
      setCountdown(REQUEST_TIMEOUT_SECONDS);

      // Step 2: Start countdown timer
      countdownIntervalRef.current = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            // Time expired — stop everything
            stopPolling();
            setRequestState('expired');
            return 0;
          }
          return prev - 1;
        });
      }, 1000);

      // Step 3: Poll for status every 2 seconds
      pollIntervalRef.current = setInterval(async () => {
        try {
          const freshToken = useAuthStore.getState().session?.accessToken;
          if (!freshToken) {
            stopPolling();
            setRequestState('error');
            setRequestError('Session expired.');
            return;
          }

          const statusResult = await invokeEdgeFunction<{
            status?: string;
            consultation_id?: string;
          }>(
            'handle-consultation-request',
            { action: 'status', request_id: rId },
            freshToken,
            'patient',
          );

          const status = statusResult?.status;

          if (status === 'accepted') {
            stopPolling();
            const cId = statusResult.consultation_id;
            if (cId) {
              setConsultationId(cId);
              setRequestState('accepted');
            } else {
              setRequestState('error');
              setRequestError('Accepted but no consultation ID returned.');
            }
          } else if (status === 'rejected') {
            stopPolling();
            setRequestState('rejected');
          } else if (status === 'expired') {
            stopPolling();
            setRequestState('expired');
          }
          // "pending" — keep polling
        } catch (pollErr) {
          console.error('[FindDoctor] Poll error:', pollErr);
          // Don't stop polling on transient errors
        }
      }, POLL_INTERVAL_MS);
    } catch (err) {
      stopPolling();
      setRequestError(
        err instanceof Error ? err.message : 'Failed to request consultation',
      );
      setRequestState('error');
    }
  }

  // ── Initials helper ──────────────────────────────────────────────────────
  function getInitials(name: string) {
    return name
      .split(' ')
      .map((w) => w[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);
  }

  // ── Render ───────────────────────────────────────────────────────────────
  return (
    <div
      className="min-h-dvh"
      style={{
        background: `linear-gradient(180deg, ${cosmic.bg} 0%, #0D1F3C 50%, ${cosmic.bg} 100%)`,
      }}
    >
      {/* ── Header ──────────────────────────────────────────────────────── */}
      <div
        className="sticky top-0 z-20 px-5 pt-4 pb-3"
        style={{
          background: `linear-gradient(180deg, ${cosmic.bg} 60%, transparent)`,
          backdropFilter: 'blur(12px)',
        }}
      >
        <div className="flex items-center gap-3 mb-2">
          <button
            onClick={() => router.back()}
            className="p-2 -ml-2 rounded-xl transition-colors"
            style={{ background: cosmic.surface }}
          >
            <ArrowLeft size={20} style={{ color: cosmic.textPrimary }} />
          </button>
          <div className="flex-1 text-center">
            <h1
              className="text-lg font-bold"
              style={{ color: cosmic.textPrimary }}
            >
              Find a Doctor
            </h1>
            <p className="text-xs" style={{ color: cosmic.textSecondary }}>
              Choose your healthcare provider
            </p>
          </div>
          <div className="w-9" /> {/* Spacer for centering */}
        </div>

        {/* Info badges */}
        <div className="flex items-center justify-center gap-2 mt-1">
          <span
            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium"
            style={{
              background: cosmic.surface,
              color: cosmic.textPrimary,
              border: `1px solid ${cosmic.border}`,
            }}
          >
            <span
              className="w-1.5 h-1.5 rounded-full"
              style={{ background: cosmic.accent }}
            />
            Tanzania
          </span>
          {service && (
            <span
              className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium"
              style={{
                background: cosmic.accentDim,
                color: cosmic.accent,
                border: `1px solid rgba(77,208,225,0.2)`,
              }}
            >
              <Stethoscope size={12} />
              {service}
            </span>
          )}
        </div>
      </div>

      {/* ── Search Bar ──────────────────────────────────────────────────── */}
      <div className="px-5 mt-3">
        <div
          className="flex items-center gap-3 px-4 py-3 rounded-2xl"
          style={{
            background: cosmic.surface,
            border: `1px solid ${cosmic.border}`,
            backdropFilter: 'blur(16px)',
          }}
        >
          <Search size={18} style={{ color: cosmic.textSecondary }} />
          <input
            type="text"
            placeholder="Search by name, specialty..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 bg-transparent text-sm outline-none placeholder:opacity-50"
            style={{ color: cosmic.textPrimary }}
          />
        </div>
      </div>

      {/* ── Filter Chips ────────────────────────────────────────────────── */}
      <div className="flex gap-2 px-5 mt-4 overflow-x-auto no-scrollbar">
        {(['all', 'online', 'offline'] as StatusFilter[]).map((f) => {
          const isActive = statusFilter === f;
          return (
            <button
              key={f}
              onClick={() => setStatusFilter(f)}
              className="px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-all"
              style={{
                background: isActive
                  ? `linear-gradient(135deg, ${cosmic.accent}, #26C6DA)`
                  : cosmic.surface,
                color: isActive ? '#0A1628' : cosmic.textSecondary,
                border: `1px solid ${isActive ? 'transparent' : cosmic.border}`,
              }}
            >
              {f === 'all' ? 'All' : f === 'online' ? 'Online' : 'Offline'}
            </button>
          );
        })}
      </div>

      {/* ── Doctor Grid ─────────────────────────────────────────────────── */}
      <div className="px-5 mt-5 pb-8">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Loader2
              size={32}
              className="animate-spin"
              style={{ color: cosmic.accent }}
            />
            <p
              className="text-sm mt-3"
              style={{ color: cosmic.textSecondary }}
            >
              Loading doctors...
            </p>
          </div>
        ) : filteredDoctors.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <div
              className="w-16 h-16 rounded-full flex items-center justify-center mb-4"
              style={{ background: cosmic.surface }}
            >
              <Users size={28} style={{ color: cosmic.textSecondary }} />
            </div>
            <p
              className="text-sm font-medium"
              style={{ color: cosmic.textPrimary }}
            >
              No doctors found
            </p>
            <p
              className="text-xs mt-1 max-w-[240px]"
              style={{ color: cosmic.textSecondary }}
            >
              {searchQuery
                ? 'Try a different search term'
                : 'No verified doctors available at the moment'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
            {filteredDoctors.map((doctor) => (
              <button
                key={doctor.doctorId}
                onClick={() => {
                  setSelectedDoctor(doctor);
                  setRequestState('idle');
                  setRequestError('');
                }}
                className="flex flex-col items-center p-3 rounded-2xl transition-all active:scale-95"
                style={{
                  background: cosmic.surface,
                  border: `1px solid ${cosmic.border}`,
                  backdropFilter: 'blur(12px)',
                }}
              >
                {/* Avatar */}
                <div className="relative mb-2">
                  <div
                    className="w-16 h-16 rounded-full overflow-hidden flex items-center justify-center"
                    style={{
                      background: `linear-gradient(135deg, ${cosmic.accentDim}, rgba(77,208,225,0.05))`,
                      border: `2px solid ${cosmic.border}`,
                    }}
                  >
                    {doctor.profilePhotoUrl ? (
                      <img
                        src={doctor.profilePhotoUrl}
                        alt={doctor.fullName}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span
                        className="text-lg font-bold"
                        style={{ color: cosmic.accent }}
                      >
                        {getInitials(doctor.fullName)}
                      </span>
                    )}
                  </div>
                  {/* Online indicator */}
                  <span
                    className="absolute bottom-0 right-0 w-4 h-4 rounded-full border-2"
                    style={{
                      background: doctor.isOnline ? cosmic.green : '#6B7280',
                      borderColor: cosmic.bg,
                    }}
                  />
                </div>

                {/* Name */}
                <p
                  className="text-xs font-semibold text-center leading-tight line-clamp-2"
                  style={{ color: cosmic.textPrimary }}
                >
                  Dr. {doctor.fullName.split(' ')[0]}
                </p>

                {/* Rating */}
                {doctor.rating > 0 && (
                  <div className="flex items-center gap-0.5 mt-1">
                    <Star
                      size={10}
                      fill={cosmic.amber}
                      style={{ color: cosmic.amber }}
                    />
                    <span
                      className="text-[10px] font-medium"
                      style={{ color: cosmic.amber }}
                    >
                      {doctor.rating.toFixed(1)}
                    </span>
                  </div>
                )}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ── Doctor Detail Bottom Sheet ──────────────────────────────────── */}
      {selectedDoctor && (
        <div className="fixed inset-0 z-50">
          {/* Backdrop */}
          <div
            className="absolute inset-0 transition-opacity"
            style={{ background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)' }}
            onClick={() => {
              if (requestState === 'idle' || requestState === 'error') setSelectedDoctor(null);
            }}
          />

          {/* Sheet */}
          <div
            className="absolute bottom-0 left-0 right-0 rounded-t-3xl max-h-[90vh] overflow-y-auto"
            style={{
              background: `linear-gradient(180deg, #121E36 0%, ${cosmic.bg} 100%)`,
              border: `1px solid ${cosmic.border}`,
              borderBottom: 'none',
              animation: 'cosmicSlideUp 0.35s cubic-bezier(0.16,1,0.3,1)',
            }}
          >
            {/* Handle */}
            <div className="flex justify-center pt-3 pb-1">
              <div
                className="w-10 h-1 rounded-full"
                style={{ background: 'rgba(255,255,255,0.2)' }}
              />
            </div>

            {/* Close button */}
            <button
              onClick={() => {
                if (requestState === 'idle' || requestState === 'error') setSelectedDoctor(null);
              }}
              className="absolute top-4 right-4 p-1.5 rounded-full"
              style={{ background: cosmic.surface }}
            >
              <X size={16} style={{ color: cosmic.textSecondary }} />
            </button>

            <div className="px-6 pb-8 pt-2">
              {/* Profile header */}
              <div className="flex flex-col items-center mb-6">
                {/* Large avatar */}
                <div
                  className="w-24 h-24 rounded-full overflow-hidden flex items-center justify-center mb-3"
                  style={{
                    background: `linear-gradient(135deg, ${cosmic.accentDim}, rgba(77,208,225,0.08))`,
                    border: `3px solid rgba(77,208,225,0.3)`,
                    boxShadow: `0 0 30px ${cosmic.accentDim}`,
                  }}
                >
                  {selectedDoctor.profilePhotoUrl ? (
                    <img
                      src={selectedDoctor.profilePhotoUrl}
                      alt={selectedDoctor.fullName}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <span
                      className="text-3xl font-bold"
                      style={{ color: cosmic.accent }}
                    >
                      {getInitials(selectedDoctor.fullName)}
                    </span>
                  )}
                </div>

                {/* Name + verified */}
                <div className="flex items-center gap-1.5 mb-1">
                  <h3
                    className="text-lg font-bold"
                    style={{ color: cosmic.textPrimary }}
                  >
                    Dr. {selectedDoctor.fullName}
                  </h3>
                  {selectedDoctor.isVerified && (
                    <BadgeCheck
                      size={18}
                      style={{ color: cosmic.accent }}
                    />
                  )}
                </div>

                <p
                  className="text-sm"
                  style={{ color: cosmic.textSecondary }}
                >
                  {selectedDoctor.specialty}
                </p>

                {/* Rating + Experience row */}
                <div className="flex items-center gap-4 mt-3">
                  <div className="flex items-center gap-1">
                    <Star
                      size={14}
                      fill={cosmic.amber}
                      style={{ color: cosmic.amber }}
                    />
                    <span
                      className="text-sm font-semibold"
                      style={{ color: cosmic.textPrimary }}
                    >
                      {selectedDoctor.rating.toFixed(1)}
                    </span>
                  </div>
                  <span
                    className="text-xs"
                    style={{ color: 'rgba(255,255,255,0.2)' }}
                  >
                    |
                  </span>
                  <div className="flex items-center gap-1">
                    <Clock size={14} style={{ color: cosmic.accent }} />
                    <span
                      className="text-sm"
                      style={{ color: cosmic.textSecondary }}
                    >
                      {selectedDoctor.yearsExperience} years experience
                    </span>
                  </div>
                </div>
              </div>

              {/* Info cards */}
              <div className="space-y-3">
                {/* Languages */}
                {selectedDoctor.languages.length > 0 && (
                  <div
                    className="flex items-start gap-3 p-3 rounded-xl"
                    style={{
                      background: cosmic.surface,
                      border: `1px solid ${cosmic.border}`,
                    }}
                  >
                    <Languages
                      size={16}
                      className="mt-0.5 shrink-0"
                      style={{ color: cosmic.accent }}
                    />
                    <div className="flex flex-wrap gap-1.5">
                      {selectedDoctor.languages.map((lang) => (
                        <span
                          key={lang}
                          className="px-2.5 py-0.5 rounded-full text-xs font-medium"
                          style={{
                            background: cosmic.accentDim,
                            color: cosmic.accent,
                          }}
                        >
                          {lang}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Bio */}
                {selectedDoctor.bio && (
                  <div
                    className="p-3 rounded-xl"
                    style={{
                      background: cosmic.surface,
                      border: `1px solid ${cosmic.border}`,
                    }}
                  >
                    <p
                      className="text-sm leading-relaxed"
                      style={{ color: cosmic.textSecondary }}
                    >
                      {selectedDoctor.bio}
                    </p>
                  </div>
                )}

                {/* Consultations count */}
                <div
                  className="flex items-center gap-3 p-3 rounded-xl"
                  style={{
                    background: cosmic.surface,
                    border: `1px solid ${cosmic.border}`,
                  }}
                >
                  <Stethoscope size={16} style={{ color: cosmic.accent }} />
                  <span
                    className="text-sm"
                    style={{ color: cosmic.textSecondary }}
                  >
                    <span
                      className="font-semibold"
                      style={{ color: cosmic.textPrimary }}
                    >
                      {selectedDoctor.totalConsultations}
                    </span>{' '}
                    total consultations
                  </span>
                </div>
              </div>

              {/* ── Action area ─────────────────────────────────────────── */}
              <div className="mt-6">
                {requestState === 'idle' && (
                  <div className="flex gap-3">
                    <button
                      onClick={() => {
                        router.push(
                          `/book-appointment?doctorId=${selectedDoctor.doctorId}&service=${service}&tier=${tier}`,
                        );
                      }}
                      className="flex-1 flex items-center justify-center gap-2 py-3.5 rounded-2xl text-sm font-semibold transition-all active:scale-95"
                      style={{
                        background: 'transparent',
                        color: cosmic.accent,
                        border: `1.5px solid ${cosmic.accent}`,
                      }}
                    >
                      <CalendarPlus size={16} />
                      Book Appointment
                    </button>
                    <button
                      onClick={() => initiateRequest(selectedDoctor)}
                      className="flex-1 flex items-center justify-center gap-2 py-3.5 rounded-2xl text-sm font-semibold transition-all active:scale-95"
                      style={{
                        background: `linear-gradient(135deg, #2A9D8F, ${cosmic.accent})`,
                        color: '#0A1628',
                        border: 'none',
                        boxShadow: '0 4px 20px rgba(42,157,143,0.3)',
                      }}
                    >
                      <MessageSquarePlus size={16} />
                      Request Consultation
                    </button>
                  </div>
                )}

                {requestState === 'requesting' && (
                  <div className="flex flex-col items-center py-6">
                    <Loader2
                      size={32}
                      className="animate-spin mb-3"
                      style={{ color: cosmic.accent }}
                    />
                    <p
                      className="text-sm font-medium"
                      style={{ color: cosmic.textPrimary }}
                    >
                      Sending request...
                    </p>
                  </div>
                )}

                {requestState === 'error' && (
                  <div className="flex flex-col items-center py-4">
                    <div
                      className="w-12 h-12 rounded-full flex items-center justify-center mb-3"
                      style={{ background: 'rgba(239,68,68,0.15)' }}
                    >
                      <AlertCircle size={24} style={{ color: cosmic.red }} />
                    </div>
                    <p
                      className="text-sm font-medium text-center mb-1"
                      style={{ color: cosmic.red }}
                    >
                      Request Failed
                    </p>
                    <p
                      className="text-xs text-center mb-4 max-w-[260px]"
                      style={{ color: cosmic.textSecondary }}
                    >
                      {requestError}
                    </p>
                    <button
                      onClick={() => initiateRequest(selectedDoctor)}
                      className="px-6 py-2.5 rounded-xl text-sm font-semibold transition-all active:scale-95"
                      style={{
                        background: cosmic.surface,
                        color: cosmic.accent,
                        border: `1px solid ${cosmic.accent}`,
                      }}
                    >
                      Retry
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>

          <style>{`
            @keyframes cosmicSlideUp {
              from { transform: translateY(100%); opacity: 0.5; }
              to { transform: translateY(0); opacity: 1; }
            }
            .no-scrollbar::-webkit-scrollbar { display: none; }
            .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
          `}</style>
        </div>
      )}
      {/* Chief Complaint Input Modal */}
      {showComplaintInput && selectedDoctor && (
        <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center" onClick={() => setShowComplaintInput(false)}>
          <div className="absolute inset-0 bg-black/60" />
          <div
            className="relative z-10 w-full max-w-md bg-white rounded-t-2xl sm:rounded-2xl p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-bold text-black mb-1">Describe Your Concern</h3>
            <p className="text-sm text-[var(--subtitle-grey)] mb-4">
              Briefly describe why you need a consultation with Dr. {selectedDoctor.fullName} (optional)
            </p>
            <textarea
              value={chiefComplaint}
              onChange={(e) => { setChiefComplaint(e.target.value); setRequestError(''); }}
              placeholder="e.g. I have been experiencing headaches and dizziness for 3 days..."
              className="w-full h-28 px-3 py-2.5 text-sm text-black placeholder-gray-400 border border-[var(--card-border)] rounded-xl resize-none focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)]"
              maxLength={1000}
            />
            <p className="text-xs text-[var(--subtitle-grey)] mt-1 mb-3">
              {chiefComplaint.length}/1000 characters (optional)
            </p>
            {requestError && <p className="text-sm text-[var(--error-red)] mb-3">{requestError}</p>}
            <div className="flex gap-3">
              <button
                onClick={() => setShowComplaintInput(false)}
                className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black"
              >
                Cancel
              </button>
              <button
                onClick={handleRequestConsultation}
                className="flex-1 h-11 rounded-xl bg-[var(--brand-teal)] text-sm font-semibold text-white"
              >
                {chiefComplaint.trim() ? 'Request' : 'Skip & Request'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Full-screen Waiting Overlay ──────────────────────────────────── */}
      {(requestState === 'waiting' || requestState === 'accepted' || requestState === 'rejected' || requestState === 'expired') && selectedDoctor && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center">
          <div
            className="absolute inset-0"
            style={{ background: `linear-gradient(180deg, ${cosmic.bg} 0%, #0D1F3C 50%, ${cosmic.bg} 100%)` }}
          />

          <div className="relative z-10 flex flex-col items-center px-8 w-full max-w-sm">
            {/* ── Waiting state ─────────────────────────────────────────── */}
            {requestState === 'waiting' && (
              <>
                {/* Doctor avatar with pulse */}
                <div className="relative mb-6">
                  <div
                    className="w-28 h-28 rounded-full overflow-hidden flex items-center justify-center cosmic-pulse-ring"
                    style={{
                      background: `linear-gradient(135deg, ${cosmic.accentDim}, rgba(77,208,225,0.08))`,
                      border: `3px solid rgba(77,208,225,0.4)`,
                      boxShadow: `0 0 40px ${cosmic.accentDim}`,
                    }}
                  >
                    {selectedDoctor.profilePhotoUrl ? (
                      <img
                        src={selectedDoctor.profilePhotoUrl}
                        alt={selectedDoctor.fullName}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <span
                        className="text-4xl font-bold"
                        style={{ color: cosmic.accent }}
                      >
                        {getInitials(selectedDoctor.fullName)}
                      </span>
                    )}
                  </div>
                </div>

                {/* Doctor name */}
                <h2
                  className="text-xl font-bold mb-1 text-center"
                  style={{ color: cosmic.textPrimary }}
                >
                  Dr. {selectedDoctor.fullName}
                </h2>
                <p
                  className="text-sm mb-8"
                  style={{ color: cosmic.textSecondary }}
                >
                  {selectedDoctor.specialty}
                </p>

                {/* Countdown circle */}
                <div className="relative w-20 h-20 mb-4">
                  <svg className="w-full h-full -rotate-90" viewBox="0 0 80 80">
                    <circle
                      cx="40" cy="40" r="36"
                      fill="none"
                      stroke="rgba(255,255,255,0.08)"
                      strokeWidth="4"
                    />
                    <circle
                      cx="40" cy="40" r="36"
                      fill="none"
                      stroke={cosmic.accent}
                      strokeWidth="4"
                      strokeLinecap="round"
                      strokeDasharray={`${2 * Math.PI * 36}`}
                      strokeDashoffset={`${2 * Math.PI * 36 * (1 - countdown / REQUEST_TIMEOUT_SECONDS)}`}
                      style={{ transition: 'stroke-dashoffset 1s linear' }}
                    />
                  </svg>
                  <div className="absolute inset-0 flex items-center justify-center">
                    <span
                      className="text-2xl font-bold tabular-nums"
                      style={{ color: cosmic.textPrimary }}
                    >
                      {countdown}
                    </span>
                  </div>
                </div>

                {/* Status text */}
                <p
                  className="text-sm font-medium text-center mb-1"
                  style={{ color: cosmic.textPrimary }}
                >
                  Waiting for Dr. {selectedDoctor.fullName.split(' ')[0]} to accept
                </p>
                <p
                  className="text-xs text-center mb-8"
                  style={{ color: cosmic.textSecondary }}
                >
                  The doctor has {countdown} seconds to respond
                </p>

                {/* Cancel button */}
                <button
                  onClick={cancelRequest}
                  className="flex items-center justify-center gap-2 px-8 py-3 rounded-2xl text-sm font-semibold transition-all active:scale-95"
                  style={{
                    background: 'rgba(239,68,68,0.15)',
                    color: cosmic.red,
                    border: `1px solid rgba(239,68,68,0.3)`,
                  }}
                >
                  <X size={16} />
                  Cancel Request
                </button>
              </>
            )}

            {/* ── Accepted state ────────────────────────────────────────── */}
            {requestState === 'accepted' && (
              <>
                <div
                  className="w-20 h-20 rounded-full flex items-center justify-center mb-5"
                  style={{
                    background: 'rgba(34,197,94,0.15)',
                    boxShadow: '0 0 40px rgba(34,197,94,0.25)',
                    animation: 'cosmicScaleIn 0.4s cubic-bezier(0.16,1,0.3,1)',
                  }}
                >
                  <CheckCircle2 size={40} style={{ color: cosmic.green }} />
                </div>
                <h2
                  className="text-xl font-bold mb-1 text-center"
                  style={{ color: cosmic.green }}
                >
                  Accepted!
                </h2>
                <p
                  className="text-sm text-center mb-2"
                  style={{ color: cosmic.textPrimary }}
                >
                  Dr. {selectedDoctor.fullName} accepted your request
                </p>
                <p
                  className="text-xs text-center"
                  style={{ color: cosmic.textSecondary }}
                >
                  Connecting to consultation...
                </p>
              </>
            )}

            {/* ── Rejected state ────────────────────────────────────────── */}
            {requestState === 'rejected' && (
              <>
                <div
                  className="w-20 h-20 rounded-full flex items-center justify-center mb-5"
                  style={{
                    background: 'rgba(239,68,68,0.15)',
                    animation: 'cosmicScaleIn 0.4s cubic-bezier(0.16,1,0.3,1)',
                  }}
                >
                  <XCircle size={40} style={{ color: cosmic.red }} />
                </div>
                <h2
                  className="text-xl font-bold mb-1 text-center"
                  style={{ color: cosmic.red }}
                >
                  Doctor Declined
                </h2>
                <p
                  className="text-sm text-center mb-6"
                  style={{ color: cosmic.textSecondary }}
                >
                  Dr. {selectedDoctor.fullName} is unable to take your consultation right now.
                  Please try another doctor.
                </p>
                <button
                  onClick={() => {
                    setRequestState('idle');
                    setSelectedDoctor(null);
                    setRequestId(null);
                  }}
                  className="flex items-center justify-center gap-2 px-8 py-3 rounded-2xl text-sm font-semibold transition-all active:scale-95"
                  style={{
                    background: `linear-gradient(135deg, #2A9D8F, ${cosmic.accent})`,
                    color: '#0A1628',
                  }}
                >
                  <Users size={16} />
                  Back to Doctor List
                </button>
              </>
            )}

            {/* ── Expired/Timed out state ───────────────────────────────── */}
            {requestState === 'expired' && (
              <>
                <div
                  className="w-20 h-20 rounded-full flex items-center justify-center mb-5"
                  style={{
                    background: 'rgba(245,158,11,0.15)',
                    animation: 'cosmicScaleIn 0.4s cubic-bezier(0.16,1,0.3,1)',
                  }}
                >
                  <TimerOff size={40} style={{ color: cosmic.amber }} />
                </div>
                <h2
                  className="text-xl font-bold mb-1 text-center"
                  style={{ color: cosmic.amber }}
                >
                  Request Timed Out
                </h2>
                <p
                  className="text-sm text-center mb-6"
                  style={{ color: cosmic.textSecondary }}
                >
                  Dr. {selectedDoctor.fullName} did not respond in time.
                  You can retry or pick another doctor.
                </p>
                <div className="flex gap-3 w-full">
                  <button
                    onClick={() => {
                      setRequestState('idle');
                      setSelectedDoctor(null);
                      setRequestId(null);
                    }}
                    className="flex-1 flex items-center justify-center gap-2 py-3 rounded-2xl text-sm font-semibold transition-all active:scale-95"
                    style={{
                      background: cosmic.surface,
                      color: cosmic.textPrimary,
                      border: `1px solid ${cosmic.border}`,
                    }}
                  >
                    Other Doctors
                  </button>
                  <button
                    onClick={() => {
                      setRequestState('idle');
                      setRequestId(null);
                      initiateRequest(selectedDoctor);
                    }}
                    className="flex-1 flex items-center justify-center gap-2 py-3 rounded-2xl text-sm font-semibold transition-all active:scale-95"
                    style={{
                      background: `linear-gradient(135deg, #2A9D8F, ${cosmic.accent})`,
                      color: '#0A1628',
                    }}
                  >
                    Retry
                  </button>
                </div>
              </>
            )}
          </div>

          <style>{`
            @keyframes cosmicPulseRing {
              0% { box-shadow: 0 0 0 0 rgba(77,208,225,0.4); }
              70% { box-shadow: 0 0 0 20px rgba(77,208,225,0); }
              100% { box-shadow: 0 0 0 0 rgba(77,208,225,0); }
            }
            .cosmic-pulse-ring {
              animation: cosmicPulseRing 2s ease-out infinite;
            }
            @keyframes cosmicScaleIn {
              from { transform: scale(0.5); opacity: 0; }
              to { transform: scale(1); opacity: 1; }
            }
          `}</style>
        </div>
      )}
    </div>
  );
}
