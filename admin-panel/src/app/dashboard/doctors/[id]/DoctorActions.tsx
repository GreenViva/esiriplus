"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { approveDoctor, rejectDoctor, deauthorizeDevice, suspendDoctor, unsuspendDoctor } from "@/lib/actions";

interface Props {
  doctorId: string;
  isVerified: boolean;
  isAvailable: boolean;
  isBanned: boolean;
  hasDevice: boolean;
}

export default function DoctorActions({ doctorId, isVerified, isAvailable, isBanned, hasDevice }: Props) {
  const router = useRouter();
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [approveModalOpen, setApproveModalOpen] = useState(false);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [suspendModalOpen, setSuspendModalOpen] = useState(false);
  const [suspendDays, setSuspendDays] = useState<number>(7);
  const [suspendReason, setSuspendReason] = useState("");

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

  async function handleSuspend() {
    setLoading("suspend");
    setError("");
    const result = await suspendDoctor(doctorId, suspendDays, suspendReason);
    if (result.error) setError(result.error);
    else {
      setSuspendModalOpen(false);
      setSuspendDays(7);
      setSuspendReason("");
      router.refresh();
    }
    setLoading(null);
  }

  async function handleUnsuspend() {
    setLoading("unsuspend");
    setError("");
    const result = await unsuspendDoctor(doctorId);
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
        {isVerified && isAvailable && !isBanned && (
          <Button
            variant="danger"
            onClick={() => setSuspendModalOpen(true)}
          >
            Suspend Doctor
          </Button>
        )}
        {isVerified && !isAvailable && !isBanned && (
          <Button
            variant="primary"
            loading={loading === "unsuspend"}
            onClick={handleUnsuspend}
          >
            Unsuspend Doctor
          </Button>
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

      <Modal
        open={suspendModalOpen}
        onClose={() => setSuspendModalOpen(false)}
        title="Suspend Doctor"
      >
        <p className="text-sm text-gray-600 mb-4">
          Choose how long to suspend this doctor. They will be automatically unsuspended when the period ends.
        </p>
        <label className="block text-sm font-medium text-gray-700 mb-2">Suspension Duration</label>
        <select
          value={suspendDays}
          onChange={(e) => setSuspendDays(Number(e.target.value))}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal focus:border-transparent mb-2"
        >
          <option value={1}>1 day</option>
          <option value={3}>3 days</option>
          <option value={7}>7 days</option>
          <option value={14}>14 days</option>
          <option value={30}>30 days</option>
        </select>
        <p className="text-xs text-gray-400 mb-4">
          Suspension ends: {new Date(Date.now() + suspendDays * 86400000).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}
        </p>
        <label className="block text-sm font-medium text-gray-700 mb-2">Reason for Suspension</label>
        <textarea
          value={suspendReason}
          onChange={(e) => setSuspendReason(e.target.value)}
          placeholder="Reason for suspension..."
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal focus:border-transparent resize-none mb-4"
        />
        <div className="flex justify-end gap-3 mt-4">
          <Button variant="outline" onClick={() => setSuspendModalOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={loading === "suspend"}
            onClick={handleSuspend}
          >
            Confirm Suspension
          </Button>
        </div>
      </Modal>
    </>
  );
}
