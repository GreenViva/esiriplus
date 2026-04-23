"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";

interface Rating {
  rating_id: string;
  doctor_id: string;
  consultation_id: string;
  patient_session_id: string;
  rating: number;
  comment: string | null;
  is_flagged: boolean;
  flagged_by: string | null;
  flagged_at: string | null;
  created_at: string;
  doctor_profiles: { full_name: string } | null;
}

interface DoctorBucket {
  doctorId: string;
  doctorName: string;
  count: number;
  avg: number;
  flagged: number;
  latestAt: string;
  hasRecentLow: boolean;
}

interface Props {
  ratings: Rating[];
}

/**
 * Summary of ratings grouped by doctor. Each row shows the doctor's avg
 * rating, total reviews, flagged count, and latest review date; clicking a
 * row routes to /dashboard/ratings/[doctorId] (or the /hr mirror) where the
 * full per-review list lives. The previous flat list was unreadable once
 * a doctor had more than a handful of reviews.
 */
export default function DoctorRatingsSummary({ ratings }: Props) {
  const [search, setSearch] = useState("");
  const [showFlaggedOnly, setShowFlaggedOnly] = useState(false);
  const pathname = usePathname();
  // Both /dashboard/ratings and /dashboard/hr/ratings use this view; route
  // the detail link to whichever section the admin is currently in so
  // breadcrumb/back-nav stays coherent.
  const detailBase = pathname.startsWith("/dashboard/hr/ratings")
    ? "/dashboard/hr/ratings"
    : "/dashboard/ratings";

  const buckets = useMemo<DoctorBucket[]>(() => {
    const byDoctor: Record<string, Rating[]> = {};
    for (const r of ratings) {
      (byDoctor[r.doctor_id] ??= []).push(r);
    }

    const thirtyDaysAgo = Date.now() - 30 * 24 * 60 * 60 * 1000;
    const result: DoctorBucket[] = Object.entries(byDoctor).map(
      ([doctorId, list]) => {
        const sum = list.reduce((s: number, r: Rating) => s + r.rating, 0);
        const flagged = list.filter((r: Rating) => r.is_flagged).length;
        const latestAt = list.reduce(
          (latest: string, r: Rating) =>
            r.created_at > latest ? r.created_at : latest,
          list[0].created_at,
        );
        // "Recent low" = a <4-star review in the last 30 days, used to flag
        // doctors trending downward without relying on explicit admin flags
        const hasRecentLow = list.some(
          (r: Rating) =>
            r.rating < 4 && new Date(r.created_at).getTime() >= thirtyDaysAgo,
        );
        return {
          doctorId,
          doctorName:
            list[0].doctor_profiles?.full_name ?? doctorId.slice(0, 8) + "…",
          count: list.length,
          avg: sum / list.length,
          flagged,
          latestAt,
          hasRecentLow,
        };
      },
    );
    return result.sort((a, b) => b.latestAt.localeCompare(a.latestAt));
  }, [ratings]);

  const filtered = useMemo(() => {
    let list = buckets;
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter((b) => b.doctorName.toLowerCase().includes(q));
    }
    if (showFlaggedOnly) {
      list = list.filter((b) => b.flagged > 0);
    }
    return list;
  }, [buckets, search, showFlaggedOnly]);

  const totalFlagged = buckets.reduce((s, b) => s + b.flagged, 0);

  function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  }

  function renderStars(avg: number) {
    const full = Math.round(avg);
    return (
      <div className="flex items-center gap-0.5">
        {[1, 2, 3, 4, 5].map((star) => (
          <svg
            key={star}
            className={`h-4 w-4 ${star <= full ? "text-yellow-400 fill-yellow-400" : "text-gray-200 fill-gray-200"}`}
            viewBox="0 0 20 20"
          >
            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
          </svg>
        ))}
      </div>
    );
  }

  function avgBadge(avg: number): string {
    if (avg >= 4.5) return "bg-green-50 text-green-700";
    if (avg >= 4) return "bg-green-50 text-green-600";
    if (avg >= 3) return "bg-amber-50 text-amber-700";
    if (avg >= 2) return "bg-orange-50 text-orange-700";
    return "bg-red-50 text-red-700";
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-3 mb-2">
        <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
          <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.562.562 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
          </svg>
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Ratings & Patient Feedback</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            One row per doctor — click a row to see all their reviews
          </p>
        </div>
      </div>

      {/* Search + Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mt-5 mb-4">
        <div className="flex-1 relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="text"
            placeholder="Search doctor..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          />
        </div>

        <button
          onClick={() => setShowFlaggedOnly((v) => !v)}
          className={`inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium border transition-colors ${
            showFlaggedOnly
              ? "bg-red-50 border-red-200 text-red-700"
              : "bg-white border-gray-200 text-gray-700 hover:bg-gray-50"
          }`}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
          </svg>
          With Flags ({totalFlagged})
        </button>
      </div>

      <p className="text-sm text-gray-500 mb-3">
        {filtered.length} doctor{filtered.length !== 1 ? "s" : ""} found
      </p>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Doctor</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Avg Rating</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Reviews</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Flagged</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Review</th>
                <th aria-label="chevron" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map((b) => (
                <tr
                  key={b.doctorId}
                  className={`group cursor-pointer hover:bg-gray-50/50 transition-colors ${b.flagged > 0 ? "bg-red-50/30" : ""}`}
                  onClick={() => {
                    // Navigate on row click for bigger tap target
                    window.location.href = `${detailBase}/${b.doctorId}`;
                  }}
                >
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
                        <span className="text-xs font-bold text-brand-teal">
                          {b.doctorName.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                        </span>
                      </div>
                      <div>
                        <Link
                          href={`${detailBase}/${b.doctorId}`}
                          onClick={(e) => e.stopPropagation()}
                          className="font-medium text-gray-900 hover:text-brand-teal"
                        >
                          {b.doctorName}
                        </Link>
                        {b.hasRecentLow && (
                          <p className="text-xs text-red-500 mt-0.5">Recent low-rating</p>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-2">
                      {renderStars(b.avg)}
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${avgBadge(b.avg)}`}>
                        {b.avg.toFixed(1)}
                      </span>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-gray-700">{b.count}</td>
                  <td className="px-5 py-4">
                    {b.flagged > 0 ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold bg-red-50 text-red-700">
                        <svg className="h-3 w-3 fill-current" viewBox="0 0 24 24">
                          <path d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
                        </svg>
                        {b.flagged}
                      </span>
                    ) : (
                      <span className="text-gray-400">—</span>
                    )}
                  </td>
                  <td className="px-5 py-4 text-gray-500 whitespace-nowrap">
                    {formatDate(b.latestAt)}
                  </td>
                  <td className="px-5 py-4 text-right">
                    <svg className="h-4 w-4 text-gray-300 group-hover:text-brand-teal transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                    </svg>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-5 py-12 text-center text-gray-400">
                    No doctors with ratings found
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
