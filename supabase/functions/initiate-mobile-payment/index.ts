// functions/initiate-mobile-payment/index.ts
//
// "Pay by Mobile Number" — provider-driven push flow.
//
// Flow:
//   1. Client submits phone + amount + payment_type (+ optional service_type /
//      consultation_id) + idempotency_key.
//   2. We insert a `payments` row with status='pending',
//      payment_method='mobile_number'.
//   3. [TODO PROVIDER] We call the mobile-money provider's wallet-push API
//      (Selcom / Azampay / M-Pesa / Yas / Halotel / etc.). The provider
//      pushes its own prompt to the user's device showing the exact amount.
//      The user confirms by entering their wallet PIN on the provider's own
//      secure UI — we never see the PIN, never store it, never route it.
//   4. Provider callback (or polling) transitions the row to
//      'completed' / 'failed' later — same shape as `mpesa-callback`.
//   5. Client polls the existing payments status endpoint (identical to STK).
//
// Rate limit: reuses the payment tier (10/min/user).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// ── Environment ─────────────────────────────────────────────────────────────
const PAYMENT_ENV = Deno.env.get("PAYMENT_ENV") ?? "mock"; // "mock" | "sandbox" | "production"

// ── Types ───────────────────────────────────────────────────────────────────
interface InitiateMobilePaymentRequest {
  phone_number: string;              // 255XXXXXXXXX
  amount: number;                    // TZS
  payment_type: "service_access" | "call_recharge";
  service_type?: string;             // e.g. "nurse" | "gp" | "specialist"
  consultation_id?: string;
  idempotency_key: string;
}

// ── Validation ──────────────────────────────────────────────────────────────
function validate(body: unknown): InitiateMobilePaymentRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be a JSON object");
  }
  const b = body as Record<string, unknown>;

  if (typeof b.phone_number !== "string" || !/^2556\d{8}$|^2557\d{8}$/.test(b.phone_number)) {
    throw new ValidationError("phone_number must be in format 255XXXXXXXXX");
  }
  if (typeof b.amount !== "number" || b.amount < 3000 || !Number.isInteger(b.amount)) {
    throw new ValidationError("amount must be an integer ≥ 3,000 TZS");
  }
  const validTypes = ["service_access", "call_recharge"];
  if (!validTypes.includes(b.payment_type as string)) {
    throw new ValidationError(`payment_type must be one of: ${validTypes.join(", ")}`);
  }
  if (typeof b.idempotency_key !== "string" || b.idempotency_key.length < 8) {
    throw new ValidationError("idempotency_key is required (min 8 chars)");
  }

  return b as unknown as InitiateMobilePaymentRequest;
}

// ── Mock provider ───────────────────────────────────────────────────────────
// Simulates a provider that pushes a wallet prompt and auto-confirms after 5s.
// Uses `mpesa-callback` to settle the payment so the post-processing pipeline
// (service_access_payments / call_recharge_payments rows + success push)
// runs identically to the STK path.
function scheduleMockProviderCallback(paymentId: string, amount: number, phoneNumber: string): void {
  setTimeout(async () => {
    try {
      const supabase = getServiceClient();
      // We don't have a checkout ID for this flow; stamp the row so the
      // callback can find it. Mirrors how mpesa-stk-push uses mpesa_checkout_request_id.
      const mockCheckoutId = `mock-mobile-${paymentId}`;
      await supabase
        .from("payments")
        .update({ mpesa_checkout_request_id: mockCheckoutId })
        .eq("payment_id", paymentId);

      await supabase.functions.invoke("mpesa-callback", {
        body: {
          Body: {
            stkCallback: {
              MerchantRequestID: `mock-mobile-merch-${paymentId}`,
              CheckoutRequestID: mockCheckoutId,
              ResultCode: 0,
              ResultDesc: "Mock mobile-number payment approved",
              CallbackMetadata: {
                Item: [
                  { Name: "Amount",             Value: amount },
                  { Name: "MpesaReceiptNumber", Value: `MOCKMN${Date.now()}` },
                  { Name: "TransactionDate",    Value: Date.now() },
                  { Name: "PhoneNumber",        Value: phoneNumber },
                ],
              },
            },
          },
        },
      });
    } catch (err) {
      console.error("Mock provider callback failed:", err);
    }
  }, 5000);
}

// ── Handler ─────────────────────────────────────────────────────────────────
Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const rateKey = auth.userId ?? auth.sessionId ?? "unknown";
    await LIMITS.payment(rateKey);

    const rawBody = await req.json();
    const body = validate(rawBody);

    const supabase = getServiceClient();

    // Idempotency — reuse existing row if this key was seen before.
    const { data: existing } = await supabase
      .from("payments")
      .select("payment_id, status")
      .eq("idempotency_key", body.idempotency_key)
      .maybeSingle();

    if (existing) {
      return successResponse({
        message: "Duplicate request — returning existing payment",
        payment_id: existing.payment_id,
        status: existing.status,
      }, 200, origin);
    }

    const { data: payment, error: insertErr } = await supabase
      .from("payments")
      .insert({
        patient_session_id: auth.sessionId,
        amount: body.amount,
        currency: "TZS",
        payment_type: body.payment_type,
        service_type: body.service_type ?? null,
        consultation_id: body.consultation_id ?? null,
        phone_number: body.phone_number,
        idempotency_key: body.idempotency_key,
        payment_method: "mobile_number",
        status: "pending",
        payment_env: PAYMENT_ENV,
      })
      .select("payment_id")
      .single();

    if (insertErr || !payment) throw insertErr ?? new Error("Failed to insert payment");

    // ────────────────────────────────────────────────────────────────────────
    // TODO PROVIDER — wallet-push integration.
    //
    // Wire in the real money-mover here. The provider call should:
    //   1. Submit { msisdn: body.phone_number, amount: body.amount, reference:
    //      payment.payment_id } to the provider API.
    //   2. The provider pushes its own secure prompt to the user's device
    //      (wallet app / USSD / SIM toolkit) with the amount.
    //   3. User enters their wallet PIN on the provider's UI (we never see it).
    //   4. Provider calls our webhook (or we poll) with the settlement result.
    //      Reuse the `mpesa-callback` post-processing so rows in
    //      service_access_payments / call_recharge_payments are created
    //      identically and the success push fires through the same code path.
    //
    // Candidate providers (rough order of fit for Tanzania):
    //   • Selcom wallet-push — HMAC plumbing already lives in _shared/payment.ts
    //   • Azampay aggregator — covers M-Pesa/Airtel/Halo/Yas in one API
    //   • Safaricom M-Pesa B2C (same credentials as STK)
    //
    // Recommended shape once wired:
    //   _shared/payment.ts::pushWalletPrompt(paymentId, phone, amount, env)
    //   auto-selects mock / sandbox / production off PAYMENT_ENV.
    // ────────────────────────────────────────────────────────────────────────
    if (PAYMENT_ENV === "mock") {
      scheduleMockProviderCallback(payment.payment_id, body.amount, body.phone_number);
    } else {
      // Real provider call lands here — see TODO above.
      // Until wired, fail loud in non-mock envs so we don't ship a silent no-op.
      throw new Error(
        "Mobile-number payments are not yet wired to a real provider. " +
        "Set PAYMENT_ENV=mock for local testing.",
      );
    }

    await logEvent({
      function_name: "initiate-mobile-payment",
      level: "info",
      session_id: auth.sessionId,
      action: "mobile_payment_initiated",
      metadata: {
        payment_id: payment.payment_id,
        amount: body.amount,
        payment_type: body.payment_type,
        payment_env: PAYMENT_ENV,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: PAYMENT_ENV === "mock"
        ? "Mock wallet push sent. Will auto-complete in 5 seconds."
        : "Wallet prompt sent. Confirm on your device to complete payment.",
      payment_id: payment.payment_id,
      payment_env: PAYMENT_ENV,
      status: "pending",
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "initiate-mobile-payment",
      level: "error",
      action: "mobile_payment_initiate_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address: getClientIp(req),
    });
    return errorResponse(err, origin);
  }
});
