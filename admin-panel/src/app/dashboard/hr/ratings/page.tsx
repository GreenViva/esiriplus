"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import DoctorRatingsSummary from "./DoctorRatingsSummary";

const PAGE_SIZE = 1000;

export default function HRRatingsPage() {
  const [allRatings, setAllRatings] = useState<
    {
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
    }[]
  >([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();

    supabase
      .from("doctor_ratings")
      .select(
        "rating_id, doctor_id, consultation_id, patient_session_id, rating, comment, is_flagged, flagged_by, flagged_at, created_at, doctor_profiles(full_name)"
      )
      .order("created_at", { ascending: false })
      .limit(PAGE_SIZE)
      .then(({ data }) => {
        setAllRatings(
          (data ?? []) as unknown as typeof allRatings
        );
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

  return (
    <>
      <RealtimeRefresh
        tables={["doctor_ratings"]}
        channelName="hr-ratings-realtime"
        onUpdate={fetchData}
      />
      <DoctorRatingsSummary ratings={allRatings} />
    </>
  );
}
