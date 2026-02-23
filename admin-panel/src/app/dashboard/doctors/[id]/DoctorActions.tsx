"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { approveDoctor, rejectDoctor, deauthorizeDevice } from "@/lib/actions";

interface Props {
  doctorId: string;
  isVerified: boolean;
  hasDevice: boolean;
}

export default function DoctorActions({ doctorId, isVerified, hasDevice }: Props) {
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

  async function handleDeauthorize() {
    setLoading("deauthorize");
    setError("");
    const result = await deauthorizeDevice(doctorId);
    if (result.error) setError(result.error);
    else router.refresh();
    setLoading(null);
  }

  return (
    <>
      <div className="flex gap-3 flex-wrap">
        {!isVerified && (
          <>
            <Button
              variant="primary"
              onClick={() => setApproveModalOpen(true)}
            >
              Approve Doctor
            </Button>
            <Button
              variant="danger"
              onClick={() => setRejectModalOpen(true)}
            >
              Reject
            </Button>
          </>
        )}
        {hasDevice && (
          <Button
            variant="outline"
            loading={loading === "deauthorize"}
            onClick={handleDeauthorize}
          >
            Deauthorize Device
          </Button>
        )}
      </div>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <Modal
        open={approveModalOpen}
        onClose={() => setApproveModalOpen(false)}
        title="Approve Doctor"
      >
        <p className="text-sm text-gray-600">
          Are you sure you want to approve this doctor? They will be able to receive consultations immediately.
        </p>
        <div className="flex justify-end gap-3 mt-4">
          <Button variant="outline" onClick={() => setApproveModalOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="primary"
            loading={loading === "approve"}
            onClick={handleApprove}
          >
            Confirm Approval
          </Button>
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
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal focus:border-transparent resize-none"
        />
        <div className="flex justify-end gap-3 mt-4">
          <Button variant="outline" onClick={() => setRejectModalOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={loading === "reject"}
            disabled={!rejectReason.trim()}
            onClick={handleReject}
          >
            Confirm Rejection
          </Button>
        </div>
      </Modal>
    </>
  );
}
