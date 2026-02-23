export const dynamic = "force-dynamic";

import { createAdminClient, fetchAllAuthUsers } from "@/lib/supabase/admin";
import StatCard from "@/components/StatCard";
import RealtimeRefresh from "@/components/RealtimeRefresh";

export default async function HRDashboardPage() {
  const supabase = createAdminClient();

  const [doctorsRes, ratingsRes, logsRes] = await Promise.all([
    supabase.from("doctor_profiles").select("doctor_id, is_verified, is_available, average_rating, total_ratings"),
    supabase.from("doctor_ratings").select("rating_id, rating"),
    supabase
      .from("admin_logs")
      .select("id, admin_id, action, target_type, target_id, details, created_at")
      .order("created_at", { ascending: false })
      .limit(10),
  ]);

  const doctors = doctorsRes.data ?? [];
  const ratings = ratingsRes.data ?? [];
  const logs = logsRes.data ?? [];

  // Fetch auth users for ban status (paginated)
  const authUsers = await fetchAllAuthUsers(supabase);

  const bannedDoctorIds = new Set<string>();
  for (const u of authUsers) {
    if (u.banned_until && new Date(u.banned_until) > new Date()) {
      bannedDoctorIds.add(u.id);
    }
  }

  const totalDoctors = doctors.length;
  const activeDoctors = doctors.filter((d) => d.is_verified && d.is_available).length;
  const pendingDoctors = doctors.filter((d) => !d.is_verified).length;
  const suspendedDoctors = doctors.filter(
    (d) => d.is_verified && !d.is_available && !bannedDoctorIds.has(d.doctor_id)
  ).length;
  const bannedDoctors = doctors.filter((d) => bannedDoctorIds.has(d.doctor_id)).length;
  const lowRated = doctors.filter((d) => d.total_ratings > 0 && d.average_rating < 3).length;
  const totalReviews = ratings.length;
  const recentActions = logs.length;

  return (
    <div>
      <RealtimeRefresh
        tables={["doctor_profiles", "doctor_ratings", "admin_logs"]}
        channelName="hr-dashboard-realtime"
      />
      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
            <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 21h19.5m-18-18v18m10.5-18v18m6-13.5V21M6.75 6.75h.75m-.75 3h.75m-.75 3h.75m3-6h.75m-.75 3h.75m-.75 3h.75M6.75 21v-3.375c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21M3 3h12m-.75 4.5H21m-3.75 3H21m-3.75 3H21" />
            </svg>
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">HR Dashboard</h1>
            <p className="text-sm text-gray-400 mt-0.5">
              Doctor governance and quality oversight
            </p>
          </div>
        </div>
        <a
          href="/dashboard/hr"
          className="text-gray-400 hover:text-gray-600 transition-colors"
          title="Refresh"
        >
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" />
          </svg>
        </a>
      </div>

      {/* Stat cards — row 1 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
        <StatCard
          label="Total Doctors"
          value={totalDoctors}
          iconBg="bg-blue-50"
          icon={
            <svg className="h-5 w-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
            </svg>
          }
        />
        <StatCard
          label="Active"
          value={activeDoctors}
          iconBg="bg-green-50"
          icon={
            <svg className="h-5 w-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zM4 19.235v-.11a6.375 6.375 0 0112.75 0v.109A12.318 12.318 0 0110.374 21c-2.331 0-4.512-.645-6.374-1.766z" />
            </svg>
          }
        />
        <StatCard
          label="Pending"
          value={pendingDoctors}
          iconBg="bg-amber-50"
          icon={
            <svg className="h-5 w-5 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
          }
        />
        <StatCard
          label="Suspended"
          value={suspendedDoctors}
          iconBg="bg-orange-50"
          icon={
            <svg className="h-5 w-5 text-orange-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
            </svg>
          }
        />
      </div>

      {/* Stat cards — row 2 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard
          label="Banned"
          value={bannedDoctors}
          iconBg="bg-red-50"
          icon={
            <svg className="h-5 w-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M22 10.5h-6m-2.25-4.125a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zM4 19.235v-.11a6.375 6.375 0 0112.75 0v.109A12.318 12.318 0 0110.374 21c-2.331 0-4.512-.645-6.374-1.766z" />
            </svg>
          }
        />
        <StatCard
          label="Low Rated (<3★)"
          value={lowRated}
          iconBg="bg-yellow-50"
          icon={
            <svg className="h-5 w-5 text-yellow-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.562.562 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
            </svg>
          }
        />
        <StatCard
          label="Total Reviews"
          value={totalReviews}
          iconBg="bg-purple-50"
          icon={
            <svg className="h-5 w-5 text-purple-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.562.562 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
            </svg>
          }
        />
        <StatCard
          label="Recent Actions"
          value={recentActions}
          iconBg="bg-indigo-50"
          icon={
            <svg className="h-5 w-5 text-indigo-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15a2.25 2.25 0 012.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V19.5a2.25 2.25 0 002.25 2.25h6.75a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08" />
            </svg>
          }
        />
      </div>

      {/* Recent Actions */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <div className="flex items-center gap-2.5 mb-5">
          <div className="w-8 h-8 rounded-lg bg-indigo-50 flex items-center justify-center">
            <svg className="h-4 w-4 text-indigo-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h3.75M9 15h3.75M9 18h3.75m3 .75H18a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08m-5.801 0c-.065.21-.1.433-.1.664 0 .414.336.75.75.75h4.5a.75.75 0 00.75-.75 2.25 2.25 0 00-.1-.664m-5.8 0A2.251 2.251 0 0113.5 2.25H15a2.25 2.25 0 012.15 1.586m-5.8 0c-.376.023-.75.05-1.124.08C9.095 4.01 8.25 4.973 8.25 6.108V19.5a2.25 2.25 0 002.25 2.25h6.75a2.25 2.25 0 002.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 00-1.123-.08" />
            </svg>
          </div>
          <h2 className="text-lg font-bold text-gray-900">Recent Actions</h2>
        </div>

        {logs.length > 0 ? (
          <div className="space-y-3">
            {logs.slice(0, 10).map((log) => (
              <div
                key={log.id}
                className="flex items-center justify-between py-3 border-b border-gray-50 last:border-0"
              >
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-gray-50 flex items-center justify-center flex-shrink-0">
                    <svg className="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                    </svg>
                  </div>
                  <div>
                    <p className="text-sm text-gray-900">
                      {formatAction(log.action, log.details as Record<string, unknown> | null)}
                    </p>
                    <p className="text-xs text-gray-400">
                      {(log.admin_id ?? "system").slice(0, 8)}... &middot; {timeAgo(log.created_at)}
                    </p>
                  </div>
                </div>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-gray-100 text-gray-500">
                  {log.action.replace(/_/g, " ")}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-400 text-center py-8">
            No recent actions yet.
          </p>
        )}
      </div>
    </div>
  );
}

function formatAction(action: string, details: Record<string, unknown> | null): string {
  const d = details ?? {};
  switch (action) {
    case "create_portal_user":
    case "portal_user_created":
      return `Created ${d.role ?? "user"} account: ${d.email ?? "unknown"}`;
    case "initial_admin_setup":
      return `Initial admin setup: ${d.email ?? ""}`;
    case "approve_doctor":
      return "Approved doctor application";
    case "reject_doctor":
      return `Rejected doctor${d.reason ? `: ${d.reason}` : ""}`;
    case "suspend_doctor":
      return "Suspended doctor";
    case "unsuspend_doctor":
      return "Unsuspended doctor";
    case "ban_doctor":
      return "Banned doctor";
    case "unban_doctor":
      return "Unbanned doctor";
    case "warn_doctor":
      return `Warned doctor${d.message ? `: "${d.message}"` : ""}`;
    case "suspend_user":
      return "Suspended user";
    case "unsuspend_user":
      return "Unsuspended user";
    case "flag_rating":
      return "Flagged a rating";
    case "unflag_rating":
      return "Unflagged a rating";
    case "deauthorize_device":
      return "Deauthorized device";
    case "doctor_registered":
      return `New doctor registered: ${d.full_name ?? ""}`;
    case "consultation_created":
      return `Consultation created (${d.service_type ?? "general"})`;
    case "payment_completed":
      return `Payment of ${d.amount ?? 0} ${d.currency ?? "TZS"} completed`;
    case "rating_submitted":
      return `Rating submitted: ${d.rating ?? "?"}★`;
    case "delete_user_role":
      return `Removed ${d.role ?? "unknown"} role from user`;
    default:
      return action.replace(/_/g, " ");
  }
}

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} minute${mins > 1 ? "s" : ""} ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs} hour${hrs > 1 ? "s" : ""} ago`;
  const days = Math.floor(hrs / 24);
  return `${days} day${days > 1 ? "s" : ""} ago`;
}
