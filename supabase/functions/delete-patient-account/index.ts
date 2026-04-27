// delete-patient-account: Patient self-initiated soft-delete.
//
// Marks the caller's patient_sessions row as deleted (sets deleted_at,
// purge_at = now() + 30 days, is_active = false). The actual permanent
// purge is done by the daily purge-deleted-patients cron.
//
// Security: validateAuth + requireRole("patient") so a user can only
// delete THEIR OWN account. The session token is invalidated as soon as
// is_active flips false; subsequent requests will fail to refresh and
// the local app cache will sign out on the next 401.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "patient");

    const sessionId = auth.sessionId;
    if (!sessionId) throw new ValidationError("No session ID found");

    const supabase = getServiceClient();
    const { data, error } = await supabase.rpc("fn_mark_patient_for_deletion", {
      p_session_id: sessionId,
    });

    if (error) {
      console.error("[delete-patient-account] RPC error:", error);
      throw new Error(error.message);
    }

    if (data !== true) {
      // Already soft-deleted, or session_id not found — return 200 either
      // way so the client treats this as success and signs out.
      console.log(`[delete-patient-account] No-op for session ${sessionId} (already deleted or missing)`);
    }

    return successResponse({
      deleted: true,
      purge_after_days: 30,
      timestamp: new Date().toISOString(),
    }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
