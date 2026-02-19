// functions/service-access-payment/index.ts
// Validates service tier and initiates payment for service access.
// All 6 service types validated. Rate limit: 10/min.

import { handlePreflight, corsHeaders } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// All valid service types with their prices in KES
const SERVICE_TIERS: Record<string, number> = {
  nurse:            500,
  clinical_officer: 800,
  pharmacist:       600,
  gp:              1200,
  specialist:      2500,
  psychologist:    1500,
};

interface ServicePaymentRequest {
  service_type: string;
  phone_number: string;
  idempotency_key: string;
}

function validate(body: unknown): ServicePaymentRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be a JSON object");
  }
  const b = body as Record<string, unknown>;

  if (typeof b.service_type !== "string" || !SERVICE_TIERS[b.service_type]) {
    throw new ValidationError(
      `service_type must be one of: ${Object.keys(SERVICE_TIERS).join(", ")}`
    );
  }

  if (typeof b.phone_number !== "string" || !/^2547\d{8}$|^2541\d{8}$/.test(b.phone_number)) {
    throw new ValidationError("phone_number must be in format 254XXXXXXXXX");
  }

  if (typeof b.idempotency_key !== "string" || b.idempotency_key.length < 8) {
    throw new ValidationError("idempotency_key is required (min 8 chars)");
  }

  return b as unknown as ServicePaymentRequest;
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

    const amount = SERVICE_TIERS[body.service_type];
    const supabase = getServiceClient();

    // Check session still valid & not already active for this service
    const { data: session } = await supabase
      .from("patient_sessions")
      .select("session_id, is_active")
      .eq("session_id", auth.sessionId)
      .single();

    if (!session?.is_active) {
      throw new ValidationError("Patient session is not active");
    }

    // Check for existing active access for this service type
    const { data: existingAccess } = await supabase
      .from("service_access_payments")
      .select("id, expires_at")
      .eq("patient_session_id", auth.sessionId)
      .eq("service_type", body.service_type)
      .eq("access_granted", true)
      .gt("expires_at", new Date().toISOString())
      .single();

    if (existingAccess) {
      return successResponse({
        message: "Active access already exists for this service",
        expires_at: existingAccess.expires_at,
        service_type: body.service_type,
      }, 200, origin);
    }

    // Delegate to STK push with service-specific payload
    const stkResponse = await supabase.functions.invoke("mpesa-stk-push", {
      body: {
        phone_number: body.phone_number,
        amount,
        payment_type: "service_access",
        service_type: body.service_type,
        idempotency_key: body.idempotency_key,
      },
      headers: { Authorization: req.headers.get("Authorization")! },
    });

    if (stkResponse.error) throw stkResponse.error;

    await logEvent({
      function_name: "service-access-payment",
      level: "info",
      session_id: auth.sessionId,
      action: "service_payment_initiated",
      metadata: {
        service_type: body.service_type,
        amount,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: `Payment of KES ${amount} initiated for ${body.service_type}`,
      service_type: body.service_type,
      amount,
      ...stkResponse.data,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "service-access-payment",
      level: "error",
      action: "service_payment_error",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
