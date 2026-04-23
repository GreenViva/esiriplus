"use client";

import React, { useEffect, useState } from "react";
import StatCard from "@/components/StatCard";
import { formatCurrency, serviceTypeLabel } from "@/lib/utils";

export interface PaymentRow {
  id: string;
  patient_id: string;
  service_type: string;
  phone: string;
  amount: number;
  currency: string;
  status: string;
  receipt: string;
  created_at: string;
}

export interface DoctorRevenueRow {
  doctor_id: string;
  doctor_name: string;
  specialty: string;
  profile_photo_url: string | null;
  phone: string | null;
  /** Sum of earnings the app still owes this doctor (status != "paid"). */
  amount_owed: number;
  consultation_count: number;
  last_active: string;
}

function formatShortDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { bg: string; text: string; icon: string; label: string }> = {
    completed: { bg: "bg-green-50", text: "text-green-700", icon: "\u2714", label: "Completed" },
    paid: { bg: "bg-green-50", text: "text-green-700", icon: "\u2714", label: "Completed" },
    pending: { bg: "bg-amber-50", text: "text-amber-700", icon: "\u23F3", label: "Pending" },
    failed: { bg: "bg-red-50", text: "text-red-700", icon: "\u2716", label: "Failed" },
    cancelled: { bg: "bg-gray-100", text: "text-gray-600", icon: "\u2716", label: "Cancelled" },
  };
  const s = map[status] ?? { bg: "bg-gray-100", text: "text-gray-600", icon: "", label: status };
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${s.bg} ${s.text}`}>
      {s.icon && <span>{s.icon}</span>}
      {s.label}
    </span>
  );
}

function ServiceBadge({ type }: { type: string }) {
  return (
    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700">
      {serviceTypeLabel(type)}
    </span>
  );
}

interface Props {
  payments: PaymentRow[];
  doctorRevenue: DoctorRevenueRow[];
  /** Gross revenue from completed consultations' consultation_fee. Computed
   *  server-side independent of the payment record tables so it always
   *  reflects real platform activity (including free-offer consultations). */
  totalRevenue: number;
  /** Counts of consultations by status. Drives the Completed / Pending /
   *  Failed cards so they match the platform activity metrics elsewhere. */
  consultationCounts: { completed: number; pending: number; failed: number };
  onRefresh?: () => void;
}

export default function PaymentsView({
  payments,
  doctorRevenue,
  totalRevenue,
  consultationCounts,
  onRefresh,
}: Props) {
  const [search, setSearch] = useState("");
  const [tab, setTab] = useState<"patients" | "doctors">("patients");
  const [expandedPatient, setExpandedPatient] = useState<string | null>(null);
  const [patientPage, setPatientPage] = useState(1);
  const PATIENTS_PER_PAGE = 20;
  const [payWithDoctor, setPayWithDoctor] = useState<DoctorRevenueRow | null>(null);


  // Apply search to patient payments
  let filteredPayments = payments;
  if (search.trim() && tab === "patients") {
    const q = search.toLowerCase();
    filteredPayments = filteredPayments.filter((p) =>
      p.patient_id.toLowerCase().includes(q) ||
      serviceTypeLabel(p.service_type).toLowerCase().includes(q) ||
      p.phone.toLowerCase().includes(q) ||
      p.receipt.toLowerCase().includes(q) ||
      String(p.amount).includes(q) ||
      p.status.toLowerCase().includes(q)
    );
  }

  // Group the filtered patient payments by patient_id so each patient
  // appears once with their payment history nested inside.
  const patientGroups = (() => {
    const map = new Map<
      string,
      {
        patient_id: string;
        total: number;
        count: number;
        lastDate: string;
        payments: PaymentRow[];
      }
    >();
    for (const p of filteredPayments) {
      const bucket = map.get(p.patient_id);
      if (bucket) {
        bucket.total += p.amount ?? 0;
        bucket.count += 1;
        if (p.created_at > bucket.lastDate) bucket.lastDate = p.created_at;
        bucket.payments.push(p);
      } else {
        map.set(p.patient_id, {
          patient_id: p.patient_id,
          total: p.amount ?? 0,
          count: 1,
          lastDate: p.created_at,
          payments: [p],
        });
      }
    }
    const arr = Array.from(map.values());
    for (const b of arr) {
      b.payments.sort((a, b) => b.created_at.localeCompare(a.created_at));
    }
    arr.sort((a, b) => b.lastDate.localeCompare(a.lastDate));
    return arr;
  })();

  // Reset to page 1 whenever the filter changes so the view doesn't land
  // on an empty page after the patient-count shrinks.
  useEffect(() => {
    setPatientPage(1);
  }, [search, tab]);

  const totalPatientPages = Math.max(1, Math.ceil(patientGroups.length / PATIENTS_PER_PAGE));
  const clampedPatientPage = Math.min(patientPage, totalPatientPages);
  const pagedPatientGroups = patientGroups.slice(
    (clampedPatientPage - 1) * PATIENTS_PER_PAGE,
    clampedPatientPage * PATIENTS_PER_PAGE,
  );

  // Apply search to doctor revenue
  let filteredDoctors = doctorRevenue;
  if (search.trim() && tab === "doctors") {
    const q = search.toLowerCase();
    filteredDoctors = filteredDoctors.filter((d) =>
      d.doctor_name.toLowerCase().includes(q) ||
      d.specialty.toLowerCase().includes(q) ||
      String(d.amount_owed).includes(q)
    );
  }

  return (
    <>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Payment Management</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            View and manage all payment transactions
          </p>
        </div>
        <button
          onClick={() => onRefresh?.()}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" />
          </svg>
          Refresh
        </button>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard
          label="Total Revenue"
          value={formatCurrency(totalRevenue)}
          iconBg="bg-emerald-50"
          icon={
            <svg className="h-5 w-5 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Completed"
          value={consultationCounts.completed}
          iconBg="bg-blue-50"
          icon={
            <svg className="h-5 w-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Pending"
          value={payments.filter((p) => p.status === "pending").length}
          iconBg="bg-amber-50"
          icon={
            <svg className="h-5 w-5 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Failed"
          value={payments.filter((p) => p.status === "failed" || p.status === "cancelled").length}
          iconBg="bg-red-50"
          icon={
            <svg className="h-5 w-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
          }
        />
      </div>

      {/* Search bar */}
      <div className="relative mb-4">
        <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 pointer-events-none" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
        </svg>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={tab === "patients" ? "Search by phone, receipt, patient..." : "Search by doctor name, specialty..."}
          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-colors"
        />
        {search && (
          <button
            onClick={() => setSearch("")}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>

      {/* Tab buttons: By Patients / By Doctor */}
      <div className="flex gap-2 mb-6">
        <button
          onClick={() => { setTab("patients"); setSearch(""); }}
          className={`px-4 py-2 text-sm font-medium rounded-full transition-colors ${
            tab === "patients"
              ? "bg-teal-600 text-white"
              : "bg-white text-gray-600 border border-gray-200 hover:bg-gray-50"
          }`}
        >
          Paid by Patient
        </button>
        <button
          onClick={() => { setTab("doctors"); setSearch(""); }}
          className={`px-4 py-2 text-sm font-medium rounded-full transition-colors ${
            tab === "doctors"
              ? "bg-teal-600 text-white"
              : "bg-white text-gray-600 border border-gray-200 hover:bg-gray-50"
          }`}
        >
          To Doctor
        </button>
      </div>

      {/* Tab content */}
      {tab === "patients" ? (
        <>
          {/* Grouped-by-patient table. One row per patient; click to expand
              and see their individual transactions. */}
          {patientGroups.length > 0 ? (
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 bg-gray-50/50">
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider w-8" />
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Patient</th>
                      <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Payments</th>
                      <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Total Paid</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Payment</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {pagedPatientGroups.map((g) => {
                      const isOpen = expandedPatient === g.patient_id;
                      return (
                        <React.Fragment key={g.patient_id}>
                          <tr
                            onClick={() => setExpandedPatient(isOpen ? null : g.patient_id)}
                            className="hover:bg-gray-50/50 transition-colors cursor-pointer"
                          >
                            <td className="px-5 py-4 text-gray-400">
                              <svg
                                className={`h-4 w-4 transition-transform ${isOpen ? "rotate-90" : ""}`}
                                fill="none"
                                viewBox="0 0 24 24"
                                stroke="currentColor"
                                strokeWidth={2}
                              >
                                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                              </svg>
                            </td>
                            <td className="px-5 py-4 text-gray-700 font-mono text-xs">
                              {g.patient_id}
                            </td>
                            <td className="px-5 py-4 text-center text-gray-700 text-sm">
                              {g.count}
                            </td>
                            <td className="px-5 py-4 text-gray-900 font-semibold text-right whitespace-nowrap">
                              TSh {g.total.toLocaleString("en-US")}
                            </td>
                            <td className="px-5 py-4 text-gray-500 whitespace-nowrap text-xs">
                              {formatShortDate(g.lastDate)}
                            </td>
                          </tr>
                          {isOpen && (
                            <tr>
                              <td colSpan={5} className="px-5 py-3 bg-gray-50/40">
                                <table className="w-full text-sm">
                                  <thead>
                                    <tr className="text-xs text-gray-400">
                                      <th className="text-left font-medium pb-2">Date</th>
                                      <th className="text-left font-medium pb-2">Service</th>
                                      <th className="text-left font-medium pb-2">Phone</th>
                                      <th className="text-right font-medium pb-2">Amount</th>
                                      <th className="text-center font-medium pb-2">Status</th>
                                      <th className="text-left font-medium pb-2">Receipt</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {g.payments.map((p) => (
                                      <tr key={p.id} className="text-xs">
                                        <td className="py-2 text-gray-700 whitespace-nowrap">
                                          {formatShortDate(p.created_at)}
                                        </td>
                                        <td className="py-2">
                                          <ServiceBadge type={p.service_type} />
                                        </td>
                                        <td className="py-2 text-gray-500">{p.phone}</td>
                                        <td className="py-2 text-gray-900 font-semibold text-right whitespace-nowrap">
                                          TSh {p.amount.toLocaleString("en-US")}
                                        </td>
                                        <td className="py-2 text-center">
                                          <StatusBadge status={p.status} />
                                        </td>
                                        <td className="py-2 text-gray-400 font-mono max-w-[200px] truncate">
                                          {p.receipt}
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </td>
                            </tr>
                          )}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          ) : (
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-12 text-center">
              <p className="text-gray-400">
                {search.trim() ? `No payments matching "${search}"` : "No payment transactions found."}
              </p>
            </div>
          )}
          {/* Footer + pagination */}
          <div className="flex items-center justify-between mt-3">
            <p className="text-xs text-gray-400">
              {patientGroups.length} patient{patientGroups.length !== 1 ? "s" : ""} · {filteredPayments.length} transaction{filteredPayments.length !== 1 ? "s" : ""}
              {totalPatientPages > 1 && (
                <> · Page {clampedPatientPage} of {totalPatientPages}</>
              )}
            </p>
            {totalPatientPages > 1 && (
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPatientPage((p) => Math.max(1, p - 1))}
                  disabled={clampedPatientPage <= 1}
                  className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Previous
                </button>
                <button
                  type="button"
                  onClick={() => setPatientPage((p) => Math.min(totalPatientPages, p + 1))}
                  disabled={clampedPatientPage >= totalPatientPages}
                  className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        </>
      ) : (
        <>
          {/* Amounts owed to each doctor (unpaid earnings).
              Click Pay With to settle a doctor's balance via mobile money. */}
          {filteredDoctors.length > 0 ? (
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 bg-gray-50/50">
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Doctor</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Specialty</th>
                      <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Consultations</th>
                      <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Amount Owed</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Active</th>
                      <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Pay</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {filteredDoctors.map((d) => (
                      <tr key={d.doctor_id} className="hover:bg-gray-50/50 transition-colors">
                        <td className="px-5 py-4">
                          <div className="flex items-center gap-3">
                            <div className="h-8 w-8 rounded-full bg-teal-100 flex items-center justify-center text-teal-700 font-semibold text-xs shrink-0">
                              {d.doctor_name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                            </div>
                            <span className="text-gray-900 font-medium text-sm">{d.doctor_name}</span>
                          </div>
                        </td>
                        <td className="px-5 py-4">
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700">
                            {d.specialty}
                          </span>
                        </td>
                        <td className="px-5 py-4 text-gray-900 font-semibold text-center">
                          {d.consultation_count}
                        </td>
                        <td className="px-5 py-4 text-gray-900 font-semibold text-right whitespace-nowrap">
                          TSh {d.amount_owed.toLocaleString("en-US")}
                        </td>
                        <td className="px-5 py-4 text-gray-500 text-xs whitespace-nowrap">
                          {formatShortDate(d.last_active)}
                        </td>
                        <td className="px-5 py-4 text-center">
                          <button
                            type="button"
                            onClick={() => setPayWithDoctor(d)}
                            disabled={d.amount_owed <= 0}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-teal-600 text-white hover:bg-teal-700 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
                          >
                            Pay With
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t-2 border-gray-200 bg-gray-50/80">
                      <td className="px-5 py-3 text-gray-900 font-bold text-sm" colSpan={2}>
                        Total ({filteredDoctors.length} doctor{filteredDoctors.length !== 1 ? "s" : ""})
                      </td>
                      <td className="px-5 py-3 text-gray-900 font-bold text-center">
                        {filteredDoctors.reduce((sum, d) => sum + d.consultation_count, 0)}
                      </td>
                      <td className="px-5 py-3 text-gray-900 font-bold text-right whitespace-nowrap">
                        TSh {filteredDoctors.reduce((sum, d) => sum + d.amount_owed, 0).toLocaleString("en-US")}
                      </td>
                      <td className="px-5 py-3" colSpan={2}></td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          ) : (
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-12 text-center">
              <p className="text-gray-400">
                {search.trim() ? `No doctors matching "${search}"` : "No doctor revenue data found."}
              </p>
            </div>
          )}
          <p className="text-xs text-gray-400 mt-3">
            {filteredDoctors.length} doctor{filteredDoctors.length !== 1 ? "s" : ""} found
          </p>
        </>
      )}

      {payWithDoctor && (
        <PayDoctorModal
          doctor={payWithDoctor}
          onClose={() => setPayWithDoctor(null)}
          onPaid={() => {
            setPayWithDoctor(null);
            onRefresh?.();
          }}
        />
      )}
    </>
  );
}

/* ── Pay doctor modal ─────────────────────────────────
 * Placeholder B2C payout UI. Admin picks a mobile-money provider, confirms
 * the doctor's phone and the amount (prefilled to the total owed), and
 * triggers a payout. Backend wiring is a follow-up — the UI validates input
 * and surfaces the intent.
 */
function PayDoctorModal({
  doctor,
  onClose,
  onPaid,
}: {
  doctor: DoctorRevenueRow;
  onClose: () => void;
  onPaid: () => void;
}) {
  const [provider, setProvider] = useState<"mpesa" | "tigo" | "airtel">("mpesa");
  const [phone, setPhone] = useState(doctor.phone ?? "");
  const [amount, setAmount] = useState(doctor.amount_owed);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setError(null);
    if (!phone.trim()) return setError("Doctor phone is required.");
    if (amount <= 0) return setError("Amount must be greater than zero.");
    if (amount > doctor.amount_owed) return setError("Cannot pay more than the amount owed.");

    setSubmitting(true);
    try {
      // TODO: wire to a pay-doctor edge function (B2C M-Pesa/Tigo/Airtel).
      // For now, simulate the request so the flow is testable end-to-end.
      await new Promise((r) => setTimeout(r, 800));
      onPaid();
    } catch (e) {
      setError((e as Error).message ?? "Payout failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between px-5 py-4 border-b border-gray-100">
          <div>
            <h2 className="text-lg font-bold text-gray-900">Pay {doctor.doctor_name}</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              Settle the app's balance to this doctor via mobile money
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="px-5 py-4 space-y-4">
          <div>
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Owed</p>
            <p className="text-xl font-bold text-gray-900">TSh {doctor.amount_owed.toLocaleString("en-US")}</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Pay with</label>
            <div className="grid grid-cols-3 gap-2">
              {(["mpesa", "tigo", "airtel"] as const).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setProvider(p)}
                  className={`px-3 py-2 rounded-lg text-sm font-medium border transition-colors ${
                    provider === p
                      ? "bg-teal-600 text-white border-teal-600"
                      : "bg-white text-gray-700 border-gray-200 hover:border-teal-600"
                  }`}
                >
                  {p === "mpesa" ? "M-Pesa" : p === "tigo" ? "Tigo Pesa" : "Airtel Money"}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Phone</label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="255..."
              className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-600/30 focus:border-teal-600"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">Amount (TSh)</label>
            <input
              type="number"
              value={amount}
              onChange={(e) => setAmount(Number(e.target.value) || 0)}
              min={0}
              max={doctor.amount_owed}
              className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-600/30 focus:border-teal-600"
            />
          </div>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
          )}
        </div>

        <div className="flex justify-end gap-3 px-5 py-4 bg-gray-50 border-t border-gray-100">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg border border-gray-200 text-sm font-medium text-gray-700 hover:bg-white"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="px-4 py-2 rounded-lg bg-teal-600 text-white text-sm font-semibold hover:bg-teal-700 disabled:opacity-50"
          >
            {submitting ? "Paying..." : "Pay"}
          </button>
        </div>
      </div>
    </div>
  );
}
