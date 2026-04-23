"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import { formatCurrency } from "@/lib/utils";
import RoleGuard from "@/components/RoleGuard";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import StatCard from "@/components/StatCard";

interface PaymentSummary {
  totalRevenue: number;
  completedCount: number;
  pendingCount: number;
  failedCount: number;
  completedAmount: number;
  pendingAmount: number;
  failedAmount: number;
  doctorCommissions: number;
  platformProfit: number;
}

interface DailyRevenue {
  date: string;
  revenue: number;
  count: number;
}

interface DuplicateCandidate {
  patient_session_id: string;
  amount: number;
  count: number;
  created_at: string;
}

export default function FinancialIntegrityPage() {
  const [summary, setSummary] = useState<PaymentSummary | null>(null);
  const [dailyRevenue, setDailyRevenue] = useState<DailyRevenue[]>([]);
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();
    const thirtyDaysAgo = new Date(Date.now() - 30 * 86400_000).toISOString();

    Promise.all([
      // Gateway payments — used for pending/failed reconciliation and
      // duplicate detection, NOT for revenue totals (free-offer and some
      // flows don't write a payments row).
      supabase
        .from("payments")
        .select("payment_id, amount, status, created_at, patient_session_id")
        .order("created_at", { ascending: false })
        .limit(500),
      // Service access payments
      supabase
        .from("service_access_payments")
        .select("payment_id, amount, status, created_at, patient_session_id")
        .order("created_at", { ascending: false })
        .limit(500),
      // Doctor earnings — already post-split by the fn_auto_create_doctor_earning
      // trigger, so summing these gives what the platform has accrued to doctors.
      supabase
        .from("doctor_earnings")
        .select("earning_id, amount, status, created_at")
        .limit(5000),
      // Consultations — source of truth for Total Revenue and Daily Revenue
      // (matches the admin dashboard and payments page). A consultation that
      // hasn't failed and carries a fee > 0 counts as gross platform revenue.
      supabase
        .from("consultations")
        .select("consultation_id, consultation_fee, status, created_at")
        .gt("consultation_fee", 0)
        .limit(5000),
    ]).then(([paymentsRes, servicePaymentsRes, earningsRes, consultationsRes]) => {
      const allPayments = [
        ...(paymentsRes.data ?? []),
        ...(servicePaymentsRes.data ?? []),
      ];

      // Gateway-level reconciliation counts (payment-record status).
      const completed = allPayments.filter(
        (p) => p.status === "completed" || p.status === "paid"
      );
      const pending = allPayments.filter((p) => p.status === "pending");
      const failed = allPayments.filter(
        (p) => p.status === "failed" || p.status === "cancelled"
      );
      const completedAmount = completed.reduce((s, p) => s + (p.amount ?? 0), 0);
      const pendingAmount = pending.reduce((s, p) => s + (p.amount ?? 0), 0);
      const failedAmount = failed.reduce((s, p) => s + (p.amount ?? 0), 0);

      // Total Revenue = sum of consultation_fee across non-failed consultations.
      // Matches the definition used by the dashboard and payments pages.
      const failedConsultationStatuses = new Set([
        "cancelled", "canceled", "timeout", "expired", "rejected", "failed",
      ]);
      const consultations = (consultationsRes.data ?? []) as Array<{
        consultation_id: string;
        consultation_fee: number | null;
        status: string | null;
        created_at: string;
      }>;
      const revenueConsultations = consultations.filter(
        (c) => !failedConsultationStatuses.has((c.status ?? "").toLowerCase())
      );
      const totalRevenue = revenueConsultations.reduce(
        (s, c) => s + (c.consultation_fee ?? 0),
        0
      );
      const doctorCommissions = (earningsRes.data ?? []).reduce(
        (s, e) => s + (e.amount ?? 0),
        0
      );

      setSummary({
        totalRevenue,
        completedCount: completed.length,
        pendingCount: pending.length,
        failedCount: failed.length,
        completedAmount,
        pendingAmount,
        failedAmount,
        doctorCommissions,
        platformProfit: totalRevenue - doctorCommissions,
      });

      // Daily revenue (last 30 days) — from consultations, same source as
      // Total Revenue so the two figures reconcile.
      const dailyMap = new Map<string, { revenue: number; count: number }>();
      for (const c of revenueConsultations) {
        if (c.created_at < thirtyDaysAgo) continue;
        const day = c.created_at.slice(0, 10);
        const existing = dailyMap.get(day);
        if (existing) {
          existing.revenue += c.consultation_fee ?? 0;
          existing.count += 1;
        } else {
          dailyMap.set(day, { revenue: c.consultation_fee ?? 0, count: 1 });
        }
      }
      const daily = Array.from(dailyMap.entries())
        .map(([date, data]) => ({ date, ...data }))
        .sort((a, b) => a.date.localeCompare(b.date));
      setDailyRevenue(daily);

      // Duplicate detection: same session + amount appearing more than once
      const dupMap = new Map<
        string,
        { amount: number; count: number; created_at: string }
      >();
      for (const p of allPayments) {
        if (!p.patient_session_id) continue;
        const key = `${p.patient_session_id}:${p.amount}`;
        const existing = dupMap.get(key);
        if (existing) {
          existing.count += 1;
        } else {
          dupMap.set(key, {
            amount: p.amount ?? 0,
            count: 1,
            created_at: p.created_at,
          });
        }
      }
      const dups = Array.from(dupMap.entries())
        .filter(([, d]) => d.count > 1)
        .map(([key, d]) => ({
          patient_session_id: key.split(":")[0],
          ...d,
        }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 20);
      setDuplicates(dups);

      setLoading(false);
    });
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
      </div>
    );
  }

  const s = summary!;

  return (
    <RoleGuard allowed={["admin", "audit"]}>
      <div>
        <RealtimeRefresh
          tables={["payments", "service_access_payments", "doctor_earnings", "consultations"]}
          channelName="financial-audit-realtime"
          onUpdate={fetchData}
        />

        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">
            Financial Integrity
          </h1>
          <p className="text-sm text-gray-400 mt-0.5">
            Payment reconciliation, revenue breakdown, and duplicate detection
          </p>
        </div>

        {/* Summary Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <StatCard
            label="Total Revenue"
            value={formatCurrency(s.totalRevenue)}
            iconBg="bg-emerald-50"
            icon={
              <svg
                className="h-5 w-5 text-emerald-500"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.5}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            }
          />
          <StatCard
            label="Platform Profit"
            value={formatCurrency(s.platformProfit)}
            iconBg="bg-blue-50"
            icon={
              <svg
                className="h-5 w-5 text-blue-500"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.5}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M2.25 18L9 11.25l4.306 4.307a11.95 11.95 0 015.814-5.519l2.74-1.22m0 0l-5.94-2.28m5.94 2.28l-2.28 5.941"
                />
              </svg>
            }
          />
          <StatCard
            label="Pending Amount"
            value={formatCurrency(s.pendingAmount)}
            iconBg="bg-amber-50"
            icon={
              <svg
                className="h-5 w-5 text-amber-500"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.5}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            }
          />
          <StatCard
            label="Failed Amount"
            value={formatCurrency(s.failedAmount)}
            iconBg="bg-red-50"
            icon={
              <svg
                className="h-5 w-5 text-red-500"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.5}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z"
                />
              </svg>
            }
          />
        </div>

        {/* Revenue Breakdown */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          {/* Payment Status Breakdown */}
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">
              Payment Status Breakdown
            </h2>
            <div className="space-y-4">
              <StatusBar
                label="Completed"
                count={s.completedCount}
                amount={s.completedAmount}
                total={s.completedCount + s.pendingCount + s.failedCount}
                color="bg-green-500"
              />
              <StatusBar
                label="Pending"
                count={s.pendingCount}
                amount={s.pendingAmount}
                total={s.completedCount + s.pendingCount + s.failedCount}
                color="bg-amber-500"
              />
              <StatusBar
                label="Failed"
                count={s.failedCount}
                amount={s.failedAmount}
                total={s.completedCount + s.pendingCount + s.failedCount}
                color="bg-red-500"
              />
            </div>

            <div className="mt-6 pt-4 border-t border-gray-100">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Doctor Commissions</span>
                <span className="font-medium text-amber-600">
                  {formatCurrency(s.doctorCommissions)}
                </span>
              </div>
              <div className="flex justify-between text-sm mt-2">
                <span className="text-gray-500">Platform Profit</span>
                <span className="font-medium text-emerald-600">
                  {formatCurrency(s.platformProfit)}
                </span>
              </div>
              {s.totalRevenue > 0 && (
                <div className="flex justify-between text-xs mt-2">
                  <span className="text-gray-400">Profit Margin</span>
                  <span className="text-gray-500">
                    {((s.platformProfit / s.totalRevenue) * 100).toFixed(1)}%
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Daily Revenue (last 30 days) */}
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">
              Daily Revenue (Last 30 Days)
            </h2>
            {dailyRevenue.length === 0 ? (
              <p className="text-sm text-gray-400 py-8 text-center">
                No revenue data in the last 30 days.
              </p>
            ) : (
              <div className="space-y-2 max-h-[320px] overflow-y-auto">
                {dailyRevenue.map((d) => {
                  const maxRevenue = Math.max(
                    ...dailyRevenue.map((r) => r.revenue)
                  );
                  const pct =
                    maxRevenue > 0 ? (d.revenue / maxRevenue) * 100 : 0;
                  const dateObj = new Date(d.date + "T00:00:00");

                  return (
                    <div key={d.date} className="flex items-center gap-3">
                      <span className="text-xs text-gray-500 w-16 flex-shrink-0">
                        {dateObj.toLocaleDateString("en-US", {
                          month: "short",
                          day: "numeric",
                        })}
                      </span>
                      <div className="flex-1 h-5 bg-gray-50 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-brand-teal/70 rounded-full"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                      <span className="text-xs font-medium text-gray-700 w-24 text-right flex-shrink-0">
                        TSh {d.revenue.toLocaleString()}
                      </span>
                      <span className="text-[10px] text-gray-400 w-8 text-right flex-shrink-0">
                        {d.count}tx
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Duplicate Detection */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="text-base font-semibold text-gray-900">
              Potential Duplicate Payments
            </h2>
            <p className="text-xs text-gray-400 mt-0.5">
              Same session and amount appearing multiple times
            </p>
          </div>
          {duplicates.length === 0 ? (
            <div className="px-6 py-8 text-center">
              <div className="w-10 h-10 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-3">
                <svg
                  className="h-5 w-5 text-green-500"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth={1.5}
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
              <p className="text-sm text-gray-500">
                No duplicate payments detected.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50/50">
                    <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Session ID
                    </th>
                    <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Amount
                    </th>
                    <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      Occurrences
                    </th>
                    <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      First Seen
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {duplicates.map((d, i) => (
                    <tr key={i} className="hover:bg-gray-50/50">
                      <td className="px-5 py-3 text-gray-700 font-mono text-xs">
                        {d.patient_session_id.slice(0, 12)}...
                      </td>
                      <td className="px-5 py-3 text-gray-900 font-semibold text-right">
                        TSh {d.amount.toLocaleString()}
                      </td>
                      <td className="px-5 py-3 text-center">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                            d.count >= 3
                              ? "bg-red-100 text-red-700"
                              : "bg-amber-100 text-amber-700"
                          }`}
                        >
                          {d.count}x
                        </span>
                      </td>
                      <td className="px-5 py-3 text-gray-500 text-xs">
                        {new Date(d.created_at).toLocaleDateString("en-US", {
                          month: "short",
                          day: "numeric",
                          year: "numeric",
                        })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </RoleGuard>
  );
}

function StatusBar({
  label,
  count,
  amount,
  total,
  color,
}: {
  label: string;
  count: number;
  amount: number;
  total: number;
  color: string;
}) {
  const pct = total > 0 ? (count / total) * 100 : 0;

  return (
    <div>
      <div className="flex justify-between text-sm mb-1">
        <span className="text-gray-700 font-medium">{label}</span>
        <span className="text-gray-500">
          {count} ({pct.toFixed(1)}%) &middot;{" "}
          <span className="font-medium text-gray-700">
            {formatCurrency(amount)}
          </span>
        </span>
      </div>
      <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
        <div
          className={`h-full ${color} rounded-full transition-all`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}
