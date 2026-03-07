// admin-portal-action – Handles admin portal operations that require service role key.
//
// Actions: check_admin_exists, setup_initial_admin, suspend_user, unsuspend_user,
//          toggle_rating_flag, delete_user_role, get_dashboard_data, get_user_roles
//
// Auth: varies by action (some are public, most require admin).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole, type AuthResult } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

type Action =
  | "check_admin_exists"
  | "setup_initial_admin"
  | "create_portal_user_with_password"
  | "suspend_user"
  | "unsuspend_user"
  | "toggle_rating_flag"
  | "delete_user_role"
  | "get_dashboard_data"
  | "get_user_roles"
  | "get_my_roles"
  | "get_all_auth_users";

const PUBLIC_ACTIONS: Action[] = ["check_admin_exists", "setup_initial_admin"];

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const raw = await req.json();
    const action = raw.action as Action;

    if (!action) throw new ValidationError("action is required");

    // Public actions (no auth required)
    if (PUBLIC_ACTIONS.includes(action)) {
      switch (action) {
        case "check_admin_exists":
          return await handleCheckAdminExists(origin);
        case "setup_initial_admin":
        case "setup_initial_admin_with_password":
          return await handleSetupInitialAdmin(raw, origin);
        default:
          throw new ValidationError("Unknown public action");
      }
    }

    // Authenticated actions
    const auth = await validateAuth(req);

    switch (action) {
      case "suspend_user":
        requireRole(auth, "admin");
        await LIMITS.sensitive(auth.userId!);
        return await handleSuspendUser(raw, auth, origin);
      case "unsuspend_user":
        requireRole(auth, "admin");
        await LIMITS.sensitive(auth.userId!);
        return await handleUnsuspendUser(raw, auth, origin);
      case "toggle_rating_flag":
        requireRole(auth, "admin", "hr");
        await LIMITS.sensitive(auth.userId!);
        return await handleToggleRatingFlag(raw, auth, origin);
      case "delete_user_role":
        requireRole(auth, "admin");
        await LIMITS.sensitive(auth.userId!);
        return await handleDeleteUserRole(raw, auth, origin);
      case "get_dashboard_data":
        requireRole(auth, "admin", "hr", "finance", "audit");
        await LIMITS.read(auth.userId!);
        return await handleGetDashboardData(origin);
      case "get_my_roles":
        await LIMITS.read(auth.userId!);
        return await handleGetMyRoles(auth, origin);
      case "get_user_roles":
        requireRole(auth, "admin");
        await LIMITS.read(auth.userId!);
        return await handleGetUserRoles(origin);
      case "create_portal_user_with_password":
        requireRole(auth, "admin");
        await LIMITS.sensitive(auth.userId!);
        return await handleCreatePortalUserWithPassword(raw, auth, origin);
      case "get_all_auth_users":
        requireRole(auth, "admin");
        await LIMITS.read(auth.userId!);
        return await handleGetAllAuthUsers(origin);
      default:
        throw new ValidationError("Unknown action");
    }
  } catch (err) {
    await logEvent({
      function_name: "admin-portal-action",
      level: "error",
      action: "admin_portal_action_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});

// ── check_admin_exists ──────────────────────────────────────────────────────

async function handleCheckAdminExists(
  origin: string | null,
): Promise<Response> {
  const supabase = getServiceClient();

  const { count } = await supabase
    .from("user_roles")
    .select("*", { count: "exact", head: true })
    .eq("role_name", "admin");

  return successResponse({ exists: (count ?? 0) > 0 }, 200, origin);
}

// ── setup_initial_admin ─────────────────────────────────────────────────────

async function handleSetupInitialAdmin(
  raw: Record<string, unknown>,
  origin: string | null,
): Promise<Response> {
  const email = raw.email as string;
  const password = raw.password as string;
  const fullName = (raw.full_name as string) || "Admin";

  if (!email || typeof email !== "string") {
    throw new ValidationError("email is required");
  }
  if (!password || typeof password !== "string" || password.length < 8) {
    throw new ValidationError("password must be at least 8 characters");
  }

  const supabase = getServiceClient();

  // Guard: reject if admin already exists
  const { count } = await supabase
    .from("user_roles")
    .select("*", { count: "exact", head: true })
    .eq("role_name", "admin");

  if ((count ?? 0) > 0) {
    throw new ValidationError("An admin account already exists.");
  }

  // Create auth user
  const { data: authData, error: createError } =
    await supabase.auth.admin.createUser({
      email,
      password,
      email_confirm: true,
      user_metadata: { full_name: fullName },
    });

  if (createError || !authData.user) {
    throw new ValidationError(createError?.message ?? "Failed to create user.");
  }

  const userId = authData.user.id;

  // Insert admin role
  const { error: roleError } = await supabase
    .from("user_roles")
    .insert({ user_id: userId, role_name: "admin" });

  if (roleError) {
    await supabase.auth.admin.deleteUser(userId);
    throw new ValidationError(roleError.message);
  }

  // Log the setup
  await supabase.from("admin_logs").insert({
    admin_id: userId,
    action: "initial_admin_setup",
    target_type: "user",
    target_id: userId,
    details: { email },
  });

  return successResponse({ success: true }, 200, origin);
}

// ── suspend_user ────────────────────────────────────────────────────────────

async function handleSuspendUser(
  raw: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null,
): Promise<Response> {
  const userId = raw.user_id as string;
  if (!userId) throw new ValidationError("user_id is required");

  const supabase = getServiceClient();

  const { error } = await supabase.auth.admin.updateUserById(userId, {
    ban_duration: "876000h",
  });

  if (error) throw new ValidationError(error.message);

  await supabase.from("admin_logs").insert({
    admin_id: auth.userId,
    action: "suspend_user",
    target_type: "user",
    target_id: userId,
  });

  return successResponse({ success: true }, 200, origin);
}

// ── unsuspend_user ──────────────────────────────────────────────────────────

async function handleUnsuspendUser(
  raw: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null,
): Promise<Response> {
  const userId = raw.user_id as string;
  if (!userId) throw new ValidationError("user_id is required");

  const supabase = getServiceClient();

  const { error } = await supabase.auth.admin.updateUserById(userId, {
    ban_duration: "none",
  });

  if (error) throw new ValidationError(error.message);

  await supabase.from("admin_logs").insert({
    admin_id: auth.userId,
    action: "unsuspend_user",
    target_type: "user",
    target_id: userId,
  });

  return successResponse({ success: true }, 200, origin);
}

// ── toggle_rating_flag ──────────────────────────────────────────────────────

async function handleToggleRatingFlag(
  raw: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null,
): Promise<Response> {
  const ratingId = raw.rating_id as string;
  const flagged = raw.flagged as boolean;

  if (!ratingId) throw new ValidationError("rating_id is required");
  if (typeof flagged !== "boolean") throw new ValidationError("flagged must be boolean");

  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_ratings")
    .update({
      is_flagged: flagged,
      flagged_by: flagged ? auth.userId : null,
      flagged_at: flagged ? new Date().toISOString() : null,
    })
    .eq("rating_id", ratingId);

  if (error) throw new ValidationError(error.message);

  await supabase.from("admin_logs").insert({
    admin_id: auth.userId,
    action: flagged ? "flag_rating" : "unflag_rating",
    target_type: "doctor_rating",
    target_id: ratingId,
  });

  return successResponse({ success: true }, 200, origin);
}

// ── delete_user_role ────────────────────────────────────────────────────────

async function handleDeleteUserRole(
  raw: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null,
): Promise<Response> {
  const userId = raw.user_id as string;
  const roleName = raw.role_name as string;

  if (!userId) throw new ValidationError("user_id is required");
  if (!roleName) throw new ValidationError("role_name is required");

  // Prevent self-deletion
  if (userId === auth.userId) {
    throw new ValidationError("You cannot remove your own role.");
  }

  const supabase = getServiceClient();

  const { error } = await supabase
    .from("user_roles")
    .delete()
    .eq("user_id", userId)
    .eq("role_name", roleName);

  if (error) throw new ValidationError(error.message);

  await supabase.from("admin_logs").insert({
    admin_id: auth.userId,
    action: "delete_user_role",
    target_type: "user",
    target_id: userId,
    details: { role: roleName },
  });

  return successResponse({ success: true }, 200, origin);
}

// ── create_portal_user_with_password ─────────────────────────────────────────

async function handleCreatePortalUserWithPassword(
  raw: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null,
): Promise<Response> {
  const email = raw.email as string;
  const password = raw.password as string;
  const role = raw.role as string;

  if (!email) throw new ValidationError("email is required");
  if (!password || password.length < 8) throw new ValidationError("password must be at least 8 characters");

  const VALID_ROLES = ["admin", "hr", "finance", "audit"];
  if (!VALID_ROLES.includes(role)) throw new ValidationError("Invalid role");

  const supabase = getServiceClient();

  // Create auth user with password
  const { data: authData, error: createError } =
    await supabase.auth.admin.createUser({
      email: email.toLowerCase(),
      password,
      email_confirm: true,
      user_metadata: { role },
    });

  if (createError || !authData.user) {
    throw new ValidationError(createError?.message ?? "Failed to create user.");
  }

  const userId = authData.user.id;

  // Assign role
  const { error: roleError } = await supabase
    .from("user_roles")
    .insert({ user_id: userId, role_name: role });

  if (roleError) {
    await supabase.auth.admin.deleteUser(userId);
    throw new ValidationError(roleError.message);
  }

  await supabase.from("admin_logs").insert({
    admin_id: auth.userId,
    action: "create_portal_user",
    target_type: "user",
    target_id: userId,
    details: { email, role },
  });

  return successResponse({ success: true }, 200, origin);
}

// ── get_dashboard_data ──────────────────────────────────────────────────────

async function handleGetDashboardData(
  origin: string | null,
): Promise<Response> {
  const supabase = getServiceClient();

  const [
    doctorsRes,
    verifiedDoctorsRes,
    pendingApplicationsRes,
    patientsRes,
    activeConsultationsRes,
    completedConsultationsRes,
    servicePaymentsRes,
    earningsRes,
    pendingCredentialsRes,
  ] = await Promise.all([
    supabase.from("doctor_profiles").select("*", { count: "exact", head: true }),
    supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).eq("is_verified", true),
    supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).eq("is_verified", false),
    supabase.from("patient_sessions").select("*", { count: "exact", head: true }),
    supabase.from("consultations").select("*", { count: "exact", head: true }).in("status", ["active", "in_progress"]),
    supabase.from("consultations").select("*", { count: "exact", head: true }).eq("status", "completed"),
    supabase.from("service_access_payments").select("amount").eq("status", "completed").limit(1000),
    supabase.from("doctor_earnings").select("amount").limit(1000),
    supabase.from("doctor_profiles").select("*", { count: "exact", head: true }).is("license_document_url", null),
  ]);

  const servicePaymentRevenue = (servicePaymentsRes.data ?? []).reduce(
    (sum: number, p: { amount: number }) => sum + (p.amount ?? 0), 0,
  );
  const doctorEarningsTotal = (earningsRes.data ?? []).reduce(
    (sum: number, e: { amount: number }) => sum + (e.amount ?? 0), 0,
  );
  const totalRevenue = servicePaymentRevenue > 0 ? servicePaymentRevenue : doctorEarningsTotal * 2;

  // Recent activity
  const [paymentsRes, recentDoctorsRes, recentConsultationsRes, logsRes] =
    await Promise.all([
      supabase
        .from("payments")
        .select("payment_id, amount, currency, payment_type, status, service_access_payments(service_type), created_at")
        .order("created_at", { ascending: false })
        .limit(10),
      supabase
        .from("doctor_profiles")
        .select("doctor_id, full_name, specialty, is_verified, created_at, updated_at")
        .order("updated_at", { ascending: false })
        .limit(10),
      supabase
        .from("consultations")
        .select("consultation_id, status, service_type, doctor_id, doctor_profiles(full_name), created_at, updated_at")
        .order("updated_at", { ascending: false })
        .limit(10),
      supabase
        .from("admin_logs")
        .select("*")
        .order("created_at", { ascending: false })
        .limit(10),
    ]);

  return successResponse({
    stats: {
      totalDoctors: doctorsRes.count ?? 0,
      verifiedDoctors: verifiedDoctorsRes.count ?? 0,
      pendingApplications: pendingApplicationsRes.count ?? 0,
      totalPatients: patientsRes.count ?? 0,
      activeConsultations: activeConsultationsRes.count ?? 0,
      completedConsultations: completedConsultationsRes.count ?? 0,
      pendingCredentials: pendingCredentialsRes.count ?? 0,
      totalRevenue,
    },
    recentPayments: paymentsRes.data ?? [],
    recentDoctors: recentDoctorsRes.data ?? [],
    recentConsultations: recentConsultationsRes.data ?? [],
    recentLogs: logsRes.data ?? [],
    errors: [
      doctorsRes.error, verifiedDoctorsRes.error, pendingApplicationsRes.error,
      patientsRes.error, activeConsultationsRes.error, completedConsultationsRes.error,
      servicePaymentsRes.error, earningsRes.error, pendingCredentialsRes.error,
    ].filter(Boolean).map((e) => e!.message),
  }, 200, origin);
}

// ── get_my_roles (returns only the authenticated user's own roles) ──────

async function handleGetMyRoles(
  auth: AuthResult,
  origin: string | null,
): Promise<Response> {
  const supabase = getServiceClient();

  const { data, error } = await supabase
    .from("user_roles")
    .select("role_name")
    .eq("user_id", auth.userId);

  if (error) throw new ValidationError(error.message);

  return successResponse({ roles: data ?? [] }, 200, origin);
}

// ── get_user_roles ──────────────────────────────────────────────────────────

async function handleGetUserRoles(
  origin: string | null,
): Promise<Response> {
  const supabase = getServiceClient();

  const { data, error } = await supabase
    .from("user_roles")
    .select("*");

  if (error) throw new ValidationError(error.message);

  return successResponse({ roles: data ?? [] }, 200, origin);
}

// ── get_all_auth_users ──────────────────────────────────────────────────────

async function handleGetAllAuthUsers(
  origin: string | null,
): Promise<Response> {
  const supabase = getServiceClient();
  const allUsers: unknown[] = [];
  const perPage = 1000;
  let page = 1;

  while (true) {
    const { data: { users }, error } = await supabase.auth.admin.listUsers({
      page,
      perPage,
    });
    if (error || !users || users.length === 0) break;
    allUsers.push(...users);
    if (users.length < perPage) break;
    page++;
  }

  return successResponse({ users: allUsers }, 200, origin);
}
