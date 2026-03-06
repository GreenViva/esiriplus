export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import StatCard from "@/components/StatCard";

const SECURITY_ACTIONS = [
  "session_locked_brute_force",
  "recovery_locked",
  "doctor_login_failed",
  "recovery_by_id_failed",
  "recovery_by_questions_failed",
  "device_deauth_failed",
  "token_generation_failed",
  "manage_consultation_failed",
];

interface RiskFlag {
  flag_id: string;
  flag_type: string;
  severity: string;
  title: string;
  description: string | null;
  created_at: string;
  doctor_profiles: { full_name: string } | null;
}

export default async function RiskActivityPage() {
  const supabase = createAdminClient();
  const twentyFourHoursAgo = new Date(
    Date.now() - 24 * 60 * 60 * 1000
  ).toISOString();

  const [
    errorsRes,
    warningsRes,
    failedAuthRes,
    lockedSessionsRes,
    securityEventsRes,
    suspiciousIpsRes,
    riskFlagsRes,
  ] = await Promise.all([
    // Errors in last 24h
    supabase
      .from("admin_logs")
      .select("*", { count: "exact", head: true })
      .eq("level", "error")
      .gte("created_at", twentyFourHoursAgo),
    // Warnings in last 24h
    supabase
      .from("admin_logs")
      .select("*", { count: "exact", head: true })
      .eq("level", "warn")
      .gte("created_at", twentyFourHoursAgo),
    // Failed auth in last 24h
    supabase
      .from("admin_logs")
      .select("*", { count: "exact", head: true })
      .in("action", SECURITY_ACTIONS)
      .gte("created_at", twentyFourHoursAgo),
    // Currently locked sessions
    supabase
      .from("patient_sessions")
      .select("*", { count: "exact", head: true })
      .eq("is_locked", true),
    // Recent security events (warn/error + security actions)
    supabase
      .from("admin_logs")
      .select("*")
      .or(
        `level.in.(warn,error),action.in.(${SECURITY_ACTIONS.join(",")})`
      )
      .order("created_at", { ascending: false })
      .limit(50),
    // Suspicious IPs — failed recovery attempts in last 24h
    supabase
      .from("recovery_attempts")
      .select("ip_address, attempted_at")
      .eq("success", false)
      .gte("attempted_at", twentyFourHoursAgo)
      .order("attempted_at", { ascending: false })
      .limit(500),
    // Active risk flags
    supabase
      .from("risk_flags")
      .select(
        "flag_id, flag_type, severity, title, description, created_at, doctor_profiles(full_name)"
      )
      .eq("is_resolved", false)
      .order("created_at", { ascending: false })
      .limit(10),
  ]);

  const errors24h = errorsRes.count ?? 0;
  const warnings24h = warningsRes.count ?? 0;
  const failedAuth24h = failedAuthRes.count ?? 0;
  const lockedSessions = lockedSessionsRes.count ?? 0;
  const securityEvents = securityEventsRes.data ?? [];
  const riskFlags = (riskFlagsRes.data ?? []) as unknown as RiskFlag[];

  // Aggregate suspicious IPs
  const ipCounts = new Map<string, { count: number; lastAttempt: string }>();
  for (const row of suspiciousIpsRes.data ?? []) {
    const ip = row.ip_address ?? "unknown";
    const existing = ipCounts.get(ip);
    if (existing) {
      existing.count++;
    } else {
      ipCounts.set(ip, { count: 1, lastAttempt: row.attempted_at });
    }
  }
  const suspiciousIps = Array.from(ipCounts.entries())
    .map(([ip, data]) => ({ ip, ...data }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 10);

  return (
    <div>
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          Risk Activity Monitor
        </h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Real-time monitoring of suspicious and high-risk activities
        </p>
      </div>

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          label="Errors (24h)"
          value={errors24h}
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
                d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"
              />
            </svg>
          }
        />
        <StatCard
          label="Warnings (24h)"
          value={warnings24h}
          iconBg="bg-orange-50"
          icon={
            <svg
              className="h-5 w-5 text-orange-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 9v3.75m0 3.75h.007v.008H12v-.008zm0-15a9 9 0 110 18 9 9 0 010-18z"
              />
            </svg>
          }
        />
        <StatCard
          label="Failed Auth (24h)"
          value={failedAuth24h}
          iconBg="bg-yellow-50"
          icon={
            <svg
              className="h-5 w-5 text-yellow-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"
              />
            </svg>
          }
        />
        <StatCard
          label="Locked Sessions"
          value={lockedSessions}
          iconBg="bg-purple-50"
          icon={
            <svg
              className="h-5 w-5 text-purple-500"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"
              />
            </svg>
          }
        />
      </div>

      {/* Security Events Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden mb-6">
        <div className="px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">
            Recent Security Events
          </h2>
          <p className="text-xs text-gray-400 mt-0.5">
            Errors, warnings, failed authentication, and security incidents
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="px-4 py-2.5 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  Time
                </th>
                <th className="px-4 py-2.5 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  Level
                </th>
                <th className="px-4 py-2.5 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  Action
                </th>
                <th className="px-4 py-2.5 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  Function
                </th>
                <th className="px-4 py-2.5 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  IP Address
                </th>
                <th className="px-4 py-2.5 text-[11px] font-semibold text-gray-500 uppercase tracking-wider">
                  Error / Details
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {securityEvents.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className="px-4 py-8 text-center text-sm text-gray-400"
                  >
                    No security events in the system.
                  </td>
                </tr>
              ) : (
                securityEvents.map((event) => {
                  const date = new Date(event.created_at);
                  const level = (event.level ?? "info") as string;
                  const details =
                    event.error_message ||
                    (event.details
                      ? JSON.stringify(event.details).slice(0, 120)
                      : event.metadata
                        ? JSON.stringify(event.metadata).slice(0, 120)
                        : "—");

                  return (
                    <tr
                      key={event.id ?? event.log_id ?? Math.random()}
                      className={
                        level === "error"
                          ? "bg-red-50/30 hover:bg-red-50/60"
                          : level === "warn"
                            ? "hover:bg-orange-50/30"
                            : "hover:bg-gray-50"
                      }
                    >
                      <td className="px-4 py-2.5 text-xs text-gray-500 whitespace-nowrap">
                        {date.toLocaleDateString("en-US", {
                          month: "short",
                          day: "numeric",
                        })}{" "}
                        {date.toLocaleTimeString("en-US", {
                          hour: "2-digit",
                          minute: "2-digit",
                          second: "2-digit",
                        })}
                      </td>
                      <td className="px-4 py-2.5">
                        <LevelBadge level={level} />
                      </td>
                      <td className="px-4 py-2.5 text-sm text-gray-900 font-medium">
                        {formatAction(event.action)}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-gray-500 font-mono">
                        {event.function_name ?? "—"}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-gray-500 font-mono">
                        {event.ip_address ?? "—"}
                      </td>
                      <td className="px-4 py-2.5 text-xs text-gray-500 max-w-[300px] truncate">
                        {details}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Two-column: Suspicious IPs + Active Risk Flags */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Suspicious IPs */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-1">
            Suspicious IPs (24h)
          </h2>
          <p className="text-xs text-gray-400 mb-4">
            IP addresses with multiple failed recovery/auth attempts
          </p>
          {suspiciousIps.length === 0 ? (
            <p className="text-sm text-gray-400 py-4">
              No suspicious IP activity detected.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-gray-100">
                    <th className="pb-2 text-[11px] font-semibold text-gray-500 uppercase">
                      IP Address
                    </th>
                    <th className="pb-2 text-[11px] font-semibold text-gray-500 uppercase text-right">
                      Failed Attempts
                    </th>
                    <th className="pb-2 text-[11px] font-semibold text-gray-500 uppercase text-right">
                      Last Attempt
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {suspiciousIps.map((ip) => {
                    const lastDate = new Date(ip.lastAttempt);
                    return (
                      <tr key={ip.ip} className="hover:bg-gray-50">
                        <td className="py-2 text-sm font-mono text-gray-900">
                          {ip.ip}
                        </td>
                        <td className="py-2 text-sm text-right">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${
                              ip.count >= 10
                                ? "bg-red-100 text-red-700"
                                : ip.count >= 5
                                  ? "bg-orange-100 text-orange-700"
                                  : "bg-yellow-100 text-yellow-700"
                            }`}
                          >
                            {ip.count}
                          </span>
                        </td>
                        <td className="py-2 text-xs text-gray-500 text-right">
                          {lastDate.toLocaleTimeString("en-US", {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Active Risk Flags */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-1">
            Active Risk Flags
          </h2>
          <p className="text-xs text-gray-400 mb-4">
            Unresolved flags from HR actions and automated checks
          </p>
          {riskFlags.length === 0 ? (
            <p className="text-sm text-gray-400 py-4">
              No active risk flags.
            </p>
          ) : (
            <ul className="space-y-3">
              {riskFlags.map((flag) => {
                const doctorName =
                  (
                    flag.doctor_profiles as unknown as {
                      full_name: string;
                    } | null
                  )?.full_name ?? "Unknown";
                const date = new Date(flag.created_at);

                return (
                  <li key={flag.flag_id} className="flex items-start gap-3">
                    <SeverityBadge severity={flag.severity} />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900">
                        {flag.title}
                      </p>
                      <p className="text-xs text-gray-600">
                        Dr. {doctorName}
                        {flag.description && (
                          <span className="text-gray-400">
                            {" "}
                            &mdash; {flag.description}
                          </span>
                        )}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {flagTypeLabel(flag.flag_type)} &middot;{" "}
                        {date.toLocaleDateString("en-US", {
                          month: "short",
                          day: "numeric",
                        })}
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
  );
}

/* ── Helper Components ──────────────────────────────────────────────────── */

function LevelBadge({ level }: { level: string }) {
  const config: Record<string, { bg: string; text: string; label: string }> = {
    error: { bg: "bg-red-100", text: "text-red-700", label: "ERROR" },
    warn: { bg: "bg-orange-100", text: "text-orange-700", label: "WARN" },
    info: { bg: "bg-blue-100", text: "text-blue-700", label: "INFO" },
  };
  const c = config[level] ?? config.info;

  return (
    <span
      className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold uppercase ${c.bg} ${c.text}`}
    >
      {c.label}
    </span>
  );
}

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

function flagTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    hr_warning: "HR Warning",
    hr_suspension: "HR Suspension",
    hr_ban: "HR Ban",
    low_online_time: "Low Online Time",
  };
  return labels[type] ?? type;
}

function formatAction(action: string): string {
  const labels: Record<string, string> = {
    session_locked_brute_force: "Session locked (brute force)",
    recovery_locked: "Recovery locked (too many attempts)",
    doctor_login_failed: "Doctor login failed",
    doctor_logged_in: "Doctor login",
    recovery_by_id_failed: "Patient recovery by ID failed",
    recovery_by_questions_failed: "Patient recovery by questions failed",
    session_recovered_by_id: "Session recovered by ID",
    session_recovered_by_questions: "Session recovered by questions",
    device_deauthorized: "Device deauthorized",
    device_deauth_failed: "Device deauth failed",
    token_generation_failed: "Token generation failed",
    manage_consultation_failed: "Consultation management failed",
    video_token_issued: "Video token issued",
    consultation_ended: "Consultation ended",
    consultation_extended: "Consultation extended",
    extension_requested: "Extension requested",
    sessions_cleaned: "Expired sessions cleaned",
    approve_doctor: "Doctor approved",
    reject_doctor: "Doctor rejected",
    suspend_doctor: "Doctor suspended",
    unsuspend_doctor: "Doctor unsuspended",
    ban_doctor: "Doctor banned",
    unban_doctor: "Doctor unbanned",
    warn_doctor: "Doctor warned",
    flag_rating: "Rating flagged",
    unflag_rating: "Rating unflagged",
  };
  return (
    labels[action] ??
    action
      .replace(/_/g, " ")
      .replace(/\b\w/g, (c) => c.toUpperCase())
  );
}
