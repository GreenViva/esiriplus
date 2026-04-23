"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { createClient } from "@/lib/supabase/client";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import RatingsView from "./RatingsView";

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

interface Props {
  doctorId: string;
  /** Which listing sent the user here — drives the back link. */
  backHref: string;
}

/**
 * Detail page for one doctor's ratings: header card with avg + count, then
 * the familiar per-review table filtered to this doctor. Server query is
 * scoped by doctor_id so this scales past the 1000-row summary cap.
 */
export default function DoctorRatingsDetail({ doctorId, backHref }: Props) {
  const [ratings, setRatings] = useState<Rating[]>([]);
  const [doctorName, setDoctorName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();
    supabase
      .from("doctor_ratings")
      .select(
        "rating_id, doctor_id, consultation_id, patient_session_id, rating, comment, is_flagged, flagged_by, flagged_at, created_at, doctor_profiles(full_name)",
      )
      .eq("doctor_id", doctorId)
      .order("created_at", { ascending: false })
      .then(({ data }) => {
        const list = (data ?? []) as unknown as Rating[];
        setRatings(list);
        setDoctorName(list[0]?.doctor_profiles?.full_name ?? null);
        setLoading(false);
      });
  }, [doctorId]);

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

  const count = ratings.length;
  const avg = count === 0 ? 0 : ratings.reduce((s, r) => s + r.rating, 0) / count;
  const flagged = ratings.filter((r) => r.is_flagged).length;
  const resolvedName = doctorName ?? doctorId.slice(0, 8) + "…";

  return (
    <>
      <RealtimeRefresh
        tables={["doctor_ratings"]}
        channelName={`admin-ratings-detail-${doctorId}`}
        onUpdate={fetchData}
      />

      {/* Back link */}
      <Link
        href={backHref}
        className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-brand-teal mb-4"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
        Back to all doctors
      </Link>

      {/* Doctor card */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 mb-6">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
            <span className="text-lg font-bold text-brand-teal">
              {resolvedName.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()}
            </span>
          </div>
          <div className="flex-1">
            <h1 className="text-xl font-bold text-gray-900">{resolvedName}</h1>
            <div className="flex items-center gap-4 mt-1 text-sm text-gray-500">
              <span>
                <span className="font-semibold text-gray-900">{avg.toFixed(1)}</span> avg rating
              </span>
              <span>
                <span className="font-semibold text-gray-900">{count}</span> review{count !== 1 ? "s" : ""}
              </span>
              {flagged > 0 && (
                <span className="text-red-600">
                  <span className="font-semibold">{flagged}</span> flagged
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <RatingsView ratings={ratings} onRefresh={fetchData} />
    </>
  );
}
