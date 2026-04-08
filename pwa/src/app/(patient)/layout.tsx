'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { Home, FileText, User, Activity, ChevronRight } from 'lucide-react';
import { useAuthStore } from '@/store/auth';
import { getAuthClient, supabase } from '@/lib/supabase';

const navItems = [
  { href: '/home', label: 'Home', icon: Home },
  { href: '/reports', label: 'Reports', icon: FileText },
  { href: '/profile', label: 'Profile', icon: User },
];

// Only show bottom nav on these root pages
const NAV_PAGES = ['/home', '/reports', '/profile'];

interface ActiveConsultation {
  consultation_id: string;
  status: string;
  service_type: string;
  service_tier: string;
}

export default function PatientLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const { session, patientSession } = useAuthStore();
  const [activeConsultation, setActiveConsultation] = useState<ActiveConsultation | null>(null);
  const showNav = NAV_PAGES.some(
    (p) => pathname === p || (p !== '/home' && pathname.startsWith(p + '/'))
  );

  // Don't show banner if already on the consultation page
  const isOnConsultationPage = pathname.startsWith('/consultation/');

  const checkActiveConsultation = useCallback(async () => {
    const sessionId = patientSession?.sessionId;
    if (!sessionId) return;
    try {
      const token = session?.accessToken;
      const db = token ? getAuthClient(token, 'patient') : supabase;
      const { data } = await db
        .from('consultations')
        .select('consultation_id, status, service_type, service_tier')
        .eq('patient_session_id', sessionId)
        .in('status', ['active', 'pending', 'awaiting_extension', 'grace_period'])
        .order('created_at', { ascending: false })
        .limit(1);
      if (data && data.length > 0) {
        setActiveConsultation(data[0] as ActiveConsultation);
      } else {
        setActiveConsultation(null);
      }
    } catch {
      // silent
    }
  }, [patientSession?.sessionId, session?.accessToken]);

  useEffect(() => {
    checkActiveConsultation();
    // Re-check every 30 seconds
    const interval = setInterval(checkActiveConsultation, 30_000);
    return () => clearInterval(interval);
  }, [checkActiveConsultation]);

  // Also re-check when navigating back to non-consultation pages
  useEffect(() => {
    if (!isOnConsultationPage) {
      checkActiveConsultation();
    }
  }, [pathname, isOnConsultationPage, checkActiveConsultation]);

  return (
    <div className="flex flex-col min-h-dvh bg-gray-50">
      {/* Active consultation banner — visible across all patient pages */}
      {activeConsultation && !isOnConsultationPage && (
        <button
          onClick={() => router.push(`/consultation/${activeConsultation.consultation_id}`)}
          className="sticky top-0 z-50 w-full flex items-center gap-3 px-4 py-3 bg-gradient-to-r from-[#2A9D8F] to-[#238377] text-white shadow-lg"
        >
          <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center shrink-0 animate-pulse">
            <Activity size={16} />
          </div>
          <div className="flex-1 text-left min-w-0">
            <p className="text-sm font-bold">
              {activeConsultation.status === 'pending' ? 'Waiting for doctor...' : 'Consultation in progress'}
            </p>
            <p className="text-[11px] text-white/70">
              {activeConsultation.service_tier === 'ROYAL' ? '\u2605 Royal' : 'Economy'} &middot; {activeConsultation.service_type?.replace('_', ' ')} &middot; Tap to resume
            </p>
          </div>
          <ChevronRight size={18} className="text-white/60 shrink-0" />
        </button>
      )}

      <main className={`flex-1 overflow-y-auto ${showNav ? 'pb-20' : ''}`}>
        {children}
      </main>

      {showNav && (
        <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-[var(--card-border)] z-40">
          <div className="flex items-center justify-around max-w-lg mx-auto h-16 px-2">
            {navItems.map(({ href, label, icon: Icon }) => {
              const isActive =
                pathname === href || pathname.startsWith(href + '/');
              return (
                <Link
                  key={href}
                  href={href}
                  className={`flex flex-col items-center justify-center gap-0.5 flex-1 py-2 rounded-xl transition-colors ${
                    isActive
                      ? 'text-[var(--brand-teal)]'
                      : 'text-gray-400 hover:text-gray-600'
                  }`}
                >
                  <Icon size={22} strokeWidth={isActive ? 2.5 : 2} />
                  <span className={`text-[10px] ${isActive ? 'font-bold' : 'font-medium'}`}>
                    {label}
                  </span>
                </Link>
              );
            })}
          </div>
          <div className="h-[env(safe-area-inset-bottom)]" />
        </nav>
      )}
    </div>
  );
}
