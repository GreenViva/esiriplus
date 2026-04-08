'use client';

import { useState } from 'react';
import { CheckCircle, Loader2, Mail, KeyRound, Lock } from 'lucide-react';
import { invokeEdgeFunction } from '@/lib/supabase';

type Step = 'EMAIL' | 'OTP' | 'NEW_PASSWORD' | 'DONE';

async function callResetPassword(action: string, body: Record<string, string>) {
  return invokeEdgeFunction('reset-password', { action, ...body });
}

interface Props {
  open: boolean;
  onClose: () => void;
  prefillEmail?: string;
}

export default function ForgotPasswordDialog({ open, onClose, prefillEmail }: Props) {
  const [step, setStep] = useState<Step>('EMAIL');
  const [email, setEmail] = useState(prefillEmail ?? '');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function handleClose() {
    setStep('EMAIL');
    setOtp('');
    setPassword('');
    setConfirmPassword('');
    setError('');
    setLoading(false);
    onClose();
  }

  async function sendOtp() {
    if (!email.trim() || !email.includes('@')) {
      setError('Please enter a valid email address');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await callResetPassword('send_otp', { email: email.trim() });
      setStep('OTP');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to send code');
    } finally {
      setLoading(false);
    }
  }

  async function verifyOtp() {
    if (otp.length !== 6) {
      setError('Please enter the 6-digit code');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await callResetPassword('verify_otp', { email: email.trim(), otp_code: otp });
      setStep('NEW_PASSWORD');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Verification failed');
    } finally {
      setLoading(false);
    }
  }

  async function setNewPassword() {
    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await callResetPassword('set_password', { email: email.trim(), new_password: password });
      setStep('DONE');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to reset password');
    } finally {
      setLoading(false);
    }
  }

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full shadow-xl">

        {/* ── Step 1: Email ──────────────────────────────────────── */}
        {step === 'EMAIL' && (
          <>
            <div className="flex items-center gap-2 mb-2">
              <Mail size={20} className="text-[#2A9D8F]" />
              <h3 className="text-lg font-bold text-black">Reset Password</h3>
            </div>
            <p className="text-sm text-[var(--subtitle-grey)] mb-4">
              Enter your email and we&apos;ll send you a verification code.
            </p>
            <input
              type="email"
              placeholder="doctor@example.com"
              value={email}
              onChange={(e) => { setEmail(e.target.value); setError(''); }}
              onKeyDown={(e) => e.key === 'Enter' && sendOtp()}
              className="w-full px-3 py-2.5 text-sm border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[#2A9D8F] focus:ring-1 focus:ring-[#2A9D8F] mb-2"
              autoFocus
            />
            {error && <p className="text-xs text-[#DC2626] mb-2">{error}</p>}
            <div className="flex gap-3 mt-4">
              <button onClick={handleClose}
                className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black hover:bg-gray-50">
                Cancel
              </button>
              <button onClick={sendOtp} disabled={loading}
                className="flex-1 h-11 rounded-xl bg-[#2A9D8F] text-sm font-semibold text-white hover:bg-[#238377] disabled:opacity-50 flex items-center justify-center gap-2">
                {loading ? <Loader2 size={16} className="animate-spin" /> : null}
                Send Code
              </button>
            </div>
          </>
        )}

        {/* ── Step 2: OTP ────────────────────────────────────────── */}
        {step === 'OTP' && (
          <>
            <div className="flex items-center gap-2 mb-2">
              <KeyRound size={20} className="text-[#2A9D8F]" />
              <h3 className="text-lg font-bold text-black">Enter Verification Code</h3>
            </div>
            <p className="text-sm text-[var(--subtitle-grey)] mb-4">
              A 6-digit code has been sent to <strong className="text-black">{email}</strong>
            </p>
            <input
              type="text"
              inputMode="numeric"
              maxLength={6}
              placeholder="000000"
              value={otp}
              onChange={(e) => { setOtp(e.target.value.replace(/\D/g, '').slice(0, 6)); setError(''); }}
              onKeyDown={(e) => e.key === 'Enter' && verifyOtp()}
              className="w-full px-3 py-3 text-center text-2xl font-mono font-bold tracking-[0.3em] border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[#2A9D8F] focus:ring-1 focus:ring-[#2A9D8F] mb-2"
              autoFocus
            />
            {error && <p className="text-xs text-[#DC2626] mb-2">{error}</p>}
            <div className="flex gap-3 mt-4">
              <button onClick={handleClose}
                className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black hover:bg-gray-50">
                Cancel
              </button>
              <button onClick={verifyOtp} disabled={loading || otp.length !== 6}
                className="flex-1 h-11 rounded-xl bg-[#2A9D8F] text-sm font-semibold text-white hover:bg-[#238377] disabled:opacity-50 flex items-center justify-center gap-2">
                {loading ? <Loader2 size={16} className="animate-spin" /> : null}
                Verify Code
              </button>
            </div>
          </>
        )}

        {/* ── Step 3: New Password ───────────────────────────────── */}
        {step === 'NEW_PASSWORD' && (
          <>
            <div className="flex items-center gap-2 mb-2">
              <Lock size={20} className="text-[#2A9D8F]" />
              <h3 className="text-lg font-bold text-black">Set New Password</h3>
            </div>
            <p className="text-sm text-[var(--subtitle-grey)] mb-4">
              Enter your new password below.
            </p>
            <input
              type="password"
              placeholder="New password"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setError(''); }}
              className="w-full px-3 py-2.5 text-sm border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[#2A9D8F] focus:ring-1 focus:ring-[#2A9D8F] mb-3"
              autoFocus
            />
            <input
              type="password"
              placeholder="Confirm password"
              value={confirmPassword}
              onChange={(e) => { setConfirmPassword(e.target.value); setError(''); }}
              onKeyDown={(e) => e.key === 'Enter' && setNewPassword()}
              className="w-full px-3 py-2.5 text-sm border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[#2A9D8F] focus:ring-1 focus:ring-[#2A9D8F] mb-2"
            />
            {error && <p className="text-xs text-[#DC2626] mb-2">{error}</p>}
            <div className="flex gap-3 mt-4">
              <button onClick={handleClose}
                className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black hover:bg-gray-50">
                Cancel
              </button>
              <button onClick={setNewPassword} disabled={loading}
                className="flex-1 h-11 rounded-xl bg-[#2A9D8F] text-sm font-semibold text-white hover:bg-[#238377] disabled:opacity-50 flex items-center justify-center gap-2">
                {loading ? <Loader2 size={16} className="animate-spin" /> : null}
                Reset Password
              </button>
            </div>
          </>
        )}

        {/* ── Step 4: Success ────────────────────────────────────── */}
        {step === 'DONE' && (
          <div className="text-center py-4">
            <div className="w-16 h-16 rounded-full bg-[#2A9D8F]/10 flex items-center justify-center mx-auto mb-4">
              <CheckCircle size={36} className="text-[#2A9D8F]" />
            </div>
            <h3 className="text-lg font-bold text-[#2A9D8F] mb-2">Password Reset!</h3>
            <p className="text-sm text-[var(--subtitle-grey)]">
              Your password has been successfully changed. You can now sign in with your new password.
            </p>
            <button onClick={handleClose}
              className="mt-6 w-full h-11 rounded-xl bg-[#2A9D8F] text-sm font-semibold text-white hover:bg-[#238377]">
              OK
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
