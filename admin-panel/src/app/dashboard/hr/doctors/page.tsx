export const dynamic = "force-dynamic";

import { createAdminClient, fetchAllAuthUsers } from "@/lib/supabase/admin";
import DoctorManagementView from "./DoctorManagementView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

export default async function HRDoctorManagementPage() {
  const supabase = createAdminClient();

  const { data: doctors } = await supabase
    .from("doctor_profiles")
    .select(
      "doctor_id, full_name, email, phone, specialty, license_number, is_verified, is_available, average_rating, total_ratings, rejection_reason, created_at"
    )
    .order("created_at", { ascending: false });

  // Get ban status from auth users (paginated)
  const authUsers = await fetchAllAuthUsers(supabase);

  const bannedIds = new Set<string>();
  for (const u of authUsers) {
    if (u.banned_until && new Date(u.banned_until) > new Date()) {
      bannedIds.add(u.id);
    }
  }

  const allDoctors = (doctors ?? []).map((d) => ({
    ...d,
    is_banned: bannedIds.has(d.doctor_id),
  }));

  return (
    <>
      <RealtimeRefresh
        tables={["doctor_profiles", "admin_logs"]}
        channelName="hr-doctors-realtime"
      />
      <DoctorManagementView doctors={allDoctors} />
    </>
  );
}
