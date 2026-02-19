// _shared/payment.ts
// Payment provider abstraction.
// PAYMENT_ENV=mock  → simulates payment instantly (development)
// PAYMENT_ENV=production → calls real Selcom API (Tanzania)

import { getServiceClient } from "./supabase.ts";
import { logEvent } from "./logger.ts";

const PAYMENT_ENV = Deno.env.get("PAYMENT_ENV") ?? "mock";

// ── Selcom credentials (only needed in production) ────────────────
const SELCOM_API_URL    = Deno.env.get("SELCOM_API_URL") ?? "https://apigw.selcommobile.com/v1";
const SELCOM_API_KEY    = Deno.env.get("SELCOM_API_KEY") ?? "";
const SELCOM_API_SECRET = Deno.env.get("SELCOM_API_SECRET") ?? "";
const SELCOM_VENDOR_ID  = Deno.env.get("SELCOM_VENDOR_ID") ?? "";

export interface PaymentRequest {
  phone_number: string;   // 255XXXXXXXXX format (Tanzania)
  amount: number;         // TZS
  payment_type: string;
  reference: string;      // unique order reference
  description: string;
  callback_url: string;
}

export interface PaymentResponse {
  provider_reference: string;   // Selcom transaction ID or mock ID
  status: "pending" | "mock_success";
  message: string;
}

// ── Selcom HMAC-SHA256 signature ──────────────────────────────────
async function selcomSignature(
  timestamp: string,
  payload: string
): Promise<string> {
  const message = `${SELCOM_API_KEY}${timestamp}${payload}`;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(SELCOM_API_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign(
    "HMAC", key, new TextEncoder().encode(message)
  );
  return btoa(String.fromCharCode(...new Uint8Array(sig)));
}

// ── Mock payment ──────────────────────────────────────────────────
// Simulates a successful payment and fires the callback automatically.
async function mockPayment(
  req: PaymentRequest,
  paymentId: string
): Promise<PaymentResponse> {
  const mockRef = `MOCK-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;

  // Auto-trigger callback after 3 seconds (simulates Selcom calling back)
  setTimeout(async () => {
    try {
      const supabase = getServiceClient();
      const callbackUrl = req.callback_url;

      // Simulate a successful Selcom callback payload
      await fetch(callbackUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          transid: mockRef,
          reference: req.reference,
          resultcode: "000",
          resultdesc: "Success",
          msisdn: req.phone_number,
          amount: req.amount.toString(),
          paymentid: paymentId,
          channel: "MOCK",
        }),
      });

      await logEvent({
        function_name: "mock-payment",
        level: "info",
        action: "mock_callback_fired",
        metadata: { mockRef, paymentId, amount: req.amount },
      });
    } catch (err) {
      console.error("Mock callback failed:", err);
    }
  }, 3000);

  return {
    provider_reference: mockRef,
    status: "mock_success",
    message: "MOCK: Payment will auto-confirm in 3 seconds",
  };
}

// ── Real Selcom payment ───────────────────────────────────────────
async function selcomPayment(req: PaymentRequest): Promise<PaymentResponse> {
  const timestamp = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);

  const payload = JSON.stringify({
    msisdn: req.phone_number,
    vendor: SELCOM_VENDOR_ID,
    pin: "",
    amount: req.amount,
    transid: req.reference,
    channel: "Wallet",
    type: "pull-ussd-push",
    date_time: timestamp,
    callback_url: req.callback_url,
    brand_name: "eSIRI",
    memo: req.description,
  });

  const signature = await selcomSignature(timestamp, payload);

  const res = await fetch(`${SELCOM_API_URL}/checkout/create-order-minimal`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `SELCOM ${SELCOM_API_KEY}:${signature}`,
      "Timestamp": timestamp,
      "Cache-Control": "no-cache",
    },
    body: payload,
  });

  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Selcom API error: ${err}`);
  }

  const data = await res.json();

  if (data.resultcode !== "000") {
    throw new Error(`Selcom rejected payment: ${data.resultdesc}`);
  }

  return {
    provider_reference: data.transid ?? req.reference,
    status: "pending",
    message: "Payment request sent. Customer will receive a prompt.",
  };
}

// ── Public function — auto-selects provider ───────────────────────
export async function initiatePayment(
  req: PaymentRequest,
  paymentId: string
): Promise<PaymentResponse> {
  if (PAYMENT_ENV === "mock") {
    console.log(`[MOCK PAYMENT] ${req.amount} TZS to ${req.phone_number}`);
    return await mockPayment(req, paymentId);
  }
  return await selcomPayment(req);
}

export function isMockMode(): boolean {
  return PAYMENT_ENV === "mock";
}
