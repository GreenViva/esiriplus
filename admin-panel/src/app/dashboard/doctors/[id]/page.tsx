export const dynamic = "force-dynamic";

import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { createAdminClient } from "@/lib/supabase/admin";
import { formatDate, specialtyLabel } from "@/lib/utils";
import Badge from "@/components/ui/Badge";
import Card from "@/components/ui/Card";
import DoctorActions from "./DoctorActions";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import DownloadPdfButton from "@/components/DownloadPdfButton";
import type { DoctorProfile, DoctorDeviceBinding } from "@/lib/types/database";

interface Props {
  params: Promise<{ id: string }>;
}

export default async function DoctorDetailPage({ params }: Props) {
  const { id } = await params;
  const supabase = createAdminClient();

  const [profileRes, bindingsRes, authUserRes] = await Promise.all([
    supabase.from("doctor_profiles").select("*").eq("doctor_id", id).single(),
    supabase.from("doctor_device_bindings").select("*").eq("doctor_id", id),
    supabase.auth.admin.getUserById(id),
  ]);

  if (!profileRes.data) notFound();

  const doctor = profileRes.data as DoctorProfile;
  const bindings = (bindingsRes.data ?? []) as DoctorDeviceBinding[];
  const isBanned = doctor.is_banned ?? false;

  return (
    <div className="max-w-3xl">
      <RealtimeRefresh
        tables={["doctor_profiles", "admin_logs"]}
        channelName={`admin-doctor-detail-${id}`}
      />
      <Link
        href="/dashboard/doctors"
        className="inline-flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors mb-4"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
        </svg>
        Back to Doctors
      </Link>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Doctor Details</h1>
        <DownloadPdfButton doctor={doctor} variant="button" />
      </div>

      <Card className="mb-6">
        <div className="flex items-start gap-4 mb-6">
          {doctor.profile_photo_url ? (
            <Image
              src={doctor.profile_photo_url}
              alt=""
              width={64}
              height={64}
              className="h-16 w-16 rounded-full object-cover"
              unoptimized
            />
          ) : (
            <div className="h-16 w-16 rounded-full bg-gray-200 flex items-center justify-center text-lg font-medium text-gray-500">
              {doctor.full_name.charAt(0)}
            </div>
          )}
          <div>
            <h2 className="text-xl font-semibold text-gray-900">{doctor.full_name}</h2>
            <p className="text-sm text-gray-600">{doctor.email}</p>
            <div className="mt-1">
              <Badge variant={doctor.is_verified ? "success" : "warning"}>
                {doctor.is_verified ? "Verified" : "Pending Verification"}
              </Badge>
            </div>
          </div>
        </div>

        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          <Field label="Phone" value={doctor.phone} />
          <Field label="Specialty" value={specialtyLabel(doctor.specialty)} />
          {doctor.specialist_field && (
            <Field label="Specialist Field" value={doctor.specialist_field} />
          )}
          <Field label="License Number" value={doctor.license_number} />
          <Field label="Years of Experience" value={String(doctor.years_experience)} />
          <Field label="Country" value={doctor.country ?? "N/A"} />
          <Field label="Rating" value={`${doctor.average_rating.toFixed(1)} (${doctor.total_ratings} reviews)`} />
          <Field label="Available" value={doctor.is_available ? "Yes" : "No"} />
          {doctor.suspended_until && (
            <Field
              label="Suspended Until"
              value={new Date(doctor.suspended_until).toLocaleDateString("en-US", {
                month: "long", day: "numeric", year: "numeric", hour: "2-digit", minute: "2-digit",
              })}
            />
          )}
          <Field label="Registered" value={formatDate(doctor.created_at)} />
        </dl>

        {doctor.bio && (
          <div className="mt-4">
            <dt className="text-xs font-medium text-gray-500 uppercase">Bio</dt>
            <dd className="mt-1 text-sm text-gray-900">{doctor.bio}</dd>
          </div>
        )}
      </Card>

      {/* Documents */}
      {(doctor.license_document_url || doctor.certificates_url) && (
        <Card className="mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-3">Documents</h3>
          <div className="space-y-4">
            {doctor.license_document_url && (
              <DocumentPreview
                url={doctor.license_document_url}
                label="License Document"
              />
            )}
            {doctor.certificates_url && (
              <DocumentPreview
                url={doctor.certificates_url}
                label="Certificates"
              />
            )}
          </div>
        </Card>
      )}

      {/* Device Bindings */}
      <Card className="mb-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-3">Device Binding</h3>
        {bindings.length === 0 ? (
          <p className="text-sm text-gray-500">No device bound.</p>
        ) : (
          <div className="space-y-2 text-sm">
            {bindings.map((b) => (
              <div key={b.id} className="flex items-center justify-between">
                <div>
                  <span className="text-gray-700">
                    Fingerprint: {b.device_fingerprint.slice(0, 12)}...
                  </span>
                  <span className="ml-2">
                    <Badge variant={b.is_active ? "success" : "neutral"}>
                      {b.is_active ? "Active" : "Inactive"}
                    </Badge>
                  </span>
                </div>
                <span className="text-gray-400">{formatDate(b.bound_at)}</span>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Actions */}
      <DoctorActions
        doctorId={doctor.doctor_id}
        isVerified={doctor.is_verified}
        isAvailable={doctor.is_available}
        isBanned={isBanned}
        hasDevice={bindings.some((b) => b.is_active)}
      />
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500 uppercase">{label}</dt>
      <dd className="mt-0.5 text-gray-900">{value}</dd>
    </div>
  );
}

function DocumentPreview({ url, label }: { url: string; label: string }) {
  const lower = url.toLowerCase();
  const isPdf = lower.endsWith(".pdf") || lower.includes("pdf");
  const isImage =
    lower.endsWith(".jpg") ||
    lower.endsWith(".jpeg") ||
    lower.endsWith(".png") ||
    lower.endsWith(".webp");

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <p className="text-sm font-medium text-gray-700">{label}</p>
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-brand-teal hover:underline"
        >
          Open in new tab
        </a>
      </div>
      {isPdf ? (
        <iframe
          src={url}
          className="w-full h-96 rounded-lg border border-gray-200"
          title={label}
        />
      ) : isImage ? (
        <Image
          src={url}
          alt={label}
          width={600}
          height={400}
          className="w-full max-h-96 object-contain rounded-lg border border-gray-200"
          unoptimized
        />
      ) : (
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="block text-brand-teal hover:underline text-sm"
        >
          Download {label}
        </a>
      )}
    </div>
  );
}
