// manage-doctor – Admin actions for doctor verification and management.
//
// Actions: approve, reject, ban, suspend, warn, unsuspend, unban
//
// Auth: admin, hr only.
// Rate limit: 5/min (sensitive).

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

// ── Types ────────────────────────────────────────────────────────────────────

type Action =
  | "approve"
  | "reject"
  | "ban"
  | "suspend"
  | "warn"
  | "unsuspend"
  | "unban";

interface RequestBody {
  action: Action;
  doctor_id: string;
  reason?: string;
  days?: number;
  message?: string;
}

const VALID_ACTIONS: Action[] = [
  "approve",
  "reject",
  "ban",
  "suspend",
  "warn",
  "unsuspend",
  "unban",
];

// ── Validation ───────────────────────────────────────────────────────────────

function validate(body: unknown): RequestBody {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  if (!VALID_ACTIONS.includes(b.action as Action)) {
    throw new ValidationError(
      `action must be one of: ${VALID_ACTIONS.join(", ")}`
    );
  }
  if (typeof b.doctor_id !== "string" || !b.doctor_id) {
    throw new ValidationError("doctor_id is required");
  }
  if (b.action === "reject" && (typeof b.reason !== "string" || !b.reason)) {
    throw new ValidationError("reason is required for reject");
  }
  if (b.action === "ban" && (typeof b.reason !== "string" || !b.reason)) {
    throw new ValidationError("reason is required for ban");
  }
  if (b.action === "suspend") {
    if (typeof b.reason !== "string" || !b.reason) {
      throw new ValidationError("reason is required for suspend");
    }
    if (typeof b.days !== "number" || b.days < 1 || b.days > 365) {
      throw new ValidationError("days must be a number between 1 and 365");
    }
  }
  if (b.action === "warn" && (typeof b.message !== "string" || !b.message)) {
    throw new ValidationError("message is required for warn");
  }

  return b as unknown as RequestBody;
}

// ── Helper: send push notification to doctor ─────────────────────────────────

async function sendDoctorNotification(
  doctorId: string,
  title: string,
  body: string,
  type: string
): Promise<void> {
  const supabase = getServiceClient();
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        user_id: doctorId,
        title,
        body,
        type,
      },
    });
  } catch (e) {
    console.error("Failed to send doctor notification:", e);
  }
}

// ── Helper: insert admin log ─────────────────────────────────────────────────

async function insertAdminLog(
  adminId: string,
  action: string,
  targetId: string,
  details?: Record<string, unknown>
): Promise<void> {
  const supabase = getServiceClient();
  const { error } = await supabase.from("admin_logs").insert({
    admin_id: adminId,
    action,
    target_type: "doctor",
    target_id: targetId,
    details: details ?? null,
    created_at: new Date().toISOString(),
  });
  if (error) {
    console.error("Failed to insert admin log:", error);
  }
}

// ── Handlers ─────────────────────────────────────────────────────────────────

async function handleApprove(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      is_verified: true,
      verification_status: "approved",
      is_available: false,
      rejection_reason: null,
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "approve_doctor", body.doctor_id);

  await sendDoctorNotification(
    body.doctor_id,
    "Account Approved",
    "Your doctor account has been verified and approved. You can now set your availability.",
    "doctor_approved"
  );

  return successResponse({ success: true, action: "approve" }, 200, origin);
}

async function handleReject(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      is_verified: false,
      verification_status: "rejected",
      rejection_reason: body.reason,
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "reject_doctor", body.doctor_id, {
    reason: body.reason,
  });

  await sendDoctorNotification(
    body.doctor_id,
    "Account Rejected",
    `Your doctor account application was rejected. Reason: ${body.reason}`,
    "doctor_rejected"
  );

  return successResponse({ success: true, action: "reject" }, 200, origin);
}

async function handleBan(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      is_banned: true,
      banned_at: new Date().toISOString(),
      ban_reason: body.reason,
      is_available: false,
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "ban_doctor", body.doctor_id, {
    reason: body.reason,
  });

  await sendDoctorNotification(
    body.doctor_id,
    "Account Banned",
    `Your doctor account has been banned. Reason: ${body.reason}`,
    "doctor_banned"
  );

  return successResponse({ success: true, action: "ban" }, 200, origin);
}

async function handleSuspend(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const suspendedUntil = new Date();
  suspendedUntil.setDate(suspendedUntil.getDate() + body.days!);

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      is_available: false,
      suspended_until: suspendedUntil.toISOString(),
      suspension_reason: body.reason,
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "suspend_doctor", body.doctor_id, {
    days: body.days,
    suspended_until: suspendedUntil.toISOString(),
    reason: body.reason,
  });

  await sendDoctorNotification(
    body.doctor_id,
    "Account Suspended",
    `Your account has been suspended for ${body.days} days. Reason: ${body.reason}`,
    "doctor_suspended"
  );

  return successResponse({ success: true, action: "suspend" }, 200, origin);
}

async function handleWarn(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      warning_message: body.message,
      warning_at: new Date().toISOString(),
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "warn_doctor", body.doctor_id, {
    message: body.message,
  });

  await sendDoctorNotification(
    body.doctor_id,
    "Account Warning",
    `You have received a warning: ${body.message}`,
    "doctor_warned"
  );

  return successResponse({ success: true, action: "warn" }, 200, origin);
}

async function handleUnsuspend(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      is_available: true,
      suspended_until: null,
      suspension_reason: null,
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "unsuspend_doctor", body.doctor_id);

  await sendDoctorNotification(
    body.doctor_id,
    "Suspension Lifted",
    "Your account suspension has been lifted. You can now resume your practice.",
    "doctor_unsuspended"
  );

  return successResponse({ success: true, action: "unsuspend" }, 200, origin);
}

async function handleUnban(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { error } = await supabase
    .from("doctor_profiles")
    .update({
      is_banned: false,
      banned_at: null,
      ban_reason: null,
      is_available: true,
    })
    .eq("doctor_id", body.doctor_id);

  if (error) throw new ValidationError(error.message);

  await insertAdminLog(auth.userId!, "unban_doctor", body.doctor_id);

  await sendDoctorNotification(
    body.doctor_id,
    "Ban Lifted",
    "Your account ban has been lifted. You can now resume your practice.",
    "doctor_unbanned"
  );

  return successResponse({ success: true, action: "unban" }, 200, origin);
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "admin", "hr");
    await LIMITS.sensitive(auth.userId!);

    const raw = await req.json();
    const body = validate(raw);

    switch (body.action) {
      case "approve":
        return await handleApprove(body, auth, origin);
      case "reject":
        return await handleReject(body, auth, origin);
      case "ban":
        return await handleBan(body, auth, origin);
      case "suspend":
        return await handleSuspend(body, auth, origin);
      case "warn":
        return await handleWarn(body, auth, origin);
      case "unsuspend":
        return await handleUnsuspend(body, auth, origin);
      case "unban":
        return await handleUnban(body, auth, origin);
      default:
        throw new ValidationError("Unknown action");
    }
  } catch (err) {
    await logEvent({
      function_name: "manage-doctor",
      level: "error",
      action: "manage_doctor_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
