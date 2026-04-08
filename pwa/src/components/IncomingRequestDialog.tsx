'use client';

import {
  Phone,
  Check,
  X,
  Hourglass,
  AlertTriangle,
  Droplets,
  Activity,
  Loader2,
} from 'lucide-react';
import type { IncomingRequestState } from '@/types';

interface Props {
  state: IncomingRequestState;
  onAccept: () => void;
  onReject: () => void;
  onDismiss: () => void;
}

function formatServiceType(raw: string | null): string {
  if (!raw) return 'Consultation';
  return raw
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function IncomingRequestDialog({
  state,
  onAccept,
  onReject,
  onDismiss,
}: Props) {
  if (!state.requestId) return null;

  const resolved = state.responseStatus !== null;
  const isPending = !resolved && !state.canRetry;
  const isError = !resolved && state.canRetry;
  const progress = state.secondsRemaining / REQUEST_TTL;
  const urgentTimer = state.secondsRemaining <= 10;

  // Status-specific content
  let icon: React.ReactNode;
  let title: string;
  let subtitle: string;
  let iconBg: string;

  if (state.responseStatus === 'accepted') {
    icon = <Check size={28} className="text-white" />;
    iconBg = 'bg-[#16A34A]';
    title = 'Request Accepted';
    subtitle = 'Redirecting to consultation\u2026';
  } else if (state.responseStatus === 'rejected') {
    icon = <X size={28} className="text-white" />;
    iconBg = 'bg-[#DC2626]';
    title = 'Request Declined';
    subtitle = 'The patient will be notified.';
  } else if (state.responseStatus === 'expired') {
    icon = <Hourglass size={28} className="text-white" />;
    iconBg = 'bg-gray-500';
    title = 'Request Expired';
    subtitle = 'The request time has passed.';
  } else if (isError) {
    icon = <AlertTriangle size={28} className="text-white" />;
    iconBg = 'bg-[#F59E0B]';
    title = 'Accept Failed';
    subtitle = state.errorMessage ?? 'Something went wrong. You can retry.';
  } else {
    icon = <Phone size={28} className="text-white" />;
    iconBg = 'bg-[#2A9D8F]';
    title = state.isSubstituteFollowUp
      ? 'Follow-up Patient Request'
      : state.isFollowUp
        ? 'Follow-up Request (Royal)'
        : 'New Consultation Request';
    subtitle = `You have ${state.secondsRemaining} seconds to respond.`;
  }

  const canDismiss = resolved || isError;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-6">
      <div
        className="bg-white rounded-2xl w-full max-w-sm shadow-2xl overflow-hidden animate-[slideUp_0.3s_ease-out]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6 flex flex-col items-center text-center">
          {/* Status icon */}
          <div
            className={`w-14 h-14 rounded-full ${iconBg} flex items-center justify-center mb-4 ${
              isPending ? 'animate-pulse' : ''
            }`}
          >
            {icon}
          </div>

          {/* Title & subtitle */}
          <h2 className="text-lg font-bold text-black mb-1">{title}</h2>
          <p className="text-sm text-[var(--subtitle-grey)] mb-4">{subtitle}</p>

          {/* Countdown timer + progress bar (pending only) */}
          {isPending && (
            <div className="w-full mb-4">
              <div className="flex justify-center mb-2">
                <span
                  className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold ${
                    urgentTimer
                      ? 'bg-red-100 text-red-700'
                      : 'bg-[#2A9D8F]/10 text-[#2A9D8F]'
                  }`}
                >
                  {state.secondsRemaining}s remaining
                </span>
              </div>
              <div className="w-full h-1.5 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-1000 ${
                    urgentTimer ? 'bg-red-500' : 'bg-[#2A9D8F]'
                  }`}
                  style={{ width: `${progress * 100}%` }}
                />
              </div>
            </div>
          )}

          {/* Patient info card (pending only) */}
          {isPending && hasPatientInfo(state) && (
            <div className="w-full rounded-xl bg-[#2A9D8F]/5 border border-[#2A9D8F]/10 p-4 mb-4 text-left">
              {/* Demographics row */}
              {(state.patientAgeGroup || state.patientSex || state.patientBloodGroup) && (
                <div className="flex items-center gap-2 flex-wrap mb-2">
                  {state.patientAgeGroup && (
                    <span className="text-xs font-medium text-black bg-white px-2 py-0.5 rounded-md">
                      {state.patientAgeGroup}
                    </span>
                  )}
                  {state.patientSex && (
                    <span className="text-xs font-medium text-black bg-white px-2 py-0.5 rounded-md">
                      {state.patientSex}
                    </span>
                  )}
                  {state.patientBloodGroup && (
                    <span className="inline-flex items-center gap-1 text-xs font-medium text-black bg-white px-2 py-0.5 rounded-md">
                      <Droplets size={10} className="text-red-500" />
                      {state.patientBloodGroup}
                    </span>
                  )}
                </div>
              )}

              {/* Chief complaint */}
              {state.chiefComplaint && (
                <div className="mb-2">
                  <p className="text-[10px] font-semibold text-[var(--subtitle-grey)] uppercase tracking-wide mb-0.5">
                    Chief Complaint
                  </p>
                  <p className="text-xs text-black font-medium line-clamp-3">{state.chiefComplaint}</p>
                </div>
              )}

              {/* Symptoms */}
              {state.symptoms && (
                <div className="mb-2">
                  <p className="text-[10px] font-semibold text-[var(--subtitle-grey)] uppercase tracking-wide mb-0.5">
                    Symptoms
                  </p>
                  <p className="text-xs text-black line-clamp-3">{state.symptoms}</p>
                </div>
              )}

              {/* Allergies (red highlight) */}
              {state.patientAllergies && (
                <div className="mb-2">
                  <p className="text-[10px] font-semibold text-red-600 uppercase tracking-wide mb-0.5">
                    Allergies
                  </p>
                  <p className="text-xs text-red-700 font-medium line-clamp-2">
                    {state.patientAllergies}
                  </p>
                </div>
              )}

              {/* Chronic conditions */}
              {state.patientChronicConditions && (
                <div>
                  <p className="text-[10px] font-semibold text-[var(--subtitle-grey)] uppercase tracking-wide mb-0.5">
                    Conditions
                  </p>
                  <p className="text-xs text-[var(--subtitle-grey)] line-clamp-2">
                    {state.patientChronicConditions}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Service type badge (pending) */}
          {isPending && (
            <div className="flex items-center gap-2 mb-5">
              <Activity size={14} className="text-[#2A9D8F]" />
              <span className="text-xs font-semibold text-black">
                {formatServiceType(state.serviceType)}
              </span>
              {state.serviceTier === 'ROYAL' && (
                <span className="text-[10px] font-bold text-[var(--royal-purple)] bg-[var(--royal-purple)]/10 px-2 py-0.5 rounded-full">
                  ROYAL
                </span>
              )}
            </div>
          )}

          {/* Action buttons */}
          {isPending && (
            <div className="flex gap-3 w-full">
              <button
                onClick={onReject}
                disabled={state.isResponding}
                className="flex-1 h-12 rounded-xl border-2 border-[#DC2626] text-sm font-bold text-[#DC2626] hover:bg-red-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Decline
              </button>
              <button
                onClick={onAccept}
                disabled={state.isResponding}
                className="flex-1 h-12 rounded-xl bg-[#16A34A] text-sm font-bold text-white hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                {state.isResponding ? (
                  <Loader2 size={18} className="animate-spin" />
                ) : (
                  'Accept'
                )}
              </button>
            </div>
          )}

          {/* Error retry buttons */}
          {isError && (
            <div className="flex gap-3 w-full">
              <button
                onClick={onDismiss}
                className="flex-1 h-12 rounded-xl border border-[var(--card-border)] text-sm font-bold text-black hover:bg-gray-50 transition-colors"
              >
                Dismiss
              </button>
              <button
                onClick={onAccept}
                className="flex-1 h-12 rounded-xl bg-[#16A34A] text-sm font-bold text-white hover:bg-green-700 transition-colors"
              >
                Retry
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

const REQUEST_TTL = 60;

function hasPatientInfo(state: IncomingRequestState): boolean {
  return !!(
    state.chiefComplaint ||
    state.symptoms ||
    state.patientAgeGroup ||
    state.patientSex ||
    state.patientBloodGroup ||
    state.patientAllergies ||
    state.patientChronicConditions
  );
}
