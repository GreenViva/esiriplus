// functions/handle-consultation-request/index.ts
// Handles the full consultation request lifecycle:
//   action: "create"  — patient sends request to a specific doctor (60s TTL)
//   action: "accept"  — doctor accepts → creates consultation + notifies patient
//   action: "reject"  — doctor rejects → notifies patient
//   action: "expire"  — client-side fallback to mark expired (server validates timestamp)
//   action: "status"  — poll current request status (fallback when Realtime is unreliable)
//
// Server-side expiry enforcement prevents race conditions:
//   - Accept after expiry is rejected
//   - Only PENDING requests can transition
//   - Only assigned doctor can accept/reject
//
// Rate limit: 10/min per user.

import { handlePreflight, corsHeaders } from "../_shared/cors.ts";
import { validateAuth, type AuthResult } from "../_shared/auth.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const REQUEST_TTL_SECONDS = 60;

// Duration per service type (minutes) — must match get_service_duration_minutes SQL
const SERVICE_DURATIONS: Record<string, number> = {
  nurse: 15,
  clinical_officer: 15,
  pharmacist: 5,
  gp: 15,
  specialist: 20,
  psychologist: 30,
};

// Service tier fees (TZS) — must match client-side service_tiers
const SERVICE_FEES: Record<string, number> = {
  nurse: 5000,
  clinical_officer: 7000,
  pharmacist: 3000,
  gp: 10000,
  specialist: 30000,
  psychologist: 50000,
};

// ── Types ────────────────────────────────────────────────────────────────────

interface CreateRequest {
  action: "create";
  doctor_id: string;
  service_type: string;
  service_tier?: string;
  consultation_type?: string;
  chief_complaint?: string;
  symptoms?: string;
  patient_age_group?: string;
  patient_sex?: string;
  patient_blood_group?: string;
  patient_allergies?: string;
  patient_chronic_conditions?: string;
  is_follow_up?: boolean;
  parent_consultation_id?: string;
  agent_id?: string;
  is_substitute_follow_up?: boolean;
  original_doctor_id?: string;
  region?: string;
  service_region?: string;
  service_district?: string;
  service_ward?: string;
  service_street?: string;
  appointment_id?: string;
}

interface RespondRequest {
  action: "accept" | "reject";
  request_id: string;
}

interface ExpireRequest {
  action: "expire";
  request_id: string;
}

interface StatusRequest {
  action: "status";
  request_id: string;
}

type RequestBody = CreateRequest | RespondRequest | ExpireRequest | StatusRequest;

// ── Validation ───────────────────────────────────────────────────────────────

function validate(body: unknown): RequestBody {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  const action = b.action as string;
  if (!["create", "accept", "reject", "expire", "status"].includes(action)) {
    throw new ValidationError(
      'action must be one of: create, accept, reject, expire, status'
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
  const isFollowUp = body.is_follow_up === true && !!body.parent_consultation_id;
  const isSubstituteFollowUp = body.is_substitute_follow_up === true && isFollowUp;

  // Validate follow-up request — reopen model (no child consultations)
  if (isFollowUp) {
    const { data: consultation } = await supabase
      .from("consultations")
      .select("consultation_id, doctor_id, patient_session_id, status, service_tier, follow_up_expiry, follow_up_count, follow_up_max, is_reopened")
      .eq("consultation_id", body.parent_consultation_id!)
      .single();

    if (!consultation) {
      throw new ValidationError("Consultation not found");
    }
    if (consultation.patient_session_id !== auth.sessionId) {
      throw new ValidationError("Not authorized for this consultation");
    }
    if (consultation.status !== "completed") {
      throw new ValidationError("Consultation is not completed");
    }
    if (consultation.is_reopened) {
      throw new ValidationError("Consultation is already reopened");
    }
    if (consultation.follow_up_expiry && new Date(consultation.follow_up_expiry) < new Date()) {
      throw new ValidationError("Follow-up window has expired");
    }
    // Check follow-up limit (-1 = unlimited for Royal)
    if (consultation.follow_up_max > 0 && consultation.follow_up_count >= consultation.follow_up_max) {
      throw new ValidationError(
        `Follow-up limit reached (${consultation.follow_up_count} of ${consultation.follow_up_max}).`
      );
    }
    // Substitute follow-ups can use a different doctor
    if (!isSubstituteFollowUp && body.doctor_id !== consultation.doctor_id) {
      throw new ValidationError("Follow-up must be with the same doctor");
    }
    if (isSubstituteFollowUp) {
      if (!body.original_doctor_id || body.original_doctor_id !== consultation.doctor_id) {
        throw new ValidationError("original_doctor_id must match the consultation's doctor");
      }
    }

    // Force follow-up to inherit consultation's tier
    body.service_tier = (consultation.service_tier ?? "ECONOMY").toUpperCase();
  }

  // ── Appointment link validation ──────────────────────────────────────────
  if (body.appointment_id) {
    const { data: appt } = await supabase
      .from("appointments")
      .select("appointment_id, patient_session_id, consultation_id, status")
      .eq("appointment_id", body.appointment_id)
      .single();

    if (!appt) {
      throw new ValidationError("Appointment not found");
    }
    if (appt.patient_session_id !== auth.sessionId) {
      throw new ValidationError("Not authorized for this appointment");
    }
    if (appt.consultation_id) {
      throw new ValidationError("This appointment already has a consultation");
    }
    if (!["booked", "confirmed", "missed"].includes(appt.status)) {
      throw new ValidationError(`Appointment cannot be started — current status: ${appt.status}`);
    }
    // Check for an existing pending request for this appointment
    const { data: pendingReq } = await supabase
      .from("consultation_requests")
      .select("request_id")
      .eq("appointment_id", body.appointment_id)
      .eq("status", "pending")
      .gt("expires_at", new Date().toISOString())
      .limit(1)
      .maybeSingle();

    if (pendingReq) {
      throw new ValidationError("A request for this appointment is already pending");
    }
  }

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

  // Check if doctor is already in an active session
  const { data: profile } = await supabase
    .from("doctor_profiles")
    .select("in_session")
    .eq("doctor_id", body.doctor_id)
    .single();

  if (profile?.in_session) {
    return successResponse(
      {
        error: "doctor_in_session",
        message: "Doctor is currently in a session. You can book an appointment for later.",
        suggest_booking: true,
      },
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
      service_tier: (body.service_tier ?? "ECONOMY").toUpperCase(),
      service_region: (body.service_region ?? "TANZANIA").toUpperCase(),
      service_district: body.service_district?.trim() || null,
      service_ward: body.service_ward?.trim() || null,
      service_street: body.service_street?.trim() || null,
      consultation_type: body.consultation_type ?? "chat",
      chief_complaint: body.chief_complaint ?? "",
      symptoms: body.symptoms ?? null,
      patient_age_group: body.patient_age_group ?? null,
      patient_sex: body.patient_sex ?? null,
      patient_blood_group: body.patient_blood_group ?? null,
      patient_allergies: body.patient_allergies ?? null,
      patient_chronic_conditions: body.patient_chronic_conditions ?? null,
      is_follow_up: isFollowUp,
      parent_consultation_id: isFollowUp ? body.parent_consultation_id : null,
      agent_id: body.agent_id ?? null,
      is_substitute_follow_up: isSubstituteFollowUp,
      original_doctor_id: isSubstituteFollowUp ? body.original_doctor_id : null,
      appointment_id: body.appointment_id ?? null,
      status: "pending",
      created_at: now.toISOString(),
      expires_at: expiresAt.toISOString(),
    })
    .select("request_id, status, created_at, expires_at")
    .single();

  if (error) throw error;

  // Best-effort: update patient_sessions.region if provided and currently NULL
  if (body.region && typeof body.region === "string" && body.region.trim()) {
    try {
      await supabase
        .from("patient_sessions")
        .update({ region: body.region.trim() })
        .eq("session_id", auth.sessionId)
        .is("region", null);
    } catch {
      // Non-blocking — region capture failure must never block consultation
    }
  }

  // Send push notification to the doctor
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        user_id: body.doctor_id,
        title: isSubstituteFollowUp ? "Follow-up Patient Request" : isFollowUp ? "Follow-up Request (Royal)" : "New Consultation Request",
        body: isSubstituteFollowUp
          ? `Follow-up patient calling — ${body.service_type} consultation. Respond within 60s.`
          : isFollowUp
          ? `A Royal patient is requesting a follow-up ${body.service_type} consultation. Respond within 60s.`
          : body.symptoms
            ? `Patient: ${[body.patient_age_group, body.patient_sex].filter(Boolean).join(" | ") || "N/A"} — "${body.symptoms.slice(0, 80)}". Respond within 60s.`
            : `A patient is requesting a ${body.service_type} consultation. You have 60 seconds to respond.`,
        type: "consultation_request",
        data: {
          request_id: request.request_id,
          service_type: body.service_type,
          doctor_id: body.doctor_id,
        },
      },
      headers: {
        "X-Service-Key": Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
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

  const patientSessionId = request.patient_session_id;
  const isFollowUpAccept = request.is_follow_up === true && !!request.parent_consultation_id;

  let consultation: { consultation_id: string; status: string; created_at: string } | null = null;
  let consultationError: { message: string; code?: string; details?: string } | null = null;

  if (isFollowUpAccept) {
    // ── REOPEN existing consultation (no new row) ──────────────────────
    const { error: reopenError } = await supabase.rpc("reopen_consultation", {
      p_consultation_id: request.parent_consultation_id,
      p_doctor_id: auth.userId,
      p_service_type: request.service_type,
    });

    if (reopenError) {
      console.error("reopen_consultation RPC error:", JSON.stringify(reopenError));
      consultationError = { message: reopenError.message };
    } else {
      // Return the SAME consultation_id — it's been reopened, not created
      consultation = {
        consultation_id: request.parent_consultation_id!,
        status: "active",
        created_at: new Date().toISOString(),
      };
      console.log("reopen_consultation succeeded for:", request.parent_consultation_id);
    }
  } else {
    // ── CREATE new consultation (original request) ─────────────────────
    const baseFee = SERVICE_FEES[request.service_type] ?? 5000;
    const tierMultiplier = (request.service_tier ?? "ECONOMY").toUpperCase() === "ROYAL" ? 10 : 1;
    const consultationFee = baseFee * tierMultiplier;

    const { data: rpcData, error: rpcError } = await supabase.rpc(
      "create_consultation_with_cleanup",
      {
        p_patient_session_id: patientSessionId,
        p_doctor_id: auth.userId,
        p_service_type: request.service_type,
        p_consultation_type: request.consultation_type ?? "chat",
        p_chief_complaint: request.chief_complaint ?? "",
        p_consultation_fee: consultationFee,
        p_request_expires_at: request.expires_at,
        p_request_id: body.request_id,
        p_service_tier: (request.service_tier ?? "ECONOMY").toUpperCase(),
        p_parent_consultation_id: null,
        p_agent_id: request.agent_id ?? null,
        p_is_substitute_follow_up: false,
        p_original_doctor_id: null,
      }
    ).maybeSingle();

    if (rpcError) {
      console.warn("create_consultation_with_cleanup RPC error:", JSON.stringify(rpcError));

      const { error: closeRpcError } = await supabase.rpc(
        "close_stale_consultations",
        { p_patient_session_id: patientSessionId }
      ).maybeSingle();
      if (closeRpcError) console.warn("close_stale RPC error:", JSON.stringify(closeRpcError));

      const now = new Date().toISOString();
      const durationMinutes = SERVICE_DURATIONS[request.service_type] ?? 15;
      const scheduledEnd = new Date(Date.now() + durationMinutes * 60_000).toISOString();
      const tier = (request.service_tier ?? "ECONOMY").toUpperCase();
      const { data: insertData, error: insertError } = await supabase
        .from("consultations")
        .insert({
          patient_session_id: patientSessionId,
          doctor_id: auth.userId,
          service_type: request.service_type,
          service_tier: tier,
          consultation_type: request.consultation_type ?? "chat",
          chief_complaint: request.chief_complaint ?? "",
          agent_id: request.agent_id ?? null,
          status: "active",
          consultation_fee: baseFee * tierMultiplier,
          request_expires_at: request.expires_at,
          request_id: body.request_id,
          session_start_time: now,
          scheduled_end_at: scheduledEnd,
          original_duration_minutes: durationMinutes,
          extension_count: 0,
          follow_up_max: tier === "ROYAL" ? -1 : 1,
          created_at: now,
          updated_at: now,
        })
        .select("consultation_id, status, created_at")
        .single();

      if (insertError) {
        consultationError = {
          message: insertError.message,
          code: (insertError as Record<string, unknown>).code as string | undefined,
          details: (insertError as Record<string, unknown>).details as string | undefined,
        };
      } else {
        consultation = insertData;
      }
    } else {
      consultation = rpcData;
      console.log("create_consultation_with_cleanup succeeded:", JSON.stringify(consultation));
    }
  }

  if (consultationError || !consultation) {
    const errMsg = consultationError?.message ?? "Unknown error";
    console.error("Consultation creation/reopen failed:", JSON.stringify(consultationError));
    return new Response(
      JSON.stringify({
        error: errMsg,
        code: "INSERT_ERROR",
        request_id: body.request_id,
        status: "insert_error",
        debug_error: errMsg,
      }),
      {
        status: 409,
        headers: { ...corsHeaders(origin), "Content-Type": "application/json" },
      },
    );
  }

  // Apply location offer if one matches. Runs after consultation is created so
  // that the consultation row is the source of truth for the final fee.
  // Non-fatal — if offer lookup fails, the consultation is already created at
  // full price and the patient just doesn't get the discount.
  // Always attempt a match, even when the patient has no location fields —
  // global offers (all-NULL targeting) still need to redeem and tick the counter.
  if (!isFollowUpAccept) {
    try {
      const tierUpper = (request.service_tier ?? "ECONOMY").toUpperCase();
      const tierMult = tierUpper === "ROYAL" ? 10 : 1;
      const tierAdjusted = (SERVICE_FEES[request.service_type] ?? 5000) * tierMult;

      const { data: offer } = await supabase.rpc("match_location_offer", {
        p_patient_session_id: patientSessionId,
        p_district: request.service_district ?? null,
        p_ward: request.service_ward ?? null,
        p_service_type: request.service_type,
        p_tier: tierUpper,
        p_region: request.service_region ?? null,
        p_street: request.service_street ?? null,
      });

      if (offer && offer.offer_id) {
        let discountedFee = tierAdjusted;
        if (offer.discount_type === "free") {
          discountedFee = 0;
        } else if (offer.discount_type === "percent") {
          const pct = Math.max(0, Math.min(100, Number(offer.discount_value) || 0));
          discountedFee = Math.max(0, Math.round(tierAdjusted * (100 - pct) / 100));
        } else if (offer.discount_type === "fixed") {
          discountedFee = Math.max(0, tierAdjusted - (Number(offer.discount_value) || 0));
        }

        // Record redemption first (unique constraint prevents double-redeem on race)
        const { error: redeemErr } = await supabase
          .from("location_offer_redemptions")
          .insert({
            offer_id: offer.offer_id,
            patient_session_id: patientSessionId,
            consultation_id: consultation.consultation_id,
            original_price: tierAdjusted,
            discounted_price: discountedFee,
          });

        if (!redeemErr && discountedFee !== tierAdjusted) {
          // IMPORTANT: consultation_fee is the DOCTOR's earning basis — leave it
          // at the full tier-adjusted price so doctors are paid the same
          // regardless of the offer. The company (via location_offer_redemptions)
          // absorbs the (tierAdjusted - discountedFee) subsidy. Patient's actual
          // payment amount is recorded on the redemption row (discounted_price).
          await supabase
            .from("consultations")
            .update({
              service_region: request.service_region ?? "TANZANIA",
              service_district: request.service_district ?? null,
              service_ward: request.service_ward ?? null,
              service_street: request.service_street ?? null,
              updated_at: new Date().toISOString(),
            })
            .eq("consultation_id", consultation.consultation_id);
          console.log(`[offer] Applied ${offer.discount_type} offer ${offer.offer_id}: patient pays ${discountedFee}, doctor earns from ${tierAdjusted}`);
        } else if (redeemErr) {
          console.warn("[offer] Redemption insert failed (patient may have already redeemed):", redeemErr.message);
        }
      }
    } catch (e) {
      console.warn("[offer] Location offer lookup failed (non-fatal):", e);
    }
  }

  // Single atomic update: mark accepted + link consultation_id
  // This fires ONE realtime event with both status="accepted" and consultation_id set,
  // so the patient client can immediately navigate to the chat screen.
  const { error: updateError } = await supabase
    .from("consultation_requests")
    .update({
      status: "accepted",
      consultation_id: consultation.consultation_id,
    })
    .eq("request_id", body.request_id)
    .eq("status", "pending"); // Optimistic lock

  if (updateError) throw updateError;

  // Link appointment to consultation (if this request came from an appointment)
  if (request.appointment_id) {
    try {
      await supabase
        .from("appointments")
        .update({
          consultation_id: consultation.consultation_id,
          status: "in_progress",
          updated_at: new Date().toISOString(),
        })
        .eq("appointment_id", request.appointment_id)
        .is("consultation_id", null); // Guard: one appointment = one consultation
    } catch (e) {
      console.error("Failed to link appointment:", e);
      // Non-fatal — consultation still proceeds
    }
  }

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
      headers: {
        "X-Service-Key": Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
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
      headers: {
        "X-Service-Key": Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
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
    .select("request_id, status, expires_at, patient_session_id, doctor_id")
    .eq("request_id", body.request_id)
    .single();

  if (!request) {
    throw new ValidationError("Request not found");
  }

  // Security: verify caller owns this request
  const isPatient = auth.sessionId === request.patient_session_id;
  const isDoctor = auth.userId === request.doctor_id;
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

async function handleStatus(
  body: StatusRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  const { data: request } = await supabase
    .from("consultation_requests")
    .select("request_id, status, consultation_id, patient_session_id, doctor_id")
    .eq("request_id", body.request_id)
    .single();

  if (!request) {
    throw new ValidationError("Request not found");
  }

  // Verify caller owns this request
  const isPatient = auth.sessionId === request.patient_session_id;
  const isDoctor = auth.userId === request.doctor_id;
  if (!isPatient && !isDoctor) {
    throw new ValidationError("Not authorized");
  }

  return successResponse(
    {
      request_id: request.request_id,
      status: request.status,
      consultation_id: request.consultation_id,
    },
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
      case "status":
        return await handleStatus(body as StatusRequest, auth, origin);
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
