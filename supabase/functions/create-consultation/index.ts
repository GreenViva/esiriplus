// functions/create-consultation/index.ts
// Creates a new consultation request for a patient.
// Requires verified service access payment. Rate limit: 10/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VALID_SERVICE_TYPES = [
  "nurse", "clinical_officer", "pharmacist", "gp", "specialist", "psychologist",
];

const VALID_CONSULTATION_TYPES = ["chat", "video", "both"];

interface CreateConsultationRequest {
  service_type: string;
  consultation_type: string;
  chief_complaint: string;
  preferred_language?: string;
  doctor_id?: string;       // optional: request a specific doctor
}

function validate(body: unknown): CreateConsultationRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  if (!VALID_SERVICE_TYPES.includes(b.service_type as string)) {
    throw new ValidationError(
      `service_type must be one of: ${VALID_SERVICE_TYPES.join(", ")}`
    );
  }

  if (!VALID_CONSULTATION_TYPES.includes(b.consultation_type as string)) {
    throw new ValidationError(
      `consultation_type must be one of: ${VALID_CONSULTATION_TYPES.join(", ")}`
    );
  }

  if (
    typeof b.chief_complaint !== "string" ||
    b.chief_complaint.trim().length < 10 ||
    b.chief_complaint.trim().length > 1000
  ) {
    throw new ValidationError("chief_complaint must be 10–1000 characters");
  }

  return b as unknown as CreateConsultationRequest;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.payment(auth.sessionId ?? "anon");

    if (!auth.sessionId) {
      throw new ValidationError("Only patients with active sessions can create consultations");
    }

    const raw = await req.json();
    const body = validate(raw);

    const supabase = getServiceClient();

    // Verify patient has paid for this service type
    const { data: access } = await supabase
      .from("service_access_payments")
      .select("id")
      .eq("patient_session_id", auth.sessionId)
      .eq("service_type", body.service_type)
      .eq("access_granted", true)
      .gt("expires_at", new Date().toISOString())
      .single();

    if (!access) {
      throw new ValidationError(
        `No active access for service type '${body.service_type}'. Please make payment first.`
      );
    }

    // If doctor_id specified, verify they're verified and available
    let assignedDoctorId: string | null = body.doctor_id ?? null;
    if (assignedDoctorId) {
      const { data: doctor } = await supabase
        .from("doctor_profiles")
        .select("doctor_id, is_verified")
        .eq("doctor_id", assignedDoctorId)
        .eq("is_verified", true)
        .eq("service_type", body.service_type)
        .single();

      if (!doctor) {
        throw new ValidationError("Specified doctor not found or not available for this service");
      }
    }

    // Check patient doesn't already have an open consultation
    const { data: openConsultation } = await supabase
      .from("consultations")
      .select("consultation_id")
      .eq("patient_session_id", auth.sessionId)
      .in("status", ["pending", "active", "in_progress"])
      .single();

    if (openConsultation) {
      return successResponse({
        message: "You already have an active consultation",
        consultation_id: openConsultation.consultation_id,
      }, 200, origin);
    }

    // Create the consultation
    const { data: consultation, error } = await supabase
      .from("consultations")
      .insert({
        patient_session_id: auth.sessionId,
        doctor_id: assignedDoctorId,
        service_type: body.service_type,
        consultation_type: body.consultation_type,
        chief_complaint: body.chief_complaint.trim(),
        preferred_language: body.preferred_language ?? "en",
        status: assignedDoctorId ? "active" : "pending",
        created_at: new Date().toISOString(),
      })
      .select("consultation_id, status, created_at")
      .single();

    if (error) throw error;

    // Notify available doctors if none assigned
    if (!assignedDoctorId) {
      await supabase.functions.invoke("send-push-notification", {
        body: {
          target: "doctors",
          service_type: body.service_type,
          title: "New Consultation Request",
          body: `New ${body.service_type} consultation pending`,
          type: "new_consultation",
          consultation_id: consultation.consultation_id,
        },
      });
    }

    await logEvent({
      function_name: "create-consultation",
      level: "info",
      session_id: auth.sessionId,
      action: "consultation_created",
      metadata: {
        consultation_id: consultation.consultation_id,
        service_type: body.service_type,
        consultation_type: body.consultation_type,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: "Consultation created successfully",
      consultation_id: consultation.consultation_id,
      status: consultation.status,
      created_at: consultation.created_at,
    }, 201, origin);

  } catch (err) {
    await logEvent({
      function_name: "create-consultation",
      level: "error",
      action: "consultation_creation_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
