// functions/mpesa-stk-push/index.ts
// Initiates an M-Pesa STK Push (Lipa Na M-Pesa Online).
// Rate limit: 10/min per user. Idempotency key required.
//
// PAYMENT_ENV modes:
//   "mock"       → Simulates STK push locally. No real API called.
//                  Auto-triggers callback after 3s to simulate user paying.
//                  Use this for development until real credentials are ready.
//   "sandbox"    → Calls Safaricom sandbox API (needs sandbox credentials).
//   "production" → Calls live Safaricom API (real money).
//
// When switching to real API: just change PAYMENT_ENV + add real credentials.
// Nothing else in this file needs to change.

import { handlePreflight, corsHeaders } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// ── Environment ──────────────────────────────────────────────────────────────
const CONSUMER_KEY    = Deno.env.get("MPESA_CONSUMER_KEY") ?? "";
const CONSUMER_SECRET = Deno.env.get("MPESA_CONSUMER_SECRET") ?? "";
const SHORTCODE       = Deno.env.get("MPESA_SHORTCODE") ?? "174379";
const PASSKEY         = Deno.env.get("MPESA_PASSKEY") ?? "";
const CALLBACK_URL    = Deno.env.get("MPESA_CALLBACK_URL")!;
const PAYMENT_ENV     = Deno.env.get("PAYMENT_ENV") ?? "mock"; // "mock" | "sandbox" | "production"

const BASE_URL = PAYMENT_ENV === "production"
  ? "https://api.safaricom.co.ke"
  : "https://sandbox.safaricom.co.ke";

// ── Mock Payment Simulator ────────────────────────────────────────────────────
// Simulates a successful STK push without calling any real API.
// Returns the same shape as a real M-Pesa response so nothing downstream breaks.
// Also fires the callback automatically after 3 seconds to simulate the user
// approving the payment on their phone.
async function mockStkPush(
  body: StkPushRequest,
  paymentId: string
): Promise<{ CheckoutRequestID: string; MerchantRequestID: string }> {
  const checkoutRequestId = `mock-checkout-${crypto.randomUUID()}`;
  const merchantRequestId = `mock-merchant-${crypto.randomUUID()}`;

  // Fire-and-forget: simulate callback after 3 seconds
  // This mimics the user seeing the prompt and approving on their phone
  setTimeout(async () => {
    try {
      const supabase = getServiceClient();
      const mockReceiptNumber = `MOCK${Date.now()}`;

      // Build the same payload shape Safaricom sends to mpesa-callback
      const mockCallbackPayload = {
        Body: {
          stkCallback: {
            MerchantRequestID: merchantRequestId,
            CheckoutRequestID: checkoutRequestId,
            ResultCode: 0,                         // 0 = success
            ResultDesc: "The service request is processed successfully.",
            CallbackMetadata: {
              Item: [
                { Name: "Amount",             Value: body.amount },
                { Name: "MpesaReceiptNumber", Value: mockReceiptNumber },
                { Name: "TransactionDate",    Value: Date.now() },
                { Name: "PhoneNumber",        Value: body.phone_number },
              ],
            },
          },
        },
      };

      // Call our own callback function to process the simulated payment
      await supabase.functions.invoke("mpesa-callback", {
        body: mockCallbackPayload,
      });
    } catch (err) {
      console.error("Mock callback failed:", err);
    }
  }, 3000);

  return { CheckoutRequestID: checkoutRequestId, MerchantRequestID: merchantRequestId };
}

// ── Types ─────────────────────────────────────────────────────────────────────
interface StkPushRequest {
  phone_number: string;   // 255XXXXXXXXX format
  amount: number;
  consultation_id?: string;
  payment_type: "service_access" | "call_recharge";
  service_type?: string;  // e.g. "nurse", "gp", "specialist" — passed through for service_access
  idempotency_key: string;
}

// ── Validation ────────────────────────────────────────────────────────────────
function validate(body: unknown): StkPushRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be a JSON object");
  }
  const b = body as Record<string, unknown>;

  // phone
  if (typeof b.phone_number !== "string" || !/^2556\d{8}$|^2557\d{8}$/.test(b.phone_number)) {
    throw new ValidationError("phone_number must be in format 255XXXXXXXXX");
  }

  // amount
  if (typeof b.amount !== "number" || b.amount < 1 || !Number.isInteger(b.amount)) {
    throw new ValidationError("amount must be a positive integer (TZS)");
  }

  // payment_type
  const validTypes = ["service_access", "call_recharge"];
  if (!validTypes.includes(b.payment_type as string)) {
    throw new ValidationError(`payment_type must be one of: ${validTypes.join(", ")}`);
  }

  // idempotency_key (required to prevent double-charges)
  if (typeof b.idempotency_key !== "string" || b.idempotency_key.length < 8) {
    throw new ValidationError("idempotency_key is required (min 8 chars)");
  }

  return b as unknown as StkPushRequest;
}

// ── M-Pesa helpers ────────────────────────────────────────────────────────────
async function getAccessToken(): Promise<string> {
  const credentials = btoa(`${CONSUMER_KEY}:${CONSUMER_SECRET}`);
  const res = await fetch(
    `${BASE_URL}/oauth/v1/generate?grant_type=client_credentials`,
    { headers: { Authorization: `Basic ${credentials}` } }
  );
  if (!res.ok) throw new Error("Failed to get M-Pesa access token");
  const data = await res.json();
  return data.access_token;
}

function generatePassword(): { password: string; timestamp: string } {
  const timestamp = new Date()
    .toISOString()
    .replace(/[-:T.Z]/g, "")
    .slice(0, 14);
  const raw = `${SHORTCODE}${PASSKEY}${timestamp}`;
  const password = btoa(raw);
  return { password, timestamp };
}

// ── Handler ───────────────────────────────────────────────────────────────────
Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // 1. Auth
    const auth = await validateAuth(req);

    // 2. Rate limit (payment tier: 10/min)
    const rateLimitKey = auth.userId ?? auth.sessionId ?? "unknown";
    await LIMITS.payment(rateLimitKey);

    // 3. Parse & validate body
    const rawBody = await req.json();
    const body = validate(rawBody);

    // 4. Idempotency — check if this key was already used
    const supabase = getServiceClient();
    const { data: existingPayment } = await supabase
      .from("payments")
      .select("payment_id, status, mpesa_checkout_request_id")
      .eq("idempotency_key", body.idempotency_key)
      .single();

    if (existingPayment) {
      // Return the existing payment result (idempotent response)
      return successResponse({
        message: "Duplicate request — returning existing payment",
        payment_id: existingPayment.payment_id,
        checkout_request_id: existingPayment.mpesa_checkout_request_id,
        status: existingPayment.status,
      }, 200, origin);
    }

    // 5. Initiate payment — mock or real API depending on PAYMENT_ENV
    // ─────────────────────────────────────────────────────────────
    // To switch to real M-Pesa: set PAYMENT_ENV=sandbox or PAYMENT_ENV=production
    // and add MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET, MPESA_SHORTCODE, MPESA_PASSKEY
    // Nothing else changes.
    // ─────────────────────────────────────────────────────────────
    let stkData: { CheckoutRequestID: string; MerchantRequestID: string };

    // First insert payment record so mock callback can find it by checkout ID
    const { data: payment, error: dbError } = await supabase
      .from("payments")
      .insert({
        patient_session_id: auth.sessionId,
        amount: body.amount,
        currency: "TZS",
        payment_type: body.payment_type,
        service_type: body.service_type ?? null,
        status: "pending",
        phone_number: body.phone_number,
        consultation_id: body.consultation_id ?? null,
        idempotency_key: body.idempotency_key,
        payment_env: PAYMENT_ENV,
      })
      .select("payment_id")
      .single();

    if (dbError) throw dbError;

    if (PAYMENT_ENV === "mock") {
      // ── MOCK MODE ──────────────────────────────────────────────
      stkData = await mockStkPush(body, payment.payment_id);

    } else {
      // ── REAL API MODE (sandbox or production) ──────────────────
      const accessToken = await getAccessToken();
      const { password, timestamp } = generatePassword();

      const stkRes = await fetch(`${BASE_URL}/mpesa/stkpush/v1/processrequest`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          BusinessShortCode: SHORTCODE,
          Password: password,
          Timestamp: timestamp,
          TransactionType: "CustomerPayBillOnline",
          Amount: body.amount,
          PartyA: body.phone_number,
          PartyB: SHORTCODE,
          PhoneNumber: body.phone_number,
          CallBackURL: CALLBACK_URL,
          AccountReference: `eSIRI-${body.payment_type}`,
          TransactionDesc: `eSIRI ${body.payment_type.replace("_", " ")} payment`,
        }),
      });

      const raw = await stkRes.json();
      if (!stkRes.ok || raw.ResponseCode !== "0") {
        throw new Error(`STK Push failed: ${raw.ResponseDescription ?? "Unknown error"}`);
      }
      stkData = raw;
    }

    // 6. Update payment record with checkout IDs from API response
    await supabase
      .from("payments")
      .update({
        mpesa_checkout_request_id: stkData.CheckoutRequestID,
        mpesa_merchant_request_id: stkData.MerchantRequestID,
      })
      .eq("payment_id", payment.payment_id);

    // 7. Audit log
    await logEvent({
      function_name: "mpesa-stk-push",
      level: "info",
      session_id: auth.sessionId,
      action: "stk_push_initiated",
      metadata: {
        payment_id: payment.payment_id,
        amount: body.amount,
        payment_type: body.payment_type,
        checkout_request_id: stkData.CheckoutRequestID,
        payment_env: PAYMENT_ENV,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: PAYMENT_ENV === "mock"
        ? "Mock payment initiated. Will auto-complete in 3 seconds."
        : "STK Push sent. Please check your phone.",
      payment_id: payment.payment_id,
      checkout_request_id: stkData.CheckoutRequestID,
      payment_env: PAYMENT_ENV,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "mpesa-stk-push",
      level: "error",
      action: "stk_push_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address: getClientIp(req),
    });
    return errorResponse(err, origin);
  }
});
