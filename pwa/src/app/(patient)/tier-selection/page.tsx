'use client';

import { useRouter } from 'next/navigation';
import { ArrowLeft, ArrowRight, Check, Star, Crown, Shield } from 'lucide-react';

export default function TierSelectionPage() {
  const router = useRouter();

  return (
    <div className="min-h-dvh bg-[#FAFAFA] flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-3 px-5 py-4">
        <button onClick={() => router.back()} className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100">
          <ArrowLeft size={22} className="text-black" />
        </button>
        <div>
          <h1 className="text-xl font-bold text-black">Choose Your Plan</h1>
          <p className="text-xs text-[var(--subtitle-grey)]">Select the service tier that fits your needs</p>
        </div>
      </div>

      <div className="h-px bg-[var(--card-border)]" />

      <div className="flex-1 px-5 py-5 space-y-4 overflow-y-auto">
        {/* Royal Card */}
        <button
          onClick={() => router.push('/service-location?tier=ROYAL')}
          className="w-full text-left rounded-[20px] overflow-hidden shadow-md active:scale-[0.99] transition-transform"
        >
          <div className="bg-gradient-to-r from-[var(--royal-purple)] to-[#7C3AED] p-5">
            {/* Badge */}
            <span className="inline-block text-[11px] font-bold text-white bg-[var(--royal-gold)] px-3 py-1 rounded-full mb-4">
              {'\u2605'} PREMIUM
            </span>

            <h2 className="text-2xl font-extrabold text-white">Royal Service</h2>
            <p className="text-sm text-white/85 mt-1">Priority care with extended benefits</p>

            <div className="mt-5 space-y-2.5">
              {[
                '14-day follow-up window',
                'Priority consultation handling',
                'Continuous doctor access',
                'Up to 2 home visit requests',
                'Extended consultation experience',
              ].map((b) => (
                <div key={b} className="flex items-center gap-2.5">
                  <div className="w-5 h-5 rounded-full bg-[var(--royal-gold)] flex items-center justify-center shrink-0">
                    <Check size={12} className="text-white" />
                  </div>
                  <span className="text-sm text-white">{b}</span>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-end gap-1 mt-4">
              <span className="text-[15px] font-semibold text-[var(--royal-gold)]">Select</span>
              <ArrowRight size={18} className="text-[var(--royal-gold)]" />
            </div>
          </div>
        </button>

        {/* Economy Card */}
        <button
          onClick={() => router.push('/service-location?tier=ECONOMY')}
          className="w-full text-left rounded-[20px] border border-[var(--brand-teal)]/40 bg-[#F0FDFA] p-5 shadow-sm active:scale-[0.99] transition-transform"
        >
          {/* Badge */}
          <span className="inline-block text-[11px] font-bold text-white bg-[var(--brand-teal)] px-3 py-1 rounded-full mb-4">
            STANDARD
          </span>

          <h2 className="text-[22px] font-bold text-black">Economy Service</h2>
          <p className="text-sm text-gray-500 mt-1">Quality care at standard rates</p>

          <div className="mt-5 space-y-2.5">
            {[
              'Basic consultation',
              '1 follow-up consultation within 14 days',
              'Standard doctor access',
            ].map((b) => (
              <div key={b} className="flex items-center gap-2.5">
                <div className="w-5 h-5 rounded-full bg-[var(--brand-teal)] flex items-center justify-center shrink-0">
                  <Check size={12} className="text-white" />
                </div>
                <span className="text-sm text-black">{b}</span>
              </div>
            ))}
          </div>

          <div className="flex items-center justify-end gap-1 mt-4">
            <span className="text-[15px] font-semibold text-[var(--brand-teal)]">Select</span>
            <ArrowRight size={18} className="text-[var(--brand-teal)]" />
          </div>
        </button>
      </div>
    </div>
  );
}
