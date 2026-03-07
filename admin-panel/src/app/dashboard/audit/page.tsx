"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import { scanForRisks } from "@/lib/adminApi";
import StatCard from "@/components/StatCard";
import RoleGuard from "@/components/RoleGuard";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import type { RiskFlag, AdminLogRow } from "@/lib/types/database";

export default function AuditDashboardPage() {
  const [openRiskFlags, setOpenRiskFlags] = useState(0);
  const [highRiskAlerts, setHighRiskAlerts] = useState(0);
  const [activityToday, setActivityToday] = useState(0);
  const [flaggedRatings, setFlaggedRatings] = useState(0);
  const [recentFlags, setRecentFlags] = useState<RiskFlag[]>([]);
  const [recentLogs, setRecentLogs] = useState<AdminLogRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<string | null>(null);

  const loadData = useCallback(() => {
    const supabase = createClient();
    return Promise.all([
      supabase
        .from("risk_flags")
        .select("*", { count: "exact", head: true })
        .eq("is_resolved", false),
      supabase
        .from("risk_flags")
        .select("*", { count: "exact", head: true })
        .eq("is_resolved", false)
        .eq("severity", "high"),
      supabase
        .from("admin_logs")
        .select("*", { count: "exact", head: true })
        .gte("created_at", new Date().toISOString().split("T")[0]),
      supabase
        .from("doctor_ratings")
        .select("*", { count: "exact", head: true })
        .eq("is_flagged", true),
      supabase
        .from("risk_flags")
        .select("flag_id, doctor_id, flag_type, severity, title, description, is_resolved, created_at, doctor_profiles(full_name)")
        .eq("is_resolved", false)
        .order("created_at", { ascending: false })
        .limit(10),
      supabase
        .from("admin_logs")
        .select("*")
        .order("created_at", { ascending: false })
        .limit(10),
    ]).then(([
      openFlagsCountRes,
      highRiskCountRes,
      todayLogsRes,
      flaggedRatingsRes,
      recentFlagsRes,
      recentLogsRes,
    ]) => {
      setOpenRiskFlags(openFlagsCountRes.count ?? 0);
      setHighRiskAlerts(highRiskCountRes.count ?? 0);
      setActivityToday(todayLogsRes.count ?? 0);
      setFlaggedRatings(flaggedRatingsRes.count ?? 0);
      setRecentFlags((recentFlagsRes.data ?? []) as unknown as RiskFlag[]);
      setRecentLogs(recentLogsRes.data ?? []);
      setLoading(false);
    });
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  async function handleScanForRisks() {
    setScanning(true);
    setScanResult(null);
    const result = await scanForRisks();
    if (result.error) {
      setScanResult(`Scan failed: ${result.error}`);
    } else {
      setScanResult(
        result.flagged
          ? `Scan complete — ${result.flagged} new risk flag${result.flagged > 1 ? "s" : ""} detected`
          : "Scan complete — no new risks detected"
      );
      loadData();
    }
    setScanning(false);
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
      </div>
    );
  }

  return (
    <RoleGuard allowed={["admin", "audit"]}>
    <div>
      <RealtimeRefresh tables={["risk_flags", "admin_logs", "doctor_ratings"]} channelName="audit-dashboard-realtime" onUpdate={loadData} />
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Audit Dashboard</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            Enterprise oversight &amp; compliance monitoring
          </p>
        </div>
        <button
          onClick={handleScanForRisks}
          disabled={scanning}
          className="flex items-center gap-2 px-4 py-2 bg-brand-teal text-white text-sm font-medium rounded-lg hover:bg-brand-teal/90 disabled:opacity-50 transition-colors"
        >
          {scanning ? (
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
          ) : (
            <svg
              className="h-4 w-4"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z"
              />
            </svg>
          )}
          {scanning ? "Scanning..." : "Scan for Risks"}
        </button>
      </div>

      {scanResult && (
        <div className={`mb-4 p-3 rounded-lg text-sm font-medium ${
          scanResult.startsWith("Scan failed")
            ? "bg-red-50 text-red-700 border border-red-200"
            : "bg-green-50 text-green-700 border border-green-200"
        }`}>
          {scanResult}
        </div>
      )}

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
        <StatCard
          label="Open Risk Flags"
          value={openRiskFlags}
          iconBg="bg-orange-50"
          icon={
            <svg className="h-5 w-5 text-orange-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
          }
        />
        <StatCard
          label="High-Risk Alerts"
          value={highRiskAlerts}
          iconBg="bg-red-50"
          icon={
            <svg className="h-5 w-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m0 3.75h.007v.008H12v-.008zm0-15a9 9 0 110 18 9 9 0 010-18z" />
            </svg>
          }
        />
        <StatCard
          label="Activity Today"
          value={activityToday}
          iconBg="bg-blue-50"
          icon={
            <svg className="h-5 w-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
            </svg>
          }
        />
        <StatCard
          label="Flagged Ratings"
          value={flaggedRatings}
          iconBg="bg-green-50"
          icon={
            <svg className="h-5 w-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Total Revenue Audited"
          value="$0"
          iconBg="bg-emerald-50"
          icon={
            <svg className="h-5 w-5 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
      </div>

      {/* Two-column: Recent Risk Flags + Activity Timeline */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Risk Flags */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">
            Recent Risk Flags
          </h2>
          {recentFlags.length === 0 ? (
            <p className="text-sm text-gray-400 py-4">No risk flags detected</p>
          ) : (
            <ul className="space-y-3">
              {recentFlags.map((flag) => {
                const date = new Date(flag.created_at);
                const dateStr = date.toLocaleDateString("en-US", {
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                });
                const doctorName =
                  (flag.doctor_profiles as unknown as { full_name: string } | null)?.full_name ??
                  "Unknown Doctor";

                return (
                  <li key={flag.flag_id} className="flex items-start gap-3">
                    <SeverityBadge severity={flag.severity} />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900">
                        {flag.title}
                      </p>
                      <p className="text-sm text-gray-600">
                        Dr. {doctorName}
                        {flag.description && (
                          <span className="text-gray-400">
                            {" "}&mdash; {flag.description}
                          </span>
                        )}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        <FlagTypeLabel type={flag.flag_type} /> &middot; {dateStr}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {/* Activity Timeline */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">
            Activity Timeline
          </h2>
          {recentLogs.length === 0 ? (
            <p className="text-sm text-gray-400 py-4">No recent activity.</p>
          ) : (
            <ul className="space-y-3">
              {recentLogs.map((log) => {
                const details = log.details as Record<string, unknown> | null;
                const email = (details?.email as string) ?? "";
                const action = formatAction(log.action);
                const date = new Date(log.created_at);
                const dateStr = date.toLocaleDateString("en-US", {
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                });
                const timeStr = date.toLocaleTimeString("en-US", {
                  hour: "2-digit",
                  minute: "2-digit",
                });

                return (
                  <li key={log.log_id} className="flex items-start gap-3">
                    <div className="w-2 h-2 rounded-full bg-brand-teal mt-1.5 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-gray-900">
                        {action}
                        {email && (
                          <span className="text-gray-400">
                            {" "}&middot; {email}
                          </span>
                        )}
                      </p>
                      <p className="text-xs text-gray-400">
                        {log.action} &middot; {dateStr}, {timeStr}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </div>
    </RoleGuard>
  );
}

/* -- Helper Components -- */

function SeverityBadge({ severity }: { severity: string }) {
  const config: Record<string, { bg: string; text: string }> = {
    high: { bg: "bg-red-100", text: "text-red-700" },
    medium: { bg: "bg-orange-100", text: "text-orange-700" },
    low: { bg: "bg-yellow-100", text: "text-yellow-700" },
  };
  const c = config[severity] ?? config.medium;

  return (
    <span
      className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase mt-0.5 flex-shrink-0 ${c.bg} ${c.text}`}
    >
      {severity}
    </span>
  );
}

function FlagTypeLabel({ type }: { type: string }) {
  const labels: Record<string, string> = {
    hr_warning: "HR Warning",
    hr_suspension: "HR Suspension",
    hr_ban: "HR Ban",
    low_online_time: "Low Online Time",
  };
  return <>{labels[type] ?? type}</>;
}

function formatAction(action: string): string {
  const labels: Record<string, string> = {
    approve_doctor: "Doctor approved",
    reject_doctor: "Doctor application rejected",
    suspend_doctor: "Doctor suspended",
    unsuspend_doctor: "Doctor unsuspended",
    ban_doctor: "Doctor banned",
    unban_doctor: "Doctor unbanned",
    warn_doctor: "Doctor warned",
    create_portal_user: "Portal user created",
    portal_user_created: "Portal user created",
    flag_rating: "Rating flagged",
    unflag_rating: "Rating unflagged",
    initial_admin_setup: "Admin account created",
    role_revoke: "Role revoked",
    delete_user_role: "User role removed",
  };
  return (
    labels[action] ??
    action
      .replace(/_/g, " ")
      .replace(/\b\w/g, (c) => c.toUpperCase())
  );
}
