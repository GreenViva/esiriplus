'use client';

import Link from 'next/link';
import { ArrowLeft, Stethoscope } from 'lucide-react';

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#F0FDFA] via-white to-[#EDE9FE] flex flex-col">
      {/* Top bar */}
      <div className="px-4 pt-4 pb-2 flex items-center gap-3">
        <Link
          href="/"
          className="w-9 h-9 rounded-full bg-white border border-[var(--card-border)] flex items-center justify-center shadow-sm hover:shadow-md transition-shadow"
        >
          <ArrowLeft size={18} className="text-black" />
        </Link>
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
            <Stethoscope size={16} className="text-[var(--brand-teal)]" />
          </div>
          <span className="text-sm font-bold text-black">eSIRI Plus</span>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col items-center px-4 pb-6 pt-2">
        <div className="w-full max-w-md">
          {children}
        </div>
      </div>
    </div>
  );
}
