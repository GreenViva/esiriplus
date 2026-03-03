"use client";

import { useState } from "react";
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
  total_revenue: number;
  doctor_commission: number;
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
}

export default function PaymentsView({ payments, doctorRevenue }: Props) {
  const [search, setSearch] = useState("");
  const [tab, setTab] = useState<"patients" | "doctors">("patients");

  // Derive groups
  const completedPayments = payments.filter((p) => p.status === "completed" || p.status === "paid");
  const pendingPayments = payments.filter((p) => p.status === "pending");
  const failedPayments = payments.filter((p) => p.status === "failed" || p.status === "cancelled");
  const totalRevenue = completedPayments.reduce((sum, p) => sum + (p.amount ?? 0), 0);

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

  // Apply search to doctor revenue
  let filteredDoctors = doctorRevenue;
  if (search.trim() && tab === "doctors") {
    const q = search.toLowerCase();
    filteredDoctors = filteredDoctors.filter((d) =>
      d.doctor_name.toLowerCase().includes(q) ||
      d.specialty.toLowerCase().includes(q) ||
      String(d.total_revenue).includes(q)
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
          onClick={() => window.location.reload()}
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
          value={completedPayments.length}
          iconBg="bg-blue-50"
          icon={
            <svg className="h-5 w-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Pending"
          value={pendingPayments.length}
          iconBg="bg-amber-50"
          icon={
            <svg className="h-5 w-5 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Failed"
          value={failedPayments.length}
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
          By Patients
        </button>
        <button
          onClick={() => { setTab("doctors"); setSearch(""); }}
          className={`px-4 py-2 text-sm font-medium rounded-full transition-colors ${
            tab === "doctors"
              ? "bg-teal-600 text-white"
              : "bg-white text-gray-600 border border-gray-200 hover:bg-gray-50"
          }`}
        >
          By Doctor
        </button>
      </div>

      {/* Tab content */}
      {tab === "patients" ? (
        <>
          {/* Payments table */}
          {filteredPayments.length > 0 ? (
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 bg-gray-50/50">
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Date</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Patient</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Service</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Phone</th>
                      <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Amount</th>
                      <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Receipt</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {filteredPayments.map((p) => (
                      <tr key={p.id} className="hover:bg-gray-50/50 transition-colors">
                        <td className="px-5 py-4 text-gray-900 whitespace-nowrap text-xs">
                          {formatShortDate(p.created_at)}
                        </td>
                        <td className="px-5 py-4 text-gray-700 font-mono text-xs">
                          {p.patient_id}
                        </td>
                        <td className="px-5 py-4">
                          <ServiceBadge type={p.service_type} />
                        </td>
                        <td className="px-5 py-4 text-gray-500 text-xs">
                          {p.phone}
                        </td>
                        <td className="px-5 py-4 text-gray-900 font-semibold text-right whitespace-nowrap">
                          TSh {p.amount.toLocaleString("en-US")}
                        </td>
                        <td className="px-5 py-4 text-center">
                          <StatusBadge status={p.status} />
                        </td>
                        <td className="px-5 py-4 text-gray-400 font-mono text-xs max-w-[200px] truncate">
                          {p.receipt}
                        </td>
                      </tr>
                    ))}
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
          <p className="text-xs text-gray-400 mt-3">
            {filteredPayments.length} transaction{filteredPayments.length !== 1 ? "s" : ""} found
          </p>
        </>
      ) : (
        <>
          {/* Doctor revenue table */}
          {filteredDoctors.length > 0 ? (
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 bg-gray-50/50">
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Doctor</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Specialty</th>
                      <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Consultations</th>
                      <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Total Revenue</th>
                      <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Doctor Commission</th>
                      <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Platform Profit</th>
                      <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Active</th>
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
                          TSh {d.total_revenue.toLocaleString("en-US")}
                        </td>
                        <td className="px-5 py-4 text-amber-600 font-medium text-right whitespace-nowrap">
                          TSh {d.doctor_commission.toLocaleString("en-US")}
                        </td>
                        <td className="px-5 py-4 text-emerald-600 font-medium text-right whitespace-nowrap">
                          TSh {(d.total_revenue - d.doctor_commission).toLocaleString("en-US")}
                        </td>
                        <td className="px-5 py-4 text-gray-500 text-xs whitespace-nowrap">
                          {formatShortDate(d.last_active)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  {/* Totals row */}
                  <tfoot>
                    <tr className="border-t-2 border-gray-200 bg-gray-50/80">
                      <td className="px-5 py-3 text-gray-900 font-bold text-sm" colSpan={2}>
                        Total ({filteredDoctors.length} doctor{filteredDoctors.length !== 1 ? "s" : ""})
                      </td>
                      <td className="px-5 py-3 text-gray-900 font-bold text-center">
                        {filteredDoctors.reduce((sum, d) => sum + d.consultation_count, 0)}
                      </td>
                      <td className="px-5 py-3 text-gray-900 font-bold text-right whitespace-nowrap">
                        TSh {filteredDoctors.reduce((sum, d) => sum + d.total_revenue, 0).toLocaleString("en-US")}
                      </td>
                      <td className="px-5 py-3 text-amber-600 font-bold text-right whitespace-nowrap">
                        TSh {filteredDoctors.reduce((sum, d) => sum + d.doctor_commission, 0).toLocaleString("en-US")}
                      </td>
                      <td className="px-5 py-3 text-emerald-600 font-bold text-right whitespace-nowrap">
                        TSh {filteredDoctors.reduce((sum, d) => sum + (d.total_revenue - d.doctor_commission), 0).toLocaleString("en-US")}
                      </td>
                      <td className="px-5 py-3"></td>
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
    </>
  );
}
