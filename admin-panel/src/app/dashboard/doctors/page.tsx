"use client";

import { useState, useEffect, useCallback } from "react";
import Image from "next/image";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import { specialtyLabel } from "@/lib/utils";
import type { DoctorProfile } from "@/lib/types/database";
import DoctorSearch from "./DoctorSearch";
import RealtimeRefresh from "@/components/RealtimeRefresh";

const PAGE_SIZE = 20;

export default function DoctorsPage() {
  const searchParams = useSearchParams();
  // Default to "pending" so approved/rejected doctors don't clutter the
  // main applications queue. "All" is an explicit filter=all choice.
  const filter = searchParams.get("filter") ?? "pending";
  const q = searchParams.get("q") ?? undefined;
  const pageStr = searchParams.get("page") ?? "1";
  const page = Math.max(1, parseInt(pageStr, 10) || 1);

  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [counts, setCounts] = useState({ pending: 0, rejected: 0, approved: 0, flagged: 0, all: 0 });
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();
    const from = (page - 1) * PAGE_SIZE;
    const to = from + PAGE_SIZE - 1;

    // Build the filtered query
    let query = supabase
      .from("doctor_profiles")
      .select("*", { count: "exact" })
      .order("created_at", { ascending: false });

    if (filter === "pending") {
      query = query.eq("is_verified", false).is("rejection_reason", null);
    } else if (filter === "rejected") {
      query = query.eq("is_verified", false).not("rejection_reason", "is", null);
    } else if (filter === "verified") {
      query = query.eq("is_verified", true);
    } else if (filter === "flagged") {
      query = query.eq("flagged", true);
    }
    // filter === "all" → no extra predicates, shows everyone.

    // Apply search server-side
    if (q?.trim()) {
      query = query.or(
        `full_name.ilike.%${q}%,email.ilike.%${q}%,specialty.ilike.%${q}%,license_number.ilike.%${q}%`
      );
    }

    // Fetch the page + counts in parallel
    Promise.all([
      query.range(from, to),
      supabase.from("doctor_profiles").select("*", { count: "exact", head: true }),
      supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).eq("is_verified", false).is("rejection_reason", null),
      supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).eq("is_verified", false).not("rejection_reason", "is", null),
      supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).eq("is_verified", true),
      supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).eq("flagged", true),
    ]).then(([dataRes, allRes, pendingRes, rejectedRes, approvedRes, flaggedRes]) => {
      if (dataRes.error) setFetchError(dataRes.error.message);
      setDoctors((dataRes.data ?? []) as DoctorProfile[]);
      setTotalCount(dataRes.count ?? 0);
      setCounts({
        all: allRes.count ?? 0,
        pending: pendingRes.count ?? 0,
        rejected: rejectedRes.count ?? 0,
        approved: approvedRes.count ?? 0,
        flagged: flaggedRes.count ?? 0,
      });
      setLoading(false);
    });
  }, [filter, q, page]);

  useEffect(() => {
    setLoading(true);
    fetchData();
  }, [fetchData]);

  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  // Build base URL for pagination links
  function pageUrl(p: number) {
    const params = new URLSearchParams();
    if (filter) params.set("filter", filter);
    if (q) params.set("q", q);
    params.set("page", String(p));
    return `/dashboard/doctors?${params.toString()}`;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
      </div>
    );
  }

  return (
    <div>
      <RealtimeRefresh
        tables={["doctor_profiles", "admin_logs"]}
        channelName="admin-doctors-realtime"
        onUpdate={fetchData}
      />
      {/* Error banner */}
      {fetchError && (
        <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 flex items-start justify-between">
          <div>
            <p className="text-sm font-medium text-red-700">
              Failed to load doctor applications. Check your Supabase connection.
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

      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Doctor Applications</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Review and manage doctor verification requests
        </p>
      </div>

      {/* Search */}
      <DoctorSearch />

      {/* Tabs with counts */}
      <div className="flex rounded-xl border border-gray-200 bg-gray-50 mb-6 overflow-hidden">
        <TabLink
          href="/dashboard/doctors?filter=pending"
          active={filter === "pending"}
          label="Pending"
          count={counts.pending}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <TabLink
          href="/dashboard/doctors?filter=rejected"
          active={filter === "rejected"}
          label="Rejected"
          count={counts.rejected}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
            </svg>
          }
        />
        <TabLink
          href="/dashboard/doctors?filter=verified"
          active={filter === "verified"}
          label="Approved"
          count={counts.approved}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
            </svg>
          }
        />
        <TabLink
          href="/dashboard/doctors?filter=flagged"
          active={filter === "flagged"}
          label="Flagged"
          count={counts.flagged}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
            </svg>
          }
        />
        <TabLink
          href="/dashboard/doctors?filter=all"
          active={filter === "all"}
          label="All"
          count={counts.all}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
            </svg>
          }
        />
      </div>

      {/* Doctor cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {doctors.map((doc) => (
          <Link
            key={doc.doctor_id}
            href={`/dashboard/doctors/detail?id=${doc.doctor_id}`}
            className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 hover:shadow-md hover:border-gray-200 transition-all cursor-pointer"
          >
            <div className="flex items-center gap-3">
              {doc.profile_photo_url ? (
                <Image
                  src={doc.profile_photo_url}
                  alt=""
                  width={44}
                  height={44}
                  className="h-11 w-11 rounded-full object-cover flex-shrink-0"
                  unoptimized
                />
              ) : (
                <div className="h-11 w-11 rounded-full bg-brand-teal/10 flex items-center justify-center text-base font-semibold text-brand-teal flex-shrink-0">
                  {doc.full_name.charAt(0)}
                </div>
              )}
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-gray-900 truncate">{doc.full_name}</p>
                <p className="text-sm text-gray-500 truncate">{specialtyLabel(doc.specialty)}</p>
              </div>
              <div className="flex flex-col items-end gap-1 flex-shrink-0">
                <span
                  className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${
                    doc.is_verified
                      ? "bg-green-50 text-green-700"
                      : doc.rejection_reason
                        ? "bg-red-50 text-red-700"
                        : "bg-amber-50 text-amber-700"
                  }`}
                >
                  {doc.is_verified ? "Approved" : doc.rejection_reason ? "Rejected" : "Pending"}
                </span>
                {doc.flagged && (
                  <span
                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-red-100 text-red-700"
                    title={doc.flag_reason ?? "Auto-flagged"}
                  >
                    <svg className="h-3 w-3 fill-current" viewBox="0 0 24 24">
                      <path d="M3 3v1.5M3 21v-6m0 0l2.77-.693a9 9 0 016.208.682l.108.054a9 9 0 006.086.71l3.114-.732a48.524 48.524 0 01-.005-10.499l-3.11.732a9 9 0 01-6.085-.711l-.108-.054a9 9 0 00-6.208-.682L3 4.5M3 15V4.5" />
                    </svg>
                    Flagged
                  </span>
                )}
              </div>
            </div>
          </Link>
        ))}
      </div>

      {doctors.length === 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-12 text-center">
          <p className="text-gray-400">No doctors found.</p>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-6">
          <p className="text-sm text-gray-500">
            Page {page} of {totalPages} ({totalCount} total)
          </p>
          <div className="flex gap-2">
            {page > 1 && (
              <Link
                href={pageUrl(page - 1)}
                className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Previous
              </Link>
            )}
            {page < totalPages && (
              <Link
                href={pageUrl(page + 1)}
                className="px-3 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Next
              </Link>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function TabLink({
  href,
  active,
  label,
  count,
  icon,
}: {
  href: string;
  active: boolean;
  label: string;
  count: number;
  icon: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium transition-colors ${
        active
          ? "bg-white text-gray-900 shadow-sm border border-gray-200 rounded-xl -m-px"
          : "text-gray-500 hover:text-gray-700"
      }`}
    >
      <span className={active ? "text-gray-900" : "text-gray-400"}>{icon}</span>
      {label}
      <span
        className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-xs font-semibold ${
          active
            ? "bg-brand-teal/10 text-brand-teal"
            : "bg-gray-200 text-gray-500"
        }`}
      >
        {count}
      </span>
    </Link>
  );
}

