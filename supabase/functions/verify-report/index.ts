// functions/verify-report/index.ts
// Public-ish endpoint to verify a consultation report by QR code.
// Rate limit: 30/min. Auth optional (pharmacies / hospitals may check without login).

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // Support both POST body and GET query param
    let verificationCode: string | null = null;

    if (req.method === "GET") {
      const url = new URL(req.url);
      verificationCode = url.searchParams.get("code");
    } else if (req.method === "POST") {
      const body = await req.json();
      verificationCode = body?.code ?? null;
    }

    if (!verificationCode || typeof verificationCode !== "string") {
      throw new ValidationError("Verification code is required (query param ?code= or POST body { code })");
    }

    // Sanitise: alphanumeric only
    if (!/^[A-Z0-9]{12}$/.test(verificationCode.toUpperCase())) {
      throw new ValidationError("Invalid verification code format");
    }

    const supabase = getServiceClient();

    const { data: report } = await supabase
      .from("consultation_reports")
      .select(`
        report_id,
        chief_complaint,
        assessment,
        plan,
        follow_up,
        is_ai_generated,
        created_at,
        consultation_id,
        consultations (
          service_type,
          started_at,
          ended_at
        ),
        doctor_profiles:doctor_id (
          full_name,
          specialization,
          license_number
        )
      `)
      .eq("verification_code", verificationCode.toUpperCase())
      .single();

    if (!report) {
      await logEvent({
        function_name: "verify-report",
        level: "warn",
        action: "invalid_verification_attempt",
        metadata: { code: verificationCode },
        ip_address: getClientIp(req),
      });

      return successResponse({
        valid: false,
        message: "Report not found. This QR code may be invalid or expired.",
      }, 200, origin);
    }

    await logEvent({
      function_name: "verify-report",
      level: "info",
      action: "report_verified",
      metadata: { report_id: report.report_id },
      ip_address: getClientIp(req),
    });

    return successResponse({
      valid: true,
      report_id: report.report_id,
      issued_at: report.created_at,
      is_ai_assisted: report.is_ai_generated,
      doctor: report.doctor_profiles,
      consultation: {
        service_type: report.consultations?.service_type,
        started_at: report.consultations?.started_at,
        ended_at: report.consultations?.ended_at,
      },
      clinical_summary: {
        chief_complaint: report.chief_complaint,
        assessment: report.assessment,
        plan: report.plan,
        follow_up: report.follow_up,
      },
    }, 200, origin);

  } catch (err) {
    return errorResponse(err, origin);
  }
});
