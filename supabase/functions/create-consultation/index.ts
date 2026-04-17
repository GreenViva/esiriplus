// functions/create-consultation/index.ts
// Creates a new consultation request for a patient.
// Requires verified service access payment. Rate limit: 10/min.

import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VALID_SERVICE_TYPES = [
  "nurse", "clinical_officer", "pharmacist", "gp", "specialist", "psychologist",
];

const MAX_DOCTOR_SLOTS = 10;

// When PAYMENT_ENV is "mock" (default in dev), skip payment validation.
// The UI payment flow is simulated client-side; real M-Pesa integration
// will create service_access_payments records in sandbox/production.
const PAYMENT_ENV = Deno.env.get("PAYMENT_ENV") ?? "mock";

const VALID_CONSULTATION_TYPES = ["chat", "video", "both"];
const VALID_TIERS = ["ECONOMY", "ROYAL"];

interface CreateConsultationRequest {
  service_type: string;
  consultation_type: string;
  chief_complaint: string;
  preferred_language?: string;
  doctor_id?: string;       // optional: request a specific doctor
  service_tier?: string;    // ECONOMY (default) | ROYAL
  service_region?: string;  // TANZANIA (default) | INTERNATIONAL
  service_district?: string; // e.g. "Ubungo" — used for location offers
  service_ward?: string;     // optional refinement
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

  if (b.service_tier !== undefined && !VALID_TIERS.includes(String(b.service_tier).toUpperCase())) {
    throw new ValidationError(`service_tier must be one of: ${VALID_TIERS.join(", ")}`);
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

    // Verify patient has paid for this service type (skipped in mock mode)
    if (PAYMENT_ENV !== "mock") {
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
    }

    // If doctor_id specified, verify they're verified and available
    let assignedDoctorId: string | null = body.doctor_id ?? null;
    if (assignedDoctorId) {
      const { data: doctor } = await supabase
        .from("doctor_profiles")
        .select("doctor_id, is_verified")
        .eq("doctor_id", assignedDoctorId)
        .eq("specialty", body.service_type)
        .single();

      if (!doctor) {
        throw new ValidationError("Specified doctor not found or not available for this service");
      }

      if (!doctor.is_verified) {
        throw new ValidationError("This doctor has not been verified yet and cannot accept consultations");
      }

      // Check doctor slot capacity
      const { count: activeCount } = await supabase
        .from("consultations")
        .select("consultation_id", { count: "exact", head: true })
        .eq("doctor_id", assignedDoctorId)
        .in("status", ["pending", "active", "in_progress"]);

      const usedSlots = activeCount ?? 0;
      if (usedSlots >= MAX_DOCTOR_SLOTS) {
        return new Response(
          JSON.stringify({
            error: "doctor_full",
            message: "Doctor has no available slots",
            available_slots: 0,
          }),
          {
            status: 409,
            headers: {
              ...corsHeaders(origin),
              "Content-Type": "application/json",
            },
          }
        );
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

    // Resolve consultation fee from service tiers table
    const { data: serviceTier } = await supabase
      .from("service_tiers")
      .select("price_amount, duration_minutes")
      .eq("category", body.service_type.toUpperCase())
      .eq("is_active", true)
      .single();

    // Normalize location + tier
    const tier = String(body.service_tier ?? "ECONOMY").toUpperCase();
    const region = String(body.service_region ?? "TANZANIA").toUpperCase();
    const district = body.service_district?.trim() || null;
    const ward = body.service_ward?.trim() || null;

    const basePrice = (body as Record<string, unknown>).price
      ? Number((body as Record<string, unknown>).price)
      : (serviceTier?.price_amount ?? 0);
    // Royal tier: 10x multiplier (matches client-side effectivePrice())
    const tierAdjustedPrice = tier === "ROYAL" ? basePrice * 10 : basePrice;
    const durationMinutes = (body as Record<string, unknown>).duration
      ? Number((body as Record<string, unknown>).duration)
      : (serviceTier?.duration_minutes ?? 15);

    // Check for an active location offer the patient qualifies for
    let consultationFee = tierAdjustedPrice;
    let matchedOffer: { offer_id: string } | null = null;
    if (district) {
      const { data: offer, error: offerErr } = await supabase.rpc("match_location_offer", {
        p_patient_session_id: auth.sessionId,
        p_district: district,
        p_ward: ward,
        p_service_type: body.service_type,
        p_tier: tier,
      });
      if (offerErr) {
        console.warn("[create-consultation] match_location_offer failed (non-fatal):", offerErr.message);
      } else if (offer && offer.offer_id) {
        matchedOffer = offer;
        if (offer.discount_type === "free") {
          consultationFee = 0;
        } else if (offer.discount_type === "percent") {
          const pct = Math.max(0, Math.min(100, Number(offer.discount_value) || 0));
          consultationFee = Math.max(0, Math.round(tierAdjustedPrice * (100 - pct) / 100));
        } else if (offer.discount_type === "fixed") {
          consultationFee = Math.max(0, tierAdjustedPrice - (Number(offer.discount_value) || 0));
        }
      }
    }

    // Create the consultation — always pending (doctor must accept)
    const now = new Date();
    const scheduledEndAt = new Date(now.getTime() + durationMinutes * 60 * 1000);
    const requestExpiresAt = new Date(now.getTime() + 5 * 60 * 1000); // 5 min to accept

    const { data: consultation, error } = await supabase
      .from("consultations")
      .insert({
        patient_session_id: auth.sessionId,
        doctor_id: assignedDoctorId,
        service_type: body.service_type,
        service_tier: tier,
        service_region: region,
        service_district: district,
        service_ward: ward,
        consultation_type: body.consultation_type,
        chief_complaint: body.chief_complaint.trim(),
        preferred_language: body.preferred_language ?? "en",
        status: "pending",
        consultation_fee: consultationFee,
        session_duration_minutes: durationMinutes,
        original_duration_minutes: durationMinutes,
        scheduled_end_at: scheduledEndAt.toISOString(),
        request_expires_at: requestExpiresAt.toISOString(),
        created_at: now.toISOString(),
        updated_at: now.toISOString(),
      })
      .select("consultation_id, status, created_at")
      .single();

    if (error) {
      console.error("[create-consultation] DB insert failed:", JSON.stringify(error));
      throw new Error(`DB insert failed: ${error.message} | code: ${error.code} | details: ${error.details}`);
    }

    // Record the offer redemption. Unique(offer_id, patient_session_id) protects
    // against double-redemption — on race, we revert this booking's fee to full
    // price so the patient doesn't get a discount without a recorded redemption.
    let appliedOfferId: string | null = null;
    if (matchedOffer && consultationFee !== tierAdjustedPrice) {
      const { error: redeemErr } = await supabase
        .from("location_offer_redemptions")
        .insert({
          offer_id: matchedOffer.offer_id,
          patient_session_id: auth.sessionId,
          consultation_id: consultation.consultation_id,
          original_price: tierAdjustedPrice,
          discounted_price: consultationFee,
        });

      if (redeemErr) {
        console.warn("[create-consultation] offer redemption failed, reverting to full price:", redeemErr.message);
        await supabase
          .from("consultations")
          .update({ consultation_fee: tierAdjustedPrice, updated_at: new Date().toISOString() })
          .eq("consultation_id", consultation.consultation_id);
        consultationFee = tierAdjustedPrice;
      } else {
        appliedOfferId = matchedOffer.offer_id;
      }
    }

    // Calculate remaining available slots for the doctor
    let availableSlots: number | undefined;
    if (assignedDoctorId) {
      // Send targeted push notification (non-fatal)
      try {
        await supabase.functions.invoke("send-push-notification", {
          body: {
            user_id: assignedDoctorId,
            title: "New Appointment Request",
            body: `You have a new ${body.service_type} appointment request`,
            type: "new_consultation",
            consultation_id: consultation.consultation_id,
          },
        });
      } catch (e) {
        console.warn("Push notification failed (non-fatal):", e);
      }

      const { count } = await supabase
        .from("consultations")
        .select("consultation_id", { count: "exact", head: true })
        .eq("doctor_id", assignedDoctorId)
        .in("status", ["pending", "active"]);

      availableSlots = MAX_DOCTOR_SLOTS - (count ?? 0);
    } else {
      // Broadcast to all doctors of this specialty (non-fatal)
      try {
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
      } catch (e) {
        console.warn("Push broadcast failed (non-fatal):", e);
      }
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
        doctor_id: assignedDoctorId,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: "Consultation created successfully",
      consultation_id: consultation.consultation_id,
      status: consultation.status,
      created_at: consultation.created_at,
      consultation_fee: consultationFee,
      original_price: tierAdjustedPrice,
      ...(appliedOfferId && { applied_offer_id: appliedOfferId }),
      ...(availableSlots !== undefined && { available_slots: availableSlots }),
    }, 201, origin);

  } catch (err) {
    await logEvent({
      function_name: "create-consultation",
      level: "error",
      action: "consultation_creation_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    // Temporarily expose error for debugging
    const errMsg = err instanceof Error ? err.message : String(err);
    console.error("[create-consultation] CAUGHT:", errMsg);
    return new Response(
      JSON.stringify({ error: errMsg, code: "INTERNAL_ERROR", request_id: crypto.randomUUID().slice(0, 8) }),
      { status: 500, headers: { ...corsHeaders(origin), "Content-Type": "application/json" } },
    );
  }
});
