"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { approveDoctor, rejectDoctor } from "@/lib/actions";
import Modal from "@/components/ui/Modal";

interface Props {
  doctorId: string;
}

export default function DoctorCardActions({ doctorId }: Props) {
  const router = useRouter();
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [approveModalOpen, setApproveModalOpen] = useState(false);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  async function handleApprove() {
    setLoading("approve");
    setError("");
    const result = await approveDoctor(doctorId);
    if (result.error) setError(result.error);
    else {
      setApproveModalOpen(false);
      router.refresh();
    }
    setLoading(null);
  }

  async function handleReject() {
    if (!rejectReason.trim()) return;
    setLoading("reject");
    setError("");
    const result = await rejectDoctor(doctorId, rejectReason);
    if (result.error) setError(result.error);
    else {
      setRejectModalOpen(false);
      setRejectReason("");
      router.refresh();
    }
    setLoading(null);
  }

  return (
    <div className="flex items-center gap-2">
      <button
        onClick={() => setApproveModalOpen(true)}
        disabled={loading === "approve"}
        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-brand-teal text-white text-sm font-medium hover:bg-brand-teal-dark disabled:opacity-50 transition-colors"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
        </svg>
        Approve
      </button>

      <button
        onClick={() => setRejectModalOpen(true)}
        disabled={loading === "reject"}
        className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-red-200 text-red-600 text-sm font-medium hover:bg-red-50 disabled:opacity-50 transition-colors"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
        Reject
      </button>

      {error && <p className="text-xs text-red-600 ml-1">{error}</p>}

      <Modal
        open={approveModalOpen}
        onClose={() => setApproveModalOpen(false)}
        title="Approve Doctor"
      >
        <p className="text-sm text-gray-600">
          Are you sure you want to approve this doctor? They will be able to receive consultations immediately.
        </p>
        <div className="flex justify-end gap-3 mt-4">
          <button
            onClick={() => setApproveModalOpen(false)}
            className="px-4 py-2 rounded-md border border-gray-300 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleApprove}
            disabled={loading === "approve"}
            className="px-4 py-2 rounded-md bg-brand-teal text-white text-sm font-medium hover:bg-brand-teal-dark disabled:opacity-50 transition-colors"
          >
            {loading === "approve" ? "Approving..." : "Confirm Approval"}
          </button>
        </div>
      </Modal>

      <Modal
        open={rejectModalOpen}
        onClose={() => setRejectModalOpen(false)}
        title="Reject Doctor"
      >
        <textarea
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="Reason for rejection..."
          rows={4}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal focus:border-transparent resize-none mb-4"
        />
        <div className="flex justify-end gap-3">
          <button
            onClick={() => setRejectModalOpen(false)}
            className="px-4 py-2 rounded-md border border-gray-300 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleReject}
            disabled={!rejectReason.trim() || loading === "reject"}
            className="px-4 py-2 rounded-md bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50 transition-colors"
          >
            {loading === "reject" ? "Rejecting..." : "Confirm Rejection"}
          </button>
        </div>
      </Modal>
    </div>
  );
}
