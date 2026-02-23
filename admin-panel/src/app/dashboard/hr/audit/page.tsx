export const dynamic = "force-dynamic";

import { createAdminClient, fetchAllAuthUsers } from "@/lib/supabase/admin";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import AuditLogView from "./AuditLogView";

export default async function HRAuditLogPage() {
  const supabase = createAdminClient();

  const { data: logs } = await supabase
    .from("admin_logs")
    .select("id, admin_id, action, target_type, target_id, details, created_at")
    .order("created_at", { ascending: false })
    .limit(200);

  const allLogs = (logs ?? []) as {
    id: string;
    admin_id: string;
    action: string;
    target_type: string | null;
    target_id: string | null;
    details: Record<string, unknown> | null;
    created_at: string;
  }[];

  // Resolve admin IDs to emails (paginated)
  const authUsers = await fetchAllAuthUsers(supabase);

  const emailMap: Record<string, string> = {};
  for (const u of authUsers) {
    emailMap[u.id] = u.email ?? u.id.slice(0, 8) + "...";
  }

  // Also resolve target doctor names where applicable
  const doctorTargetIds = allLogs
    .filter((l) => l.target_type === "doctor_profile" && l.target_id)
    .map((l) => l.target_id!);

  let doctorNameMap: Record<string, string> = {};
  if (doctorTargetIds.length > 0) {
    const { data: doctors } = await supabase
      .from("doctor_profiles")
      .select("doctor_id, full_name")
      .in("doctor_id", doctorTargetIds);

    for (const d of doctors ?? []) {
      doctorNameMap[d.doctor_id] = d.full_name;
    }
  }

  const enrichedLogs = allLogs.map((log) => ({
    ...log,
    admin_email: emailMap[log.admin_id] ?? log.admin_id.slice(0, 8) + "...",
    target_name: log.target_id ? (doctorNameMap[log.target_id] ?? null) : null,
  }));

  return (
    <>
      <RealtimeRefresh
        tables={["admin_logs"]}
        channelName="hr-audit-realtime"
      />
      <AuditLogView logs={enrichedLogs} />
    </>
  );
}
