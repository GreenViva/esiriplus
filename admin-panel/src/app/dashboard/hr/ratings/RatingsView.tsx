"use client";

import { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { toggleRatingFlag } from "@/lib/actions";

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

type RatingFilter = "all" | "5" | "4" | "3" | "2" | "1";
type TimeFilter = "all" | "today" | "week" | "month" | "year";

interface Props {
  ratings: Rating[];
}

export default function RatingsView({ ratings }: Props) {
  const router = useRouter();
  const [search, setSearch] = useState("");
  const [ratingFilter, setRatingFilter] = useState<RatingFilter>("all");
  const [timeFilter, setTimeFilter] = useState<TimeFilter>("all");
  const [showFlaggedOnly, setShowFlaggedOnly] = useState(false);
  const [loading, setLoading] = useState<string | null>(null);

  const flaggedCount = ratings.filter((r) => r.is_flagged).length;

  const filtered = useMemo(() => {
    let list = ratings;

    // Search
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(
        (r) =>
          (r.doctor_profiles?.full_name ?? "").toLowerCase().includes(q) ||
          (r.comment ?? "").toLowerCase().includes(q)
      );
    }

    // Rating filter
    if (ratingFilter !== "all") {
      const target = parseInt(ratingFilter);
      list = list.filter((r) => r.rating === target);
    }

    // Time filter
    if (timeFilter !== "all") {
      const now = new Date();
      let cutoff: Date;
      switch (timeFilter) {
        case "today":
          cutoff = new Date(now.getFullYear(), now.getMonth(), now.getDate());
          break;
        case "week":
          cutoff = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
          break;
        case "month":
          cutoff = new Date(now.getFullYear(), now.getMonth(), 1);
          break;
        case "year":
          cutoff = new Date(now.getFullYear(), 0, 1);
          break;
        default:
          cutoff = new Date(0);
      }
      list = list.filter((r) => new Date(r.created_at) >= cutoff);
    }

    // Flagged only
    if (showFlaggedOnly) {
      list = list.filter((r) => r.is_flagged);
    }

    return list;
  }, [ratings, search, ratingFilter, timeFilter, showFlaggedOnly]);

  async function handleToggleFlag(ratingId: string, currentlyFlagged: boolean) {
    setLoading(ratingId);
    await toggleRatingFlag(ratingId, !currentlyFlagged);
    setLoading(null);
    router.refresh();
  }

  function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  }

  function renderStars(rating: number) {
    return (
      <div className="flex items-center gap-0.5">
        {[1, 2, 3, 4, 5].map((star) => (
          <svg
            key={star}
            className={`h-4 w-4 ${star <= rating ? "text-yellow-400 fill-yellow-400" : "text-gray-200 fill-gray-200"}`}
            viewBox="0 0 20 20"
          >
            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
          </svg>
        ))}
      </div>
    );
  }

  const ratingBadgeColor: Record<number, string> = {
    5: "bg-green-50 text-green-700",
    4: "bg-green-50 text-green-600",
    3: "bg-amber-50 text-amber-700",
    2: "bg-orange-50 text-orange-700",
    1: "bg-red-50 text-red-700",
  };

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
            Review all patient ratings, identify patterns, and flag concerns
          </p>
        </div>
      </div>

      {/* Search + Filters row */}
      <div className="flex flex-col sm:flex-row gap-3 mt-5 mb-4">
        {/* Search */}
        <div className="flex-1 relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="text"
            placeholder="Search by doctor name or comment..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          />
        </div>

        {/* Rating filter */}
        <select
          value={ratingFilter}
          onChange={(e) => setRatingFilter(e.target.value as RatingFilter)}
          className="px-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal min-w-[140px]"
        >
          <option value="all">All Ratings</option>
          <option value="5">5 Stars</option>
          <option value="4">4 Stars</option>
          <option value="3">3 Stars</option>
          <option value="2">2 Stars</option>
          <option value="1">1 Star</option>
        </select>

        {/* Time filter */}
        <select
          value={timeFilter}
          onChange={(e) => setTimeFilter(e.target.value as TimeFilter)}
          className="px-4 py-2.5 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal min-w-[130px]"
        >
          <option value="all">All Time</option>
          <option value="today">Today</option>
          <option value="week">This Week</option>
          <option value="month">This Month</option>
          <option value="year">This Year</option>
        </select>

        {/* Flagged toggle */}
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
          Flagged ({flaggedCount})
        </button>
      </div>

      {/* Count */}
      <p className="text-sm text-gray-500 mb-3">
        {filtered.length} review{filtered.length !== 1 ? "s" : ""} found
      </p>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Doctor</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Rating</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Comment</th>
                <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Date</th>
                <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Flags</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map((r) => {
                const doctorName = r.doctor_profiles?.full_name ?? r.doctor_id.slice(0, 8) + "...";
                const isLowRating = r.rating < 4;
                const isLoading = loading === r.rating_id;

                return (
                  <tr
                    key={r.rating_id}
                    className={`hover:bg-gray-50/50 transition-colors ${r.is_flagged ? "bg-red-50/30" : ""}`}
                  >
                    {/* Doctor */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
                          <span className="text-xs font-bold text-brand-teal">
                            {doctorName.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
                          </span>
                        </div>
                        <span className="font-medium text-gray-900">{doctorName}</span>
                      </div>
                    </td>

                    {/* Rating */}
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        {renderStars(r.rating)}
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${ratingBadgeColor[r.rating] ?? "bg-gray-100 text-gray-600"}`}>
                          {r.rating}.0
                        </span>
                      </div>
                    </td>

                    {/* Comment */}
                    <td className="px-5 py-4 max-w-sm">
                      {r.comment ? (
                        <p className="text-gray-700 line-clamp-2">{r.comment}</p>
                      ) : (
                        <span className="text-gray-400 italic">
                          {isLowRating ? "Missing (required)" : "No comment"}
                        </span>
                      )}
                      {isLowRating && !r.comment && (
                        <span className="inline-flex items-center gap-1 mt-1 text-xs text-red-500">
                          <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                          </svg>
                          Comment required for ratings below 4 stars
                        </span>
                      )}
                    </td>

                    {/* Date */}
                    <td className="px-5 py-4 text-gray-500 whitespace-nowrap">
                      {formatDate(r.created_at)}
                    </td>

                    {/* Flag action */}
                    <td className="px-5 py-4 text-center">
                      <button
                        onClick={() => handleToggleFlag(r.rating_id, r.is_flagged)}
                        disabled={isLoading}
                        className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                          r.is_flagged
                            ? "bg-red-100 text-red-700 hover:bg-red-200"
                            : "bg-gray-100 text-gray-500 hover:bg-gray-200"
                        } disabled:opacity-50`}
                        title={r.is_flagged ? "Unflag this review" : "Flag this review for attention"}
                      >
                        {isLoading ? (
                          "..."
                        ) : (
                          <>
                            <svg className="h-3.5 w-3.5" fill={r.is_flagged ? "currentColor" : "none"} viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
                            </svg>
                            {r.is_flagged ? "Flagged" : "Flag"}
                          </>
                        )}
                      </button>
                    </td>
                  </tr>
                );
              })}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-5 py-12 text-center text-gray-400">
                    No ratings found
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
