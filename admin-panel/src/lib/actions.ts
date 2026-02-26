"use server";

import { revalidatePath } from "next/cache";
import { createAdminClient } from "@/lib/supabase/admin";
import { createClient } from "@/lib/supabase/server";

/** Verify caller is authenticated and has a portal role. Returns user or error. */
async function requireAuth() {
  const serverClient = await createClient();
  const { data: { user } } = await serverClient.auth.getUser();
  if (!user) return { error: "Not authenticated" as const, user: null };
  return { error: null, user };
}

function revalidateDoctorPaths() {
  revalidatePath("/dashboard/doctors");
  revalidatePath("/dashboard/hr/doctors");
  revalidatePath("/dashboard/hr");
  revalidatePath("/dashboard/hr/audit");
  revalidatePath("/dashboard");
}

async function sendDoctorNotification(
  doctorId: string,
  title: string,
  body: string,
  type: "doctor_approved" | "doctor_rejected",
) {
  try {
    const serverClient = await createClient();
    const { data: { session } } = await serverClient.auth.getSession();
    if (!session) return;

    await fetch(
      `${process.env.NEXT_PUBLIC_SUPABASE_URL}/functions/v1/send-push-notification`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.access_token}`,
        },
        body: JSON.stringify({ user_id: doctorId, title, body, type }),
      },
    );
  } catch (e) {
    console.error("Failed to send push notification:", e);
  }
}

export async function approveDoctor(doctorId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({ is_verified: true, is_available: false, rejection_reason: null, updated_at: new Date().toISOString() })
    .eq("doctor_id", doctorId);

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "approve_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
  });

  await sendDoctorNotification(
    doctorId,
    "Application Approved!",
    "Congratulations! Your application has been approved. You can now go online and start accepting patient consultations.",
    "doctor_approved",
  );

  revalidateDoctorPaths();
  return { success: true };
}

export async function rejectDoctor(doctorId: string, reason: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error: updateError } = await supabase
    .from("doctor_profiles")
    .update({ is_verified: false, rejection_reason: reason, updated_at: new Date().toISOString() })
    .eq("doctor_id", doctorId);

  if (updateError) return { error: updateError.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "reject_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
    details: { reason },
  });

  await sendDoctorNotification(
    doctorId,
    "Application Update",
    `Your application was not approved. Reason: ${reason}. Please review and resubmit your credentials.`,
    "doctor_rejected",
  );

  revalidateDoctorPaths();
  return { success: true };
}

export async function banDoctor(doctorId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  // Ban auth user (100-year ban)
  const { error } = await supabase.auth.admin.updateUserById(doctorId, {
    ban_duration: "876000h",
  });

  if (error) return { error: error.message };

  // Also mark as unavailable in profile
  await supabase
    .from("doctor_profiles")
    .update({ is_available: false, updated_at: new Date().toISOString() })
    .eq("doctor_id", doctorId);

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "ban_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
  });

  revalidateDoctorPaths();
  return { success: true };
}

export async function warnDoctor(doctorId: string, message: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "warn_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
    details: { message },
  });

  revalidateDoctorPaths();
  return { success: true };
}

export async function toggleRatingFlag(ratingId: string, flagged: boolean) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error } = await supabase
    .from("doctor_ratings")
    .update({
      is_flagged: flagged,
      flagged_by: flagged ? auth.user.id : null,
      flagged_at: flagged ? new Date().toISOString() : null,
    })
    .eq("rating_id", ratingId);

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: flagged ? "flag_rating" : "unflag_rating",
    target_type: "doctor_rating",
    target_id: ratingId,
  });

  revalidatePath("/dashboard/hr/ratings");
  revalidatePath("/dashboard/hr");
  revalidatePath("/dashboard/hr/audit");
  return { success: true };
}

export async function suspendDoctor(doctorId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({ is_available: false, updated_at: new Date().toISOString() })
    .eq("doctor_id", doctorId);

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "suspend_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
  });

  revalidateDoctorPaths();
  return { success: true };
}

export async function unsuspendDoctor(doctorId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({ is_available: true, updated_at: new Date().toISOString() })
    .eq("doctor_id", doctorId);

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "unsuspend_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
  });

  revalidateDoctorPaths();
  return { success: true };
}

export async function unbanDoctor(doctorId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error: authError } = await supabase.auth.admin.updateUserById(doctorId, {
    ban_duration: "none",
  });

  if (authError) return { error: authError.message };

  const { error } = await supabase
    .from("doctor_profiles")
    .update({ is_available: true, updated_at: new Date().toISOString() })
    .eq("doctor_id", doctorId);

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "unban_doctor",
    target_type: "doctor_profile",
    target_id: doctorId,
  });

  revalidateDoctorPaths();
  return { success: true };
}

export async function deauthorizeDevice(doctorId: string) {
  const serverClient = await createClient();
  const { data: { session } } = await serverClient.auth.getSession();

  if (!session) return { error: "Not authenticated" };

  const res = await fetch(
    `${process.env.NEXT_PUBLIC_SUPABASE_URL}/functions/v1/deauthorize-device`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${session.access_token}`,
      },
      body: JSON.stringify({ doctor_id: doctorId }),
    },
  );

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    return { error: body.error ?? "Failed to deauthorize device" };
  }

  revalidatePath(`/dashboard/doctors/${doctorId}`);
  return { success: true };
}

export async function suspendUser(userId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error } = await supabase.auth.admin.updateUserById(userId, {
    ban_duration: "876000h",
  });

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "suspend_user",
    target_type: "user",
    target_id: userId,
  });

  revalidatePath("/dashboard/users");
  revalidatePath("/dashboard/hr");
  revalidatePath("/dashboard/hr/doctors");
  return { success: true };
}

export async function unsuspendUser(userId: string) {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  const { error } = await supabase.auth.admin.updateUserById(userId, {
    ban_duration: "none",
  });

  if (error) return { error: error.message };

  await supabase.from("admin_logs").insert({
    admin_id: auth.user.id,
    action: "unsuspend_user",
    target_type: "user",
    target_id: userId,
  });

  revalidatePath("/dashboard/users");
  revalidatePath("/dashboard/hr");
  revalidatePath("/dashboard/hr/doctors");
  return { success: true };
}

export async function checkAdminExists(): Promise<{ exists: boolean }> {
  const supabase = createAdminClient();

  const { count } = await supabase
    .from("user_roles")
    .select("*", { count: "exact", head: true })
    .eq("role_name", "admin");

  return { exists: (count ?? 0) > 0 };
}

export async function setupInitialAdmin(data: {
  email: string;
  password: string;
  full_name: string;
}): Promise<{ success?: boolean; error?: string }> {
  const supabase = createAdminClient();

  // Guard: reject if an admin already exists
  const { exists } = await checkAdminExists();
  if (exists) {
    return { error: "An admin account already exists." };
  }

  // Create auth user via admin API (bypasses RLS, confirms email)
  const { data: authData, error: createError } =
    await supabase.auth.admin.createUser({
      email: data.email,
      password: data.password,
      email_confirm: true,
      user_metadata: { full_name: data.full_name },
    });

  if (createError || !authData.user) {
    return { error: createError?.message ?? "Failed to create user." };
  }

  const userId = authData.user.id;

  // Insert admin role (no .select() to avoid schema cache issues)
  const { error: roleError } = await supabase
    .from("user_roles")
    .insert({ user_id: userId, role_name: "admin" });

  if (roleError) {
    await supabase.auth.admin.deleteUser(userId);
    return { error: roleError.message };
  }

  // Log the initial setup (best effort)
  await supabase
    .from("admin_logs")
    .insert({
      admin_id: userId,
      action: "initial_admin_setup",
      target_type: "user",
      target_id: userId,
      details: { email: data.email },
    })
    .then(() => {}, (e) => console.error("admin_logs insert failed:", e));

  return { success: true };
}

export async function createPortalUserWithPassword(data: {
  email: string;
  password: string;
  role: string;
}): Promise<{ success?: boolean; error?: string }> {
  const VALID_ROLES = ["admin", "hr", "finance", "audit"];
  if (!VALID_ROLES.includes(data.role)) {
    return { error: "Invalid role." };
  }

  const supabase = createAdminClient();

  // Verify the caller is an authenticated admin
  const serverClient = await createClient();
  const { data: { user: caller } } = await serverClient.auth.getUser();
  if (!caller) return { error: "Not authenticated" };

  const { data: callerRole } = await supabase
    .from("user_roles")
    .select("role_name")
    .eq("user_id", caller.id)
    .single();

  if (!callerRole || callerRole.role_name !== "admin") {
    return { error: "Only admins can create portal users." };
  }

  // Create the auth user with the provided email + password
  const { data: authData, error: createError } =
    await supabase.auth.admin.createUser({
      email: data.email.toLowerCase(),
      password: data.password,
      email_confirm: true,
      user_metadata: { role: data.role },
    });

  if (createError || !authData.user) {
    return { error: createError?.message ?? "Failed to create user." };
  }

  const userId = authData.user.id;

  // Assign the role
  const { error: roleError } = await supabase
    .from("user_roles")
    .insert({ user_id: userId, role_name: data.role });

  if (roleError) {
    // Rollback: delete the auth user if role insert fails
    await supabase.auth.admin.deleteUser(userId);
    return { error: roleError.message };
  }

  // Log the action
  await supabase
    .from("admin_logs")
    .insert({
      admin_id: caller.id,
      action: "create_portal_user",
      target_type: "user",
      target_id: userId,
      details: { email: data.email, role: data.role },
    })
    .then(() => {}, (e) => console.error("admin_logs insert failed:", e));

  revalidatePath("/dashboard/users");
  return { success: true };
}

export async function deleteUserRole(
  userId: string,
  roleName: string,
): Promise<{ success?: boolean; error?: string }> {
  const auth = await requireAuth();
  if (auth.error) return { error: auth.error };

  const supabase = createAdminClient();

  // Verify the caller is an admin
  const { data: callerRole } = await supabase
    .from("user_roles")
    .select("role_name")
    .eq("user_id", auth.user.id)
    .single();

  if (!callerRole || callerRole.role_name !== "admin") {
    return { error: "Only admins can delete roles." };
  }

  // Prevent self-deletion
  if (userId === auth.user.id) {
    return { error: "You cannot remove your own role." };
  }

  const { error } = await supabase
    .from("user_roles")
    .delete()
    .eq("user_id", userId)
    .eq("role_name", roleName);

  if (error) return { error: error.message };

  await supabase
    .from("admin_logs")
    .insert({
      admin_id: auth.user.id,
      action: "delete_user_role",
      target_type: "user",
      target_id: userId,
      details: { role: roleName },
    })
    .then(() => {}, (e) => console.error("admin_logs insert failed:", e));

  revalidatePath("/dashboard/users");
  return { success: true };
}
