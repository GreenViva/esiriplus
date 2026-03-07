/**
 * Client-side API wrapper for admin operations.
 * Replaces server actions — calls Supabase Edge Functions directly.
 * Safe for static export (no server-side dependencies).
 */

import { createClient } from "@/lib/supabase/client";

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";

// ── Helper: invoke edge function with auth ──────────────────────────────────

async function invokeEdgeFunction<T = Record<string, unknown>>(
  functionName: string,
  body: Record<string, unknown>,
): Promise<{ data?: T; error?: string }> {
  const supabase = createClient();
  const { data: { session } } = await supabase.auth.getSession();

  if (!session) return { error: "Not authenticated" };

  const res = await fetch(`${SUPABASE_URL}/functions/v1/${functionName}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${session.access_token}`,
    },
    body: JSON.stringify(body),
  });

  const json = await res.json().catch(() => null);

  if (!res.ok) {
    return { error: json?.error ?? `Request failed (${res.status})` };
  }

  return { data: json ?? {} };
}

// Invoke without auth (for public actions like check_admin_exists)
async function invokePublicEdgeFunction<T = Record<string, unknown>>(
  functionName: string,
  body: Record<string, unknown>,
): Promise<{ data?: T; error?: string }> {
  const res = await fetch(`${SUPABASE_URL}/functions/v1/${functionName}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
    },
    body: JSON.stringify(body),
  });

  const json = await res.json().catch(() => null);

  if (!res.ok) {
    return { error: json?.error ?? `Request failed (${res.status})` };
  }

  return { data: json ?? {} };
}

// ── Doctor management (uses existing manage-doctor edge function) ────────────

export async function approveDoctor(doctorId: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "approve",
    doctor_id: doctorId,
  });
}

export async function rejectDoctor(doctorId: string, reason: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "reject",
    doctor_id: doctorId,
    reason,
  });
}

export async function banDoctor(doctorId: string, reason: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "ban",
    doctor_id: doctorId,
    reason,
  });
}

export async function warnDoctor(doctorId: string, message: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "warn",
    doctor_id: doctorId,
    message,
  });
}

export async function suspendDoctor(doctorId: string, days: number, reason: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "suspend",
    doctor_id: doctorId,
    days,
    reason,
  });
}

export async function unsuspendDoctor(doctorId: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "unsuspend",
    doctor_id: doctorId,
  });
}

export async function unbanDoctor(doctorId: string) {
  return invokeEdgeFunction("manage-doctor", {
    action: "unban",
    doctor_id: doctorId,
  });
}

// ── Rating management ───────────────────────────────────────────────────────

export async function toggleRatingFlag(ratingId: string, flagged: boolean) {
  return invokeEdgeFunction("admin-portal-action", {
    action: "toggle_rating_flag",
    rating_id: ratingId,
    flagged,
  });
}

// ── User management ─────────────────────────────────────────────────────────

export async function suspendUser(userId: string) {
  return invokeEdgeFunction("admin-portal-action", {
    action: "suspend_user",
    user_id: userId,
  });
}

export async function unsuspendUser(userId: string) {
  return invokeEdgeFunction("admin-portal-action", {
    action: "unsuspend_user",
    user_id: userId,
  });
}

export async function deleteUserRole(userId: string, roleName: string) {
  return invokeEdgeFunction("admin-portal-action", {
    action: "delete_user_role",
    user_id: userId,
    role_name: roleName,
  });
}

export async function createPortalUserWithPassword(data: {
  email: string;
  password: string;
  role: string;
}): Promise<{ success?: boolean; error?: string }> {
  const result = await invokeEdgeFunction("admin-portal-action", {
    action: "create_portal_user_with_password",
    ...data,
  });
  if (result.error) return { error: result.error };
  return { success: true };
}

// ── Public actions (no auth required) ───────────────────────────────────────

export async function checkAdminExists(): Promise<{ exists: boolean }> {
  const result = await invokePublicEdgeFunction<{ exists?: boolean }>("admin-portal-action", {
    action: "check_admin_exists",
  });
  return { exists: result.data?.exists ?? false };
}

export async function setupInitialAdmin(data: {
  email: string;
  password: string;
  full_name: string;
}): Promise<{ success?: boolean; error?: string }> {
  const result = await invokePublicEdgeFunction("admin-portal-action", {
    action: "setup_initial_admin",
    ...data,
  });
  if (result.error) return { error: result.error };
  return { success: true };
}

// ── Audit ────────────────────────────────────────────────────────────────────

export async function scanForRisks(): Promise<{
  success?: boolean;
  flagged?: number;
  error?: string;
}> {
  const result = await invokeEdgeFunction<{ flagged?: number }>("admin-portal-action", {
    action: "scan_for_risks",
  });
  if (result.error) return { error: result.error };
  return { success: true, flagged: result.data?.flagged ?? 0 };
}

// ── Dashboard data ──────────────────────────────────────────────────────────

export async function getDashboardData() {
  return invokeEdgeFunction("admin-portal-action", {
    action: "get_dashboard_data",
  });
}

// ── Device management (uses existing deauthorize-device edge function) ──────

export async function deauthorizeDevice(doctorId: string) {
  return invokeEdgeFunction("deauthorize-device", {
    doctor_id: doctorId,
  });
}

// ── Analytics (uses existing generate-health-analytics edge function) ────────

export interface HealthAnalyticsReport {
  generated_at: string;
  data_summary: {
    total_consultations: number;
    total_diagnoses: number;
    total_prescriptions: number;
    regions_count: number;
  };
  report: {
    executive_summary: string;
    regional_hotspots: Array<{
      region: string;
      concern: string;
      priority: "high" | "medium" | "low";
    }>;
    disease_patterns: string;
    demographic_insights: string;
    service_utilization: string;
    recommendations: string[];
    data_quality_notes: string;
  };
}

export async function generateHealthAnalytics(): Promise<{
  data?: HealthAnalyticsReport;
  error?: string;
}> {
  const result = await invokeEdgeFunction<HealthAnalyticsReport>("generate-health-analytics", {});
  if (result.error) return { error: result.error };
  return { data: result.data };
}

// ── User roles ──────────────────────────────────────────────────────────────

export async function getUserRoles() {
  return invokeEdgeFunction("admin-portal-action", {
    action: "get_user_roles",
  });
}

export async function getAllAuthUsers() {
  return invokeEdgeFunction<{ users: Array<{ id: string; email?: string; banned_until?: string }> }>("admin-portal-action", {
    action: "get_all_auth_users",
  });
}

// ── Ratings (uses existing get-all-ratings edge function) ────────────────────

export async function getAllRatings(params?: {
  search?: string;
  rating_filter?: number;
  flagged_only?: boolean;
  limit?: number;
  offset?: number;
}) {
  return invokeEdgeFunction("get-all-ratings", params ?? {});
}

// ── Audit logs (uses existing get-audit-logs edge function) ──────────────────

export async function getAuditLogs(params?: {
  search?: string;
  limit?: number;
  offset?: number;
}) {
  return invokeEdgeFunction("get-audit-logs", params ?? {});
}

// ── Doctors list (uses existing list-all-doctors edge function) ──────────────

export async function listAllDoctors() {
  return invokeEdgeFunction("list-all-doctors", {});
}
