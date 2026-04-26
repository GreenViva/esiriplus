// functions/cleanup-expired-messages/index.ts
//
// Scheduled function (cron): drops chat messages older than 14 days. Called
// daily — schedule at deploy time in the Supabase dashboard alongside
// expire-followup-escrow. Mirrors the local Android MessageCleanupWorker
// which prunes the patient's Room cache on the same window.
//
// The actual delete logic lives in the SQL function fn_cleanup_expired_messages
// (migration 20260427100000_message_retention_14d.sql) so the policy is
// expressed once in one place.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const serviceKey = Deno.env.get("INTERNAL_SERVICE_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const cronSecret = Deno.env.get("CRON_SECRET") ?? "";

    const isServiceCall = authHeader.includes(serviceKey);
    const isCronCall = req.headers.get("X-Cron-Secret") === cronSecret && cronSecret !== "";
    const isAnonCall = authHeader.includes(Deno.env.get("SUPABASE_ANON_KEY") ?? "NONE");

    if (!isServiceCall && !isCronCall && !isAnonCall) {
      return new Response("Unauthorized", { status: 401 });
    }

    const supabase = getServiceClient();
    const { data, error } = await supabase.rpc("fn_cleanup_expired_messages");

    if (error) {
      console.error("[cleanup-messages] RPC error:", error);
      return successResponse({ deleted: 0, error: error.message }, 200, origin);
    }

    const deletedCount = data ?? 0;
    console.log(`[cleanup-messages] Deleted ${deletedCount} expired messages`);

    return successResponse({
      deleted: deletedCount,
      timestamp: new Date().toISOString(),
    }, 200, origin);

  } catch (err) {
    console.error("[cleanup-messages] Error:", err);
    return errorResponse(err, origin);
  }
});
