// functions/update-fcm-token/index.ts
// Lightweight endpoint to update FCM token for any authenticated user.
// Patients → patient_sessions.fcm_token
// Doctors → fcm_tokens.token (upsert)
// Rate limit: 10/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.read(auth.userId ?? auth.sessionId ?? "anon");

    const body = await req.json();
    const fcmToken = body?.fcm_token;

    if (typeof fcmToken !== "string" || fcmToken.trim().length === 0) {
      throw new ValidationError("fcm_token is required");
    }

    const supabase = getServiceClient();

    if (auth.role === "doctor" || auth.role === "admin" || auth.role === "hr") {
      // Doctor/admin: upsert into fcm_tokens table
      const { error } = await supabase
        .from("fcm_tokens")
        .upsert(
          {
            user_id: auth.userId,
            token: fcmToken,
            updated_at: new Date().toISOString(),
          },
          { onConflict: "user_id" },
        );

      if (error) {
        console.error("[update-fcm-token] Doctor upsert failed:", error.message);
        throw new Error("Failed to update FCM token");
      }

      console.log(`[update-fcm-token] Doctor ${auth.userId} token updated`);
    } else {
      // Patient: update patient_sessions.fcm_token
      if (!auth.sessionId) {
        throw new ValidationError("Patient session not found");
      }

      const { error } = await supabase
        .from("patient_sessions")
        .update({ fcm_token: fcmToken })
        .eq("session_id", auth.sessionId);

      if (error) {
        console.error("[update-fcm-token] Patient update failed:", error.message);
        throw new Error("Failed to update FCM token");
      }

      console.log(`[update-fcm-token] Patient session ${auth.sessionId} token updated`);
    }

    return successResponse({ updated: true }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
