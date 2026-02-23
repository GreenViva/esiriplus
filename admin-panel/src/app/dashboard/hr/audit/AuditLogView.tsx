"use client";

import { useState, useMemo } from "react";

interface LogEntry {
  id: string;
  admin_id: string;
  admin_email: string;
  action: string;
  target_type: string | null;
  target_id: string | null;
  target_name: string | null;
  details: Record<string, unknown> | null;
  created_at: string;
}

type ActionFilter = "all" | "approve" | "reject" | "suspend" | "ban" | "warn" | "create" | "flag" | "consultation" | "payment" | "rating" | "registration" | "other";

interface Props {
  logs: LogEntry[];
}

export default function AuditLogView({ logs }: Props) {
  const [search, setSearch] = useState("");
  const [actionFilter, setActionFilter] = useState<ActionFilter>("all");

  function getActionCategory(action: string): ActionFilter {
    if (action.includes("unsuspend") || action === "unban_doctor") return "approve";
    if (action.includes("approve") || action === "doctor_verified") return "approve";
    if (action.includes("reject") || action === "doctor_rejected") return "reject";
    if (action.includes("suspend") || action === "doctor_deactivated") return "suspend";
    if (action.includes("ban")) return "ban";
    if (action.includes("warn")) return "warn";
    if (action.includes("create") || action.includes("setup") || action.includes("role_assigned")) return "create";
    if (action.includes("flag")) return "flag";
    if (action.includes("consultation")) return "consultation";
    if (action.includes("payment")) return "payment";
    if (action.includes("rating")) return "rating";
    if (action.includes("register") || action === "doctor_registered" || action === "patient_session_created") return "registration";
    return "other";
  }

  const filtered = useMemo(() => {
    let list = logs;

    // Search
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(
        (l) =>
          l.admin_email.toLowerCase().includes(q) ||
          l.action.toLowerCase().includes(q) ||
          formatActionDescription(l).toLowerCase().includes(q) ||
          (l.target_type ?? "").toLowerCase().includes(q)
      );
    }

    // Action filter
    if (actionFilter !== "all") {
      list = list.filter((l) => getActionCategory(l.action) === actionFilter);
    }

    return list;
  }, [logs, search, actionFilter]);

  function formatActionDescription(log: LogEntry): string {
    const d = log.details ?? {};
    const targetName = log.target_name;
    const name = targetName ?? (d.full_name as string) ?? log.target_id?.slice(0, 8) ?? "unknown";

    switch (log.action) {
      // Admin/HR manual actions
      case "approve_doctor":
        return `Approved doctor: ${name}`;
      case "reject_doctor":
        return `Rejected doctor: ${name}${d.reason ? ` — ${d.reason}` : ""}`;
      case "suspend_doctor":
        return `Suspended doctor: ${name}`;
      case "ban_doctor":
        return `Banned doctor: ${name}`;
      case "warn_doctor":
        return `Warned doctor: ${name}${d.message ? ` — "${d.message}"` : ""}`;
      case "suspend_user":
        return `Suspended user ${log.target_id?.slice(0, 8) ?? "unknown"}`;
      case "unsuspend_user":
        return `Unsuspended user ${log.target_id?.slice(0, 8) ?? "unknown"}`;
      case "create_portal_user":
        return `Created ${d.role ?? "user"} account: ${d.email ?? "unknown"}`;
      case "portal_user_created":
        return `Created ${d.role ?? "user"} account: ${d.email ?? "unknown"}`;
      case "initial_admin_setup":
        return `Initial admin setup: ${d.email ?? ""}`;
      case "flag_rating":
        return `Flagged rating ${log.target_id?.slice(0, 8) ?? ""}`;
      case "unflag_rating":
        return `Unflagged rating ${log.target_id?.slice(0, 8) ?? ""}`;
      case "unsuspend_doctor":
        return `Unsuspended doctor: ${name}`;
      case "unban_doctor":
        return `Unbanned doctor: ${name}`;
      case "deauthorize_device":
        return `Deauthorized device for doctor ${name}`;
      case "delete_user_role":
        return `Removed ${d.role ?? "unknown"} role from user ${log.target_id?.slice(0, 8) ?? "unknown"}`;

      // Auto-triggered events
      case "doctor_registered":
        return `New doctor registered: ${d.full_name ?? name} (${d.specialty ?? ""})`;
      case "doctor_verified":
        return `Doctor verified: ${d.full_name ?? name}`;
      case "doctor_activated":
        return `Doctor activated: ${d.full_name ?? name}`;
      case "doctor_deactivated":
        return `Doctor deactivated: ${d.full_name ?? name}`;
      case "doctor_rejected":
        return `Doctor rejected: ${d.full_name ?? name}${d.reason ? ` — ${d.reason}` : ""}`;
      case "consultation_created":
        return `New consultation created (${d.service_type ?? "general"})`;
      case "consultation_active":
      case "consultation_in_progress":
        return `Consultation started (${d.new_status ?? "active"})`;
      case "consultation_completed":
        return `Consultation completed`;
      case "consultation_cancelled":
        return `Consultation cancelled`;
      case "consultation_pending":
        return `Consultation pending`;
      case "payment_created":
        return `Payment of ${d.amount ?? 0} ${d.currency ?? "TZS"} created`;
      case "payment_completed":
        return `Payment of ${d.amount ?? 0} ${d.currency ?? "TZS"} completed`;
      case "rating_submitted":
        return `Patient rated doctor ${d.rating ?? "?"}★${d.has_comment ? " with comment" : ""}`;
      case "patient_session_created":
        return `New patient session${d.region ? ` from ${d.region}` : ""}`;
      case "role_assigned":
        return `Role "${d.role ?? "unknown"}" assigned to user`;
      case "role_revoked":
        return `Role "${d.role ?? "unknown"}" revoked from user`;

      default:
        return log.action.replace(/_/g, " ");
    }
  }

  function getTypeBadge(action: string): { label: string; bg: string; text: string } {
    // Order matters: check more specific strings before generic ones
    if (action.includes("unsuspend") || action === "unban_doctor") return { label: "restore", bg: "bg-green-50", text: "text-green-700" };
    if (action.includes("unflag")) return { label: "unflag", bg: "bg-gray-100", text: "text-gray-600" };
    if (action.includes("approve") || action === "doctor_verified") return { label: "approve", bg: "bg-green-50", text: "text-green-700" };
    if (action.includes("reject") || action === "doctor_rejected") return { label: "reject", bg: "bg-red-50", text: "text-red-700" };
    if (action === "doctor_deactivated") return { label: "deactivate", bg: "bg-orange-50", text: "text-orange-700" };
    if (action === "doctor_activated") return { label: "activate", bg: "bg-green-50", text: "text-green-700" };
    if (action.includes("suspend")) return { label: "suspend", bg: "bg-orange-50", text: "text-orange-700" };
    if (action.includes("ban")) return { label: "ban", bg: "bg-red-50", text: "text-red-700" };
    if (action.includes("warn")) return { label: "warn", bg: "bg-amber-50", text: "text-amber-700" };
    if (action.includes("create") || action.includes("setup") || action === "role_assigned") return { label: "create", bg: "bg-blue-50", text: "text-blue-700" };
    if (action.includes("flag")) return { label: "flag", bg: "bg-purple-50", text: "text-purple-700" };
    if (action.includes("revoke") || action === "role_revoked") return { label: "role_revoke", bg: "bg-gray-100", text: "text-gray-700" };
    if (action.includes("deauthorize")) return { label: "deauthorize", bg: "bg-gray-100", text: "text-gray-700" };
    if (action === "doctor_registered" || action === "patient_session_created") return { label: "registration", bg: "bg-indigo-50", text: "text-indigo-700" };
    if (action.includes("consultation")) return { label: "consultation", bg: "bg-teal-50", text: "text-teal-700" };
    if (action.includes("payment")) return { label: "payment", bg: "bg-emerald-50", text: "text-emerald-700" };
    if (action.includes("rating")) return { label: "rating", bg: "bg-yellow-50", text: "text-yellow-700" };
    return { label: action.replace(/_/g, " "), bg: "bg-gray-100", text: "text-gray-600" };
  }

  function formatTimestamp(iso: string): string {
    return new Date(iso).toLocaleString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  function getDetailsText(log: LogEntry): string {
    if (log.target_type) return log.target_type;
    const d = log.details;
    if (!d) return "—";
    return Object.entries(d)
      .filter(([k]) => k !== "email" && k !== "role" && k !== "reason" && k !== "message")
      .map(([k, v]) => `${v}`)
      .join(", ") || "—";
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
          <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15a2.25 2.25 0 012.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V19.5a2.25 2.25 0 002.25 2.25h6.75a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08" />
          </svg>
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Audit Log</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            Your HR activity audit trail
          </p>
        </div>
      </div>

      {/* Search + Filter */}
      <div className="flex flex-col sm:flex-row gap-3 mt-5 mb-4">
        <div className="flex-1 relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="text"
            placeholder="Search actions..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          />
        </div>
        <select
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value as ActionFilter)}
          className="px-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal min-w-[150px]"
        >
          <option value="all">All Actions</option>
          <option value="approve">Approve / Verify</option>
          <option value="reject">Reject</option>
          <option value="suspend">Suspend / Deactivate</option>
          <option value="ban">Ban</option>
          <option value="warn">Warn</option>
          <option value="create">Create / Assign</option>
          <option value="flag">Flag</option>
          <option value="consultation">Consultation</option>
          <option value="payment">Payment</option>
          <option value="rating">Rating</option>
          <option value="registration">Registration</option>
          <option value="other">Other</option>
        </select>
      </div>

      {/* Count */}
      <p className="text-sm text-gray-500 mb-3">
        {filtered.length} entr{filtered.length !== 1 ? "ies" : "y"}
      </p>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Timestamp</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">User</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Action</th>
                <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Type</th>
                <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map((log) => {
                const badge = getTypeBadge(log.action);
                return (
                  <tr key={log.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-4 text-gray-500 whitespace-nowrap">
                      {formatTimestamp(log.created_at)}
                    </td>
                    <td className="px-5 py-4 text-gray-700">
                      {log.admin_email}
                    </td>
                    <td className="px-5 py-4 text-gray-900">
                      {formatActionDescription(log)}
                    </td>
                    <td className="px-5 py-4 text-center">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${badge.bg} ${badge.text}`}>
                        {badge.label}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-gray-500 text-right">
                      {getDetailsText(log)}
                    </td>
                  </tr>
                );
              })}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-5 py-12 text-center text-gray-400">
                    No audit entries found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
