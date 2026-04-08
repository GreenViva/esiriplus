'use client';

import { Suspense, use } from 'react';
import { useSearchParams } from 'next/navigation';
import VideoCall from '@/components/VideoCall';
import type { CallType } from '@/types';

export default function PatientVideoCallPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  return (
    <Suspense fallback={<Fallback />}>
      <Content params={params} />
    </Suspense>
  );
}

function Content({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const searchParams = useSearchParams();
  const callType = (searchParams.get('type') as CallType) ?? 'VIDEO';
  const roomId = searchParams.get('room') ?? undefined;

  return (
    <VideoCall
      consultationId={id}
      callType={callType}
      role="patient"
      roomId={roomId}
    />
  );
}

function Fallback() {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black">
      <div className="text-center">
        <div className="mx-auto mb-4 h-16 w-16 rounded-full border-4 border-transparent border-t-[var(--brand-teal)] animate-spin" />
        <p className="text-white text-lg font-semibold">Preparing call…</p>
      </div>
    </div>
  );
}
