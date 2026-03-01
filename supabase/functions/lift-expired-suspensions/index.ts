// functions/lift-expired-suspensions/index.ts
// Automatically lifts expired timed suspensions.
// Called by pg_cron every 15 minutes via pg_net.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const CRON_SECRET = Deno.env.get("CRON_SECRET") ?? "";
const SYSTEM_ADMIN_ID = "00000000-0000-0000-0000-000000000000";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // Auth: accept cron secret header
    const cronSecret = req.headers.get("X-Cron-Secret");
    if (!cronSecret || cronSecret !== CRON_SECRET) {
      return new Response(
        JSON.stringify({ error: "Unauthorized" }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      );
    }

    const supabase = getServiceClient();
    const now = new Date().toISOString();

    // Find all doctors whose timed suspension has expired
    const { data: expiredDoctors, error: queryError } = await supabase
      .from("doctor_profiles")
      .select("doctor_id, full_name, suspended_until")
      .not("suspended_until", "is", null)
      .lte("suspended_until", now)
      .eq("is_available", false);

    if (queryError) throw new Error(`Query failed: ${queryError.message}`);

    if (!expiredDoctors || expiredDoctors.length === 0) {
      return successResponse({ message: "No expired suspensions", lifted: 0 }, 200, origin);
    }

    let lifted = 0;

    for (const doctor of expiredDoctors) {
      // Lift the suspension
      const { error: updateError } = await supabase
        .from("doctor_profiles")
        .update({ is_available: true, suspended_until: null, updated_at: now })
        .eq("doctor_id", doctor.doctor_id);

      if (updateError) {
        console.error(`Failed to unsuspend ${doctor.doctor_id}:`, updateError);
        continue;
      }

      // Log the auto-unsuspension
      await supabase.from("admin_logs").insert({
        admin_id: SYSTEM_ADMIN_ID,
        action: "auto_unsuspend_doctor",
        target_type: "doctor_profile",
        target_id: doctor.doctor_id,
        details: { original_suspended_until: doctor.suspended_until, lifted_at: now },
      });

      // Send notification via the existing send-push-notification function
      try {
        await fetch(`${SUPABASE_URL}/functions/v1/send-push-notification`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
            "X-Service-Key": SERVICE_ROLE_KEY,
          },
          body: JSON.stringify({
            user_id: doctor.doctor_id,
            title: "Suspension Lifted",
            body: "Your account suspension period has ended. You can now go online and accept consultations.",
            type: "doctor_unsuspended",
          }),
        });
      } catch (e) {
        console.error(`Failed to send notification to ${doctor.doctor_id}:`, e);
      }

      lifted++;
    }

    await logEvent({
      function_name: "lift-expired-suspensions",
      level: "info",
      action: "suspensions_lifted",
      metadata: { lifted, processed_at: now },
    });

    return successResponse({ message: "Suspension lift completed", lifted }, 200, origin);
  } catch (err) {
    await logEvent({
      function_name: "lift-expired-suspensions",
      level: "error",
      action: "suspension_lift_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
