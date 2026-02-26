export const dynamic = "force-dynamic";

import Image from "next/image";
import Link from "next/link";
import { createAdminClient } from "@/lib/supabase/admin";
import { specialtyLabel } from "@/lib/utils";
import type { DoctorProfile } from "@/lib/types/database";
import DoctorCardActions from "./DoctorCardActions";
import DoctorSearch from "./DoctorSearch";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import DownloadPdfButton from "@/components/DownloadPdfButton";

interface Props {
  searchParams: Promise<{ filter?: string; q?: string }>;
}

export default async function DoctorsPage({ searchParams }: Props) {
  const { filter, q } = await searchParams;
  const supabase = createAdminClient();

  // Fetch all doctors
  const { data, error: fetchError } = await supabase
    .from("doctor_profiles")
    .select("*")
    .order("created_at", { ascending: false });

  const allDoctors = (data ?? []) as DoctorProfile[];

  // Derive counts from the full list
  // Pending: Anyone not yet verified (matches dashboard count)
  const pendingDoctors = allDoctors.filter((d) => !d.is_verified);
  // Specifically rejected (subset of unverified)
  const rejectedDoctors = allDoctors.filter((d) => !d.is_verified && !!d.rejection_reason);
  // Approved
  const approvedDoctors = allDoctors.filter((d) => d.is_verified);

  let doctors = allDoctors;
  if (filter === "pending") {
    doctors = pendingDoctors;
  } else if (filter === "rejected") {
    doctors = rejectedDoctors;
  } else if (filter === "verified") {
    doctors = approvedDoctors;
  }

  // Apply search query
  if (q?.trim()) {
    const query = q.toLowerCase();
    doctors = doctors.filter(
      (d) =>
        d.full_name.toLowerCase().includes(query) ||
        d.email.toLowerCase().includes(query) ||
        d.specialty.toLowerCase().includes(query) ||
        (d.license_number ?? "").toLowerCase().includes(query),
    );
  }

  return (
    <div>
      <RealtimeRefresh
        tables={["doctor_profiles", "admin_logs"]}
        channelName="admin-doctors-realtime"
      />
      {/* Error banner */}
      {fetchError && (
        <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200">
          <p className="text-sm font-medium text-red-700">
            Failed to load doctor applications. Check your Supabase connection.
          </p>
          <p className="text-xs text-red-500 mt-1">{fetchError.message}</p>
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
          count={pendingDoctors.length}
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
          count={rejectedDoctors.length}
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
          count={approvedDoctors.length}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
            </svg>
          }
        />
        <TabLink
          href="/dashboard/doctors"
          active={!filter}
          label="All"
          count={allDoctors.length}
          icon={
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
            </svg>
          }
        />
      </div>

      {/* Doctor cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {doctors.map((doc) => (
          <div
            key={doc.doctor_id}
            className="bg-white rounded-xl border border-gray-100 shadow-sm p-6"
          >
            {/* Top row: avatar + name + badge */}
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                {doc.profile_photo_url ? (
                  <Image
                    src={doc.profile_photo_url}
                    alt=""
                    width={44}
                    height={44}
                    className="h-11 w-11 rounded-full object-cover"
                    unoptimized
                  />
                ) : (
                  <div className="h-11 w-11 rounded-full bg-brand-teal/10 flex items-center justify-center text-base font-semibold text-brand-teal">
                    {doc.full_name.charAt(0)}
                  </div>
                )}
                <div>
                  <p className="font-semibold text-gray-900">{doc.full_name}</p>
                  <p className="text-sm text-gray-400">{specialtyLabel(doc.specialty)}</p>
                </div>
              </div>
              <span
                className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${
                  doc.is_verified
                    ? "bg-green-50 text-green-700"
                    : doc.rejection_reason
                      ? "bg-red-50 text-red-700"
                      : "bg-amber-50 text-amber-700"
                }`}
              >
                {doc.is_verified ? "Approved" : doc.rejection_reason ? "Rejected" : "Pending Verification"}
              </span>
            </div>

            {/* Details grid */}
            <div className="grid grid-cols-2 gap-x-6 gap-y-3 mb-4 text-sm">
              <div>
                <p className="text-gray-400">Email:</p>
                <p className="text-gray-900 font-medium truncate">{doc.email}</p>
              </div>
              <div>
                <p className="text-gray-400">Phone:</p>
                <p className="text-gray-900 font-medium">{doc.phone}</p>
              </div>
              <div>
                <p className="text-gray-400">Country:</p>
                <p className="text-gray-900 font-medium">{doc.country || "N/A"}</p>
              </div>
              <div>
                <p className="text-gray-400">Medical License:</p>
                <p className="text-gray-900 font-medium">{doc.license_number}</p>
              </div>
              <div>
                <p className="text-gray-400">Experience:</p>
                <p className="text-gray-900 font-medium">{doc.years_experience} years</p>
              </div>
            </div>

            {/* Uploaded Credentials */}
            {(doc.profile_photo_url || doc.license_document_url || doc.certificates_url) ? (
              <div className="mb-4 space-y-3">
                {doc.profile_photo_url && (
                  <CredentialPreview url={doc.profile_photo_url} label="Profile Photo" />
                )}
                {doc.license_document_url && (
                  <CredentialPreview url={doc.license_document_url} label="Medical License" />
                )}
                {doc.certificates_url && (
                  <CredentialPreview url={doc.certificates_url} label="Certificates" />
                )}
              </div>
            ) : (
              <div className="mb-4 p-3 rounded-lg bg-amber-50 border border-amber-100">
                <p className="text-sm text-amber-700 font-medium">No credentials uploaded</p>
              </div>
            )}

            {/* Bio */}
            {doc.bio && (
              <p className="text-sm text-gray-400 mb-4 line-clamp-2">{doc.bio}</p>
            )}

            {/* Rejection reason */}
            {doc.rejection_reason && (
              <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-100">
                <p className="text-xs font-medium text-red-700 mb-0.5">Rejection Reason:</p>
                <p className="text-sm text-red-600">{doc.rejection_reason}</p>
              </div>
            )}

            {/* Action buttons */}
            <div className="flex items-center gap-2 pt-2 border-t border-gray-50">
              <Link
                href={`/dashboard/doctors/${doc.doctor_id}`}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-gray-200 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
              >
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
                View Credentials
              </Link>
              <DownloadPdfButton doctor={doc} />
              {!doc.is_verified && (
                <DoctorCardActions doctorId={doc.doctor_id} />
              )}
            </div>
          </div>
        ))}
      </div>

      {doctors.length === 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-12 text-center">
          <p className="text-gray-400">No doctors found.</p>
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

function CredentialPreview({ url, label }: { url: string; label: string }) {
  const lower = url.toLowerCase();
  const isPdf = lower.endsWith(".pdf") || lower.includes("pdf");
  const isImage =
    lower.endsWith(".jpg") ||
    lower.endsWith(".jpeg") ||
    lower.endsWith(".png") ||
    lower.endsWith(".webp");

  return (
    <div className="rounded-lg border border-gray-200 overflow-hidden">
      <div className="flex items-center justify-between px-3 py-2 bg-gray-50 border-b border-gray-200">
        <p className="text-xs font-medium text-gray-700">{label}</p>
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-brand-teal hover:underline"
        >
          Open full
        </a>
      </div>
      {isPdf ? (
        <iframe
          src={url}
          className="w-full h-48 bg-white"
          title={label}
        />
      ) : isImage ? (
        <Image
          src={url}
          alt={label}
          width={400}
          height={200}
          className="w-full h-48 object-contain bg-white"
          unoptimized
        />
      ) : (
        <div className="px-3 py-3">
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm text-brand-teal hover:underline"
          >
            Download {label}
          </a>
        </div>
      )}
    </div>
  );
}
