// functions/check-device-binding/index.ts
// Checks whether a doctor is bound to a specific device.
//
// Expects JSON body: { doctor_id, device_fingerprint }
// Returns: { bound: boolean, matches: boolean }

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    // Tight rate limit by IP (called during login — no auth available yet)
    await checkRateLimit(`check-device:${clientIp}`, 10, 60);

    const body = await req.json();
    const { doctor_id, device_fingerprint } = body;

    if (typeof doctor_id !== "string" || !doctor_id.trim()) {
      throw new ValidationError("doctor_id is required and must be a non-empty string");
    }
    if (typeof device_fingerprint !== "string" || !device_fingerprint.trim()) {
      throw new ValidationError("device_fingerprint is required and must be a non-empty string");
    }

    // Basic UUID format check for doctor_id
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(doctor_id.trim())) {
      throw new ValidationError("doctor_id must be a valid UUID");
    }

    const supabase = getServiceClient();

    // Look up the doctor's active binding
    const { data: binding } = await supabase
      .from("doctor_device_bindings")
      .select("device_fingerprint")
      .eq("doctor_id", doctor_id)
      .eq("is_active", true)
      .single();

    if (!binding) {
      // No binding exists — doctor has not bound any device
      return successResponse({ bound: false, matches: false }, 200, origin);
    }

    const matches = binding.device_fingerprint === device_fingerprint;

    return successResponse({ bound: true, matches }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
