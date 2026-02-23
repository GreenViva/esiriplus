"use client";

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import StatCard from "@/components/StatCard";
import { formatCurrency, formatDateTime, serviceTypeLabel } from "@/lib/utils";

export interface PaymentRow {
  payment_id: string;
  amount: number;
  currency: string;
  payment_type: string | null;
  payment_method: string | null;
  status: string;
  created_at: string;
  service_access_payments: { service_type: string } | null;
}

function serviceLabel(slug: string | null | undefined): string {
  if (!slug) return "\u2014";
  return serviceTypeLabel(slug);
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { bg: string; text: string; label: string }> = {
    completed: { bg: "bg-green-50", text: "text-green-700", label: "Completed" },
    pending: { bg: "bg-amber-50", text: "text-amber-700", label: "Pending" },
    failed: { bg: "bg-red-50", text: "text-red-700", label: "Failed" },
    cancelled: { bg: "bg-gray-100", text: "text-gray-600", label: "Cancelled" },
    refunded: { bg: "bg-purple-50", text: "text-purple-700", label: "Refunded" },
  };
  const s = map[status] ?? { bg: "bg-gray-100", text: "text-gray-600", label: status };
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${s.bg} ${s.text}`}>
      {s.label}
    </span>
  );
}

interface Props {
  initialPayments: PaymentRow[];
  currentPage: number;
  totalPages: number;
}

export default function PaymentsView({ initialPayments, currentPage, totalPages }: Props) {
  const searchParams = useSearchParams();
  const filter = searchParams.get("filter") ?? "";
  const allPayments = initialPayments;
  const [search, setSearch] = useState("");

  // Derive groups
  const completedPayments = allPayments.filter((p) => p.status === "completed");
  const pendingPayments = allPayments.filter((p) => p.status === "pending");
  const failedPayments = allPayments.filter((p) => p.status === "failed" || p.status === "cancelled");
  const totalRevenue = completedPayments.reduce((sum, p) => sum + (p.amount ?? 0), 0);

  // Apply tab filter
  let payments = allPayments;
  if (filter === "completed") {
    payments = completedPayments;
  } else if (filter === "pending") {
    payments = pendingPayments;
  } else if (filter === "failed") {
    payments = failedPayments;
  }

  // Apply search
  if (search.trim()) {
    const q = search.toLowerCase();
    payments = payments.filter((p) => {
      const sap = p.service_access_payments as unknown as { service_type?: string } | null;
      const svc = serviceLabel(sap?.service_type ?? p.payment_type).toLowerCase();
      const method = (p.payment_method ?? "").toLowerCase().replace(/_/g, " ");
      const amount = `${p.amount ?? 0}`;
      const currency = (p.currency ?? "tzs").toLowerCase();
      const status = p.status.toLowerCase();
      const date = formatDateTime(p.created_at).toLowerCase();
      return (
        svc.includes(q) ||
        method.includes(q) ||
        amount.includes(q) ||
        currency.includes(q) ||
        status.includes(q) ||
        date.includes(q)
      );
    });
  }

  return (
    <>
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
          iconBg="bg-green-50"
          icon={
            <svg className="h-5 w-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
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
          label="Failed / Cancelled"
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
          placeholder="Search by service, method, amount, status, or date..."
          className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
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

      {/* Tabs */}
      <div className="flex rounded-xl border border-gray-200 bg-gray-50 mb-6 overflow-hidden">
        <TabLink href="/dashboard/payments" active={!filter} label="All" count={allPayments.length} />
        <TabLink href="/dashboard/payments?filter=completed" active={filter === "completed"} label="Completed" count={completedPayments.length} />
        <TabLink href="/dashboard/payments?filter=pending" active={filter === "pending"} label="Pending" count={pendingPayments.length} />
        <TabLink href="/dashboard/payments?filter=failed" active={filter === "failed"} label="Failed" count={failedPayments.length} />
      </div>

      {/* Payments table */}
      {payments.length > 0 ? (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50/50">
                  <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Date</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Service</th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Method</th>
                  <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Amount</th>
                  <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {payments.map((p) => {
                  const sap = p.service_access_payments as unknown as { service_type?: string } | null;
                  const svc = sap?.service_type ?? p.payment_type;
                  return (
                    <tr key={p.payment_id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4 text-gray-900 whitespace-nowrap">{formatDateTime(p.created_at)}</td>
                      <td className="px-5 py-4 text-gray-700">{serviceLabel(svc)}</td>
                      <td className="px-5 py-4 text-gray-500 capitalize">{(p.payment_method ?? "—").replace(/_/g, " ")}</td>
                      <td className="px-5 py-4 text-gray-900 font-semibold text-right whitespace-nowrap">
                        {(p.amount ?? 0).toLocaleString()} {p.currency ?? "TZS"}
                      </td>
                      <td className="px-5 py-4 text-center"><StatusBadge status={p.status} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-12 text-center">
          <p className="text-gray-400">
            {search.trim() ? `No payments matching "${search}"` : "No payments found."}
          </p>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-gray-500">
            Page {currentPage} of {totalPages}
          </p>
          <div className="flex gap-2">
            {currentPage > 1 && (
              <Link
                href={`/dashboard/payments?page=${currentPage - 1}${filter ? `&filter=${filter}` : ""}`}
                className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Previous
              </Link>
            )}
            {currentPage < totalPages && (
              <Link
                href={`/dashboard/payments?page=${currentPage + 1}${filter ? `&filter=${filter}` : ""}`}
                className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Next
              </Link>
            )}
          </div>
        </div>
      )}
    </>
  );
}

function TabLink({ href, active, label, count }: { href: string; active: boolean; label: string; count: number }) {
  return (
    <Link
      href={href}
      className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium transition-colors ${
        active
          ? "bg-white text-gray-900 shadow-sm border border-gray-200 rounded-xl -m-px"
          : "text-gray-500 hover:text-gray-700"
      }`}
    >
      {label}
      <span
        className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-xs font-semibold ${
          active ? "bg-brand-teal/10 text-brand-teal" : "bg-gray-200 text-gray-500"
        }`}
      >
        {count}
      </span>
    </Link>
  );
}
