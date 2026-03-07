"use client";

import { useRole } from "@/lib/RoleContext";
import type { PortalRole } from "@/lib/types/database";

interface Props {
  allowed: PortalRole[];
  children: React.ReactNode;
}

export default function RoleGuard({ allowed, children }: Props) {
  const role = useRole();

  if (!allowed.includes(role)) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-4">
            <svg className="h-6 w-6 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-gray-900">Access Denied</h2>
          <p className="text-sm text-gray-500 mt-1">
            This section is restricted to {allowed.join(", ")} users only.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
