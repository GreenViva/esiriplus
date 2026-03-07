"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import RoleGuard from "@/components/RoleGuard";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import type { AdminLogRow } from "@/lib/types/database";

const PAGE_SIZE = 25;

type ActionFilter = "all" | "approve_doctor" | "reject_doctor" | "suspend_doctor" | "ban_doctor" | "warn_doctor" | "create_portal_user" | "delete_user_role" | "flag_rating";

export default function AuditLogExplorerPage() {
  const [logs, setLogs] = useState<AdminLogRow[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [actionFilter, setActionFilter] = useState<ActionFilter>("all");
  const [search, setSearch] = useState("");
  const [searchDebounced, setSearchDebounced] = useState("");

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => setSearchDebounced(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const fetchData = useCallback(() => {
    const supabase = createClient();
    const from = (page - 1) * PAGE_SIZE;
    const to = from + PAGE_SIZE - 1;

    let query = supabase
      .from("admin_logs")
      .select("*", { count: "exact" })
      .order("created_at", { ascending: false })
      .range(from, to);

    if (actionFilter !== "all") {
      query = query.eq("action", actionFilter);
    }

    if (searchDebounced.trim()) {
      query = query.or(
        `action.ilike.%${searchDebounced}%,target_type.ilike.%${searchDebounced}%`
      );
    }

    query.then(({ data, count }) => {
      setLogs(data ?? []);
      setTotalCount(count ?? 0);
      setLoading(false);
    });
  }, [page, actionFilter, searchDebounced]);

  useEffect(() => {
    setLoading(true);
    fetchData();
  }, [fetchData]);

  // Reset to page 1 when filters change
  useEffect(() => {
    setPage(1);
  }, [actionFilter, searchDebounced]);

  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  return (
    <RoleGuard allowed={["admin", "audit"]}>
    <div>
      <RealtimeRefresh tables={["admin_logs"]} channelName="audit-logs-realtime" onUpdate={fetchData} />

      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Audit Log Explorer</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Browse and search all administrative actions
        </p>
      </div>

      {/* Search + Filter */}
      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <div className="flex-1 relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="text"
            placeholder="Search by action or target..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          />
        </div>
        <select
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value as ActionFilter)}
          className="px-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal min-w-[180px]"
        >
          <option value="all">All Actions</option>
          <option value="approve_doctor">Approve Doctor</option>
          <option value="reject_doctor">Reject Doctor</option>
          <option value="suspend_doctor">Suspend Doctor</option>
          <option value="ban_doctor">Ban Doctor</option>
          <option value="warn_doctor">Warn Doctor</option>
          <option value="create_portal_user">Create Portal User</option>
          <option value="delete_user_role">Delete User Role</option>
          <option value="flag_rating">Flag Rating</option>
        </select>
      </div>

      {/* Count */}
      <p className="text-sm text-gray-500 mb-3">{totalCount} log{totalCount !== 1 ? "s" : ""} found</p>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Timestamp
                  </th>
                  <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Action
                  </th>
                  <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Admin
                  </th>
                  <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Target
                  </th>
                  <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Details
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {logs.length === 0 ? (
                  <tr>
                    <td
                      colSpan={5}
                      className="px-5 py-8 text-center text-sm text-gray-400"
                    >
                      No audit logs found.
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => {
                    const date = new Date(log.created_at);
                    const details = log.details as Record<string, unknown> | null;
                    const email = (details?.email as string) ?? "";

                    return (
                      <tr key={log.log_id} className="hover:bg-gray-50">
                        <td className="px-5 py-3 text-sm text-gray-600 whitespace-nowrap">
                          {date.toLocaleDateString("en-US", {
                            month: "short",
                            day: "numeric",
                            year: "numeric",
                          })}{" "}
                          {date.toLocaleTimeString("en-US", {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </td>
                        <td className="px-5 py-3">
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-700">
                            {formatAction(log.action)}
                          </span>
                        </td>
                        <td className="px-5 py-3 text-sm text-gray-600 truncate max-w-[200px]">
                          {log.admin_id?.slice(0, 8) ?? "\u2014"}
                        </td>
                        <td className="px-5 py-3 text-sm text-gray-600">
                          {log.target_type ?? "\u2014"}
                          {email && (
                            <span className="text-gray-400 ml-1">({email})</span>
                          )}
                        </td>
                        <td className="px-5 py-3 text-sm text-gray-400 max-w-[300px]">
                          {details ? (
                            <DetailsDisplay details={details} />
                          ) : (
                            "\u2014"
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-gray-500">
            Page {page} of {totalPages}
          </p>
          <div className="flex gap-2">
            {page > 1 && (
              <button
                onClick={() => setPage(page - 1)}
                className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Previous
              </button>
            )}
            {page < totalPages && (
              <button
                onClick={() => setPage(page + 1)}
                className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Next
              </button>
            )}
          </div>
        </div>
      )}
    </div>
    </RoleGuard>
  );
}

function formatAction(action: string): string {
  const labels: Record<string, string> = {
    approve_doctor: "Approve Doctor",
    reject_doctor: "Reject Doctor",
    suspend_doctor: "Suspend Doctor",
    unsuspend_doctor: "Unsuspend Doctor",
    ban_doctor: "Ban Doctor",
    unban_doctor: "Unban Doctor",
    warn_doctor: "Warn Doctor",
    create_portal_user: "Create User",
    portal_user_created: "Create User",
    flag_rating: "Flag Rating",
    unflag_rating: "Unflag Rating",
    initial_admin_setup: "Admin Setup",
    delete_user_role: "Delete Role",
    suspend_user: "Suspend User",
    unsuspend_user: "Unsuspend User",
  };
  return labels[action] ?? action.replace(/_/g, " ");
}

function DetailsDisplay({ details }: { details: Record<string, unknown> }) {
  const entries = Object.entries(details).filter(([, v]) => v != null && v !== "");
  if (entries.length === 0) return <span>{"\u2014"}</span>;

  return (
    <div className="space-y-0.5">
      {entries.slice(0, 4).map(([key, value]) => (
        <div key={key} className="text-xs">
          <span className="text-gray-500">{key}:</span>{" "}
          <span className="text-gray-700">{String(value).slice(0, 100)}</span>
        </div>
      ))}
    </div>
  );
}
