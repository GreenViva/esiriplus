// functions/logout/index.ts
// Cleans up server-side state when a doctor logs out:
//  1. Deletes the FCM token so pushes stop going to the old device
//  2. Marks the doctor as offline
//  3. Revokes the Supabase Auth session
//
// Rate limit: 5/min per user.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const identifier = auth.userId ?? auth.sessionId ?? "anon";
    await LIMITS.consultation(identifier);

    const supabase = getServiceClient();

    if (auth.userId) {
      // 1. Delete FCM token — stops push notifications to this device
      const { error: fcmErr } = await supabase
        .from("fcm_tokens")
        .delete()
        .eq("user_id", auth.userId);

      if (fcmErr) {
        console.warn(`[logout] Failed to delete FCM token for ${auth.userId}:`, fcmErr.message);
      } else {
        console.log(`[logout] FCM token deleted for doctor ${auth.userId}`);
      }

      // 2. Mark doctor as offline
      await supabase
        .from("doctor_profiles")
        .update({ is_available: false })
        .eq("doctor_id", auth.userId);

      // 3. Close any open online log entry
      const { data: openLog } = await supabase
        .from("doctor_online_logs")
        .select("id")
        .eq("doctor_id", auth.userId)
        .is("went_offline_at", null)
        .order("went_online_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (openLog) {
        await supabase
          .from("doctor_online_logs")
          .update({ went_offline_at: new Date().toISOString() })
          .eq("id", openLog.id);
      }

      // 4. Revoke the Supabase Auth session
      try {
        await supabase.auth.admin.signOut(auth.jwt, "global");
      } catch (e) {
        // Non-fatal — token will expire naturally
        console.warn(`[logout] Session revocation failed:`, e);
      }

      await logEvent({
        function_name: "logout",
        level: "info",
        user_id: auth.userId,
        action: "doctor_logged_out",
      });
    }

    return successResponse({ logged_out: true }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
