'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  Copy,
  Check,
  LogOut,
  User,
  Heart,
  MapPin,
  Droplet,
  AlertCircle,
  Activity,
  ChevronRight,
  Edit3,
  Save,
  Shield,
  ShieldCheck,
  Loader2,
} from 'lucide-react';
import { Button, Card, Input, Badge } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction } from '@/lib/supabase';
import { useSupabase } from '@/hooks/useSupabase';

export default function ProfilePage() {
  const router = useRouter();
  const { session, patientSession, setPatientSession, logout } = useAuthStore();
  const db = useSupabase();

  const [editing, setEditing] = useState(false);
  const [copied, setCopied] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  // Editable fields
  const [ageGroup, setAgeGroup] = useState(patientSession?.ageGroup ?? '');
  const [sex, setSex] = useState(patientSession?.sex ?? '');
  const [region, setRegion] = useState(patientSession?.region ?? '');
  const [bloodType, setBloodType] = useState(patientSession?.bloodType ?? '');
  const [allergies, setAllergies] = useState(
    patientSession?.allergies?.join(', ') ?? '',
  );
  const [chronicConditions, setChronicConditions] = useState(
    patientSession?.chronicConditions?.join(', ') ?? '',
  );

  // Recovery questions state
  const [recoverySetup, setRecoverySetup] = useState(false);
  const [recoveryLoading, setRecoveryLoading] = useState(true);
  const [showRecoveryForm, setShowRecoveryForm] = useState(false);
  const [questions, setQuestions] = useState<{ key: string; label: string }[]>([]);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [recoverySubmitting, setRecoverySubmitting] = useState(false);
  const [recoveryError, setRecoveryError] = useState('');
  const [patientIdResult, setPatientIdResult] = useState('');
  const [patientIdCopied, setPatientIdCopied] = useState(false);

  // Check if recovery is already set up
  useEffect(() => {
    if (!db || !patientSession?.sessionId) { setRecoveryLoading(false); return; }
    db.from('patient_sessions')
      .select('recovery_setup')
      .eq('session_id', patientSession.sessionId)
      .single()
      .then(({ data }) => {
        setRecoverySetup(data?.recovery_setup === true);
        setRecoveryLoading(false);
      })
      .catch(() => setRecoveryLoading(false));
  }, [db, patientSession?.sessionId]);

  // Load questions when form opens
  useEffect(() => {
    if (!showRecoveryForm || questions.length > 0) return;
    invokeEdgeFunction<{ key: string; label: string }[]>('get-security-questions', {})
      .then((data) => {
        if (Array.isArray(data)) setQuestions(data);
      })
      .catch(() => {
        // Fallback questions if edge function fails
        setQuestions([
          { key: 'favorite_animal', label: "What's your favourite animal?" },
          { key: 'favorite_city', label: "What is your favorite city?" },
          { key: 'birth_city', label: "In which city were you born?" },
          { key: 'primary_school', label: "What was the name of your primary school?" },
          { key: 'favorite_teacher', label: "What was your favorite teacher's name?" },
        ]);
      });
  }, [showRecoveryForm, questions.length]);

  async function handleRecoverySubmit() {
    const allAnswered = questions.every((q) => answers[q.key]?.trim());
    if (!allAnswered) { setRecoveryError('Please answer all questions'); return; }

    setRecoverySubmitting(true);
    setRecoveryError('');
    try {
      const token = useAuthStore.getState().session?.accessToken;
      const result = await invokeEdgeFunction<{ patient_id?: string }>('setup-recovery', {
        answers,
      }, token ?? undefined, 'patient');

      if (result?.patient_id) {
        setPatientIdResult(result.patient_id);
        setRecoverySetup(true);
        setShowRecoveryForm(false);
      }
    } catch (err) {
      setRecoveryError(err instanceof Error ? err.message : 'Failed to set up recovery');
    } finally {
      setRecoverySubmitting(false);
    }
  }

  function copySessionId() {
    if (!patientSession?.sessionId) return;
    navigator.clipboard.writeText(patientSession.sessionId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  async function handleSave() {
    if (!patientSession?.sessionId || !db) return;
    setSaving(true);

    const updates = {
      age_group: ageGroup || null,
      sex: sex || null,
      region: region || null,
      blood_type: bloodType || null,
      allergies: allergies
        ? allergies.split(',').map((s) => s.trim()).filter(Boolean)
        : [],
      chronic_conditions: chronicConditions
        ? chronicConditions.split(',').map((s) => s.trim()).filter(Boolean)
        : [],
    };

    try {
      await db
        .from('patient_sessions')
        .update(updates)
        .eq('session_id', patientSession.sessionId);

      setPatientSession({
        ...patientSession,
        ageGroup: updates.age_group ?? undefined,
        sex: updates.sex ?? undefined,
        region: updates.region ?? undefined,
        bloodType: updates.blood_type ?? undefined,
        allergies: updates.allergies,
        chronicConditions: updates.chronic_conditions,
      });

      setEditing(false);
    } catch {
      // Handle error
    } finally {
      setSaving(false);
    }
  }

  function handleLogout() {
    logout();
    router.push('/');
  }

  const infoItems = [
    { icon: User, label: 'Age Group', value: ageGroup, setter: setAgeGroup },
    { icon: Heart, label: 'Sex', value: sex, setter: setSex },
    { icon: MapPin, label: 'Region', value: region, setter: setRegion },
    { icon: Droplet, label: 'Blood Type', value: bloodType, setter: setBloodType },
  ];

  return (
    <div className="min-h-dvh bg-gray-50">
      {/* Header */}
      <div className="bg-gradient-to-br from-[var(--brand-teal)] to-[#1A7A6E] px-5 pt-12 pb-8 rounded-b-3xl">
        <div className="flex items-center gap-3 mb-5">
          <button
            onClick={() => router.push('/home')}
            className="p-1.5 -ml-1.5 rounded-xl hover:bg-white/10 transition-colors"
          >
            <ArrowLeft size={22} className="text-white" />
          </button>
          <h1 className="text-lg font-bold text-white">My Profile</h1>
          <button
            onClick={() => {
              if (editing) handleSave();
              else setEditing(true);
            }}
            className="ml-auto p-2 rounded-xl hover:bg-white/10 transition-colors"
          >
            {editing ? (
              saving ? (
                <div className="w-5 h-5 border-2 border-transparent border-t-white rounded-full animate-spin" />
              ) : (
                <Save size={20} className="text-white" />
              )
            ) : (
              <Edit3 size={20} className="text-white" />
            )}
          </button>
        </div>

        {/* Avatar */}
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-2xl bg-white/20 flex items-center justify-center">
            <span className="text-2xl font-bold text-white">
              {session?.user?.fullName?.charAt(0) ?? 'P'}
            </span>
          </div>
          <div>
            <p className="text-white text-lg font-bold">
              {session?.user?.fullName ?? 'Patient'}
            </p>
            <p className="text-white/70 text-sm">{session?.user?.phone}</p>
          </div>
        </div>
      </div>

      <div className="px-5 -mt-4 space-y-4 pb-24">
        {/* Patient ID Card */}
        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-[var(--subtitle-grey)] uppercase tracking-wide font-medium">
                Patient ID
              </p>
              <p className="text-sm font-mono font-bold text-black mt-0.5">
                {patientSession?.sessionId
                  ? `${patientSession.sessionId.slice(0, 8)}...${patientSession.sessionId.slice(-4)}`
                  : 'N/A'}
              </p>
            </div>
            <button
              onClick={copySessionId}
              className="p-2 rounded-xl hover:bg-gray-100 transition-colors"
            >
              {copied ? (
                <Check size={18} className="text-[var(--success-green)]" />
              ) : (
                <Copy size={18} className="text-[var(--subtitle-grey)]" />
              )}
            </button>
          </div>
        </Card>

        {/* Info Fields */}
        <Card>
          <h3 className="text-sm font-bold text-black mb-4">Personal Information</h3>
          <div className="space-y-4">
            {infoItems.map(({ icon: Icon, label, value, setter }) => (
              <div key={label}>
                {editing ? (
                  <Input
                    label={label}
                    value={value}
                    onChange={(e) => setter(e.target.value)}
                    icon={<Icon size={16} />}
                  />
                ) : (
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-[var(--brand-teal)]/10 flex items-center justify-center shrink-0">
                      <Icon size={16} className="text-[var(--brand-teal)]" />
                    </div>
                    <div className="flex-1">
                      <p className="text-xs text-[var(--subtitle-grey)]">{label}</p>
                      <p className="text-sm font-semibold text-black">
                        {value || 'Not set'}
                      </p>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>

        {/* Medical Info */}
        <Card>
          <h3 className="text-sm font-bold text-black mb-4">Medical Information</h3>
          <div className="space-y-4">
            {/* Allergies */}
            <div>
              {editing ? (
                <Input
                  label="Allergies (comma separated)"
                  value={allergies}
                  onChange={(e) => setAllergies(e.target.value)}
                  icon={<AlertCircle size={16} />}
                  placeholder="e.g. Penicillin, Peanuts"
                />
              ) : (
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-lg bg-[var(--error-red)]/10 flex items-center justify-center shrink-0">
                    <AlertCircle size={16} className="text-[var(--error-red)]" />
                  </div>
                  <div className="flex-1">
                    <p className="text-xs text-[var(--subtitle-grey)]">Allergies</p>
                    <div className="flex flex-wrap gap-1.5 mt-1">
                      {patientSession?.allergies?.length ? (
                        patientSession.allergies.map((a, i) => (
                          <Badge key={i} variant="red">
                            {a}
                          </Badge>
                        ))
                      ) : (
                        <p className="text-sm text-black">None reported</p>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Chronic Conditions */}
            <div>
              {editing ? (
                <Input
                  label="Chronic Conditions (comma separated)"
                  value={chronicConditions}
                  onChange={(e) => setChronicConditions(e.target.value)}
                  icon={<Activity size={16} />}
                  placeholder="e.g. Hypertension, Diabetes"
                />
              ) : (
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-lg bg-[var(--royal-gold)]/10 flex items-center justify-center shrink-0">
                    <Activity size={16} className="text-[var(--royal-gold)]" />
                  </div>
                  <div className="flex-1">
                    <p className="text-xs text-[var(--subtitle-grey)]">
                      Chronic Conditions
                    </p>
                    <div className="flex flex-wrap gap-1.5 mt-1">
                      {patientSession?.chronicConditions?.length ? (
                        patientSession.chronicConditions.map((c, i) => (
                          <Badge key={i} variant="gold">
                            {c}
                          </Badge>
                        ))
                      ) : (
                        <p className="text-sm text-black">None reported</p>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </Card>

        {/* Recovery Questions */}
        <Card>
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
              recoverySetup ? 'bg-green-100' : 'bg-amber-100'
            }`}>
              {recoverySetup ? (
                <ShieldCheck size={20} className="text-green-600" />
              ) : (
                <Shield size={20} className="text-amber-600" />
              )}
            </div>
            <div className="flex-1">
              <p className="text-sm font-bold text-black">Recovery Questions</p>
              {recoveryLoading ? (
                <p className="text-xs text-[var(--subtitle-grey)]">Checking...</p>
              ) : recoverySetup ? (
                <p className="text-xs text-green-600 font-medium">Recovery questions set up</p>
              ) : (
                <p className="text-xs text-amber-600 font-medium">Not set up — tap to protect your account</p>
              )}
            </div>
            {!recoveryLoading && (
              recoverySetup ? (
                <div className="w-6 h-6 rounded-full bg-green-100 flex items-center justify-center">
                  <Check size={14} className="text-green-600" />
                </div>
              ) : (
                <button
                  onClick={() => setShowRecoveryForm(true)}
                  className="px-3 py-1.5 bg-[var(--brand-teal)] text-white text-xs font-semibold rounded-lg"
                >
                  Set Up
                </button>
              )
            )}
          </div>
        </Card>

        {/* Patient ID result (shown after recovery setup) */}
        {patientIdResult && (
          <Card className="!border-green-200 !bg-green-50">
            <div className="text-center">
              <ShieldCheck size={32} className="text-green-600 mx-auto mb-2" />
              <p className="text-sm font-bold text-black mb-1">Recovery Set Up Successfully!</p>
              <p className="text-xs text-[var(--subtitle-grey)] mb-3">Save this Patient ID — you&apos;ll need it to recover your account</p>
              <div className="flex items-center justify-center gap-2 bg-white rounded-xl px-4 py-3 border border-green-200">
                <p className="text-lg font-mono font-bold text-black tracking-wider">{patientIdResult}</p>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(patientIdResult);
                    setPatientIdCopied(true);
                    setTimeout(() => setPatientIdCopied(false), 2000);
                  }}
                  className="p-1.5 rounded-lg hover:bg-gray-100"
                >
                  {patientIdCopied ? <Check size={16} className="text-green-600" /> : <Copy size={16} className="text-gray-400" />}
                </button>
              </div>
            </div>
          </Card>
        )}

        {/* Logout */}
        <Button variant="danger" fullWidth size="lg" onClick={() => setShowLogoutConfirm(true)}>
          <LogOut size={18} className="mr-2" />
          Logout
        </Button>

        {/* Logout Confirmation */}
        {showLogoutConfirm && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-white rounded-2xl p-6 mx-6 max-w-sm w-full shadow-xl">
              <h3 className="text-lg font-bold text-black mb-2">Log Out?</h3>
              <p className="text-sm text-[var(--subtitle-grey)] mb-6">
                Make sure you have saved your Patient ID before logging out. You will need it to access your records again.
              </p>
              <div className="flex gap-3">
                <Button variant="outline" fullWidth onClick={() => setShowLogoutConfirm(false)}>
                  Cancel
                </Button>
                <Button variant="danger" fullWidth onClick={handleLogout}>
                  Log Out
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Recovery Questions Form Modal */}
      {showRecoveryForm && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-end justify-center" onClick={() => !recoverySubmitting && setShowRecoveryForm(false)}>
          <div
            className="bg-white rounded-t-3xl w-full max-w-lg max-h-[85vh] overflow-y-auto animate-[slideUp_0.3s_ease-out]"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="sticky top-0 bg-white px-5 pt-5 pb-3 border-b border-gray-100 z-10">
              <div className="w-10 h-1 bg-gray-200 rounded-full mx-auto mb-4" />
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center">
                  <Shield size={20} className="text-[var(--brand-teal)]" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-black">Set Up Recovery</h2>
                  <p className="text-xs text-[var(--subtitle-grey)]">Answer 5 questions to protect your account</p>
                </div>
              </div>
            </div>

            <div className="px-5 py-4 space-y-4">
              {questions.length === 0 ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 size={24} className="text-[var(--brand-teal)] animate-spin" />
                </div>
              ) : (
                questions.map((q) => (
                  <div key={q.key}>
                    <label className="block text-sm font-medium text-black mb-1.5">{q.label}</label>
                    <input
                      type="text"
                      value={answers[q.key] ?? ''}
                      onChange={(e) => setAnswers((prev) => ({ ...prev, [q.key]: e.target.value }))}
                      placeholder="Your answer..."
                      className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm text-black focus:outline-none focus:ring-2 focus:ring-[var(--brand-teal)]/30 focus:border-[var(--brand-teal)]"
                    />
                  </div>
                ))
              )}

              {recoveryError && (
                <p className="text-sm text-red-500 text-center">{recoveryError}</p>
              )}

              <div className="flex gap-3 pt-2 pb-4">
                <button
                  onClick={() => setShowRecoveryForm(false)}
                  disabled={recoverySubmitting}
                  className="flex-1 py-3 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 disabled:opacity-50"
                >
                  Cancel
                </button>
                <button
                  onClick={handleRecoverySubmit}
                  disabled={recoverySubmitting || questions.length === 0}
                  className="flex-1 py-3 rounded-xl bg-[var(--brand-teal)] text-white text-sm font-semibold disabled:opacity-50 flex items-center justify-center gap-2"
                >
                  {recoverySubmitting ? (
                    <><Loader2 size={16} className="animate-spin" /> Setting up...</>
                  ) : (
                    'Save & Get Patient ID'
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
