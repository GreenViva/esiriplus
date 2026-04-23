"use client";

import { useState, useEffect, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import DoctorManagementView, { type Doctor } from "@/components/DoctorManagementView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

const PAGE_SIZE = 20;

export default function HRDoctorManagementPage() {
  const searchParams = useSearchParams();
  const pageStr = searchParams.get("page") ?? "1";
  const page = Math.max(1, parseInt(pageStr, 10) || 1);

  const [allDoctors, setAllDoctors] = useState<Doctor[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();

    const from = (page - 1) * PAGE_SIZE;
    const to = from + PAGE_SIZE - 1;

    Promise.all([
      supabase
        .from("doctor_profiles")
        .select("*", { count: "exact", head: true }),
      supabase
        .from("doctor_profiles")
        .select(
          "doctor_id, full_name, email, phone, specialty, license_number, is_verified, is_available, average_rating, total_ratings, rejection_reason, created_at, profile_photo_url, license_document_url, certificates_url, bio, years_experience, country, suspended_until, is_banned, banned_at, ban_reason, flagged, flag_reason, flagged_at"
        )
        .order("created_at", { ascending: false })
        .range(from, to),
    ]).then(([countRes, doctorsRes]) => {
      if (doctorsRes.error) setFetchError(doctorsRes.error.message);
      const doctors = (doctorsRes.data ?? []).map((d) => ({
        ...d,
        is_banned: d.is_banned ?? false,
      }));
      setAllDoctors(doctors);
      setTotalPages(Math.ceil((countRes.count ?? 0) / PAGE_SIZE));
      setLoading(false);
    });
  }, [page]);

  useEffect(() => {
    setLoading(true);
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
        tables={["doctor_profiles", "admin_logs"]}
        channelName="hr-doctors-realtime"
        onUpdate={fetchData}
      />
      {fetchError && (
        <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 flex items-start justify-between">
          <div>
            <p className="text-sm font-medium text-red-700">
              Failed to load doctor data. Check your Supabase connection.
            </p>
            <p className="text-xs text-red-500 mt-1">{fetchError}</p>
          </div>
          <button
            onClick={() => { setFetchError(null); fetchData(); }}
            className="ml-4 px-3 py-1.5 text-xs font-medium text-red-700 bg-red-100 rounded-lg hover:bg-red-200 transition-colors flex-shrink-0"
          >
            Retry
          </button>
        </div>
      )}
      <DoctorManagementView
        doctors={allDoctors}
        currentPage={page}
        totalPages={totalPages}
        basePath="/dashboard/hr/doctors"
        onRefresh={fetchData}
      />
    </>
  );
}
