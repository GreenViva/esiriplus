'use client';

import { useState, useEffect, useCallback, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ArrowLeft, Star, Clock, Crown, Shield, ChevronRight,
  Stethoscope, Pill, Brain, Leaf, FlaskConical, HeartPulse,
  UserCheck, AlertTriangle, X, Smartphone, CreditCard, Landmark,
  Lock, Check, Loader2, ArrowLeftCircle, Copy,
} from 'lucide-react';
import { Button, Badge } from '@/components/ui';
import { useAuthStore } from '@/store/auth';

// ── Service categories (matching Android's DatabaseCallback seed) ──

const SERVICES = [
  {
    id: 'tier_nurse',
    category: 'NURSE',
    name: 'Nurse',
    description: 'Normal consultations for everyday health concerns',
    price: 3000,
    royalPrice: 322000,
    duration: 15,
    features: ['Basic health advice', 'Symptom assessment', 'Health education'],
    icon: HeartPulse,
    color: '#2A9D8F',
    popular: false,
  },
  {
    id: 'tier_clinical_officer',
    category: 'CLINICAL_OFFICER',
    name: 'Clinical Officer',
    description: 'Daily medical consultations for common ailments',
    price: 5000,
    royalPrice: 350000,
    duration: 15,
    features: ['Medical diagnosis', 'Treatment recommendations', 'Prescription guidance'],
    icon: UserCheck,
    color: '#3B82F6',
    popular: false,
  },
  {
    id: 'tier_pharmacist',
    category: 'PHARMACIST',
    name: 'Pharmacist',
    description: 'Quick medication advice and drug interaction checks',
    price: 3000,
    royalPrice: 322000,
    duration: 5,
    features: ['Medication advice', 'Drug interaction checks', 'Dosage guidance'],
    icon: Pill,
    color: '#F59E0B',
    popular: false,
  },
  {
    id: 'tier_gp',
    category: 'GP',
    name: 'General Practitioner',
    description: 'Comprehensive care with specialist referrals when needed',
    price: 10000,
    royalPrice: 420000,
    duration: 15,
    features: ['Full medical assessment', 'Treatment planning', 'Specialist referrals'],
    icon: Stethoscope,
    color: '#2A9D8F',
    popular: true,
  },
  {
    id: 'tier_specialist',
    category: 'SPECIALIST',
    name: 'Specialist',
    description: 'Expert consultation in specialized medical fields',
    price: 30000,
    royalPrice: 700000,
    duration: 15,
    features: ['Specialized expertise', 'Advanced diagnostics', 'Detailed treatment plans'],
    icon: FlaskConical,
    color: '#EF4444',
    popular: false,
  },
  {
    id: 'tier_psychologist',
    category: 'PSYCHOLOGIST',
    name: 'Psychologist',
    description: 'Professional mental health support and counseling',
    price: 50000,
    royalPrice: 980000,
    duration: 20,
    features: ['Mental health support', 'Professional counseling', 'Therapy session'],
    icon: Brain,
    color: '#8B5CF6',
    popular: false,
  },
  {
    id: 'tier_herbalist',
    category: 'HERBALIST',
    name: 'Herbalist',
    description: 'Traditional and herbal medicine consultation',
    price: 5000,
    royalPrice: 350000,
    duration: 15,
    features: ['Herbal medicine consultation', 'Traditional remedy guidance', 'Natural supplement advice'],
    icon: Leaf,
    color: '#16A34A',
    popular: false,
  },
  {
    id: 'tier_drug_interaction',
    category: 'DRUG_INTERACTION',
    name: 'Drug Interaction',
    description: 'Check drug interactions and get safety guidance',
    price: 5000,
    royalPrice: 350000,
    duration: 5,
    features: ['Drug interaction checks', 'Safety alerts', 'Dosage guidance'],
    icon: AlertTriangle,
    color: '#2A9D8F',
    popular: false,
  },
];

type Tier = 'ECONOMY' | 'ROYAL';

// Per-tier price lookup. The 10× multiplier was dropped 2026-05-01 — Royal
// prices are explicit per service. Mirrors PricingEngine.kt.
function effectivePrice(service: { price: number; royalPrice: number }, tier: Tier): number {
  return tier === 'ROYAL' ? service.royalPrice : service.price;
}

function formatTZS(amount: number) {
  return `TSh ${amount.toLocaleString()}`;
}

// ── Mobile Money Providers ──

interface MobileProvider {
  id: string;
  name: string;
  letter: string;
  color: string;
  ussd: string;
  steps: string[];
}

const PROVIDERS: MobileProvider[] = [
  {
    id: 'mpesa',
    name: 'M-Pesa',
    letter: 'M',
    color: '#4CAF50',
    ussd: '*150*00#',
    steps: [
      'Dial *150*00#',
      'Choose "Lipa kwa M-Pesa"',
      'Choose "Malipo ya Kampuni"',
      'Choose "Esiri Plus"',
      'Enter Patient ID as reference',
      'Enter exact amount',
      'Enter M-Pesa PIN',
      'Confirm (Thibitisha)',
    ],
  },
  {
    id: 'airtel',
    name: 'Airtel Money',
    letter: 'A',
    color: '#E53935',
    ussd: '*150*60#',
    steps: [
      'Dial *150*60#',
      'Make Payments',
      'Pay Bill',
      'Enter "Esiri Plus"',
      'Enter Patient ID',
      'Enter amount',
      'Enter PIN',
      'Confirm',
    ],
  },
  {
    id: 'halopesa',
    name: 'HaloPesa',
    letter: 'H',
    color: '#2E7D32',
    ussd: '*150*88#',
    steps: [
      'Dial *150*88#',
      'Lipa',
      'Lipa kwa Kampuni',
      'Enter "Esiri Plus"',
      'Enter Patient ID',
      'Enter amount',
      'Enter PIN',
      'Confirm',
    ],
  },
  {
    id: 'yas',
    name: 'Yas',
    letter: 'Y',
    color: '#43A047',
    ussd: '*150*01#',
    steps: [
      'Dial *150*01#',
      'Payments',
      'Pay Business',
      'Enter "Esiri Plus"',
      'Enter Patient ID',
      'Enter amount',
      'Enter PIN',
      'Confirm',
    ],
  },
];

const STEP_COLORS = [
  '#2A9D8F', '#3B82F6', '#F59E0B', '#8B5CF6',
  '#EF4444', '#16A34A', '#EC4899', '#2A9D8F',
];

// ── Payment Flow Modal ──

type PaymentStep = 0 | 1 | 2 | 3 | 4 | 5; // 0 = closed

function PaymentFlow({
  open,
  onClose,
  serviceName,
  price,
  tier,
  serviceCategory,
  duration,
}: {
  open: boolean;
  onClose: () => void;
  serviceName: string;
  price: number;
  tier: string;
  serviceCategory: string;
  duration: number;
}) {
  const router = useRouter();
  const patientId = useAuthStore((s) => s.session?.user?.id ?? '');
  const [step, setStep] = useState<PaymentStep>(1);
  const [selectedProvider, setSelectedProvider] = useState<MobileProvider | null>(null);
  const [copied, setCopied] = useState(false);

  // Reset when opened
  useEffect(() => {
    if (open) {
      setStep(1);
      setSelectedProvider(null);
      setCopied(false);
    }
  }, [open]);

  // Auto-advance from verifying (step 4) to success (step 5)
  useEffect(() => {
    if (step === 4) {
      const timer = setTimeout(() => setStep(5), 3000);
      return () => clearTimeout(timer);
    }
  }, [step]);

  const referenceNumber = `ESP-${patientId.slice(0, 8).toUpperCase() || 'UNKNOWN'}`;

  const handleCopyRef = useCallback(() => {
    navigator.clipboard?.writeText(referenceNumber);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [referenceNumber]);

  const handleNavigateToDoctor = useCallback(() => {
    const params = new URLSearchParams({
      tier,
      service: serviceCategory,
      price: price.toString(),
      duration: duration.toString(),
    });
    router.push(`/find-doctor?${params.toString()}`);
  }, [router, tier, serviceCategory, price, duration]);

  if (!open) return null;

  const isDismissible = step < 4;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center"
      onClick={isDismissible ? onClose : undefined}
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/50" />

      {/* Modal */}
      <div
        className="relative z-10 w-full max-w-md max-h-[90dvh] bg-white rounded-t-2xl sm:rounded-2xl overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ── Step 1: Choose Payment Method ── */}
        {step === 1 && (
          <div className="p-5">
            <div className="flex items-center justify-between mb-1">
              <h2 className="text-lg font-bold text-black">Choose Payment Method</h2>
              <button onClick={onClose} className="p-1 rounded-full hover:bg-gray-100">
                <X size={20} className="text-black" />
              </button>
            </div>
            <p className="text-sm text-black mb-5">
              {serviceName} &middot; <span className="font-semibold" style={{ color: '#2A9D8F' }}>{formatTZS(price)}</span>
            </p>

            {/* Mobile Money - active */}
            <button
              onClick={() => setStep(2)}
              className="w-full flex items-center gap-3 p-4 rounded-xl border-2 border-[#2A9D8F] bg-[#E0F2F1] mb-3 text-left"
            >
              <div className="w-10 h-10 rounded-full bg-[#4CAF50] flex items-center justify-center shrink-0">
                <Smartphone size={20} className="text-white" />
              </div>
              <div className="flex-1">
                <p className="text-sm font-bold text-black">Mobile Money</p>
                <p className="text-xs text-black">M-Pesa, Airtel Money, HaloPesa, Yas</p>
              </div>
              <ChevronRight size={18} className="text-[#2A9D8F]" />
            </button>

            {/* Card Payment - disabled */}
            <div className="w-full flex items-center gap-3 p-4 rounded-xl border border-gray-200 bg-gray-50 mb-3 opacity-60">
              <div className="w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center shrink-0">
                <CreditCard size={20} className="text-white" />
              </div>
              <div className="flex-1">
                <p className="text-sm font-bold text-black">Card Payment</p>
                <p className="text-xs text-black">Visa, Mastercard</p>
              </div>
              <span className="text-[10px] font-bold text-white bg-gray-400 px-2 py-0.5 rounded-full">Coming Soon</span>
            </div>

            {/* Bank Transfer - disabled */}
            <div className="w-full flex items-center gap-3 p-4 rounded-xl border border-gray-200 bg-gray-50 mb-3 opacity-60">
              <div className="w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center shrink-0">
                <Landmark size={20} className="text-white" />
              </div>
              <div className="flex-1">
                <p className="text-sm font-bold text-black">Bank Transfer</p>
                <p className="text-xs text-black">CRDB, NMB, NBC</p>
              </div>
              <span className="text-[10px] font-bold text-white bg-gray-400 px-2 py-0.5 rounded-full">Coming Soon</span>
            </div>

            <div className="flex items-center justify-center gap-1.5 mt-4">
              <Lock size={12} className="text-gray-400" />
              <p className="text-xs text-black">All payments are secure and encrypted</p>
            </div>
          </div>
        )}

        {/* ── Step 2: Select Provider ── */}
        {step === 2 && (
          <div className="p-5">
            <div className="flex items-center justify-between mb-1">
              <h2 className="text-lg font-bold text-black">Select Your Provider</h2>
              <button onClick={onClose} className="p-1 rounded-full hover:bg-gray-100">
                <X size={20} className="text-black" />
              </button>
            </div>
            <button
              onClick={() => setStep(1)}
              className="flex items-center gap-1 text-xs font-semibold text-white bg-[#EF4444] px-3 py-1 rounded-full mb-4"
            >
              <ArrowLeftCircle size={14} /> Back
            </button>

            <div className="grid grid-cols-2 gap-3">
              {PROVIDERS.map((p) => (
                <button
                  key={p.id}
                  onClick={() => {
                    setSelectedProvider(p);
                    setStep(3);
                  }}
                  className="flex flex-col items-center gap-2 p-4 rounded-xl border-2 border-gray-200 bg-white hover:border-[#2A9D8F] hover:shadow-md transition-all"
                >
                  <div
                    className="w-12 h-12 rounded-full flex items-center justify-center"
                    style={{ backgroundColor: p.color }}
                  >
                    <span className="text-white font-bold text-lg">{p.letter}</span>
                  </div>
                  <p className="text-sm font-bold text-black">{p.name}</p>
                  <p className="text-[10px] text-black">{p.ussd}</p>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* ── Step 3: Payment Instructions ── */}
        {step === 3 && selectedProvider && (
          <div className="p-5">
            <div className="flex items-center justify-between mb-1">
              <h2 className="text-lg font-bold text-black">{selectedProvider.name}</h2>
              <button onClick={onClose} className="p-1 rounded-full hover:bg-gray-100">
                <X size={20} className="text-black" />
              </button>
            </div>
            <button
              onClick={() => setStep(2)}
              className="flex items-center gap-1 text-xs font-semibold text-white bg-[#EF4444] px-3 py-1 rounded-full mb-4"
            >
              <ArrowLeftCircle size={14} /> Back
            </button>

            {/* Provider icon + USSD */}
            <div className="flex flex-col items-center mb-4">
              <div
                className="w-14 h-14 rounded-full flex items-center justify-center mb-2"
                style={{ backgroundColor: selectedProvider.color }}
              >
                <span className="text-white font-bold text-xl">{selectedProvider.letter}</span>
              </div>
              <p className="text-sm font-semibold text-black">{selectedProvider.ussd}</p>
            </div>

            {/* Reference Number */}
            <div className="bg-[#E8F5E9] rounded-xl p-3 mb-3 flex items-center justify-between">
              <div>
                <p className="text-[10px] text-black font-medium">Reference Number</p>
                <p className="text-sm font-bold text-black">{referenceNumber}</p>
              </div>
              <button
                onClick={handleCopyRef}
                className="p-1.5 rounded-lg hover:bg-[#C8E6C9] transition-colors"
              >
                {copied ? <Check size={16} className="text-[#4CAF50]" /> : <Copy size={16} className="text-black" />}
              </button>
            </div>

            {/* Amount */}
            <div className="text-center mb-4">
              <p className="text-xs text-black font-medium">Amount to Pay</p>
              <p className="text-2xl font-bold" style={{ color: '#2A9D8F' }}>{formatTZS(price)}</p>
            </div>

            {/* Steps */}
            <p className="text-sm font-bold text-black mb-2">Follow these steps:</p>
            <div className="space-y-2.5 mb-4">
              {selectedProvider.steps.map((text, i) => (
                <div key={i} className="flex items-start gap-3">
                  <div
                    className="w-6 h-6 rounded-full flex items-center justify-center shrink-0 mt-0.5"
                    style={{ backgroundColor: STEP_COLORS[i % STEP_COLORS.length] }}
                  >
                    <span className="text-white text-xs font-bold">{i + 1}</span>
                  </div>
                  <p className="text-sm text-black">{text}</p>
                </div>
              ))}
            </div>

            {/* Warning */}
            <div className="bg-[#FFF9C4] rounded-xl p-3 mb-5 flex items-start gap-2">
              <AlertTriangle size={16} className="text-[#F59E0B] shrink-0 mt-0.5" />
              <p className="text-xs text-black">
                Please enter the <strong>EXACT</strong> amount of{' '}
                <strong>{formatTZS(price)}</strong>. Different amounts will delay your consultation.
              </p>
            </div>

            {/* Buttons */}
            <div className="flex gap-3">
              <button
                onClick={() => setStep(2)}
                className="flex-1 py-3 rounded-xl border-2 border-[#2A9D8F] text-sm font-bold text-[#2A9D8F]"
              >
                Change Provider
              </button>
              <button
                onClick={() => setStep(4)}
                className="flex-1 py-3 rounded-xl bg-[#2A9D8F] text-sm font-bold text-white flex items-center justify-center gap-2"
              >
                <Check size={16} /> I Have Paid
              </button>
            </div>
          </div>
        )}

        {/* ── Step 4: Verifying Payment ── */}
        {step === 4 && (
          <div className="p-8 flex flex-col items-center justify-center min-h-[280px]">
            <Loader2 size={48} className="text-[#2A9D8F] animate-spin mb-4" />
            <h2 className="text-lg font-bold text-black mb-1">Verifying Payment...</h2>
            <p className="text-sm text-black text-center">Please wait while we confirm your payment</p>
          </div>
        )}

        {/* ── Step 5: Payment Successful ── */}
        {step === 5 && (
          <div className="p-8 flex flex-col items-center justify-center min-h-[280px]">
            <div className="w-16 h-16 rounded-full bg-[#4CAF50] flex items-center justify-center mb-4">
              <Check size={32} className="text-white" />
            </div>
            <h2 className="text-lg font-bold text-black mb-1">Payment Successful!</h2>
            <p className="text-sm text-black text-center mb-6">
              Your payment has been confirmed. You will now be connected with a doctor.
            </p>
            <button
              onClick={handleNavigateToDoctor}
              className="w-full py-3 rounded-xl bg-[#2A9D8F] text-sm font-bold text-white"
            >
              Continue
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default function ServicesPage() {
  return <Suspense><ServicesContent /></Suspense>;
}

function ServicesContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const tier = (searchParams.get('tier') as Tier) || 'ECONOMY';
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [paymentOpen, setPaymentOpen] = useState(false);

  const isRoyal = tier === 'ROYAL';
  const selectedService = SERVICES.find((s) => s.id === selectedId);
  const selectedPrice = selectedService ? effectivePrice(selectedService, tier) : 0;

  function handleContinue() {
    if (!selectedService) return;
    setPaymentOpen(true);
  }

  return (
    <div className="min-h-dvh bg-gradient-to-b from-white to-[#E0F2F1] flex flex-col">
      {/* Header */}
      <div className="px-5 pt-5 pb-3">
        <div className="flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft size={22} className="text-black" />
          </button>
          <div>
            <h1 className="text-xl font-bold text-black">Choose Your Service</h1>
            <p className="text-xs text-[var(--subtitle-grey)]">Select a consultation type</p>
          </div>
        </div>
      </div>

      {/* Royal Banner */}
      {isRoyal && (
        <div className="mx-5 mb-3 bg-gradient-to-r from-[var(--royal-purple)] to-[#7C3AED] rounded-xl px-4 py-2.5 flex items-center gap-2">
          <span className="text-lg">&#9819;</span>
          <div>
            <p className="text-white text-xs font-bold">Royal Service Active</p>
            <p className="text-white/80 text-[10px]">10x pricing \u00b7 14-day unlimited follow-ups \u00b7 Priority matching</p>
          </div>
        </div>
      )}

      {/* Service List */}
      <div className="flex-1 overflow-y-auto px-5 pb-28 space-y-3">
        {SERVICES.map((service) => {
          const price = effectivePrice(service, tier);
          const isSelected = selectedId === service.id;
          const Icon = service.icon;

          return (
            <button
              key={service.id}
              onClick={() => setSelectedId(service.id)}
              className={`w-full text-left rounded-2xl border-2 bg-white p-3 transition-all ${
                isSelected
                  ? 'border-[var(--brand-teal)] shadow-md'
                  : 'border-[var(--card-border)] hover:border-gray-300'
              }`}
            >
              {/* Popular badge */}
              {service.popular && (
                <div className="flex justify-end -mt-1 mb-1">
                  <span className="text-[10px] font-bold text-white bg-[#EA580C] px-2 py-0.5 rounded-full">
                    Popular
                  </span>
                </div>
              )}

              <div className="flex items-center gap-3">
                {/* Icon */}
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
                  style={{ backgroundColor: service.color }}
                >
                  <Icon size={20} className="text-white" />
                </div>

                {/* Name + description */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-bold text-black">{service.name}</p>
                  <p className="text-xs text-[var(--subtitle-grey)] truncate">{service.description}</p>
                </div>

                {/* Price + duration */}
                <div className="text-right shrink-0">
                  <p className="text-sm font-bold" style={{ color: isRoyal ? '#4C1D95' : '#2A9D8F' }}>
                    {formatTZS(price)}
                  </p>
                  <p className="text-[10px] text-[var(--subtitle-grey)]">{service.duration} min</p>
                </div>

                {/* Radio */}
                <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${
                  isSelected ? 'border-[var(--brand-teal)] bg-[var(--brand-teal)]' : 'border-gray-300'
                }`}>
                  {isSelected && <div className="w-2 h-2 rounded-full bg-white" />}
                </div>
              </div>

              {/* Feature tags */}
              <div className="flex flex-wrap gap-1.5 mt-2 ml-[52px]">
                {service.features.map((f, i) => (
                  <span
                    key={i}
                    className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-gray-100 text-[var(--subtitle-grey)]"
                  >
                    {f}
                  </span>
                ))}
              </div>
            </button>
          );
        })}
      </div>

      {/* Bottom CTA - fixed to screen bottom */}
      <div className="fixed bottom-0 left-0 right-0 z-20 border-t border-[var(--card-border)] bg-white px-5 py-4 shadow-[0_-2px_10px_rgba(0,0,0,0.05)]">
        <Button
          fullWidth
          size="lg"
          disabled={!selectedId}
          onClick={handleContinue}
        >
          {selectedService
            ? `Pay ${formatTZS(selectedPrice)} to Continue`
            : 'Select a service to continue'
          }
        </Button>
        <p className="text-center text-xs text-[var(--subtitle-grey)] mt-2">
          Payment via M-Pesa required before consultation
        </p>
      </div>

      {/* Payment Flow Modal */}
      {selectedService && (
        <PaymentFlow
          open={paymentOpen}
          onClose={() => setPaymentOpen(false)}
          serviceName={selectedService.name}
          price={selectedPrice}
          tier={tier}
          serviceCategory={selectedService.category}
          duration={selectedService.duration}
        />
      )}
    </div>
  );
}
