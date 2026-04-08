'use client';

import { Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowLeft, ArrowRight, MapPin } from 'lucide-react';

export default function ServiceLocationPage() {
  return <Suspense><ServiceLocationContent /></Suspense>;
}

function ServiceLocationContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const tier = searchParams.get('tier') || 'ECONOMY';

  function handleInsideTanzania() {
    router.push(`/services?tier=${tier}`);
  }

  function handleOutsideTanzania() {
    // International service not yet available
    alert('International service is coming soon. Currently, eSIRI Plus is available only within Tanzania.');
  }

  return (
    <div className="min-h-dvh bg-gradient-to-b from-white to-[#E0F2F1] flex flex-col items-center px-5">
      {/* Back button */}
      <div className="w-full py-4">
        <button onClick={() => router.back()} className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100">
          <ArrowLeft size={22} className="text-black" />
        </button>
      </div>

      {/* Icon */}
      <div className="w-[72px] h-[72px] rounded-full bg-[#F0FDFA] flex items-center justify-center mt-4 mb-6">
        <MapPin size={36} className="text-[var(--brand-teal)]" />
      </div>

      <h1 className="text-[22px] font-bold text-black text-center">
        Where would you like service from?
      </h1>
      <p className="text-sm text-gray-500 mt-2 text-center">
        Select your preferred service region
      </p>

      <div className="w-full mt-8 space-y-4">
        {/* Inside Tanzania */}
        <button
          onClick={handleInsideTanzania}
          className="w-full flex items-center justify-between p-5 rounded-2xl bg-[var(--brand-teal)] text-white active:scale-[0.99] transition-transform shadow-md"
        >
          <div>
            <p className="text-lg font-bold">Inside Tanzania</p>
            <p className="text-sm text-white/80 mt-1">Connect with local doctors</p>
          </div>
          <ArrowRight size={22} />
        </button>

        {/* Outside Tanzania */}
        <button
          onClick={handleOutsideTanzania}
          className="w-full flex items-center justify-between p-5 rounded-2xl bg-white border border-[var(--card-border)] text-black active:scale-[0.99] transition-transform"
        >
          <div>
            <p className="text-lg font-bold">Outside Tanzania</p>
            <p className="text-sm text-gray-500 mt-1">International doctors</p>
          </div>
          <ArrowRight size={22} className="text-gray-400" />
        </button>
      </div>
    </div>
  );
}
