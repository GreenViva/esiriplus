export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import { formatCurrency, serviceTypeLabel } from "@/lib/utils";
import StatCard from "@/components/StatCard";
import ActivityFeed, { type ActivityItem } from "@/components/ActivityFeed";
import RealtimeRefresh from "@/components/RealtimeRefresh";

export default async function DashboardPage() {
  const supabase = createAdminClient();

  // ── Stat card queries (all hit the database) ──────────────────
  const [
    doctorsRes,
    verifiedDoctorsRes,
    pendingApplicationsRes,
    patientsRes,
    activeConsultationsRes,
    completedConsultationsRes,
    revenueRes,
    pendingCredentialsRes,
  ] = await Promise.all([
    supabase.from("doctor_profiles").select("*", { count: "exact", head: true }),
    supabase
      .from("doctor_profiles")
      .select("*", { count: "exact", head: true })
      .eq("is_verified", true),
    supabase
      .from("doctor_profiles")
      .select("*", { count: "exact", head: true })
      .eq("is_verified", false),
    supabase.from("patient_sessions").select("*", { count: "exact", head: true }),
    supabase
      .from("consultations")
      .select("*", { count: "exact", head: true })
      .in("status", ["active", "in_progress"]),
    supabase
      .from("consultations")
      .select("*", { count: "exact", head: true })
      .eq("status", "completed"),
    supabase
      .from("service_access_payments")
      .select("amount")
      .eq("status", "completed")
      .limit(1000),
    supabase
      .from("doctor_profiles")
      .select("*", { count: "exact", head: true })
      .is("license_document_url", null),
  ]);

  // Surface any query errors so admins see them
  const queryErrors = [
    doctorsRes.error,
    verifiedDoctorsRes.error,
    pendingApplicationsRes.error,
    patientsRes.error,
    activeConsultationsRes.error,
    completedConsultationsRes.error,
    revenueRes.error,
    pendingCredentialsRes.error,
  ].filter(Boolean);

  const totalDoctors = doctorsRes.count ?? 0;
  const verifiedDoctors = verifiedDoctorsRes.count ?? 0;
  const pendingApplications = pendingApplicationsRes.count ?? 0;
  const totalPatients = patientsRes.count ?? 0;
  const activeConsultations = activeConsultationsRes.count ?? 0;
  const completedConsultations = completedConsultationsRes.count ?? 0;
  const pendingCredentials = pendingCredentialsRes.count ?? 0;

  const totalRevenue = (revenueRes.data ?? []).reduce(
    (sum, p) => sum + (p.amount ?? 0),
    0,
  );

  function formatRevenue(amount: number): string {
    if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1)}M TZS`;
    if (amount >= 1_000) return `${Math.round(amount / 1_000)}K TZS`;
    return formatCurrency(amount);
  }

  // ── Recent activity from multiple tables ──────────────────────
  const [paymentsRes, recentDoctorsRes, recentConsultationsRes, logsRes] =
    await Promise.all([
      // Recent payments
      supabase
        .from("payments")
        .select("payment_id, amount, currency, payment_type, status, service_access_payments(service_type), created_at")
        .order("created_at", { ascending: false })
        .limit(10),
      // Recently registered / verified doctors
      supabase
        .from("doctor_profiles")
        .select("doctor_id, full_name, specialty, is_verified, created_at, updated_at")
        .order("updated_at", { ascending: false })
        .limit(10),
      // Recent consultations
      supabase
        .from("consultations")
        .select("consultation_id, status, service_type, doctor_id, doctor_profiles(full_name), created_at, updated_at")
        .order("updated_at", { ascending: false })
        .limit(10),
      // Admin actions
      supabase
        .from("admin_logs")
        .select("*")
        .order("created_at", { ascending: false })
        .limit(10),
    ]);

  // Build unified activity list
  const activities: ActivityItem[] = [];

  // Payments
  for (const p of paymentsRes.data ?? []) {
    const sap = p.service_access_payments as unknown as { service_type?: string } | null;
    const serviceLabel = serviceTypeLabel(sap?.service_type ?? p.payment_type ?? "service");
    activities.push({
      id: `pay-${p.payment_id}`,
      type: "payment",
      title: `Payment of ${(p.amount ?? 0).toLocaleString()} ${p.currency ?? "TZS"} for ${serviceLabel}`,
      subtitle: p.status === "completed" ? "Completed" : p.status === "pending" ? "Pending" : p.status,
      timestamp: p.created_at,
    });
  }

  // Doctors
  for (const d of recentDoctorsRes.data ?? []) {
    if (d.is_verified) {
      activities.push({
        id: `doc-ver-${d.doctor_id}`,
        type: "verification",
        title: `Dr. ${d.full_name} verified`,
        subtitle: serviceTypeLabel(d.specialty),
        timestamp: d.updated_at ?? d.created_at,
      });
    } else {
      activities.push({
        id: `doc-reg-${d.doctor_id}`,
        type: "registration",
        title: `Dr. ${d.full_name} registered`,
        subtitle: `${serviceTypeLabel(d.specialty)} — awaiting verification`,
        timestamp: d.created_at,
      });
    }
  }

  // Consultations
  for (const c of recentConsultationsRes.data ?? []) {
    const dp = c.doctor_profiles as unknown as { full_name?: string } | null;
    const doctorName = dp?.full_name ? `Dr. ${dp.full_name}` : "a doctor";
    const serviceLabel = serviceTypeLabel(c.service_type ?? "general");

    if (c.status === "completed") {
      activities.push({
        id: `con-${c.consultation_id}`,
        type: "consultation",
        title: `Consultation completed — ${serviceLabel} with ${doctorName}`,
        subtitle: "Completed",
        timestamp: c.updated_at ?? c.created_at,
      });
    } else if (c.status === "active" || c.status === "in_progress") {
      activities.push({
        id: `con-${c.consultation_id}`,
        type: "consultation",
        title: `Consultation in progress — ${serviceLabel} with ${doctorName}`,
        subtitle: "Active",
        timestamp: c.updated_at ?? c.created_at,
      });
    } else if (c.status === "pending") {
      activities.push({
        id: `con-${c.consultation_id}`,
        type: "consultation",
        title: `Consultation awaiting report — ${serviceLabel} with ${doctorName}`,
        subtitle: "Pending",
        timestamp: c.created_at,
      });
    }
  }

  // Admin logs
  for (const log of logsRes.data ?? []) {
    const details = log.details as Record<string, unknown> | null;
    activities.push({
      id: `log-${log.log_id}`,
      type: "admin",
      title: formatAdminAction(log.action, details),
      subtitle: log.target_type ?? undefined,
      timestamp: log.created_at,
    });
  }

  // Sort by timestamp descending, take top 20
  activities.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  const recentActivity = activities.slice(0, 20);

  return (
    <div>
      <RealtimeRefresh
        tables={["doctor_profiles", "admin_logs", "consultations", "payments"]}
        channelName="admin-dashboard-realtime"
      />
      {/* Error banner */}
      {queryErrors.length > 0 && (
        <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200">
          <p className="text-sm font-medium text-red-700">
            Failed to load some dashboard data. Check your Supabase connection.
          </p>
          <p className="text-xs text-red-500 mt-1">
            {queryErrors.map((e) => e!.message).join("; ")}
          </p>
        </div>
      )}

      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Admin Dashboard</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Platform overview and real-time activity
        </p>
      </div>

      {/* Stat cards — Row 1 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
        <StatCard
          label="Total Doctors"
          value={totalDoctors}
          href="/dashboard/doctors"
          iconBg="bg-blue-50"
          icon={
            <svg className="h-5 w-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
            </svg>
          }
        />
        <StatCard
          label="Verified Doctors"
          value={verifiedDoctors}
          href="/dashboard/doctors?filter=verified"
          iconBg="bg-green-50"
          icon={
            <svg className="h-5 w-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Pending Applications"
          value={pendingApplications}
          href="/dashboard/doctors?filter=pending"
          iconBg="bg-yellow-50"
          icon={
            <svg className="h-5 w-5 text-yellow-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Total Patients"
          value={totalPatients}
          iconBg="bg-purple-50"
          icon={
            <svg className="h-5 w-5 text-purple-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
            </svg>
          }
        />
      </div>

      {/* Stat cards — Row 2 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          label="Active Consultations"
          value={activeConsultations}
          iconBg="bg-orange-50"
          icon={
            <svg className="h-5 w-5 text-orange-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
            </svg>
          }
        />
        <StatCard
          label="Completed"
          value={completedConsultations}
          iconBg="bg-teal-50"
          icon={
            <svg className="h-5 w-5 text-teal-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Total Revenue"
          value={formatRevenue(totalRevenue)}
          iconBg="bg-emerald-50"
          icon={
            <svg className="h-5 w-5 text-emerald-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
        />
        <StatCard
          label="Pending Credentials"
          value={pendingCredentials}
          href="/dashboard/doctors"
          iconBg="bg-indigo-50"
          icon={
            <svg className="h-5 w-5 text-indigo-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
            </svg>
          }
        />
      </div>

      {/* Recent Activity */}
      <ActivityFeed items={recentActivity} />
    </div>
  );
}

// ── Helpers ────────────────────────────────────────────────────

function formatAdminAction(action: string, details: Record<string, unknown> | null): string {
  const d = details ?? {};
  const email = d.email as string | undefined;
  const labels: Record<string, string> = {
    approve_doctor: "Doctor approved",
    reject_doctor: "Doctor application rejected",
    suspend_doctor: "Doctor suspended",
    unsuspend_doctor: "Doctor unsuspended",
    ban_doctor: "Doctor banned",
    unban_doctor: "Doctor unbanned",
    warn_doctor: "Doctor warned",
    suspend_user: "User suspended",
    unsuspend_user: "User unsuspended",
    initial_admin_setup: "Admin account created",
    create_portal_user: "Portal user created",
    portal_user_created: "Portal user created",
    deauthorize_device: "Device deauthorized",
    flag_rating: "Rating flagged",
    unflag_rating: "Rating unflagged",
    doctor_registered: "New doctor registered",
    consultation_created: "Consultation created",
    payment_completed: "Payment completed",
    rating_submitted: "Rating submitted",
    delete_user_role: "User role removed",
  };
  const label = labels[action] ?? action.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
  return email ? `${label} — ${email}` : label;
}
