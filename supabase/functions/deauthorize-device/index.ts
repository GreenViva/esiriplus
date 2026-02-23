// functions/deauthorize-device/index.ts
// Admin-only: deauthorizes a doctor's device binding (for lost device recovery).
//
// Expects JSON body: { doctor_id }
// Returns: { deauthorized: true, doctor_id }

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    await checkRateLimit(`deauth-device:${clientIp}`, 5, 60);

    const auth = await validateAuth(req);
    requireRole(auth, "admin");

    const body = await req.json();
    const { doctor_id } = body;

    if (!doctor_id) {
      throw new ValidationError("doctor_id is required");
    }

    const supabase = getServiceClient();

    const { error: deleteError } = await supabase
      .from("doctor_device_bindings")
      .delete()
      .eq("doctor_id", doctor_id);

    if (deleteError) throw deleteError;

    await logEvent({
      function_name: "deauthorize-device",
      level: "info",
      user_id: auth.userId,
      action: "device_deauthorized",
      metadata: { target_doctor_id: doctor_id },
      ip_address: clientIp,
    });

    return successResponse(
      { deauthorized: true, doctor_id },
      200,
      origin,
    );
  } catch (err) {
    await logEvent({
      function_name: "deauthorize-device",
      level: "warn",
      action: "device_deauth_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address: clientIp,
    });
    return errorResponse(err, origin);
  }
});
