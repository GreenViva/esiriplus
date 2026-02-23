"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import Modal from "@/components/ui/Modal";
import type { PortalRole } from "@/lib/types/database";

interface NavItem {
  href: string;
  label: string;
  icon: React.FC<{ className?: string }>;
}

const adminItems: NavItem[] = [
  { href: "/dashboard", label: "Admin Dashboard", icon: DashboardIcon },
  { href: "/dashboard/doctors", label: "Doctor Applications", icon: DoctorAppIcon },
  { href: "/dashboard/payments", label: "Payments", icon: PaymentsIcon },
  { href: "/dashboard/analytics", label: "Health Analytics", icon: AnalyticsIcon },
  { href: "/dashboard/users", label: "Role Management", icon: RoleIcon },
];

const hrItems: NavItem[] = [
  { href: "/dashboard/hr", label: "HR Dashboard", icon: HRDashboardIcon },
  { href: "/dashboard/hr/doctors", label: "Doctor Management", icon: DoctorMgmtIcon },
  { href: "/dashboard/hr/ratings", label: "Ratings & Feedback", icon: RatingsIcon },
  { href: "/dashboard/hr/audit", label: "Audit Log", icon: AuditLogIcon },
];

const financeItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: DashboardIcon },
  { href: "/dashboard/payments", label: "Payments", icon: PaymentsIcon },
  { href: "/dashboard/analytics", label: "Health Analytics", icon: AnalyticsIcon },
];

const auditItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: DashboardIcon },
];

const portalConfig: Record<string, { title: string; subtitle: string }> = {
  admin: { title: "Admin Portal", subtitle: "Administrator" },
  hr: { title: "HR Portal", subtitle: "Human Resources" },
  finance: { title: "Finance Portal", subtitle: "Finance" },
  audit: { title: "Audit Portal", subtitle: "Audit" },
};

interface SidebarProps {
  email?: string;
  fullName?: string;
  role?: PortalRole;
}

export default function Sidebar({ email, fullName, role = "admin" }: SidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const itemsByRole: Record<string, NavItem[]> = {
    admin: adminItems,
    hr: hrItems,
    finance: financeItems,
    audit: auditItems,
  };
  const items = itemsByRole[role] ?? adminItems;
  const config = portalConfig[role] ?? portalConfig.admin;

  function isActive(href: string) {
    if (role === "hr") {
      if (href === "/dashboard/hr") return pathname === "/dashboard/hr";
      return pathname.startsWith(href);
    }
    if (href === "/dashboard") return pathname === "/dashboard";
    return pathname.startsWith(href);
  }

  async function handleSignOut() {
    setLoggingOut(true);
    const supabase = createClient();
    await supabase.auth.signOut();
    router.push("/login");
    router.refresh();
  }

  return (
    <aside className="w-64 bg-white border-r border-gray-200 h-screen flex flex-col flex-shrink-0">
      {/* Logo */}
      <div className="px-5 py-5 flex items-center gap-3">
        <div className="w-9 h-9 rounded-full bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
          {role === "hr" ? (
            <HRDashboardIcon className="h-4 w-4 text-brand-teal" />
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-brand-teal">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
          )}
        </div>
        <div>
          <p className="text-sm font-bold text-gray-900">{config.title}</p>
          <p className="text-xs text-gray-400">{config.subtitle}</p>
        </div>
      </div>

      {/* Scrollable nav area */}
      <div className="flex-1 overflow-y-auto">
        <div className="px-5 pt-3 pb-1">
          <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
            {role === "hr" ? "Human Resources" : role === "finance" ? "Finance" : role === "audit" ? "Audit" : "Administration"}
          </p>
        </div>
        <nav className="px-3 space-y-0.5 pb-4">
          {items.map((item) => (
            <NavLink key={item.href} item={item} active={isActive(item.href)} />
          ))}
        </nav>
      </div>

      {/* User info + Sign Out (pinned bottom) */}
      <div className="border-t border-gray-100 px-5 py-4">
        <p className="text-sm font-medium text-gray-900">{fullName || role.toUpperCase()}</p>
        <p className="text-xs text-gray-400 truncate">{email}</p>
        <button
          onClick={() => setLogoutOpen(true)}
          className="flex items-center gap-2 mt-3 text-sm text-gray-500 hover:text-red-600 transition-colors"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3-3h-9m9 0l-3-3m3 3l-3 3" />
          </svg>
          Sign Out
        </button>
      </div>

      {/* Logout Confirmation Dialog */}
      <Modal open={logoutOpen} onClose={() => setLogoutOpen(false)} title="Sign Out">
        <p className="text-sm text-gray-600">
          Are you sure you want to sign out of the {config.title}?
        </p>
        <div className="flex justify-end gap-3 mt-5">
          <button
            onClick={() => setLogoutOpen(false)}
            className="px-4 py-2 rounded-xl border border-gray-200 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSignOut}
            disabled={loggingOut}
            className="px-4 py-2 rounded-xl bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50 transition-colors"
          >
            {loggingOut ? "Signing out..." : "Sign Out"}
          </button>
        </div>
      </Modal>
    </aside>
  );
}

function NavLink({ item, active }: { item: NavItem; active: boolean }) {
  return (
    <Link
      href={item.href}
      className={`flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] font-medium transition-colors ${
        active
          ? "bg-brand-teal/10 text-brand-teal"
          : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
      }`}
    >
      <item.icon className={`h-4 w-4 ${active ? "text-brand-teal" : "text-gray-400"}`} />
      {item.label}
    </Link>
  );
}

/* ── Icons ─────────────────────────────────────────────── */

function DashboardIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z" />
    </svg>
  );
}

function DoctorAppIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15a2.25 2.25 0 012.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V8.25m0 0H4.875c-.621 0-1.125.504-1.125 1.125v11.25c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V9.375c0-.621-.504-1.125-1.125-1.125H8.25zM6.75 12h.008v.008H6.75V12zm0 3h.008v.008H6.75V15zm0 3h.008v.008H6.75V18z" />
    </svg>
  );
}

function PaymentsIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3m-3.75 3h15a2.25 2.25 0 002.25-2.25V6.75A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25v10.5A2.25 2.25 0 004.5 19.5z" />
    </svg>
  );
}

function AnalyticsIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
    </svg>
  );
}

function RoleIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
    </svg>
  );
}

function HRDashboardIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12m-.75 4.5H21m-3.75 3H21m-3.75 3H21" />
    </svg>
  );
}

function DoctorMgmtIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
    </svg>
  );
}

function RatingsIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.562.562 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
    </svg>
  );
}

function AuditLogIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15a2.25 2.25 0 012.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V19.5a2.25 2.25 0 002.25 2.25h6.75a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08" />
    </svg>
  );
}
