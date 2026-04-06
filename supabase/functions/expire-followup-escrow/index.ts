// functions/expire-followup-escrow/index.ts
//
// Scheduled function (cron): expires Economy follow-up escrow after 14 days.
// Moves unclaimed 20% funds to admin_review status.
// Should be called daily via Supabase cron or external scheduler.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // Allow invocation via service key or cron header
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

    // Call the database function that handles expiry logic
    const { data, error } = await supabase.rpc("fn_expire_followup_escrow");

    if (error) {
      console.error("[expire-escrow] RPC error:", error);
      return successResponse({ expired: 0, error: error.message }, 200, origin);
    }

    const expiredCount = data ?? 0;
    console.log(`[expire-escrow] Expired ${expiredCount} follow-up escrow entries`);

    return successResponse({
      expired: expiredCount,
      timestamp: new Date().toISOString(),
    }, 200, origin);

  } catch (err) {
    console.error("[expire-escrow] Error:", err);
    return errorResponse(err, origin);
  }
});
