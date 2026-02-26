"use client";

import { useState, useMemo, Fragment } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { specialtyLabel } from "@/lib/utils";
import {
  approveDoctor,
  rejectDoctor,
  suspendDoctor,
  unsuspendDoctor,
  banDoctor,
  unbanDoctor,
  warnDoctor,
} from "@/lib/actions";

interface Doctor {
  doctor_id: string;
  full_name: string;
  email: string;
  phone: string | null;
  specialty: string;
  license_number: string | null;
  is_verified: boolean;
  is_available: boolean;
  average_rating: number;
  total_ratings: number;
  rejection_reason: string | null;
  created_at: string;
  is_banned: boolean;
  profile_photo_url: string | null;
  license_document_url: string | null;
  certificates_url: string | null;
  bio: string | null;
  years_experience: number | null;
  country: string | null;
}

type SortKey = "status" | "rating";
type SortDir = "asc" | "desc";
type StatusFilter = "all" | "pending" | "active" | "suspended" | "banned" | "rejected";

interface Props {
  doctors: Doctor[];
}

export default function DoctorManagementView({ doctors }: Props) {
  const router = useRouter();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [error, setError] = useState("");
  const [sortDir, setSortDir] = useState<SortDir>("asc");
  const [loading, setLoading] = useState<string | null>(null);
  const [rejectModal, setRejectModal] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [warnModal, setWarnModal] = useState<string | null>(null);
  const [warnMessage, setWarnMessage] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);

  function getStatus(d: Doctor): string {
    if (d.is_banned) return "banned";
    if (d.rejection_reason && !d.is_verified) return "rejected";
    if (!d.is_verified) return "pending";
    if (!d.is_available) return "suspended";
    return "active";
  }

  const filtered = useMemo(() => {
    let list = doctors;

    // Search
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(
        (d) =>
          d.full_name.toLowerCase().includes(q) ||
          d.email.toLowerCase().includes(q) ||
          (d.license_number ?? "").toLowerCase().includes(q)
      );
    }

    // Status filter
    if (statusFilter !== "all") {
      list = list.filter((d) => getStatus(d) === statusFilter);
    }

    // Sort
    if (sortKey === "status") {
      const order: Record<string, number> = { pending: 0, active: 1, suspended: 2, banned: 3, rejected: 4 };
      list = [...list].sort((a, b) => {
        const diff = (order[getStatus(a)] ?? 5) - (order[getStatus(b)] ?? 5);
        return sortDir === "asc" ? diff : -diff;
      });
    } else if (sortKey === "rating") {
      list = [...list].sort((a, b) => {
        const diff = a.average_rating - b.average_rating;
        return sortDir === "asc" ? diff : -diff;
      });
    }

    return list;
  }, [doctors, search, statusFilter, sortKey, sortDir]);

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  }

  async function handleApprove(doctorId: string) {
    setLoading(doctorId);
    setError("");
    const result = await approveDoctor(doctorId);
    if (result.error) setError(result.error);
    setLoading(null);
    router.refresh();
  }

  async function handleReject(doctorId: string) {
    if (!rejectReason.trim()) return;
    setLoading(doctorId);
    setError("");
    const result = await rejectDoctor(doctorId, rejectReason.trim());
    if (result.error) {
      setError(result.error);
    } else {
      setRejectModal(null);
      setRejectReason("");
    }
    setLoading(null);
    router.refresh();
  }

  async function handleSuspend(doctorId: string) {
    setLoading(doctorId);
    setError("");
    const result = await suspendDoctor(doctorId);
    if (result.error) setError(result.error);
    setLoading(null);
    router.refresh();
  }

  async function handleBan(doctorId: string) {
    if (!confirm("Are you sure you want to ban this doctor? This will prevent them from logging in.")) return;
    setLoading(doctorId);
    setError("");
    const result = await banDoctor(doctorId);
    if (result.error) setError(result.error);
    setLoading(null);
    router.refresh();
  }

  async function handleUnsuspend(doctorId: string) {
    setLoading(doctorId);
    setError("");
    const result = await unsuspendDoctor(doctorId);
    if (result.error) setError(result.error);
    setLoading(null);
    router.refresh();
  }

  async function handleUnban(doctorId: string) {
    if (!confirm("Are you sure you want to unban this doctor? They will be able to log in again.")) return;
    setLoading(doctorId);
    setError("");
    const result = await unbanDoctor(doctorId);
    if (result.error) setError(result.error);
    setLoading(null);
    router.refresh();
  }

  async function handleWarn(doctorId: string) {
    if (!warnMessage.trim()) return;
    setLoading(doctorId);
    setError("");
    const result = await warnDoctor(doctorId, warnMessage.trim());
    if (result.error) {
      setError(result.error);
    } else {
      setWarnModal(null);
      setWarnMessage("");
    }
    setLoading(null);
    router.refresh();
  }

  const statusBadge: Record<string, { bg: string; text: string }> = {
    pending: { bg: "bg-amber-50", text: "text-amber-700" },
    active: { bg: "bg-green-50", text: "text-green-700" },
    suspended: { bg: "bg-orange-50", text: "text-orange-700" },
    banned: { bg: "bg-red-50", text: "text-red-700" },
    rejected: { bg: "bg-gray-100", text: "text-gray-600" },
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
          <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
          </svg>
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Doctor Management</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            View, search, and manage all doctors on the platform
          </p>
        </div>
      </div>

      {/* Search + Filter row */}
      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <div className="flex-1 relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="text"
            placeholder="Search by name, email, license..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
          className="px-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal min-w-[140px]"
        >
          <option value="all">All Status</option>
          <option value="pending">Pending</option>
          <option value="active">Active</option>
          <option value="suspended">Suspended</option>
          <option value="banned">Banned</option>
          <option value="rejected">Rejected</option>
        </select>
      </div>

      {/* Count */}
      <p className="text-sm text-gray-500 mb-3">{filtered.length} doctor{filtered.length !== 1 ? "s" : ""} found</p>

      {/* Error banner */}
      {error && (
        <div className="mb-3 px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700 flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError("")} className="text-red-500 hover:text-red-700 ml-2">&times;</button>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Doctor</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">License</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Specialty</th>
                <th
                  className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider cursor-pointer select-none"
                  onClick={() => toggleSort("status")}
                >
                  Status {sortKey === "status" ? (sortDir === "asc" ? "↑" : "↓") : "↕"}
                </th>
                <th
                  className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider cursor-pointer select-none"
                  onClick={() => toggleSort("rating")}
                >
                  Rating {sortKey === "rating" ? (sortDir === "asc" ? "↑" : "↓") : "↕"}
                </th>
                <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Reviews</th>
                <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map((d) => {
                const status = getStatus(d);
                const badge = statusBadge[status] ?? statusBadge.pending;
                const isLoading = loading === d.doctor_id;

                return (
                  <Fragment key={d.doctor_id}>
                  <tr className="hover:bg-gray-50/50 transition-colors">
                    {/* Doctor */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        {d.profile_photo_url ? (
                          <Image
                            src={d.profile_photo_url}
                            alt=""
                            width={36}
                            height={36}
                            className="w-9 h-9 rounded-full object-cover flex-shrink-0"
                            unoptimized
                          />
                        ) : (
                          <div className="w-9 h-9 rounded-full bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
                            <span className="text-xs font-bold text-brand-teal">
                              {d.full_name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                            </span>
                          </div>
                        )}
                        <div>
                          <p className="font-medium text-gray-900">{d.full_name}</p>
                          <p className="text-xs text-gray-400">{d.email}</p>
                        </div>
                      </div>
                    </td>

                    {/* License */}
                    <td className="px-5 py-4 text-gray-700 text-xs font-mono">
                      {d.license_number ?? "—"}
                    </td>

                    {/* Specialty */}
                    <td className="px-5 py-4 text-gray-700">
                      {specialtyLabel(d.specialty)}
                    </td>

                    {/* Status */}
                    <td className="px-5 py-4">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${badge.bg} ${badge.text}`}>
                        {status}
                      </span>
                    </td>

                    {/* Rating */}
                    <td className="px-5 py-4 text-gray-700">
                      <span className="inline-flex items-center gap-1">
                        <svg className="h-3.5 w-3.5 text-yellow-400 fill-yellow-400" viewBox="0 0 20 20">
                          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                        </svg>
                        {d.average_rating.toFixed(1)}
                      </span>
                    </td>

                    {/* Reviews */}
                    <td className="px-5 py-4 text-center text-gray-700">
                      {d.total_ratings}
                    </td>

                    {/* Actions */}
                    <td className="px-5 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => setExpandedId(expandedId === d.doctor_id ? null : d.doctor_id)}
                          className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                            expandedId === d.doctor_id
                              ? "bg-brand-teal text-white"
                              : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                          }`}
                        >
                          <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                            <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          </svg>
                          Credentials
                        </button>
                        {isLoading ? (
                          <span className="text-xs text-gray-400">Processing...</span>
                        ) : (status === "pending" || status === "rejected") ? (
                          <>
                            <button
                              onClick={() => handleApprove(d.doctor_id)}
                              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-green-500 text-white hover:bg-green-600 transition-colors"
                            >
                              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                              </svg>
                              Approve
                            </button>
                            <button
                              onClick={() => setRejectModal(d.doctor_id)}
                              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-red-500 text-white hover:bg-red-600 transition-colors"
                            >
                              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                              </svg>
                              {status === "rejected" ? "Reject Again" : "Reject"}
                            </button>
                          </>
                        ) : status === "active" ? (
                          <>
                            <button
                              onClick={() => setWarnModal(d.doctor_id)}
                              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                            >
                              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                              </svg>
                              Warn
                            </button>
                            <button
                              onClick={() => handleSuspend(d.doctor_id)}
                              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-orange-500 text-white hover:bg-orange-600 transition-colors"
                            >
                              Suspend
                            </button>
                            <button
                              onClick={() => handleBan(d.doctor_id)}
                              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-red-500 text-white hover:bg-red-600 transition-colors"
                            >
                              Ban
                            </button>
                          </>
                        ) : status === "suspended" ? (
                          <button
                            onClick={() => handleUnsuspend(d.doctor_id)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-green-500 text-white hover:bg-green-600 transition-colors"
                          >
                            Unsuspend
                          </button>
                        ) : status === "banned" ? (
                          <button
                            onClick={() => handleUnban(d.doctor_id)}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-orange-500 text-white hover:bg-orange-600 transition-colors"
                          >
                            Unban
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                  {expandedId === d.doctor_id && (
                    <tr>
                      <td colSpan={7} className="px-5 py-4 bg-gray-50/80">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {/* Doctor details */}
                          <div className="space-y-2 text-sm">
                            {d.phone && (
                              <div><span className="text-gray-400">Phone:</span> <span className="font-medium text-gray-900">{d.phone}</span></div>
                            )}
                            {d.country && (
                              <div><span className="text-gray-400">Country:</span> <span className="font-medium text-gray-900">{d.country}</span></div>
                            )}
                            {d.years_experience != null && (
                              <div><span className="text-gray-400">Experience:</span> <span className="font-medium text-gray-900">{d.years_experience} years</span></div>
                            )}
                            {d.bio && (
                              <div><span className="text-gray-400">Bio:</span> <span className="text-gray-700">{d.bio}</span></div>
                            )}
                            {d.rejection_reason && (
                              <div className="p-2 rounded-lg bg-red-50 border border-red-100">
                                <span className="text-xs font-medium text-red-700">Rejection Reason:</span>
                                <p className="text-sm text-red-600">{d.rejection_reason}</p>
                              </div>
                            )}
                          </div>

                          {/* Credentials */}
                          <div className="space-y-3">
                            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Uploaded Credentials</p>
                            {(d.profile_photo_url || d.license_document_url || d.certificates_url) ? (
                              <div className="space-y-2">
                                {d.profile_photo_url && (
                                  <CredentialPreview url={d.profile_photo_url} label="Profile Photo" />
                                )}
                                {d.license_document_url && (
                                  <CredentialPreview url={d.license_document_url} label="Medical License" />
                                )}
                                {d.certificates_url && (
                                  <CredentialPreview url={d.certificates_url} label="Certificates" />
                                )}
                              </div>
                            ) : (
                              <div className="p-3 rounded-lg bg-amber-50 border border-amber-100">
                                <p className="text-sm text-amber-700 font-medium">No credentials uploaded</p>
                              </div>
                            )}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                  </Fragment>
                );
              })}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-gray-400">
                    No doctors found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Reject Modal */}
      {rejectModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6 mx-4">
            <h3 className="text-lg font-bold text-gray-900 mb-1">Reject Doctor</h3>
            <p className="text-sm text-gray-500 mb-4">Provide a reason for rejection.</p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Reason for rejection..."
              rows={3}
              className="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-red-300 focus:border-red-400 mb-4"
            />
            <div className="flex justify-end gap-3">
              <button
                onClick={() => { setRejectModal(null); setRejectReason(""); }}
                className="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => handleReject(rejectModal)}
                disabled={!rejectReason.trim() || loading === rejectModal}
                className="px-4 py-2 bg-red-500 text-white text-sm font-semibold rounded-xl hover:bg-red-600 disabled:opacity-50 transition-colors"
              >
                {loading === rejectModal ? "Rejecting..." : "Reject"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Warn Modal */}
      {warnModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6 mx-4">
            <h3 className="text-lg font-bold text-gray-900 mb-1">Warn Doctor</h3>
            <p className="text-sm text-gray-500 mb-4">Send a warning message to this doctor.</p>
            <textarea
              value={warnMessage}
              onChange={(e) => setWarnMessage(e.target.value)}
              placeholder="Warning message..."
              rows={3}
              className="w-full px-4 py-3 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-300 focus:border-amber-400 mb-4"
            />
            <div className="flex justify-end gap-3">
              <button
                onClick={() => { setWarnModal(null); setWarnMessage(""); }}
                className="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => handleWarn(warnModal)}
                disabled={!warnMessage.trim() || loading === warnModal}
                className="px-4 py-2 bg-amber-500 text-white text-sm font-semibold rounded-xl hover:bg-amber-600 disabled:opacity-50 transition-colors"
              >
                {loading === warnModal ? "Sending..." : "Send Warning"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function CredentialPreview({ url, label }: { url: string; label: string }) {
  const lower = url.toLowerCase();
  const isPdf = lower.endsWith(".pdf") || lower.includes("pdf");
  const isImage =
    lower.endsWith(".jpg") ||
    lower.endsWith(".jpeg") ||
    lower.endsWith(".png") ||
    lower.endsWith(".webp") ||
    lower.includes("profile-photo") ||
    lower.includes("image");

  return (
    <div className="rounded-lg border border-gray-200 overflow-hidden">
      <div className="flex items-center justify-between px-3 py-1.5 bg-gray-50 border-b border-gray-200">
        <p className="text-xs font-medium text-gray-700">{label}</p>
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-brand-teal hover:underline"
        >
          Open full
        </a>
      </div>
      {isPdf ? (
        <iframe
          src={url}
          className="w-full h-40 bg-white"
          title={label}
        />
      ) : isImage ? (
        <Image
          src={url}
          alt={label}
          width={400}
          height={160}
          className="w-full h-40 object-contain bg-white"
          unoptimized
        />
      ) : (
        <div className="px-3 py-2">
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm text-brand-teal hover:underline"
          >
            Download {label}
          </a>
        </div>
      )}
    </div>
  );
}
