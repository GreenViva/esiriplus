// functions/get-patient-reports/index.ts
// Returns consultation reports for the authenticated patient.
// Rate limit: 30/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.read(auth.userId ?? auth.sessionId ?? "anon");

    const supabase = getServiceClient();

    // Determine patient session ID based on auth role
    let patientSessionId: string | null = null;

    if (auth.role === "patient" || auth.sessionId) {
      patientSessionId = auth.sessionId;
    } else if (auth.role === "doctor") {
      // Doctors can also fetch reports (for their own consultations)
      // Return all reports they authored
      const { data: reports } = await supabase
        .from("consultation_reports")
        .select("*")
        .eq("doctor_id", auth.userId)
        .order("created_at", { ascending: false });

      return successResponse({ reports: reports ?? [] }, 200, origin);
    }

    if (!patientSessionId) {
      console.log("[get-patient-reports] No patientSessionId, returning empty");
      return successResponse({ reports: [] }, 200, origin);
    }

    console.log(`[get-patient-reports] Looking up reports for session: ${patientSessionId}`);

    // For patients: fetch reports via patient_session_id column
    // (set directly on consultation_reports by generate-consultation-report)
    const { data: directReports, error: directErr } = await supabase
      .from("consultation_reports")
      .select("*")
      .eq("patient_session_id", patientSessionId)
      .order("created_at", { ascending: false });

    console.log(`[get-patient-reports] Direct query: found=${directReports?.length ?? 0}, error=${directErr?.message ?? "none"}`);

    if (directReports && directReports.length > 0) {
      return successResponse({ reports: directReports }, 200, origin);
    }

    // Fallback: join through consultations table
    const { data: consultations, error: consultErr } = await supabase
      .from("consultations")
      .select("consultation_id")
      .eq("patient_session_id", patientSessionId);

    console.log(`[get-patient-reports] Fallback consultations: found=${consultations?.length ?? 0}, error=${consultErr?.message ?? "none"}`);

    if (!consultations || consultations.length === 0) {
      return successResponse({ reports: [] }, 200, origin);
    }

    const consultationIds = consultations.map((c) => c.consultation_id);
    console.log(`[get-patient-reports] Looking up reports for consultation_ids:`, consultationIds);

    const { data: reports, error: reportsErr } = await supabase
      .from("consultation_reports")
      .select("*")
      .in("consultation_id", consultationIds)
      .order("created_at", { ascending: false });

    console.log(`[get-patient-reports] Fallback reports: found=${reports?.length ?? 0}, error=${reportsErr?.message ?? "none"}`);

    await logEvent({
      function_name: "get-patient-reports",
      level: "info",
      user_id: auth.userId,
      session_id: auth.sessionId,
      action: "reports_fetched",
      metadata: { count: (reports ?? []).length },
      ip_address: getClientIp(req),
    });

    return successResponse({ reports: reports ?? [] }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "get-patient-reports",
      level: "error",
      action: "reports_fetch_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
