"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import Modal from "@/components/ui/Modal";
import type { PortalRole } from "@/lib/types/database";

const portalLabels: Record<string, string> = {
  admin: "Admin Portal",
  hr: "HR Portal",
  finance: "Finance Portal",
  audit: "Audit Portal",
};

interface TopbarProps {
  email: string;
  role: PortalRole;
}

export default function Topbar({ email, role }: TopbarProps) {
  const label = portalLabels[role] ?? "Admin Portal";
  const router = useRouter();
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleSignOut() {
    setLoggingOut(true);
    const supabase = createClient();
    await supabase.auth.signOut();
    router.push("/login");
    router.refresh();
  }

  return (
    <>
      <header className="h-14 bg-white border-b border-gray-200 flex items-center justify-between px-6">
        {/* Left — title */}
        <div className="flex items-center gap-2.5">
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="text-brand-teal"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          </svg>
          <span className="text-sm font-semibold text-gray-900">{label}</span>
        </div>

        {/* Right — refresh + email + sign out */}
        <div className="flex items-center gap-4">
          <button
            onClick={() => window.location.reload()}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            title="Refresh"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" />
            </svg>
          </button>
          <span className="text-sm text-gray-500 truncate max-w-[200px]">{email}</span>
          <button
            onClick={() => setLogoutOpen(true)}
            className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-red-600 transition-colors"
            title="Sign Out"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3-3h-9m9 0l-3-3m3 3l-3 3" />
            </svg>
          </button>
        </div>
      </header>

      {/* Logout Confirmation Dialog */}
      <Modal open={logoutOpen} onClose={() => setLogoutOpen(false)} title="Sign Out">
        <p className="text-sm text-gray-600">
          Are you sure you want to sign out of the {label}?
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
    </>
  );
}
