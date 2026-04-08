'use client';

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import {
  Mic, MicOff, Camera, CameraOff, PhoneOff,
  Volume2, VolumeX, Plus, Clock, User, SwitchCamera,
} from 'lucide-react';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction } from '@/lib/supabase';
import type { CallType, VideoCallToken } from '@/types';

// VideoSDK imports — lazy loaded
let MeetingProvider: any;
let useMeeting: any;
let useParticipant: any;
let MeetingConsumer: any;

type CallPhase = 'init' | 'connecting' | 'waiting' | 'active' | 'ended' | 'error';

interface VideoCallProps {
  consultationId: string;
  callType: CallType;
  role: 'patient' | 'doctor';
  roomId?: string; // if joining existing call
}

// ── Remote Participant View ──────────────────────────────────────────────────

function RemoteParticipantView({ participantId, isVideo }: { participantId: string; isVideo: boolean }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

  const { webcamStream, micStream, webcamOn } = useParticipant(participantId);

  useEffect(() => {
    if (webcamStream && videoRef.current) {
      const stream = new MediaStream();
      stream.addTrack(webcamStream.track);
      videoRef.current.srcObject = stream;
      videoRef.current.play().catch(() => {});
    }
  }, [webcamStream]);

  useEffect(() => {
    if (micStream && audioRef.current) {
      const stream = new MediaStream();
      stream.addTrack(micStream.track);
      audioRef.current.srcObject = stream;
      audioRef.current.play().catch(() => {});
    }
  }, [micStream]);

  if (isVideo && webcamOn) {
    return (
      <>
        <video ref={videoRef} autoPlay playsInline className="absolute inset-0 h-full w-full object-cover" />
        <audio ref={audioRef} autoPlay playsInline />
      </>
    );
  }

  return (
    <>
      <audio ref={audioRef} autoPlay playsInline />
      <div className="absolute inset-0 flex flex-col items-center justify-center bg-gradient-to-b from-gray-950 via-gray-900 to-black">
        <div className="relative mb-5">
          <div className="absolute -inset-3 rounded-full bg-[var(--brand-teal)]/10 animate-pulse" />
          <div className="relative flex h-28 w-28 items-center justify-center rounded-full bg-gradient-to-br from-[var(--brand-teal)]/30 to-[var(--brand-teal)]/10 ring-2 ring-[var(--brand-teal)]/40">
            <User size={52} className="text-white/90" />
          </div>
        </div>
        <p className="text-white text-xl font-bold">
          {isVideo ? 'Camera Off' : 'Voice Call'}
        </p>
      </div>
    </>
  );
}

// ── Local PiP View ───────────────────────────────────────────────────────────

function LocalParticipantView() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const { webcamStream, webcamOn } = useMeeting();

  useEffect(() => {
    if (webcamStream && videoRef.current) {
      const stream = new MediaStream();
      stream.addTrack(webcamStream.track);
      videoRef.current.srcObject = stream;
      videoRef.current.play().catch(() => {});
    }
  }, [webcamStream]);

  if (!webcamOn) return null;

  return (
    <div className="absolute right-4 top-16 z-20 h-[160px] w-[120px] overflow-hidden rounded-2xl border-2 border-white/20 bg-black shadow-2xl">
      <video ref={videoRef} autoPlay playsInline muted className="h-full w-full object-cover [transform:scaleX(-1)]" />
    </div>
  );
}

// ── Meeting Room (inside MeetingProvider) ────────────────────────────────────

function MeetingRoom({ callType, role, onEnd }: { callType: CallType; role: string; onEnd: (duration: number) => void }) {
  const isVideo = callType === 'VIDEO';
  const [isMuted, setIsMuted] = useState(false);
  const [isCameraOff, setIsCameraOff] = useState(!isVideo);
  const [callDuration, setCallDuration] = useState(0);
  const [phase, setPhase] = useState<'waiting' | 'active'>('waiting');
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const {
    join,
    leave,
    toggleMic,
    toggleWebcam,
    participants,
    localParticipant,
  } = useMeeting({
    onMeetingJoined: () => setPhase('waiting'),
    onMeetingLeft: () => onEnd(callDuration),
    onParticipantJoined: () => setPhase('active'),
    onParticipantLeft: () => {
      // If we're the only one left, end
      if (participants.size <= 1) {
        onEnd(callDuration);
      }
    },
  });

  // Auto-join on mount
  useEffect(() => {
    join();
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  // Remote participant
  const remoteParticipantId = useMemo(() => {
    const ids = [...participants.keys()].filter((id: string) => id !== localParticipant?.id);
    return ids[0] ?? null;
  }, [participants, localParticipant]);

  // Update phase when remote joins
  useEffect(() => {
    if (remoteParticipantId) setPhase('active');
  }, [remoteParticipantId]);

  // Call duration timer
  useEffect(() => {
    if (phase !== 'active') return;
    timerRef.current = setInterval(() => setCallDuration((d) => d + 1), 1000);
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [phase]);

  function formatDuration(s: number) {
    return `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;
  }

  function handleToggleMic() {
    toggleMic();
    setIsMuted((m) => !m);
  }

  function handleToggleCamera() {
    toggleWebcam();
    setIsCameraOff((c) => !c);
  }

  function handleEndCall() {
    leave();
    onEnd(callDuration);
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black select-none">
      {/* Remote area */}
      <div className="relative flex-1 overflow-hidden">
        {remoteParticipantId ? (
          <RemoteParticipantView participantId={remoteParticipantId} isVideo={isVideo} />
        ) : (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-gradient-to-b from-gray-950 via-gray-900 to-black">
            <div className="relative mb-5">
              <div className="absolute -inset-3 rounded-full bg-[var(--brand-teal)]/10" style={{ animation: 'pulse 2.5s ease-in-out infinite' }} />
              <div className="relative flex h-28 w-28 items-center justify-center rounded-full bg-gradient-to-br from-[var(--brand-teal)]/30 to-[var(--brand-teal)]/10 ring-2 ring-[var(--brand-teal)]/40">
                <User size={52} className="text-white/90" />
              </div>
            </div>
            <p className="text-white text-xl font-bold">Waiting for {role === 'patient' ? 'doctor' : 'patient'}…</p>
            <div className="mt-4 flex gap-1.5">
              {[0, 1, 2].map((i) => (
                <span key={i} className="block h-2 w-2 rounded-full bg-[var(--brand-teal)]" style={{ animation: 'pulse 1.4s ease-in-out infinite', animationDelay: `${i * 0.2}s` }} />
              ))}
            </div>
          </div>
        )}

        {/* Local PiP */}
        {isVideo && !isCameraOff && <LocalParticipantView />}

        {/* Top bar */}
        <div className="absolute inset-x-0 top-0 z-30 bg-gradient-to-b from-black/70 to-transparent pb-8 pt-[max(env(safe-area-inset-top),12px)] px-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 bg-black/40 rounded-full px-3 py-1.5">
              <div className={`w-2 h-2 rounded-full ${phase === 'active' ? 'bg-green-400 animate-pulse' : 'bg-amber-400'}`} />
              <span className="text-white/90 text-xs font-medium">
                {phase === 'active' ? formatDuration(callDuration) : 'Connecting…'}
              </span>
            </div>
            <div className="bg-black/40 rounded-full px-3 py-1.5">
              <span className="text-white/70 text-xs font-medium">
                {isVideo ? 'Video' : 'Voice'} Call
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Control bar */}
      <div className="bg-gradient-to-t from-black via-black/95 to-transparent pt-6 pb-[max(env(safe-area-inset-bottom),20px)] px-6">
        <div className="flex items-center justify-center gap-5">
          {/* Mute */}
          <button onClick={handleToggleMic} className={`flex h-14 w-14 items-center justify-center rounded-full transition-all active:scale-90 ${isMuted ? 'bg-red-500/90' : 'bg-white/15'}`}>
            {isMuted ? <MicOff size={22} className="text-white" /> : <Mic size={22} className="text-white" />}
          </button>

          {/* Camera (video only) */}
          {isVideo && (
            <button onClick={handleToggleCamera} className={`flex h-14 w-14 items-center justify-center rounded-full transition-all active:scale-90 ${isCameraOff ? 'bg-red-500/90' : 'bg-white/15'}`}>
              {isCameraOff ? <CameraOff size={22} className="text-white" /> : <Camera size={22} className="text-white" />}
            </button>
          )}

          {/* End call */}
          <button onClick={handleEndCall} className="flex h-16 w-16 items-center justify-center rounded-full bg-red-500 shadow-lg shadow-red-500/30 transition-transform active:scale-90">
            <PhoneOff size={26} className="text-white" />
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Main VideoCall Component ─────────────────────────────────────────────────

export default function VideoCall({ consultationId, callType, role, roomId }: VideoCallProps) {
  const router = useRouter();
  const isVideo = callType === 'VIDEO';
  const [phase, setPhase] = useState<CallPhase>('init');
  const [token, setToken] = useState<string | null>(null);
  const [meetingId, setMeetingId] = useState<string | null>(null);
  const [callDuration, setCallDuration] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [sdkLoaded, setSdkLoaded] = useState(false);

  // Lazy-load VideoSDK
  useEffect(() => {
    import('@videosdk.live/react-sdk').then((mod) => {
      MeetingProvider = mod.MeetingProvider;
      useMeeting = mod.useMeeting;
      useParticipant = mod.useParticipant;
      MeetingConsumer = mod.MeetingConsumer;
      setSdkLoaded(true);
    }).catch((err) => {
      setError('Failed to load video SDK');
      setPhase('error');
    });
  }, []);

  // Fetch token
  useEffect(() => {
    if (!sdkLoaded) return;
    let cancelled = false;

    async function init() {
      setPhase('connecting');
      try {
        const currentToken = useAuthStore.getState().session?.accessToken;
        const result = await invokeEdgeFunction<VideoCallToken>(
          'videosdk-token',
          {
            consultation_id: consultationId,
            call_type: callType,
            room_id: roomId ?? '',
          },
          currentToken ?? undefined,
          role,
        );

        if (cancelled) return;

        // Edge function returns snake_case: room_id, expires_in
        const resToken = result?.token;
        const resRoomId = (result as any)?.roomId ?? (result as any)?.room_id;

        if (!resToken || !resRoomId) {
          console.error('[VideoCall] Token response:', JSON.stringify(result));
          throw new Error('Invalid token response');
        }

        setToken(resToken);
        setMeetingId(resRoomId);
        setPhase('connecting');
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to connect');
          setPhase('error');
        }
      }
    }

    init();
    return () => { cancelled = true; };
  }, [sdkLoaded, consultationId, callType, role, roomId]);

  function handleEnd(duration: number) {
    setCallDuration(duration);
    setPhase('ended');
  }

  function formatDuration(s: number) {
    return `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;
  }

  // ── Loading / Connecting ───────────────────────────────────────────────────

  if (phase === 'init' || (phase === 'connecting' && (!token || !meetingId))) {
    return (
      <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gradient-to-b from-gray-950 to-black">
        <div className="relative mb-8">
          <div className="absolute inset-0 rounded-full bg-[var(--brand-teal)]/20 animate-ping" style={{ animationDuration: '2s' }} />
          <div className="relative flex h-28 w-28 items-center justify-center rounded-full bg-[var(--brand-teal)]/10 ring-2 ring-[var(--brand-teal)]/30">
            <User size={52} className="text-[var(--brand-teal)]" />
          </div>
        </div>
        <p className="text-white text-xl font-semibold mb-1">Connecting…</p>
        <p className="text-white/50 text-sm">{isVideo ? 'Video Call' : 'Voice Call'}</p>
        <div className="mt-6 flex gap-1.5">
          {[0, 1, 2].map((i) => (
            <span key={i} className="block h-2 w-2 rounded-full bg-[var(--brand-teal)]" style={{ animation: 'pulse 1.4s ease-in-out infinite', animationDelay: `${i * 0.2}s` }} />
          ))}
        </div>
        <button onClick={() => router.back()} className="mt-12 flex h-16 w-16 items-center justify-center rounded-full bg-red-500 transition-transform active:scale-90">
          <PhoneOff size={26} className="text-white" />
        </button>
        <p className="mt-3 text-white/40 text-xs">Tap to cancel</p>
      </div>
    );
  }

  // ── Error ──────────────────────────────────────────────────────────────────

  if (phase === 'error') {
    return (
      <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gradient-to-b from-gray-950 to-black px-6">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-red-500/20">
          <PhoneOff size={36} className="text-red-400" />
        </div>
        <h2 className="text-white text-xl font-bold mb-2">Call Failed</h2>
        <p className="text-white/50 text-sm text-center mb-8">{error}</p>
        <button onClick={() => router.back()} className="w-full max-w-xs rounded-2xl bg-white/10 py-4 text-center text-white font-semibold transition-transform active:scale-95">
          Return to Chat
        </button>
      </div>
    );
  }

  // ── Call Ended ─────────────────────────────────────────────────────────────

  if (phase === 'ended') {
    return (
      <div className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gradient-to-b from-gray-950 to-black px-6">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-white/10">
          <PhoneOff size={36} className="text-white/60" />
        </div>
        <h2 className="text-white text-2xl font-bold mb-2">Call Ended</h2>
        <div className="flex items-center gap-2 text-white/60 text-sm mb-8">
          <Clock size={16} />
          <span>Duration: {formatDuration(callDuration)}</span>
        </div>
        <button onClick={() => router.back()} className="w-full max-w-xs rounded-2xl bg-[var(--brand-teal)] py-4 text-center text-white font-semibold transition-transform active:scale-95">
          Return to Chat
        </button>
      </div>
    );
  }

  // ── Active Call (inside MeetingProvider) ────────────────────────────────────

  if (!MeetingProvider || !token || !meetingId) return null;

  return (
    <MeetingProvider
      config={{
        meetingId,
        micEnabled: true,
        webcamEnabled: isVideo,
        name: role === 'doctor' ? 'Doctor' : 'Patient',
      }}
      token={token}
    >
      <MeetingRoom callType={callType} role={role} onEnd={handleEnd} />
    </MeetingProvider>
  );
}
