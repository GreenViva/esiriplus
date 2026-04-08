'use client';

import { useState, useEffect, useRef, use } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  FileText,
  X,
  CheckCircle,
  Pill,
  Syringe,
  Droplets,
  Search,
  Minus,
  Plus,
} from 'lucide-react';
import { Card, Button, TextArea, Select, Badge } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction } from '@/lib/supabase';
import { MEDICATIONS, isInjectable, searchMedications, formatDosage } from '@/data/medications';
import type { Prescription } from '@/types';
import type { PrescriptionForm, InjectionRoute } from '@/data/medications';

const categories = [
  { value: 'General Medicine', label: 'General Medicine' },
  { value: 'Neurological Conditions', label: 'Neurological Conditions' },
  { value: 'Cardiovascular', label: 'Cardiovascular' },
  { value: 'Respiratory', label: 'Respiratory' },
  { value: 'Gastrointestinal', label: 'Gastrointestinal' },
  { value: 'Musculoskeletal', label: 'Musculoskeletal' },
  { value: 'Dermatological', label: 'Dermatological' },
  { value: 'Mental Health', label: 'Mental Health' },
  { value: 'Infectious Disease', label: 'Infectious Disease' },
  { value: 'Other', label: 'Other' },
];

const severityOptions = [
  { value: 'Mild', label: 'Mild' },
  { value: 'Moderate', label: 'Moderate' },
  { value: 'Severe', label: 'Severe' },
];

// ── Badge/border colors by form ────────────────────────────────────────────
const formColors: Record<string, { badge: string; border: string; bg: string }> = {
  Tablets: { badge: 'teal', border: 'border-[#2A9D8F]/40', bg: 'bg-[#2A9D8F]/10' },
  Syrup: { badge: 'gold', border: 'border-[#F59E0B]/40', bg: 'bg-[#F59E0B]/10' },
  Injection: { badge: 'red', border: 'border-[#EF4444]/40', bg: 'bg-[#EF4444]/10' },
};

const formIcons: Record<string, React.ReactNode> = {
  Tablets: <Pill size={16} className="text-[#2A9D8F]" />,
  Syrup: <Droplets size={16} className="text-[#F59E0B]" />,
  Injection: <Syringe size={16} className="text-[#EF4444]" />,
};

export default function ReportPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();

  // Form state
  const [diagnosedProblem, setDiagnosedProblem] = useState('');
  const [category, setCategory] = useState('');
  const [otherCategory, setOtherCategory] = useState('');
  const [severity, setSeverity] = useState('Mild');
  const [treatmentPlan, setTreatmentPlan] = useState('');
  const [furtherNotes, setFurtherNotes] = useState('');
  const [followUpRecommended, setFollowUpRecommended] = useState(false);
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [verificationCode, setVerificationCode] = useState<string | null>(null);

  // Medication search state
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<string[]>([]);
  const [showResults, setShowResults] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  // Dosage config dialog state
  const [pendingMed, setPendingMed] = useState<string | null>(null);
  const [dosageForm, setDosageForm] = useState<PrescriptionForm>('Tablets');
  const [dosageQuantity, setDosageQuantity] = useState(1);
  const [dosageTimesPerDay, setDosageTimesPerDay] = useState(1);
  const [dosageDays, setDosageDays] = useState(1);
  const [dosageRoute, setDosageRoute] = useState<InjectionRoute>('IM');

  // ── Search handling ──────────────────────────────────────────────────────

  useEffect(() => {
    const excludes = prescriptions.map((p) => p.medication);
    setSearchResults(searchMedications(searchQuery, excludes));
    setShowResults(searchQuery.length >= 2);
  }, [searchQuery, prescriptions]);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setShowResults(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  function selectMedication(med: string) {
    setPendingMed(med);
    setSearchQuery('');
    setShowResults(false);
    // Auto-detect form
    if (isInjectable(med)) {
      setDosageForm('Injection');
      setDosageRoute('IM');
    } else {
      setDosageForm('Tablets');
    }
    setDosageQuantity(1);
    setDosageTimesPerDay(1);
    setDosageDays(1);
  }

  function addPrescription() {
    if (!pendingMed) return;
    if (dosageForm === 'Injection' && !dosageRoute) return;
    const p: Prescription = {
      medication: pendingMed,
      form: dosageForm,
      quantity: dosageForm === 'Injection' ? 1 : dosageQuantity,
      timesPerDay: dosageTimesPerDay,
      days: dosageDays,
      ...(dosageForm === 'Injection' ? { route: dosageRoute } : {}),
    };
    setPrescriptions((prev) => [...prev, p]);
    setPendingMed(null);
  }

  function removePrescription(index: number) {
    setPrescriptions((prev) => prev.filter((_, i) => i !== index));
  }

  // ── Submission ───────────────────────────────────────────────────────────

  async function handleSubmit() {
    if (!diagnosedProblem.trim() || !category || !treatmentPlan.trim()) return;
    if (category === 'Other' && !otherCategory.trim()) return;
    setSubmitting(true);

    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      const prescriptionItems = prescriptions.map((p) => ({
        medication: p.medication,
        form: p.form,
        dosage: formatDosage(p.form, p.quantity, p.timesPerDay, p.days, p.route),
        ...(p.form === 'Injection' && p.route ? { route: p.route } : {}),
      }));
      const result = await invokeEdgeFunction<{ report_id?: string; verification_code?: string }>(
        'generate-consultation-report',
        {
          consultation_id: id,
          diagnosed_problem: diagnosedProblem.trim(),
          category: category === 'Other' && otherCategory.trim() ? otherCategory.trim() : category,
          severity,
          treatment_plan: treatmentPlan.trim(),
          further_notes: furtherNotes.trim() || undefined,
          follow_up_recommended: followUpRecommended,
          prescriptions: prescriptionItems,
        },
        currentToken ?? undefined,
        'doctor',
      );

      setVerificationCode(result?.verification_code ?? null);
      setSubmitted(true);
    } catch {
      // empty
    } finally {
      setSubmitting(false);
    }
  }

  useEffect(() => {
    if (!submitted) return;
    const timer = setTimeout(() => router.push('/dashboard'), 2000);
    return () => clearTimeout(timer);
  }, [submitted, router]);

  // ── Success ──────────────────────────────────────────────────────────────

  if (submitted) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[70vh] px-6">
        <div className="w-20 h-20 rounded-full bg-[var(--success-green)]/10 flex items-center justify-center mb-6">
          <CheckCircle size={44} className="text-[var(--success-green)]" />
        </div>
        <h2 className="text-xl font-bold text-black mb-2">Report Submitted Successfully</h2>
        {verificationCode && (
          <p className="text-sm font-mono font-bold text-[var(--brand-teal)] mb-2">
            Verification Code: {verificationCode}
          </p>
        )}
        <p className="text-sm text-[var(--subtitle-grey)] text-center">Redirecting to dashboard...</p>
      </div>
    );
  }

  // ── Dosage preview ───────────────────────────────────────────────────────

  const dosagePreview = pendingMed
    ? formatDosage(dosageForm, dosageQuantity, dosageTimesPerDay, dosageDays, dosageRoute)
    : '';

  // ── Form ─────────────────────────────────────────────────────────────────

  return (
    <div className="px-4 lg:px-8 py-6 max-w-2xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => router.back()} className="p-1 rounded-lg hover:bg-gray-100 lg:hidden">
          <ArrowLeft size={20} className="text-black" />
        </button>
        <div>
          <h1 className="text-xl font-bold text-black">Consultation Report</h1>
          <p className="text-xs text-[var(--subtitle-grey)]">Complete before taking new consultations</p>
        </div>
      </div>

      <div className="space-y-5">
        <TextArea label="Diagnosed Problem *" placeholder="Describe the diagnosed condition..."
          value={diagnosedProblem} onChange={(e) => setDiagnosedProblem(e.target.value)} required />

        <div className="grid grid-cols-2 gap-3">
          <Select label="Category *" options={categories} placeholder="Select category"
            value={category} onChange={(e) => setCategory(e.target.value)} required />
          <Select label="Severity" options={severityOptions} placeholder="Select severity"
            value={severity} onChange={(e) => setSeverity(e.target.value)} />
        </div>

        {category === 'Other' && (
          <div>
            <label className="text-sm font-semibold text-black mb-1.5 block">Specify Category *</label>
            <input type="text" placeholder="Enter custom category..."
              value={otherCategory} onChange={(e) => setOtherCategory(e.target.value)}
              className="w-full px-3 py-2.5 text-sm border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)]" />
          </div>
        )}

        <TextArea label="Treatment Plan *" placeholder="Describe the recommended treatment plan..."
          value={treatmentPlan} onChange={(e) => setTreatmentPlan(e.target.value)} required />

        <TextArea label="Further Notes" placeholder="Any additional notes or observations..."
          value={furtherNotes} onChange={(e) => setFurtherNotes(e.target.value)} />

        {/* ── Medication / Prescription ──────────────────────────────── */}
        <div>
          <p className="text-sm font-bold text-black mb-3">Medication / Prescription</p>

          {/* Search */}
          <div ref={searchRef} className="relative mb-3">
            <div className="relative">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="Search medication... (min 2 chars)"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => searchQuery.length >= 2 && setShowResults(true)}
                className="w-full pl-9 pr-3 py-2.5 text-sm border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)]"
              />
            </div>
            {showResults && searchResults.length > 0 && (
              <div className="absolute z-20 left-0 right-0 mt-1 bg-white border border-[var(--card-border)] rounded-xl shadow-lg max-h-60 overflow-y-auto">
                {searchResults.map((med) => (
                  <button key={med} onClick={() => selectMedication(med)}
                    className="w-full text-left px-4 py-2.5 text-sm text-black hover:bg-[var(--brand-teal)]/5 border-b border-[var(--card-border)] last:border-b-0 flex items-center gap-2">
                    {isInjectable(med)
                      ? <Syringe size={14} className="text-[#EF4444] shrink-0" />
                      : <Pill size={14} className="text-[#2A9D8F] shrink-0" />}
                    <span className="truncate">{med}</span>
                  </button>
                ))}
              </div>
            )}
            {showResults && searchResults.length === 0 && searchQuery.length >= 2 && (
              <div className="absolute z-20 left-0 right-0 mt-1 bg-white border border-[var(--card-border)] rounded-xl shadow-lg px-4 py-3">
                <p className="text-sm text-[var(--subtitle-grey)]">No medications found</p>
              </div>
            )}
          </div>

          {/* Prescription cards */}
          {prescriptions.length === 0 ? (
            <Card className="border-dashed !border-[var(--card-border)]">
              <p className="text-sm text-[var(--subtitle-grey)] text-center py-2">No medications prescribed yet</p>
            </Card>
          ) : (
            <div className="space-y-2">
              <p className="text-xs font-semibold text-[var(--subtitle-grey)] mb-1">Prescribed Medications:</p>
              {prescriptions.map((p, i) => {
                const colors = formColors[p.form] ?? formColors.Tablets;
                return (
                  <div key={i} className={`relative rounded-xl border ${colors.border} p-3`}>
                    <button onClick={() => removePrescription(i)}
                      className="absolute top-2 right-2 p-1 rounded-full hover:bg-gray-100">
                      <X size={14} className="text-gray-400" />
                    </button>
                    <div className="flex items-start gap-2.5 pr-6">
                      <div className={`w-8 h-8 rounded-lg ${colors.bg} flex items-center justify-center mt-0.5 shrink-0`}>
                        {formIcons[p.form]}
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-black truncate">{p.medication}</p>
                        <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">
                          {p.form}{p.route ? ` (${p.route})` : ''}
                        </p>
                        <p className="text-xs text-[var(--brand-teal)] mt-0.5 font-medium">
                          {formatDosage(p.form, p.quantity, p.timesPerDay, p.days, p.route)}
                        </p>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Follow-up checkbox */}
        <label className="flex items-center gap-3 py-2">
          <input type="checkbox" checked={followUpRecommended}
            onChange={(e) => setFollowUpRecommended(e.target.checked)}
            className="w-5 h-5 rounded border-[var(--card-border)] text-[var(--brand-teal)] accent-[var(--brand-teal)]" />
          <span className="text-sm font-semibold text-black">Follow-up recommended</span>
        </label>

        {/* Submit */}
        <Button fullWidth size="lg" loading={submitting}
          disabled={!diagnosedProblem.trim() || !category || !treatmentPlan.trim() || (category === 'Other' && !otherCategory.trim())}
          onClick={handleSubmit}>
          <FileText size={18} className="mr-2" /> Submit &amp; Generate Report
        </Button>
      </div>

      {/* ── Dosage Configuration Dialog ─────────────────────────────────── */}
      {pendingMed && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-2xl p-5 max-w-sm w-full shadow-xl max-h-[85vh] overflow-y-auto">
            <h3 className="text-base font-bold text-black mb-1">Configure Dosage</h3>
            <p className="text-xs text-[var(--subtitle-grey)] mb-4 truncate">{pendingMed}</p>

            {/* Form selection */}
            <p className="text-xs font-semibold text-[var(--subtitle-grey)] mb-2">Form:</p>
            {isInjectable(pendingMed) ? (
              <div className="flex gap-2 mb-4">
                <button className="flex-1 h-9 rounded-lg bg-[#EF4444]/10 text-[#EF4444] text-sm font-semibold border-2 border-[#EF4444]">
                  Injection
                </button>
              </div>
            ) : (
              <div className="flex gap-2 mb-4">
                {(['Tablets', 'Syrup'] as PrescriptionForm[]).map((f) => (
                  <button key={f} onClick={() => setDosageForm(f)}
                    className={`flex-1 h-9 rounded-lg text-sm font-semibold transition-colors ${
                      dosageForm === f
                        ? 'bg-[#2A9D8F] text-white'
                        : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                    }`}>
                    {f}
                  </button>
                ))}
              </div>
            )}

            {/* Route (injection only) */}
            {(isInjectable(pendingMed) || dosageForm === 'Injection') && (
              <>
                <p className="text-xs font-semibold text-[var(--subtitle-grey)] mb-2">Route of Administration:</p>
                <div className="flex gap-2 mb-4">
                  {(['IM', 'IV', 'SC'] as InjectionRoute[]).map((r) => (
                    <button key={r} onClick={() => setDosageRoute(r)}
                      className={`flex-1 h-9 rounded-lg text-sm font-semibold transition-colors ${
                        dosageRoute === r
                          ? 'bg-[#EF4444] text-white'
                          : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                      }`}>
                      {r}
                    </button>
                  ))}
                </div>
              </>
            )}

            {/* Quantity (non-injection) */}
            {!isInjectable(pendingMed) && dosageForm !== 'Injection' && (
              <StepperField
                label={`Quantity per dose (${dosageForm === 'Syrup' ? 'ml' : 'tablet(s)'})`}
                value={dosageQuantity} min={1} max={99}
                onChange={setDosageQuantity}
              />
            )}

            {/* Times per day */}
            <StepperField label="Times per day" value={dosageTimesPerDay} min={1} max={99} onChange={setDosageTimesPerDay} />

            {/* Duration */}
            <StepperField label="Duration (days)" value={dosageDays} min={1} max={999} onChange={setDosageDays} />

            {/* Preview */}
            <div className="bg-gray-50 rounded-lg px-3 py-2 mb-4">
              <p className="text-[10px] font-semibold text-[var(--subtitle-grey)] mb-0.5">Preview:</p>
              <p className="text-sm text-[var(--brand-teal)] font-medium">{dosagePreview}</p>
            </div>

            {/* Actions */}
            <div className="flex gap-3">
              <button onClick={() => setPendingMed(null)}
                className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black hover:bg-gray-50">
                Cancel
              </button>
              <button onClick={addPrescription}
                className="flex-1 h-11 rounded-xl bg-[var(--brand-teal)] text-sm font-semibold text-white hover:bg-[#238377]">
                Add ✓
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Stepper component ──────────────────────────────────────────────────────

function StepperField({
  label, value, min, max, onChange,
}: {
  label: string; value: number; min: number; max: number;
  onChange: (v: number) => void;
}) {
  return (
    <div className="mb-4">
      <p className="text-xs font-semibold text-[var(--subtitle-grey)] mb-2">{label}:</p>
      <div className="flex items-center gap-3">
        <button onClick={() => onChange(Math.max(min, value - 1))}
          disabled={value <= min}
          className="w-10 h-10 rounded-xl bg-gray-100 flex items-center justify-center hover:bg-gray-200 disabled:opacity-30">
          <Minus size={16} />
        </button>
        <span className="w-12 text-center text-lg font-bold text-black tabular-nums">{value}</span>
        <button onClick={() => onChange(Math.min(max, value + 1))}
          disabled={value >= max}
          className="w-10 h-10 rounded-xl bg-gray-100 flex items-center justify-center hover:bg-gray-200 disabled:opacity-30">
          <Plus size={16} />
        </button>
      </div>
    </div>
  );
}
