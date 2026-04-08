'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  LayoutDashboard,
  MessageSquare,
  Crown,
  Calendar,
  CalendarClock,
  Bell,
  DollarSign,
  LogOut,
  Stethoscope,
  Menu,
  X,
} from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { useAuthStore } from '@/store/auth';
import { useIncomingRequest } from '@/hooks/useIncomingRequest';
import { ensureFreshToken } from '@/lib/supabase';
import IncomingRequestDialog from '@/components/IncomingRequestDialog';

const navItems = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/consultations', label: 'Consultations', icon: MessageSquare },
  { href: '/royal-clients', label: 'Royal Clients', icon: Crown },
  { href: '/availability', label: 'Availability', icon: Calendar },
  { href: '/appointments', label: 'Appointments', icon: CalendarClock },
  { href: '/notifications', label: 'Notifications', icon: Bell },
  { href: '/earnings', label: 'Earnings', icon: DollarSign },
];

const mobileNavItems = [
  { href: '/dashboard', label: 'Home', icon: LayoutDashboard },
  { href: '/consultations', label: 'Consults', icon: MessageSquare },
  { href: '/royal-clients', label: 'Royal', icon: Crown },
  { href: '/appointments', label: 'Schedule', icon: CalendarClock },
  { href: '/earnings', label: 'Earnings', icon: DollarSign },
];

export default function DoctorLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const { session, logout } = useAuthStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const { state: incomingRequest, acceptRequest, rejectRequest, dismiss: dismissRequest } =
    useIncomingRequest();

  // Proactive token refresh — check every 2 minutes, refresh 5 min before expiry
  const refreshRef = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    if (session?.user?.role !== 'doctor') return;
    // Refresh immediately on mount (in case token is already stale)
    ensureFreshToken().catch(() => {});
    refreshRef.current = setInterval(() => {
      ensureFreshToken().catch(() => {});
    }, 2 * 60 * 1000);
    return () => {
      if (refreshRef.current) clearInterval(refreshRef.current);
    };
  }, [session?.user?.role]);

  // Navigate to consultation when doctor accepts
  useEffect(() => {
    if (incomingRequest.responseStatus === 'accepted' && incomingRequest.consultationId) {
      const consultationId = incomingRequest.consultationId;
      // Small delay to let the "Accepted" state show before navigating
      const timer = setTimeout(() => {
        router.push(`/doc-consultation/${consultationId}`);
      }, 1200);
      return () => clearTimeout(timer);
    }
  }, [incomingRequest.responseStatus, incomingRequest.consultationId, router]);

  const doctorName = session?.user?.fullName ?? 'Doctor';
  const initials = doctorName
    .split(' ')
    .map((n) => n[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  const handleLogout = () => {
    logout();
    router.push('/');
  };

  const isActive = (href: string) =>
    pathname === href || pathname.startsWith(href + '/');

  return (
    <div className="flex min-h-dvh bg-gray-50">
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:flex-col lg:w-64 bg-white border-r border-[var(--card-border)] fixed inset-y-0 left-0 z-30">
        {/* Logo */}
        <div className="flex items-center gap-3 px-6 h-16 border-b border-[var(--card-border)]">
          <div className="w-9 h-9 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
            <Stethoscope size={18} className="text-[var(--brand-teal)]" />
          </div>
          <span className="text-base font-bold text-black">eSIRI Plus</span>
        </div>

        {/* Doctor info */}
        <div className="px-6 py-4 border-b border-[var(--card-border)]">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-[var(--brand-teal)] flex items-center justify-center text-white font-bold text-sm">
              {initials}
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-black truncate">{doctorName}</p>
              <p className="text-xs text-[var(--subtitle-grey)]">Doctor</p>
            </div>
          </div>
        </div>

        {/* Nav items */}
        <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
          {navItems.map(({ href, label, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                isActive(href)
                  ? 'bg-[var(--brand-teal)]/10 text-[var(--brand-teal)]'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-black'
              }`}
            >
              <Icon size={20} strokeWidth={isActive(href) ? 2.5 : 2} />
              {label}
            </Link>
          ))}
        </nav>

        {/* Logout */}
        <div className="p-3 border-t border-[var(--card-border)]">
          <button
            onClick={() => setShowLogoutConfirm(true)}
            className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-[var(--error-red)] hover:bg-red-50 transition-colors w-full"
          >
            <LogOut size={20} />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Mobile sidebar overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-black/50" onClick={() => setSidebarOpen(false)} />
          <aside className="absolute left-0 top-0 bottom-0 w-72 bg-white shadow-xl">
            <div className="flex items-center justify-between px-4 h-14 border-b border-[var(--card-border)]">
              <div className="flex items-center gap-2">
                <Stethoscope size={18} className="text-[var(--brand-teal)]" />
                <span className="text-sm font-bold text-black">eSIRI Plus</span>
              </div>
              <button onClick={() => setSidebarOpen(false)} className="p-1.5 rounded-full hover:bg-gray-100">
                <X size={20} className="text-gray-500" />
              </button>
            </div>
            <div className="px-4 py-4 border-b border-[var(--card-border)]">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[var(--brand-teal)] flex items-center justify-center text-white font-bold text-sm">
                  {initials}
                </div>
                <div>
                  <p className="text-sm font-semibold text-black">{doctorName}</p>
                  <p className="text-xs text-[var(--subtitle-grey)]">Doctor</p>
                </div>
              </div>
            </div>
            <nav className="py-3 px-3 space-y-1">
              {navItems.map(({ href, label, icon: Icon }) => (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setSidebarOpen(false)}
                  className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                    isActive(href)
                      ? 'bg-[var(--brand-teal)]/10 text-[var(--brand-teal)]'
                      : 'text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  <Icon size={20} strokeWidth={isActive(href) ? 2.5 : 2} />
                  {label}
                </Link>
              ))}
            </nav>
            <div className="absolute bottom-0 left-0 right-0 p-3 border-t border-[var(--card-border)]">
              <button
                onClick={handleLogout}
                className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium text-[var(--error-red)] hover:bg-red-50 transition-colors w-full"
              >
                <LogOut size={20} />
                Sign Out
              </button>
            </div>
          </aside>
        </div>
      )}

      {/* Main content area */}
      <div className="flex-1 flex flex-col lg:ml-64">
        {/* Mobile top header */}
        <header className="lg:hidden sticky top-0 z-20 bg-white border-b border-[var(--card-border)] flex items-center justify-between px-4 h-14">
          <button onClick={() => setSidebarOpen(true)} className="p-1.5 rounded-lg hover:bg-gray-100">
            <Menu size={22} className="text-black" />
          </button>
          <div className="flex items-center gap-2">
            <Stethoscope size={16} className="text-[var(--brand-teal)]" />
            <span className="text-sm font-bold text-black">eSIRI Plus</span>
          </div>
          <Link href="/notifications" className="relative p-1.5 rounded-lg hover:bg-gray-100">
            <Bell size={22} className="text-black" />
          </Link>
        </header>

        {/* Page content */}
        <main className="flex-1 pb-20 lg:pb-6 overflow-y-auto">{children}</main>

        {/* Mobile bottom nav */}
        <nav className="lg:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-[var(--card-border)] z-40">
          <div className="flex items-center justify-around max-w-lg mx-auto h-16 px-2">
            {mobileNavItems.map(({ href, label, icon: Icon }) => {
              const active = isActive(href);
              return (
                <Link
                  key={href}
                  href={href}
                  className={`flex flex-col items-center justify-center gap-0.5 flex-1 py-2 rounded-xl transition-colors ${
                    active
                      ? 'text-[var(--brand-teal)]'
                      : 'text-gray-400 hover:text-gray-600'
                  }`}
                >
                  <Icon size={22} strokeWidth={active ? 2.5 : 2} />
                  <span className={`text-[10px] ${active ? 'font-bold' : 'font-medium'}`}>
                    {label}
                  </span>
                </Link>
              );
            })}
          </div>
          <div className="h-[env(safe-area-inset-bottom)]" />
        </nav>

        {/* Incoming Consultation Request Dialog */}
        <IncomingRequestDialog
          state={incomingRequest}
          onAccept={acceptRequest}
          onReject={rejectRequest}
          onDismiss={dismissRequest}
        />

        {/* Logout Confirmation */}
        {showLogoutConfirm && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
            <div className="bg-white rounded-2xl p-6 mx-6 max-w-sm w-full shadow-xl">
              <h3 className="text-lg font-bold text-black mb-2">Sign Out?</h3>
              <p className="text-sm text-[var(--subtitle-grey)] mb-6">
                Are you sure you want to sign out? You will need to log in again to access your dashboard.
              </p>
              <div className="flex gap-3">
                <button
                  onClick={() => setShowLogoutConfirm(false)}
                  className="flex-1 h-11 rounded-xl border border-[var(--card-border)] text-sm font-semibold text-black hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  onClick={handleLogout}
                  className="flex-1 h-11 rounded-xl bg-[var(--error-red)] text-sm font-semibold text-white hover:bg-red-700"
                >
                  Sign Out
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
