// functions/bind-device/index.ts
// Binds a doctor to a specific device via fingerprint.
//
// Expects JSON body: { doctor_id, device_fingerprint }
// Returns: { bound: true, doctor_id, device_fingerprint }

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

    await checkRateLimit(`bind-device:${clientIp}`, 5, 60);

    const auth = await validateAuth(req);
    requireRole(auth, "doctor");

    const body = await req.json();
    const { doctor_id, device_fingerprint } = body;

    if (!doctor_id || !device_fingerprint) {
      throw new ValidationError("doctor_id and device_fingerprint are required");
    }

    // Ensure the authenticated doctor matches the request
    if (auth.userId !== doctor_id) {
      return errorResponse(
        Object.assign(new Error("Cannot bind device for another doctor"), {
          status: 403,
        }),
        origin,
      );
    }

    const supabase = getServiceClient();

    // Check if device is already bound to a different doctor
    const { data: existingDevice } = await supabase
      .from("doctor_device_bindings")
      .select("doctor_id")
      .eq("device_fingerprint", device_fingerprint)
      .eq("is_active", true)
      .single();

    if (existingDevice && existingDevice.doctor_id !== doctor_id) {
      return errorResponse(
        Object.assign(
          new Error("This device is already bound to another doctor account"),
          { status: 409 },
        ),
        origin,
      );
    }

    // Upsert binding (insert or update on conflict)
    const { error: upsertError } = await supabase
      .from("doctor_device_bindings")
      .upsert(
        {
          doctor_id,
          device_fingerprint,
          bound_at: new Date().toISOString(),
          is_active: true,
        },
        { onConflict: "doctor_id" },
      );

    if (upsertError) {
      // Check for unique device constraint violation
      if (upsertError.code === "23505") {
        return errorResponse(
          Object.assign(
            new Error("This device is already bound to another doctor account"),
            { status: 409 },
          ),
          origin,
        );
      }
      throw upsertError;
    }

    await logEvent({
      function_name: "bind-device",
      level: "info",
      user_id: doctor_id,
      action: "device_bound",
      metadata: { device_fingerprint: device_fingerprint.substring(0, 8) + "..." },
      ip_address: clientIp,
    });

    return successResponse(
      { bound: true, doctor_id, device_fingerprint },
      200,
      origin,
    );
  } catch (err) {
    await logEvent({
      function_name: "bind-device",
      level: "warn",
      action: "device_bind_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address: clientIp,
    });
    return errorResponse(err, origin);
  }
});
