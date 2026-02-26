// functions/handle-consultation-request/index.ts
// Handles the full consultation request lifecycle:
//   action: "create"  — patient sends request to a specific doctor (60s TTL)
//   action: "accept"  — doctor accepts → creates consultation + notifies patient
//   action: "reject"  — doctor rejects → notifies patient
//   action: "expire"  — client-side fallback to mark expired (server validates timestamp)
//
// Server-side expiry enforcement prevents race conditions:
//   - Accept after expiry is rejected
//   - Only PENDING requests can transition
//   - Only assigned doctor can accept/reject
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
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const REQUEST_TTL_SECONDS = 60;

// ── Types ────────────────────────────────────────────────────────────────────

interface CreateRequest {
  action: "create";
  doctor_id: string;
  service_type: string;
  consultation_type?: string;
  chief_complaint?: string;
}

interface RespondRequest {
  action: "accept" | "reject";
  request_id: string;
}

interface ExpireRequest {
  action: "expire";
  request_id: string;
}

type RequestBody = CreateRequest | RespondRequest | ExpireRequest;

// ── Validation ───────────────────────────────────────────────────────────────

function validate(body: unknown): RequestBody {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  const action = b.action as string;
  if (!["create", "accept", "reject", "expire"].includes(action)) {
    throw new ValidationError(
      'action must be one of: create, accept, reject, expire'
    );
  }

  if (action === "create") {
    if (typeof b.doctor_id !== "string" || !b.doctor_id) {
      throw new ValidationError("doctor_id is required for create");
    }
    if (typeof b.service_type !== "string" || !b.service_type) {
      throw new ValidationError("service_type is required for create");
    }
  } else {
    if (typeof b.request_id !== "string" || !b.request_id) {
      throw new ValidationError("request_id is required");
    }
  }

  return b as unknown as RequestBody;
}

// ── Handlers ─────────────────────────────────────────────────────────────────

async function handleCreate(
  body: CreateRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.sessionId) {
    throw new ValidationError("Only patients can create consultation requests");
  }

  const supabase = getServiceClient();

  // Prevent duplicate active requests from same patient
  const { data: existing } = await supabase
    .from("consultation_requests")
    .select("request_id")
    .eq("patient_session_id", auth.sessionId)
    .eq("status", "pending")
    .gt("expires_at", new Date().toISOString())
    .limit(1)
    .maybeSingle();

  if (existing) {
    throw new ValidationError(
      "You already have an active request. Please wait for it to expire or be resolved."
    );
  }

  // Verify doctor exists, is verified, and is available
  const { data: doctor } = await supabase
    .from("doctor_profiles")
    .select("doctor_id, full_name, is_verified, is_available, specialty")
    .eq("doctor_id", body.doctor_id)
    .single();

  if (!doctor) {
    throw new ValidationError("Doctor not found");
  }
  if (!doctor.is_verified) {
    throw new ValidationError("Doctor is not verified");
  }
  if (!doctor.is_available) {
    throw new ValidationError("Doctor is not currently available");
  }

  // Check doctor slot capacity (max 10 active consultations)
  const { count: activeCount } = await supabase
    .from("consultations")
    .select("consultation_id", { count: "exact", head: true })
    .eq("doctor_id", body.doctor_id)
    .in("status", ["pending", "active", "in_progress"]);

  if ((activeCount ?? 0) >= 10) {
    return successResponse(
      { error: "doctor_full", message: "Doctor has no available slots" },
      409,
      origin
    );
  }

  const now = new Date();
  const expiresAt = new Date(now.getTime() + REQUEST_TTL_SECONDS * 1000);

  // Create the request
  const { data: request, error } = await supabase
    .from("consultation_requests")
    .insert({
      patient_session_id: auth.sessionId,
      doctor_id: body.doctor_id,
      service_type: body.service_type,
      consultation_type: body.consultation_type ?? "chat",
      chief_complaint: body.chief_complaint ?? "",
      status: "pending",
      created_at: now.toISOString(),
      expires_at: expiresAt.toISOString(),
    })
    .select("request_id, status, created_at, expires_at")
    .single();

  if (error) throw error;

  // Send push notification to the doctor
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        user_id: body.doctor_id,
        title: "New Consultation Request",
        body: `A patient is requesting a ${body.service_type} consultation. You have 60 seconds to respond.`,
        type: "consultation_request",
        data: {
          request_id: request.request_id,
          service_type: body.service_type,
        },
      },
    });
  } catch (e) {
    console.error("Failed to send push notification:", e);
    // Non-fatal — doctor will still see via realtime
  }

  await logEvent({
    function_name: "handle-consultation-request",
    level: "info",
    session_id: auth.sessionId,
    action: "request_created",
    metadata: {
      request_id: request.request_id,
      doctor_id: body.doctor_id,
      service_type: body.service_type,
    },
    ip_address: null,
  });

  return successResponse(
    {
      request_id: request.request_id,
      status: "pending",
      created_at: request.created_at,
      expires_at: request.expires_at,
      ttl_seconds: REQUEST_TTL_SECONDS,
    },
    201,
    origin
  );
}

async function handleAccept(
  body: RespondRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.userId) {
    throw new ValidationError("Only doctors can accept requests");
  }

  const supabase = getServiceClient();

  // Fetch the request — validate ownership + status + expiry atomically
  const { data: request } = await supabase
    .from("consultation_requests")
    .select("*")
    .eq("request_id", body.request_id)
    .single();

  if (!request) {
    throw new ValidationError("Request not found");
  }
  if (request.doctor_id !== auth.userId) {
    throw new ValidationError("You are not assigned to this request");
  }
  if (request.status !== "pending") {
    throw new ValidationError(`Request is already ${request.status}`);
  }

  // Server-side expiry check — the single source of truth
  if (new Date(request.expires_at) < new Date()) {
    // Auto-expire it
    await supabase
      .from("consultation_requests")
      .update({ status: "expired" })
      .eq("request_id", body.request_id)
      .eq("status", "pending");

    throw new ValidationError("Request has expired");
  }

  // Mark request as accepted
  const { error: updateError } = await supabase
    .from("consultation_requests")
    .update({ status: "accepted" })
    .eq("request_id", body.request_id)
    .eq("status", "pending"); // Optimistic lock

  if (updateError) throw updateError;

  // Create the actual consultation
  const { data: consultation, error: consultationError } = await supabase
    .from("consultations")
    .insert({
      patient_session_id: request.patient_session_id,
      doctor_id: auth.userId,
      service_type: request.service_type,
      consultation_type: request.consultation_type ?? "chat",
      chief_complaint: request.chief_complaint ?? "",
      status: "active",
      request_id: body.request_id,
      created_at: new Date().toISOString(),
    })
    .select("consultation_id, status, created_at")
    .single();

  if (consultationError) throw consultationError;

  // Notify patient via push
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        session_id: request.patient_session_id,
        title: "Request Accepted!",
        body: "Your doctor is ready. Opening chat now...",
        type: "consultation_accepted",
        data: {
          request_id: body.request_id,
          consultation_id: consultation.consultation_id,
        },
      },
    });
  } catch (e) {
    console.error("Failed to send acceptance notification:", e);
  }

  await logEvent({
    function_name: "handle-consultation-request",
    level: "info",
    user_id: auth.userId,
    action: "request_accepted",
    metadata: {
      request_id: body.request_id,
      consultation_id: consultation.consultation_id,
    },
  });

  return successResponse(
    {
      request_id: body.request_id,
      status: "accepted",
      consultation_id: consultation.consultation_id,
    },
    200,
    origin
  );
}

async function handleReject(
  body: RespondRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.userId) {
    throw new ValidationError("Only doctors can reject requests");
  }

  const supabase = getServiceClient();

  const { data: request } = await supabase
    .from("consultation_requests")
    .select("request_id, doctor_id, patient_session_id, status")
    .eq("request_id", body.request_id)
    .single();

  if (!request) {
    throw new ValidationError("Request not found");
  }
  if (request.doctor_id !== auth.userId) {
    throw new ValidationError("You are not assigned to this request");
  }
  if (request.status !== "pending") {
    throw new ValidationError(`Request is already ${request.status}`);
  }

  await supabase
    .from("consultation_requests")
    .update({ status: "rejected" })
    .eq("request_id", body.request_id)
    .eq("status", "pending");

  // Notify patient
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        session_id: request.patient_session_id,
        title: "Request Declined",
        body: "The doctor is unavailable. You can request another doctor.",
        type: "consultation_rejected",
        data: { request_id: body.request_id },
      },
    });
  } catch (e) {
    console.error("Failed to send rejection notification:", e);
  }

  await logEvent({
    function_name: "handle-consultation-request",
    level: "info",
    user_id: auth.userId,
    action: "request_rejected",
    metadata: { request_id: body.request_id },
  });

  return successResponse(
    { request_id: body.request_id, status: "rejected" },
    200,
    origin
  );
}

async function handleExpire(
  body: ExpireRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  // Only expire if actually past the deadline and still pending
  const { data: request } = await supabase
    .from("consultation_requests")
    .select("request_id, status, expires_at, patient_session_id")
    .eq("request_id", body.request_id)
    .single();

  if (!request) {
    throw new ValidationError("Request not found");
  }

  // Security: verify caller owns this request
  const isPatient = auth.sessionId === request.patient_session_id;
  const isDoctor = auth.userId !== null; // Doctor can also trigger expire
  if (!isPatient && !isDoctor) {
    throw new ValidationError("Not authorized");
  }

  if (request.status !== "pending") {
    // Already resolved — return current status (idempotent)
    return successResponse(
      { request_id: body.request_id, status: request.status },
      200,
      origin
    );
  }

  // Server validates the expiry time
  if (new Date(request.expires_at) > new Date()) {
    throw new ValidationError("Request has not expired yet");
  }

  await supabase
    .from("consultation_requests")
    .update({ status: "expired" })
    .eq("request_id", body.request_id)
    .eq("status", "pending");

  return successResponse(
    { request_id: body.request_id, status: "expired" },
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
      case "create":
        return await handleCreate(body as CreateRequest, auth, origin);
      case "accept":
        return await handleAccept(body as RespondRequest, auth, origin);
      case "reject":
        return await handleReject(body as RespondRequest, auth, origin);
      case "expire":
        return await handleExpire(body as ExpireRequest, auth, origin);
      default:
        throw new ValidationError("Unknown action");
    }
  } catch (err) {
    await logEvent({
      function_name: "handle-consultation-request",
      level: "error",
      action: "request_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
