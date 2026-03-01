// functions/manage-consultation/index.ts
// Manages timed consultation sessions, extension workflow, and session ending.
//
// Actions:
//   sync               — both: fetch consultation state + server time for timer sync
//   end                — doctor: end the consultation
//   timer_expired      — either: mark session as awaiting_extension
//   request_extension  — doctor: notify patient about extension request
//   accept_extension   — patient: start grace period for payment
//   decline_extension  — patient: decline, doctor can then end
//   payment_confirmed  — patient: extend consultation after successful payment
//   cancel_payment     — patient: revert grace_period → awaiting_extension
//
// Rate limit: 10/min per user.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, type AuthResult } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// Duration per service type (must match get_service_duration_minutes SQL)
const SERVICE_DURATIONS: Record<string, number> = {
  nurse: 15,
  clinical_officer: 15,
  pharmacist: 5,
  gp: 15,
  specialist: 20,
  psychologist: 30,
};

// ── Types ────────────────────────────────────────────────────────────────────

type Action =
  | "sync"
  | "end"
  | "timer_expired"
  | "request_extension"
  | "accept_extension"
  | "decline_extension"
  | "payment_confirmed"
  | "cancel_payment";

interface RequestBody {
  action: Action;
  consultation_id: string;
  payment_id?: string;
}

const VALID_ACTIONS: Action[] = [
  "sync",
  "end",
  "timer_expired",
  "request_extension",
  "accept_extension",
  "decline_extension",
  "payment_confirmed",
  "cancel_payment",
];

// ── Validation ───────────────────────────────────────────────────────────────

function validate(body: unknown): RequestBody {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  if (!VALID_ACTIONS.includes(b.action as Action)) {
    throw new ValidationError(
      `action must be one of: ${VALID_ACTIONS.join(", ")}`
    );
  }
  if (typeof b.consultation_id !== "string" || !b.consultation_id) {
    throw new ValidationError("consultation_id is required");
  }
  if (b.action === "payment_confirmed" && (typeof b.payment_id !== "string" || !b.payment_id)) {
    throw new ValidationError("payment_id is required for payment_confirmed");
  }

  return b as unknown as RequestBody;
}

// ── Helper: insert system message into chat ──────────────────────────────────

async function insertSystemMessage(
  consultationId: string,
  text: string
): Promise<void> {
  const supabase = getServiceClient();
  const { error } = await supabase.from("messages").insert({
    consultation_id: consultationId,
    sender_type: "system",
    sender_id: "system",
    message_text: text,
    message_type: "text",
    is_read: false,
    created_at: new Date().toISOString(),
  });
  if (error) {
    console.error("Failed to insert system message:", error);
  }
}

// ── Helper: fetch consultation with guards ───────────────────────────────────

interface ConsultationRow {
  consultation_id: string;
  patient_session_id: string;
  doctor_id: string;
  status: string;
  service_type: string;
  consultation_fee: number;
  scheduled_end_at: string | null;
  extension_count: number;
  grace_period_end_at: string | null;
  original_duration_minutes: number;
  session_start_time: string | null;
}

async function fetchConsultation(consultationId: string): Promise<ConsultationRow> {
  const supabase = getServiceClient();
  const { data, error } = await supabase
    .from("consultations")
    .select(
      "consultation_id, patient_session_id, doctor_id, status, service_type, " +
      "consultation_fee, scheduled_end_at, extension_count, grace_period_end_at, " +
      "original_duration_minutes, session_start_time"
    )
    .eq("consultation_id", consultationId)
    .single();

  if (error || !data) {
    throw new ValidationError("Consultation not found");
  }
  return data as ConsultationRow;
}

function buildSyncPayload(c: ConsultationRow, serverTime: string) {
  return {
    consultation_id: c.consultation_id,
    status: c.status,
    service_type: c.service_type,
    consultation_fee: c.consultation_fee,
    scheduled_end_at: c.scheduled_end_at,
    extension_count: c.extension_count,
    grace_period_end_at: c.grace_period_end_at,
    original_duration_minutes: c.original_duration_minutes,
    session_start_time: c.session_start_time,
    server_time: serverTime,
  };
}

// ── Handlers ─────────────────────────────────────────────────────────────────

async function handleSync(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const consultation = await fetchConsultation(body.consultation_id);

  // Verify caller is participant
  const isDoctor = auth.userId === consultation.doctor_id;
  const isPatient = auth.sessionId === consultation.patient_session_id;
  if (!isDoctor && !isPatient) {
    throw new ValidationError("You are not a participant of this consultation");
  }

  const supabase = getServiceClient();
  const { data: timeData } = await supabase.rpc("get_server_time");
  const serverTime = timeData ?? new Date().toISOString();

  return successResponse(buildSyncPayload(consultation, serverTime), 200, origin);
}

async function handleEnd(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.userId) {
    throw new ValidationError("Only doctors can end consultations");
  }

  const supabase = getServiceClient();
  const { error } = await supabase.rpc("end_consultation", {
    p_consultation_id: body.consultation_id,
    p_doctor_id: auth.userId,
  });

  if (error) {
    throw new ValidationError(error.message);
  }

  await insertSystemMessage(body.consultation_id, "Consultation ended by doctor.");

  await logEvent({
    function_name: "manage-consultation",
    level: "info",
    user_id: auth.userId,
    action: "consultation_ended",
    metadata: { consultation_id: body.consultation_id },
  });

  const consultation = await fetchConsultation(body.consultation_id);
  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(consultation, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

async function handleTimerExpired(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const consultation = await fetchConsultation(body.consultation_id);

  // Verify caller is participant
  const isDoctor = auth.userId === consultation.doctor_id;
  const isPatient = auth.sessionId === consultation.patient_session_id;
  if (!isDoctor && !isPatient) {
    throw new ValidationError("You are not a participant of this consultation");
  }

  // Only doctors can trigger this — they see the timer
  // But we accept from either side as a fallback
  const doctorId = consultation.doctor_id;

  const supabase = getServiceClient();
  const { error } = await supabase.rpc("mark_awaiting_extension", {
    p_consultation_id: body.consultation_id,
    p_doctor_id: doctorId,
  });

  if (error) {
    // If already in awaiting_extension, treat as idempotent
    if (error.message.includes("not active") || error.message.includes("not found")) {
      const updated = await fetchConsultation(body.consultation_id);
      if (updated.status === "awaiting_extension") {
        const { data: timeData } = await supabase.rpc("get_server_time");
        return successResponse(
          buildSyncPayload(updated, timeData ?? new Date().toISOString()),
          200,
          origin
        );
      }
    }
    throw new ValidationError(error.message);
  }

  await insertSystemMessage(body.consultation_id, "Session time has ended.");

  const updated = await fetchConsultation(body.consultation_id);
  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(updated, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

async function handleRequestExtension(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.userId) {
    throw new ValidationError("Only doctors can request extensions");
  }

  const consultation = await fetchConsultation(body.consultation_id);
  if (consultation.doctor_id !== auth.userId) {
    throw new ValidationError("You are not the doctor for this consultation");
  }
  if (consultation.status !== "awaiting_extension") {
    throw new ValidationError("Consultation is not awaiting extension");
  }

  await insertSystemMessage(body.consultation_id, "Doctor requested time extension.");

  // Notify patient via push
  const supabase = getServiceClient();
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        session_id: consultation.patient_session_id,
        title: "Extension Requested",
        body: "Your doctor would like to extend the session. Would you like to continue?",
        type: "extension_requested",
        data: {
          consultation_id: body.consultation_id,
          fee: consultation.consultation_fee,
          duration_minutes: consultation.original_duration_minutes,
        },
      },
    });
  } catch (e) {
    console.error("Failed to send extension notification:", e);
  }

  await logEvent({
    function_name: "manage-consultation",
    level: "info",
    user_id: auth.userId,
    action: "extension_requested",
    metadata: { consultation_id: body.consultation_id },
  });

  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(consultation, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

async function handleAcceptExtension(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.sessionId) {
    throw new ValidationError("Only patients can accept extensions");
  }

  const supabase = getServiceClient();
  const { error } = await supabase.rpc("start_grace_period", {
    p_consultation_id: body.consultation_id,
    p_patient_session_id: auth.sessionId,
  });

  if (error) {
    throw new ValidationError(error.message);
  }

  await insertSystemMessage(body.consultation_id, "Patient is processing extension payment.");

  const consultation = await fetchConsultation(body.consultation_id);
  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(consultation, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

async function handleDeclineExtension(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.sessionId) {
    throw new ValidationError("Only patients can decline extensions");
  }

  const consultation = await fetchConsultation(body.consultation_id);
  if (consultation.patient_session_id !== auth.sessionId) {
    throw new ValidationError("You are not the patient for this consultation");
  }
  if (consultation.status !== "awaiting_extension") {
    throw new ValidationError("Consultation is not awaiting extension");
  }

  await insertSystemMessage(body.consultation_id, "Patient declined the extension.");

  const supabase = getServiceClient();
  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(consultation, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

async function handlePaymentConfirmed(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.sessionId) {
    throw new ValidationError("Only patients can confirm extension payment");
  }

  const supabase = getServiceClient();
  const { error } = await supabase.rpc("extend_consultation", {
    p_consultation_id: body.consultation_id,
    p_payment_id: body.payment_id,
  });

  if (error) {
    throw new ValidationError(error.message);
  }

  const consultation = await fetchConsultation(body.consultation_id);
  const duration = consultation.original_duration_minutes;

  await insertSystemMessage(
    body.consultation_id,
    `Session extended by ${duration} minutes.`
  );

  await logEvent({
    function_name: "manage-consultation",
    level: "info",
    session_id: auth.sessionId,
    action: "consultation_extended",
    metadata: {
      consultation_id: body.consultation_id,
      payment_id: body.payment_id,
      extension_count: consultation.extension_count,
    },
  });

  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(consultation, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

async function handleCancelPayment(
  body: RequestBody,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.sessionId) {
    throw new ValidationError("Only patients can cancel extension payment");
  }

  const supabase = getServiceClient();
  const { error } = await supabase.rpc("cancel_extension", {
    p_consultation_id: body.consultation_id,
  });

  if (error) {
    throw new ValidationError(error.message);
  }

  await insertSystemMessage(body.consultation_id, "Extension payment cancelled.");

  const consultation = await fetchConsultation(body.consultation_id);
  const { data: timeData } = await supabase.rpc("get_server_time");

  return successResponse(
    buildSyncPayload(consultation, timeData ?? new Date().toISOString()),
    200,
    origin
  );
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const identifier = auth.userId ?? auth.sessionId ?? "anon";
    await LIMITS.payment(identifier);

    const raw = await req.json();
    const body = validate(raw);

    switch (body.action) {
      case "sync":
        return await handleSync(body, auth, origin);
      case "end":
        return await handleEnd(body, auth, origin);
      case "timer_expired":
        return await handleTimerExpired(body, auth, origin);
      case "request_extension":
        return await handleRequestExtension(body, auth, origin);
      case "accept_extension":
        return await handleAcceptExtension(body, auth, origin);
      case "decline_extension":
        return await handleDeclineExtension(body, auth, origin);
      case "payment_confirmed":
        return await handlePaymentConfirmed(body, auth, origin);
      case "cancel_payment":
        return await handleCancelPayment(body, auth, origin);
      default:
        throw new ValidationError("Unknown action");
    }
  } catch (err) {
    await logEvent({
      function_name: "manage-consultation",
      level: "error",
      action: "manage_consultation_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
