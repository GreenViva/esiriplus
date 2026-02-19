// functions/create-portal-user/index.ts
// Creates admin portal users (HR, finance, audit, admin roles).
// Only existing admins can create new portal users.
// Rate limit: 5/min (sensitive).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VALID_PORTAL_ROLES = ["hr", "finance", "audit", "admin"] as const;
type PortalRole = typeof VALID_PORTAL_ROLES[number];

interface CreatePortalUserRequest {
  email: string;
  full_name: string;
  role: PortalRole;
  department?: string;
  phone?: string;
}

function validate(body: unknown): CreatePortalUserRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be a JSON object");
  }
  const b = body as Record<string, unknown>;

  // Email
  if (
    typeof b.email !== "string" ||
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(b.email)
  ) {
    throw new ValidationError("Valid email is required");
  }

  // Full name
  if (
    typeof b.full_name !== "string" ||
    b.full_name.trim().length < 2 ||
    b.full_name.trim().length > 100
  ) {
    throw new ValidationError("full_name must be 2–100 characters");
  }

  // Role
  if (!VALID_PORTAL_ROLES.includes(b.role as PortalRole)) {
    throw new ValidationError(
      `role must be one of: ${VALID_PORTAL_ROLES.join(", ")}`
    );
  }

  // Phone (optional but validated if present)
  if (b.phone !== undefined && typeof b.phone !== "string") {
    throw new ValidationError("phone must be a string if provided");
  }

  return b as unknown as CreatePortalUserRequest;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "admin"); // Only admins can create portal users
    await LIMITS.sensitive(auth.userId!);

    const raw = await req.json();
    const body = validate(raw);
    const supabase = getServiceClient();

    // Check if email already exists
    const { data: existing } = await supabase
      .from("auth.users")
      .select("id")
      .eq("email", body.email.toLowerCase())
      .single();

    if (existing) {
      throw new ValidationError("A user with this email already exists");
    }

    // Create the Supabase auth user
    const { data: newUser, error: createErr } = await supabase.auth.admin.createUser({
      email: body.email.toLowerCase(),
      email_confirm: true,
      user_metadata: {
        full_name: body.full_name.trim(),
        role: body.role,
        department: body.department ?? null,
        phone: body.phone ?? null,
      },
    });

    if (createErr || !newUser?.user) {
      throw new Error(`Failed to create auth user: ${createErr?.message}`);
    }

    const userId = newUser.user.id;

    // Assign role in user_roles table
    const { error: roleErr } = await supabase.from("user_roles").insert({
      user_id: userId,
      role_name: body.role,
      assigned_by: auth.userId,
      assigned_at: new Date().toISOString(),
    });

    if (roleErr) throw roleErr;

    // Send password reset email so they can set their own password
    await supabase.auth.admin.generateLink({
      type: "recovery",
      email: body.email.toLowerCase(),
    });

    await logEvent({
      function_name: "create-portal-user",
      level: "info",
      user_id: auth.userId,
      action: "portal_user_created",
      metadata: {
        new_user_id: userId,
        email: body.email,
        role: body.role,
        department: body.department,
        created_by: auth.userId,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: `Portal user created. A password reset email has been sent to ${body.email}.`,
      user_id: userId,
      email: body.email,
      role: body.role,
    }, 201, origin);

  } catch (err) {
    await logEvent({
      function_name: "create-portal-user",
      level: "error",
      action: "portal_user_creation_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
