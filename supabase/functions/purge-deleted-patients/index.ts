// purge-deleted-patients: Daily cron — permanently deletes patient
// sessions whose 30-day soft-delete window has elapsed. Mirrors the
// expire-followup-escrow / cleanup-expired-messages pattern.
//
// The actual delete logic (cascading through messages, consultations,
// requests, reports, appointments, patient_reports) lives in the SQL
// function fn_purge_deleted_patients (migration
// 20260427110000_patient_account_deletion.sql).

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
    const { data, error } = await supabase.rpc("fn_purge_deleted_patients");

    if (error) {
      console.error("[purge-deleted-patients] RPC error:", error);
      return successResponse({ purged: 0, error: error.message }, 200, origin);
    }

    const purgedCount = data ?? 0;
    console.log(`[purge-deleted-patients] Purged ${purgedCount} expired soft-deleted accounts`);

    return successResponse({
      purged: purgedCount,
      timestamp: new Date().toISOString(),
    }, 200, origin);

  } catch (err) {
    console.error("[purge-deleted-patients] Error:", err);
    return errorResponse(err, origin);
  }
});
