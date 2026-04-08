'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Stethoscope, UserPlus, KeyRound, ArrowRight } from 'lucide-react';
import ContactUs from '@/components/ContactUs';
import { Button } from '@/components/ui';
import { useAuthStore } from '@/store/auth';

export default function HomePage() {
  const [showSplash, setShowSplash] = useState(true);
  const { session } = useAuthStore();
  const router = useRouter();

  // Auto-redirect if already authenticated
  useEffect(() => {
    if (session) {
      const dest = session.user.role === 'doctor' ? '/dashboard' : '/home';
      router.replace(dest);
    }
  }, [session, router]);

  if (showSplash) {
    return <SplashScreen onContinue={() => setShowSplash(false)} />;
  }

  return <RoleSelection />;
}

// ── Splash Screen ────────────────────────────────────────────────────────────

function SplashScreen({ onContinue }: { onContinue: () => void }) {
  const [visible, setVisible] = useState(false);
  const [tappable, setTappable] = useState(false);

  useEffect(() => {
    // Fade in
    requestAnimationFrame(() => setVisible(true));
    // Enable tap after 2 seconds
    const timer = setTimeout(() => setTappable(true), 2000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div
      className="min-h-dvh flex flex-col items-center justify-center bg-gradient-to-b from-[#F0FDFA] to-white cursor-pointer select-none"
      onClick={() => tappable && onContinue()}
    >
      <div
        className="flex flex-col items-center transition-opacity duration-[3000ms]"
        style={{ opacity: visible ? 1 : 0 }}
      >
        {/* Logo */}
        <div className="w-28 h-28 rounded-full bg-gradient-to-br from-[var(--brand-teal)] to-[#1A7A6E] flex items-center justify-center shadow-lg mb-6">
          <Stethoscope size={56} className="text-white" strokeWidth={1.5} />
        </div>

        {/* App name */}
        <h1 className="text-3xl font-bold text-black mb-3">eSIRI Plus</h1>

        {/* Swahili tagline */}
        <p className="text-lg italic text-[var(--brand-teal)] mb-1">
          Afya yako, kipaumbele chetu
        </p>

        {/* English subtitle */}
        <p className="text-sm text-[var(--subtitle-grey)]">
          Your health, our priority
        </p>

        {/* Tap to continue */}
        <p
          className="mt-12 text-sm text-[var(--subtitle-grey)] transition-opacity duration-500"
          style={{ opacity: tappable ? 1 : 0 }}
        >
          Tap to continue
        </p>

        {/* Pulsing dot */}
        {tappable && (
          <div className="mt-3 w-2 h-2 rounded-full bg-[var(--brand-teal)] animate-pulse" />
        )}
      </div>
    </div>
  );
}

// ── Role Selection ───────────────────────────────────────────────────────────

function RoleSelection() {
  const router = useRouter();

  return (
    <div className="min-h-dvh bg-gradient-to-b from-[#F0FDFA] to-white flex flex-col">
      <div className="flex-1 flex flex-col px-6 pt-12 pb-4 max-w-md mx-auto w-full">
        {/* Header */}
        <div className="flex items-center gap-3 mb-8">
          <div className="w-12 h-12 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
            <Stethoscope size={24} className="text-[var(--brand-teal)]" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-black">eSIRI Plus</h1>
            <p className="text-sm text-[var(--subtitle-grey)]">Your health, our priority</p>
          </div>
        </div>

        {/* FOR PATIENTS */}
        <p className="text-xs font-bold text-[var(--subtitle-grey)] uppercase tracking-wide mb-3">For Patients</p>

        <button
          onClick={() => router.push('/patient-setup')}
          className="w-full flex items-center gap-3 p-3.5 bg-white border border-[var(--card-border)] rounded-2xl shadow-sm hover:shadow-md transition-shadow mb-3 active:scale-[0.99]"
        >
          <div className="w-10 h-10 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center shrink-0">
            <UserPlus size={20} className="text-[var(--brand-teal)]" />
          </div>
          <div className="flex-1 text-left">
            <p className="text-[15px] font-semibold text-black">New to eSIRI Plus?</p>
            <p className="text-xs text-[var(--subtitle-grey)]">Start a consultation in minutes</p>
          </div>
          <ArrowRight size={18} className="text-gray-400" />
        </button>

        <button
          onClick={() => router.push('/patient-setup?mode=returning')}
          className="w-full flex items-center gap-3 p-3.5 bg-white border border-[var(--card-border)] rounded-2xl shadow-sm hover:shadow-md transition-shadow mb-2 active:scale-[0.99]"
        >
          <div className="w-10 h-10 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center shrink-0">
            <KeyRound size={20} className="text-[var(--brand-teal)]" />
          </div>
          <div className="flex-1 text-left">
            <p className="text-[15px] font-semibold text-black">I have my Patient ID</p>
            <p className="text-xs text-[var(--subtitle-grey)]">Access your medical records</p>
          </div>
          <ArrowRight size={18} className="text-gray-400" />
        </button>

        <div className="flex items-center justify-between px-1 mb-4">
          <p className="text-xs text-[var(--subtitle-grey)]">Save your Patient ID for future visits</p>
          <button
            onClick={() => router.push('/patient-setup?mode=recover')}
            className="text-xs font-medium text-[var(--brand-teal)]"
          >
            Forgot ID?
          </button>
        </div>

        {/* Divider */}
        <div className="flex items-center gap-4 my-4">
          <div className="flex-1 h-px bg-[var(--card-border)]" />
          <span className="text-sm text-[var(--subtitle-grey)]">or</span>
          <div className="flex-1 h-px bg-[var(--card-border)]" />
        </div>

        {/* DOCTOR PORTAL */}
        <p className="text-xs font-bold text-[var(--subtitle-grey)] uppercase tracking-wide mb-3">Doctor Portal</p>

        <div className="flex gap-3 mb-2">
          <Button variant="outline" fullWidth onClick={() => router.push('/login')}>
            Sign In
          </Button>
          <Button fullWidth onClick={() => router.push('/register')}>
            Sign Up
          </Button>
        </div>

        <button
          onClick={() => router.push('/login?forgot=true')}
          className="text-xs font-medium text-[var(--brand-teal)] self-center py-2"
        >
          Forgot password?
        </button>

        {/* Divider */}
        <div className="flex items-center gap-4 my-4">
          <div className="flex-1 h-px bg-[var(--card-border)]" />
          <span className="text-sm text-[var(--subtitle-grey)]">or</span>
          <div className="flex-1 h-px bg-[var(--card-border)]" />
        </div>

        {/* AGENTS */}
        <button
          onClick={() => router.push('/agent')}
          className="w-full flex items-center gap-3 p-3.5 bg-[#FFF7ED] border border-[var(--royal-gold)]/30 rounded-2xl shadow-sm hover:shadow-md transition-shadow active:scale-[0.99]"
        >
          <div className="w-10 h-10 rounded-full bg-gradient-to-br from-[var(--royal-gold)] to-[var(--agent-orange)] flex items-center justify-center shrink-0">
            <span className="text-white font-extrabold text-sm">e+</span>
          </div>
          <div className="flex-1 text-left">
            <p className="text-[15px] font-semibold text-black">eSIRIPlus Agents</p>
            <p className="text-xs text-[var(--subtitle-grey)]">Earn money by becoming an agent</p>
          </div>
          <ArrowRight size={18} className="text-[var(--royal-gold)]" />
        </button>

        {/* Spacer */}
        <div className="flex-1" />

        <ContactUs />
        <p className="text-center text-[11px] text-[var(--subtitle-grey)] pb-2">
          &copy; 2026 eSIRI Plus. All rights reserved.
        </p>
      </div>
    </div>
  );
}
