// functions/call-recharge-payment/index.ts
// Top-up video call minutes via M-Pesa. Rate limit: 10/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// Available recharge packages: minutes → TZS price
const RECHARGE_PACKAGES: Record<number, number> = {
  10:  200,
  30:  500,
  60:  900,
  120: 1500,
};

interface RechargeRequest {
  consultation_id: string;
  minutes: number;           // must be one of the valid packages
  phone_number: string;
  idempotency_key: string;
}

function validate(body: unknown): RechargeRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be a JSON object");
  }
  const b = body as Record<string, unknown>;

  if (typeof b.consultation_id !== "string" || !b.consultation_id) {
    throw new ValidationError("consultation_id is required");
  }

  if (typeof b.minutes !== "number" || !RECHARGE_PACKAGES[b.minutes]) {
    throw new ValidationError(
      `minutes must be one of: ${Object.keys(RECHARGE_PACKAGES).join(", ")}`
    );
  }

  if (typeof b.phone_number !== "string" || !/^2556\d{8}$|^2557\d{8}$/.test(b.phone_number)) {
    throw new ValidationError("phone_number must be in format 255XXXXXXXXX");
  }

  if (typeof b.idempotency_key !== "string" || b.idempotency_key.length < 8) {
    throw new ValidationError("idempotency_key is required (min 8 chars)");
  }

  return b as unknown as RechargeRequest;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.payment(auth.sessionId ?? auth.userId ?? "anon");

    const raw = await req.json();
    const body = validate(raw);

    const amount = RECHARGE_PACKAGES[body.minutes];
    const supabase = getServiceClient();

    // Verify the consultation belongs to this patient & is active
    const { data: consultation } = await supabase
      .from("consultations")
      .select("consultation_id, status, patient_session_id")
      .eq("consultation_id", body.consultation_id)
      .eq("patient_session_id", auth.sessionId)
      .single();

    if (!consultation) {
      throw new ValidationError("Consultation not found or does not belong to you");
    }

    if (!["active", "in_progress"].includes(consultation.status)) {
      throw new ValidationError("Cannot recharge minutes for a non-active consultation");
    }

    // Initiate STK Push via shared function
    const stkResponse = await supabase.functions.invoke("mpesa-stk-push", {
      body: {
        phone_number: body.phone_number,
        amount,
        payment_type: "call_recharge",
        consultation_id: body.consultation_id,
        idempotency_key: body.idempotency_key,
      },
      headers: { Authorization: req.headers.get("Authorization")! },
    });

    if (stkResponse.error) throw stkResponse.error;

    await logEvent({
      function_name: "call-recharge-payment",
      level: "info",
      session_id: auth.sessionId,
      action: "call_recharge_initiated",
      metadata: {
        consultation_id: body.consultation_id,
        minutes: body.minutes,
        amount,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: `Recharge of ${body.minutes} minutes (TZS ${amount}) initiated`,
      minutes: body.minutes,
      amount,
      ...stkResponse.data,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "call-recharge-payment",
      level: "error",
      action: "recharge_error",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
