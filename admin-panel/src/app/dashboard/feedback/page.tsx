"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { createClient } from "@/lib/supabase/client";
import RoleGuard from "@/components/RoleGuard";
import RealtimeRefresh from "@/components/RealtimeRefresh";

interface FeedbackRow {
  id: string;
  reasons: string[];
  comment: string | null;
  locale: string | null;
  created_at: string;
}

const PAGE_SIZE = 25;

// Same canonical codes as the submit-deletion-feedback edge function /
// feature/patient strings. Keep in sync if more reasons are added.
const REASON_LABELS: Record<string, string> = {
  no_longer_needed: "No longer needed",
  privacy: "Privacy concerns",
  too_slow: "Too slow",
  bad_experience: "Bad experience",
  other: "Other",
};

const REASON_COLORS: Record<string, string> = {
  no_longer_needed: "bg-gray-100 text-gray-700",
  privacy: "bg-amber-100 text-amber-800",
  too_slow: "bg-blue-100 text-blue-800",
  bad_experience: "bg-rose-100 text-rose-800",
  other: "bg-violet-100 text-violet-800",
};

export default function PatientDeletionFeedbackPage() {
  const [rows, setRows] = useState<FeedbackRow[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [reasonFilter, setReasonFilter] = useState<string>("all");
  const [search, setSearch] = useState("");
  const [searchDebounced, setSearchDebounced] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setSearchDebounced(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  const fetchData = useCallback(async () => {
    setLoading(true);
    const supabase = createClient();
    const from = (page - 1) * PAGE_SIZE;
    const to = from + PAGE_SIZE - 1;

    let query = supabase
      .from("patient_deletion_feedback")
      .select("*", { count: "exact" })
      .order("created_at", { ascending: false })
      .range(from, to);

    if (reasonFilter !== "all") {
      query = query.contains("reasons", [reasonFilter]);
    }
    if (searchDebounced) {
      query = query.ilike("comment", `%${searchDebounced}%`);
    }

    const { data, error: err, count } = await query;
    if (err) {
      setError(err.message);
      setRows([]);
    } else {
      setError(null);
      setRows((data ?? []) as FeedbackRow[]);
      setTotalCount(count ?? 0);
    }
    setLoading(false);
  }, [page, reasonFilter, searchDebounced]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  const reasonCounts = useMemo(() => {
    const counts: Record<string, number> = { all: totalCount };
    rows.forEach((r) => r.reasons.forEach((c) => { counts[c] = (counts[c] ?? 0) + 1; }));
    return counts;
  }, [rows, totalCount]);

  return (
    <RoleGuard allowed={["admin", "audit"]}>
      <div className="p-6 space-y-4">
        <RealtimeRefresh
          tables={["patient_deletion_feedback"]}
          channelName="admin-deletion-feedback"
          onUpdate={fetchData}
        />

        <header className="flex items-end justify-between flex-wrap gap-3">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Patient deletion feedback</h1>
            <p className="text-sm text-gray-500 mt-0.5">
              Optional reason a patient leaves when deleting their account. Anonymous.
            </p>
          </div>
          <div className="text-xs text-gray-400">{totalCount} total</div>
        </header>

        <div className="flex flex-wrap gap-2 items-center">
          <select
            value={reasonFilter}
            onChange={(e) => { setReasonFilter(e.target.value); setPage(1); }}
            className="px-3 py-2 rounded-lg border border-gray-200 bg-white text-sm text-gray-900"
          >
            <option value="all">All reasons</option>
            {Object.entries(REASON_LABELS).map(([code, label]) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>
          <input
            type="text"
            placeholder="Search comments…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(1); }}
            className="flex-1 min-w-[200px] px-3 py-2 rounded-lg border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400"
          />
        </div>

        {error && (
          <div className="rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-sm px-3 py-2">
            {error}
          </div>
        )}

        {loading ? (
          <div className="text-sm text-gray-400 px-2">Loading…</div>
        ) : rows.length === 0 ? (
          <div className="text-sm text-gray-400 px-2">No feedback yet.</div>
        ) : (
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50/50">
                  <th className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">When</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Reasons</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Comment</th>
                  <th className="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Locale</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {rows.map((row) => (
                  <tr key={row.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-4 py-3 text-gray-600 text-xs whitespace-nowrap align-top">
                      {new Date(row.created_at).toLocaleString("en-US", {
                        year: "numeric", month: "short", day: "numeric",
                        hour: "2-digit", minute: "2-digit",
                      })}
                    </td>
                    <td className="px-4 py-3 align-top">
                      {row.reasons.length === 0 ? (
                        <span className="text-gray-400 text-xs italic">— none —</span>
                      ) : (
                        <div className="flex flex-wrap gap-1.5">
                          {row.reasons.map((code) => (
                            <span
                              key={code}
                              className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${REASON_COLORS[code] ?? "bg-gray-100 text-gray-700"}`}
                            >
                              {REASON_LABELS[code] ?? code}
                            </span>
                          ))}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-700 align-top">
                      {row.comment ? (
                        <p className="whitespace-pre-wrap text-sm leading-relaxed max-w-md">
                          {row.comment}
                        </p>
                      ) : (
                        <span className="text-gray-400 text-xs italic">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs align-top whitespace-nowrap">
                      {row.locale ?? "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between text-xs text-gray-500 px-1">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
              className="px-3 py-1.5 rounded-lg border border-gray-200 text-gray-700 disabled:opacity-40"
            >
              Previous
            </button>
            <span>Page {page} of {totalPages}</span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
              className="px-3 py-1.5 rounded-lg border border-gray-200 text-gray-700 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </RoleGuard>
  );
}
