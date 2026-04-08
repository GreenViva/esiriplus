'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft, Mail, Lock, Eye, EyeOff, User, Phone, MapPin,
  AlertTriangle, ArrowRight, Loader2,
} from 'lucide-react';
import { Button, Input, Card } from '@/components/ui';
import ForgotPasswordDialog from '@/components/ForgotPasswordDialog';
import { useAuthStore } from '@/store/auth';

import { invokeEdgeFunction } from '@/lib/supabase';

/** Calls an edge function via the server-side proxy (avoids CORS). */
async function anonCall<T = unknown>(fn: string, body: Record<string, unknown>): Promise<T> {
  return invokeEdgeFunction<T>(fn, body);
}

export default function AgentPage() {
  const router = useRouter();
  const { setSession } = useAuthStore();

  const [tab, setTab] = useState<'signin' | 'signup'>('signin');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [forgotOpen, setForgotOpen] = useState(false);

  // Sign In
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);

  // Sign Up — Step 1 fields
  const [signupStep, setSignupStep] = useState<1 | 2>(1);
  const [fullName, setFullName] = useState('');
  const [mobile, setMobile] = useState('');
  const [signupEmail, setSignupEmail] = useState('');
  const [residence, setResidence] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [showSignupPassword, setShowSignupPassword] = useState(false);

  // Sign Up — Step 2 OTP
  const [otp, setOtp] = useState('');
  const [resendCooldown, setResendCooldown] = useState(0);

  // Resend cooldown timer
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const t = setTimeout(() => setResendCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [resendCooldown]);

  // ── Sign In ──────────────────────────────────────────────────────────────

  async function handleSignIn(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (!loginEmail.trim() || !loginPassword) { setError('Email and password are required'); return; }
    setLoading(true);
    try {
      const raw = await anonCall<Record<string, unknown>>('login-agent', {
        email: loginEmail.trim().toLowerCase(),
        password: loginPassword,
      });
      const user = raw.user as Record<string, unknown> | undefined;
      setSession({
        accessToken: (raw.access_token ?? raw.accessToken) as string,
        refreshToken: (raw.refresh_token ?? raw.refreshToken) as string,
        expiresAt: typeof raw.expires_at === 'number' ? (raw.expires_at as number) * 1000 : Date.now() + 3600_000,
        user: {
          id: (user?.id ?? '') as string,
          fullName: (user?.full_name ?? user?.fullName ?? '') as string,
          phone: '',
          email: (user?.email ?? '') as string,
          role: 'agent',
          isVerified: true,
        },
      });
      router.push('/home');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  // ── Sign Up Step 1: Send OTP ─────────────────────────────────────────────

  async function handleSignUpStep1(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (!fullName.trim()) { setError('Full name is required'); return; }
    if (!mobile.trim()) { setError('Mobile number is required'); return; }
    if (!signupEmail.trim() || !signupEmail.includes('@')) { setError('Valid email is required'); return; }
    if (!residence.trim()) { setError('Place of residence is required'); return; }
    if (signupPassword.length < 6) { setError('Password must be at least 6 characters'); return; }

    setLoading(true);
    try {
      await anonCall('send-doctor-otp', { email: signupEmail.trim().toLowerCase() });
      setSignupStep(2);
      setResendCooldown(60);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to send code');
    } finally {
      setLoading(false);
    }
  }

  // ── Sign Up Step 2: Verify & Register ────────────────────────────────────

  async function handleVerifyAndRegister() {
    if (otp.length !== 6) { setError('Enter the 6-digit code'); return; }
    setLoading(true);
    setError('');
    try {
      // Verify OTP
      await anonCall('verify-doctor-otp', {
        email: signupEmail.trim().toLowerCase(),
        otp_code: otp,
      });

      // Create account
      const raw = await anonCall<Record<string, unknown>>('register-agent', {
        full_name: fullName.trim(),
        mobile_number: mobile.trim(),
        email: signupEmail.trim().toLowerCase(),
        place_of_residence: residence.trim(),
        password: signupPassword,
      });

      const user = raw.user as Record<string, unknown> | undefined;
      setSession({
        accessToken: (raw.access_token ?? raw.accessToken) as string,
        refreshToken: (raw.refresh_token ?? raw.refreshToken) as string,
        expiresAt: typeof raw.expires_at === 'number' ? (raw.expires_at as number) * 1000 : Date.now() + 3600_000,
        user: {
          id: (user?.id ?? '') as string,
          fullName: (user?.full_name ?? user?.fullName ?? '') as string,
          phone: mobile.trim(),
          email: (user?.email ?? '') as string,
          role: 'agent',
          isVerified: true,
        },
      });
      router.push('/home');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verification failed');
    } finally {
      setLoading(false);
    }
  }

  async function resendOtp() {
    if (resendCooldown > 0) return;
    setError('');
    try {
      await anonCall('send-doctor-otp', { email: signupEmail.trim().toLowerCase() });
      setResendCooldown(60);
    } catch {
      setError('Failed to resend code');
    }
  }

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="max-w-md mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => router.back()} className="p-1 rounded-lg hover:bg-gray-100">
          <ArrowLeft size={20} className="text-black" />
        </button>
        <span className="text-sm font-semibold text-black">eSIRIPlus Agent</span>
      </div>

      {/* Brand */}
      <div className="text-center mb-6">
        <div className="w-[72px] h-[72px] rounded-full bg-gradient-to-br from-[#F59E0B] to-[#EF6C00] flex items-center justify-center mx-auto mb-3">
          <span className="text-white font-extrabold text-xl">e+</span>
        </div>
        <h1 className="text-[22px] font-bold text-black">eSIRIPlus Agents</h1>
        <p className="text-[14px] text-[var(--subtitle-grey)]">Earn money by helping patients access healthcare</p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-[var(--card-border)] mb-5">
        <button
          onClick={() => { setTab('signin'); setError(''); setSignupStep(1); }}
          className={`flex-1 pb-3 text-sm font-semibold text-center border-b-2 transition-colors ${
            tab === 'signin' ? 'border-[#2A9D8F] text-[#2A9D8F]' : 'border-transparent text-black/60'
          }`}
        >
          Sign In
        </button>
        <button
          onClick={() => { setTab('signup'); setError(''); }}
          className={`flex-1 pb-3 text-sm font-semibold text-center border-b-2 transition-colors ${
            tab === 'signup' ? 'border-[#2A9D8F] text-[#2A9D8F]' : 'border-transparent text-black/60'
          }`}
        >
          Sign Up
        </button>
      </div>

      {/* ── Sign In Tab ─────────────────────────────────────── */}
      {tab === 'signin' && (
        <form onSubmit={handleSignIn} className="space-y-4">
          <Input label="Email" type="email" placeholder="agent@example.com"
            icon={<Mail size={18} />} value={loginEmail}
            onChange={(e) => { setLoginEmail(e.target.value); setError(''); }} required />

          <div className="relative">
            <Input label="Password" type={showLoginPassword ? 'text' : 'password'}
              placeholder="Enter your password" icon={<Lock size={18} />}
              value={loginPassword} onChange={(e) => { setLoginPassword(e.target.value); setError(''); }} required />
            <button type="button" onClick={() => setShowLoginPassword(!showLoginPassword)}
              className="absolute right-3 top-9 text-gray-400 hover:text-gray-600">
              {showLoginPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>

          <div className="text-right">
            <button type="button" onClick={() => setForgotOpen(true)}
              className="text-[13px] font-medium text-[#2A9D8F]">
              Forgot Password? &rarr;
            </button>
          </div>

          {error && <p className="text-sm text-[#DC2626] text-center">{error}</p>}

          <Button type="submit" fullWidth size="lg" loading={loading}>
            Sign In
          </Button>
        </form>
      )}

      {/* ── Sign Up Tab ─────────────────────────────────────── */}
      {tab === 'signup' && (
        <>
          {/* Step indicator */}
          <div className="flex items-center justify-center gap-2 mb-4">
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-[13px] font-bold ${
              signupStep >= 1 ? 'bg-[#2A9D8F] text-white' : 'bg-[#E5E7EB] text-gray-400'
            }`}>1</div>
            <div className={`w-10 h-0.5 ${signupStep >= 2 ? 'bg-[#2A9D8F]' : 'bg-[#E5E7EB]'}`} />
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-[13px] font-bold ${
              signupStep >= 2 ? 'bg-[#2A9D8F] text-white' : 'bg-[#E5E7EB] text-gray-400'
            }`}>2</div>
          </div>
          <p className="text-center text-[13px] font-semibold text-[var(--subtitle-grey)] mb-4">
            {signupStep === 1 ? 'Step 1: Your Details' : 'Step 2: Verify Email'}
          </p>

          {signupStep === 1 && (
            <form onSubmit={handleSignUpStep1} className="space-y-4">
              <Input label="Full Name" placeholder="Your full name"
                icon={<User size={18} />} value={fullName}
                onChange={(e) => { setFullName(e.target.value); setError(''); }} required />

              <div>
                <Input label="Mobile Number" type="tel" placeholder="0712345678"
                  icon={<Phone size={18} />} value={mobile}
                  onChange={(e) => { setMobile(e.target.value); setError(''); }} required />
                <div className="flex items-start gap-1.5 mt-1.5 px-1">
                  <AlertTriangle size={12} className="text-[#B45309] shrink-0 mt-0.5" />
                  <p className="text-[11px] text-[#B45309]">
                    Use a valid mobile money number — this will be used for payments
                  </p>
                </div>
              </div>

              <Input label="Email" type="email" placeholder="agent@example.com"
                icon={<Mail size={18} />} value={signupEmail}
                onChange={(e) => { setSignupEmail(e.target.value); setError(''); }} required />

              <Input label="Place of Residence" placeholder="Dar es Salaam"
                icon={<MapPin size={18} />} value={residence}
                onChange={(e) => { setResidence(e.target.value); setError(''); }} required />

              <div className="relative">
                <Input label="Password" type={showSignupPassword ? 'text' : 'password'}
                  placeholder="Min 6 characters" icon={<Lock size={18} />}
                  value={signupPassword} onChange={(e) => { setSignupPassword(e.target.value); setError(''); }} required />
                <button type="button" onClick={() => setShowSignupPassword(!showSignupPassword)}
                  className="absolute right-3 top-9 text-gray-400 hover:text-gray-600">
                  {showSignupPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>

              {error && <p className="text-sm text-[#DC2626] text-center">{error}</p>}

              <Button type="submit" fullWidth size="lg" loading={loading}>
                Sign Up <ArrowRight size={16} />
              </Button>
            </form>
          )}

          {signupStep === 2 && (
            <div className="space-y-4">
              <p className="text-sm text-[var(--subtitle-grey)] text-center">
                A verification code was sent to:<br />
                <strong className="text-black">{signupEmail}</strong>
              </p>

              <input
                type="text" inputMode="numeric" maxLength={6}
                placeholder="000000"
                value={otp}
                onChange={(e) => { setOtp(e.target.value.replace(/\D/g, '').slice(0, 6)); setError(''); }}
                onKeyDown={(e) => e.key === 'Enter' && handleVerifyAndRegister()}
                className="w-full px-3 py-3 text-center text-2xl font-mono font-bold tracking-[0.3em] border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[#2A9D8F] focus:ring-1 focus:ring-[#2A9D8F]"
                autoFocus
              />

              {error && <p className="text-sm text-[#DC2626] text-center">{error}</p>}

              <Button fullWidth size="lg" loading={loading} disabled={otp.length !== 6}
                onClick={handleVerifyAndRegister}>
                Verify Code
              </Button>

              <div className="text-center">
                <button onClick={resendOtp} disabled={resendCooldown > 0}
                  className="text-sm font-medium text-[#2A9D8F] disabled:text-gray-400">
                  {resendCooldown > 0 ? `Resend Code (${resendCooldown}s)` : 'Resend Code'}
                </button>
              </div>

              <button onClick={() => { setSignupStep(1); setOtp(''); setError(''); }}
                className="w-full text-center text-sm font-medium text-[var(--subtitle-grey)]">
                &larr; Back to details
              </button>
            </div>
          )}
        </>
      )}

      <ForgotPasswordDialog
        open={forgotOpen}
        onClose={() => setForgotOpen(false)}
        prefillEmail={loginEmail}
      />
    </div>
  );
}
