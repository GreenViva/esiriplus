export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import DoctorManagementView from "@/components/DoctorManagementView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

const PAGE_SIZE = 20;

interface Props {
  searchParams: Promise<{ page?: string }>;
}

export default async function AdminDoctorManagementPage({ searchParams }: Props) {
  const { page: pageStr } = await searchParams;
  const page = Math.max(1, parseInt(pageStr ?? "1", 10) || 1);
  const supabase = createAdminClient();

  // Get total count
  const { count: totalCount } = await supabase
    .from("doctor_profiles")
    .select("*", { count: "exact", head: true });

  // Paginated fetch
  const from = (page - 1) * PAGE_SIZE;
  const to = from + PAGE_SIZE - 1;

  const { data: doctors, error: fetchError } = await supabase
    .from("doctor_profiles")
    .select(
      "doctor_id, full_name, email, phone, specialty, license_number, is_verified, is_available, average_rating, total_ratings, rejection_reason, created_at, profile_photo_url, license_document_url, certificates_url, bio, years_experience, country, suspended_until, is_banned, banned_at, ban_reason"
    )
    .order("created_at", { ascending: false })
    .range(from, to);

  const allDoctors = (doctors ?? []).map((d) => ({
    ...d,
    is_banned: d.is_banned ?? false,
  }));

  const totalPages = Math.ceil((totalCount ?? 0) / PAGE_SIZE);

  return (
    <>
      <RealtimeRefresh
        tables={["doctor_profiles", "admin_logs"]}
        channelName="admin-doctor-mgmt-realtime"
      />
      {fetchError && (
        <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200">
          <p className="text-sm font-medium text-red-700">
            Failed to load doctor data. Check your Supabase connection.
          </p>
          <p className="text-xs text-red-500 mt-1">{fetchError.message}</p>
        </div>
      )}
      <DoctorManagementView
        doctors={allDoctors}
        currentPage={page}
        totalPages={totalPages}
        basePath="/dashboard/doctors/management"
      />
    </>
  );
}
