'use client';

import { useState, useEffect, useCallback, use, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  Smartphone,
  CheckCircle,
  XCircle,
  Clock,
  Shield,
} from 'lucide-react';
import { Button, Card, Input } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction } from '@/lib/supabase';
import { useSupabase } from '@/hooks/useSupabase';

type PaymentStatus = 'input' | 'processing' | 'polling' | 'success' | 'failed';

export default function PaymentPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  return <Suspense><PaymentContent params={params} /></Suspense>;
}

function PaymentContent({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: consultationId } = use(params);
  const router = useRouter();
  const searchParams = useSearchParams();
  const isRecharge = searchParams.get('type') === 'recharge';

  const { patientSession } = useAuthStore();
  const db = useSupabase();

  const [phone, setPhone] = useState('255');
  const [phoneError, setPhoneError] = useState('');
  const [amount, setAmount] = useState<number>(0);
  const [status, setStatus] = useState<PaymentStatus>('input');
  const [paymentId, setPaymentId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Load consultation amount
  useEffect(() => {
    if (!db) return;
    async function loadAmount() {
      if (isRecharge) {
        setAmount(2000); // Recharge amount
        return;
      }
      const { data } = await db!
        .from('consultations')
        .select('consultation_fee')
        .eq('consultation_id', consultationId)
        .single();

      if (data) setAmount(data.consultation_fee);
    }
    loadAmount();
  }, [consultationId, isRecharge, db]);

  function validatePhone(value: string) {
    // Tanzania phone: 255XXXXXXXXX (12 digits total)
    const cleaned = value.replace(/\D/g, '');
    if (cleaned.length !== 12) return 'Enter a valid phone number (255XXXXXXXXX)';
    if (!cleaned.startsWith('255')) return 'Phone must start with 255';
    return '';
  }

  async function handlePay() {
    const error = validatePhone(phone);
    if (error) {
      setPhoneError(error);
      return;
    }

    setPhoneError('');
    setSubmitting(true);
    setStatus('processing');

    try {
      const currentToken = useAuthStore.getState().session?.accessToken;
      const result = await invokeEdgeFunction<{ paymentId: string }>(
        'initiate-mpesa-payment',
        {
          consultationId,
          phone: phone.replace(/\D/g, ''),
          amount,
          type: isRecharge ? 'recharge' : 'consultation',
        },
        currentToken ?? undefined,
      );

      if (result?.paymentId) {
        setPaymentId(result.paymentId);
        setStatus('polling');
      } else {
        setStatus('failed');
      }
    } catch {
      setStatus('failed');
    } finally {
      setSubmitting(false);
    }
  }

  // Poll payment status
  useEffect(() => {
    if (status !== 'polling' || !paymentId || !db) return;

    let attempts = 0;
    const maxAttempts = 30;

    const interval = setInterval(async () => {
      attempts++;

      try {
        const { data } = await db!
          .from('payments')
          .select('status')
          .eq('payment_id', paymentId)
          .single();

        if (data?.status === 'completed') {
          setStatus('success');
          clearInterval(interval);
        } else if (data?.status === 'failed') {
          setStatus('failed');
          clearInterval(interval);
        }
      } catch {
        // Continue polling
      }

      if (attempts >= maxAttempts) {
        setStatus('failed');
        clearInterval(interval);
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [status, paymentId, db]);

  function handleContinue() {
    router.push(`/consultation/${consultationId}`);
  }

  function handleRetry() {
    setStatus('input');
    setPaymentId(null);
  }

  function formatTZS(amt: number) {
    return `TZS ${amt.toLocaleString()}`;
  }

  return (
    <div className="min-h-dvh bg-gray-50">
      {/* Header */}
      <div className="sticky top-0 z-10 bg-white border-b border-[var(--card-border)] px-5 py-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft size={22} className="text-black" />
          </button>
          <h1 className="text-lg font-bold text-black">
            {isRecharge ? 'Add Call Time' : 'Payment'}
          </h1>
        </div>
      </div>

      <div className="px-5 py-6 max-w-md mx-auto">
        {/* Input State */}
        {status === 'input' && (
          <div className="space-y-6">
            {/* Amount display */}
            <Card className="text-center !py-6">
              <p className="text-sm text-[var(--subtitle-grey)] mb-1">Amount to pay</p>
              <p className="text-3xl font-bold text-black">{formatTZS(amount)}</p>
              {isRecharge && (
                <p className="text-xs text-[var(--subtitle-grey)] mt-2">
                  5 additional minutes of call time
                </p>
              )}
            </Card>

            {/* M-Pesa branding */}
            <Card className="!p-4">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl bg-green-600 flex items-center justify-center">
                  <Smartphone size={20} className="text-white" />
                </div>
                <div>
                  <p className="text-sm font-bold text-black">Pay with M-Pesa</p>
                  <p className="text-xs text-[var(--subtitle-grey)]">
                    Secure mobile payment
                  </p>
                </div>
                <Shield size={16} className="ml-auto text-green-600" />
              </div>

              <Input
                label="M-Pesa Phone Number"
                type="tel"
                placeholder="255XXXXXXXXX"
                value={phone}
                onChange={(e) => {
                  setPhone(e.target.value);
                  setPhoneError('');
                }}
                error={phoneError}
                icon={<Smartphone size={18} />}
              />

              <p className="text-xs text-[var(--subtitle-grey)] mt-2">
                You will receive a payment prompt on your phone. Enter your M-Pesa PIN
                to complete the payment.
              </p>
            </Card>

            <Button fullWidth size="lg" onClick={handlePay} loading={submitting}>
              Pay {formatTZS(amount)}
            </Button>
          </div>
        )}

        {/* Processing State */}
        {status === 'processing' && (
          <div className="flex flex-col items-center text-center mt-16">
            <div className="w-20 h-20 mb-6">
              <div className="w-full h-full rounded-full border-4 border-transparent border-t-[var(--brand-teal)] animate-spin" />
            </div>
            <h2 className="text-lg font-bold text-black mb-2">Processing Payment</h2>
            <p className="text-sm text-[var(--subtitle-grey)]">
              Initiating M-Pesa request...
            </p>
          </div>
        )}

        {/* Polling State */}
        {status === 'polling' && (
          <div className="flex flex-col items-center text-center mt-16">
            <div className="w-20 h-20 rounded-full bg-[var(--royal-gold)]/10 flex items-center justify-center mb-6">
              <Clock size={36} className="text-[var(--royal-gold)] animate-pulse" />
            </div>
            <h2 className="text-lg font-bold text-black mb-2">
              Awaiting Payment
            </h2>
            <p className="text-sm text-[var(--subtitle-grey)] max-w-[280px] mb-2">
              Check your phone for the M-Pesa prompt and enter your PIN to complete
              payment.
            </p>
            <p className="text-xs text-[var(--subtitle-grey)]">
              This may take up to 90 seconds
            </p>
          </div>
        )}

        {/* Success State */}
        {status === 'success' && (
          <div className="flex flex-col items-center text-center mt-16">
            <div className="w-20 h-20 rounded-full bg-[var(--success-green)]/10 flex items-center justify-center mb-6">
              <CheckCircle size={40} className="text-[var(--success-green)]" />
            </div>
            <h2 className="text-lg font-bold text-black mb-2">Payment Successful!</h2>
            <p className="text-sm text-[var(--subtitle-grey)] mb-1">
              {formatTZS(amount)} paid via M-Pesa
            </p>
            <p className="text-xs text-[var(--subtitle-grey)] mb-8">
              {isRecharge
                ? 'Your call time has been extended.'
                : 'Your consultation is ready to begin.'}
            </p>
            <Button fullWidth size="lg" onClick={handleContinue}>
              {isRecharge ? 'Return to Call' : 'Start Consultation'}
            </Button>
          </div>
        )}

        {/* Failed State */}
        {status === 'failed' && (
          <div className="flex flex-col items-center text-center mt-16">
            <div className="w-20 h-20 rounded-full bg-[var(--error-red)]/10 flex items-center justify-center mb-6">
              <XCircle size={40} className="text-[var(--error-red)]" />
            </div>
            <h2 className="text-lg font-bold text-black mb-2">Payment Failed</h2>
            <p className="text-sm text-[var(--subtitle-grey)] max-w-[280px] mb-8">
              The payment could not be completed. Please check your M-Pesa balance and
              try again.
            </p>
            <div className="flex gap-3 w-full">
              <Button
                variant="outline"
                fullWidth
                onClick={() => router.push('/home')}
              >
                Cancel
              </Button>
              <Button fullWidth onClick={handleRetry}>
                Try Again
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
