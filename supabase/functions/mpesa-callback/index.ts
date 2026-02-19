// functions/mpesa-callback/index.ts
// Receives payment confirmation from Safaricom servers (or mock simulator).
// No auth required (called by Safaricom or internally by mock).
// IMPORTANT: IP allowlist is only enforced when PAYMENT_ENV=production.
//
// In mock mode: called internally by mpesa-stk-push after 3s delay.
// In sandbox/production: called by Safaricom servers after user approves payment.
// The payload shape is identical in all modes — nothing here needs to change
// when you switch from mock → sandbox → production.

import { successResponse, errorResponse } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const PAYMENT_ENV = Deno.env.get("PAYMENT_ENV") ?? "mock";

// Known Safaricom callback IP ranges — only enforced in production
const SAFARICOM_IPS = [
  "196.201.214.200", "196.201.214.206", "196.201.213.114",
  "196.201.214.207", "196.201.214.208", "196.201.213.44",
  "196.201.212.127", "196.201.212.138", "196.201.212.129",
  "196.201.212.136", "196.201.212.74",  "196.201.212.69",
];

Deno.serve(async (req: Request) => {
  try {
    // Only accept POST from Safaricom
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    // IP allowlist — only enforced in production (not mock or sandbox)
    const clientIp = getClientIp(req);
    if (
      PAYMENT_ENV === "production" &&
      clientIp &&
      !SAFARICOM_IPS.includes(clientIp)
    ) {
      await logEvent({
        function_name: "mpesa-callback",
        level: "warn",
        action: "blocked_unknown_ip",
        ip_address: clientIp,
      });
      return new Response("Forbidden", { status: 403 });
    }

    const body = await req.json();
    const stk = body?.Body?.stkCallback;
    if (!stk) throw new Error("Invalid callback payload structure");

    const checkoutRequestId: string = stk.CheckoutRequestID;
    const resultCode: number = stk.ResultCode;
    const resultDesc: string = stk.ResultDesc;

    const supabase = getServiceClient();

    // Fetch the pending payment by checkout request ID
    const { data: payment, error: fetchErr } = await supabase
      .from("payments")
      .select("payment_id, payment_type, patient_session_id, consultation_id, amount")
      .eq("mpesa_checkout_request_id", checkoutRequestId)
      .single();

    if (fetchErr || !payment) {
      await logEvent({
        function_name: "mpesa-callback",
        level: "warn",
        action: "payment_not_found",
        metadata: { checkoutRequestId },
      });
      // Return 200 so Safaricom doesn't retry
      return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (resultCode === 0) {
      // Payment successful — extract M-Pesa receipt
      const items: { Name: string; Value: unknown }[] =
        stk.CallbackMetadata?.Item ?? [];
      const get = (name: string) =>
        items.find((i) => i.Name === name)?.Value;

      const mpesaReceiptNumber = get("MpesaReceiptNumber") as string;
      const transactionDate    = get("TransactionDate") as string;
      const phoneNumber        = get("PhoneNumber") as string;

      // Update payment to completed
      await supabase
        .from("payments")
        .update({
          status: "completed",
          mpesa_receipt_number: mpesaReceiptNumber,
          mpesa_transaction_date: transactionDate,
          paid_phone_number: String(phoneNumber),
          completed_at: new Date().toISOString(),
        })
        .eq("payment_id", payment.payment_id);

      // Trigger downstream logic based on payment type
      if (payment.payment_type === "service_access") {
        await supabase.from("service_access_payments").insert({
          payment_id: payment.payment_id,
          patient_session_id: payment.patient_session_id,
          access_granted: true,
          granted_at: new Date().toISOString(),
        });
      } else if (payment.payment_type === "call_recharge") {
        // Add minutes based on amount paid (e.g. 1 KES = 1 minute)
        const minutes = Math.floor(payment.amount);
        await supabase.from("call_recharge_payments").insert({
          payment_id: payment.payment_id,
          patient_session_id: payment.patient_session_id,
          minutes_added: minutes,
          recharged_at: new Date().toISOString(),
        });
        // Update consultation remaining minutes
        if (payment.consultation_id) {
          await supabase.rpc("add_call_minutes", {
            p_consultation_id: payment.consultation_id,
            p_minutes: minutes,
          });
        }
      }

      // Send push notification to patient
      await supabase.functions.invoke("send-push-notification", {
        body: {
          session_id: payment.patient_session_id,
          title: "Payment Confirmed ✓",
          body: `Your payment of KES ${payment.amount} was successful.`,
          type: "payment_success",
        },
      });

      await logEvent({
        function_name: "mpesa-callback",
        level: "info",
        action: "payment_completed",
        session_id: payment.patient_session_id,
        metadata: {
          payment_id: payment.payment_id,
          receipt: mpesaReceiptNumber,
          amount: payment.amount,
        },
      });

    } else {
      // Payment failed
      await supabase
        .from("payments")
        .update({
          status: "failed",
          failure_reason: resultDesc,
          failed_at: new Date().toISOString(),
        })
        .eq("payment_id", payment.payment_id);

      await logEvent({
        function_name: "mpesa-callback",
        level: "warn",
        action: "payment_failed",
        session_id: payment.patient_session_id,
        metadata: {
          payment_id: payment.payment_id,
          result_code: resultCode,
          result_desc: resultDesc,
        },
      });
    }

    // Always return 200 to Safaricom
    return new Response(
      JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );

  } catch (err) {
    await logEvent({
      function_name: "mpesa-callback",
      level: "error",
      action: "callback_error",
      error_message: err instanceof Error ? err.message : String(err),
    });
    // Still return 200 so Safaricom doesn't flood with retries
    return new Response(
      JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  }
});
