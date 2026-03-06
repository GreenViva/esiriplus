// functions/verify-doctor-otp/index.ts
// Verifies a 6-digit OTP sent to the doctor's email.
// POST: { email, otp_code }

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    // Rate limit per IP
    await checkRateLimit(`verify-otp:${clientIp}`, 20, 3600);

    const body = await req.json();
    const email = (body.email ?? "").trim().toLowerCase();
    const otpCode = (body.otp_code ?? "").trim();

    if (!email) {
      throw new ValidationError("email is required");
    }
    if (!otpCode || !/^\d{6}$/.test(otpCode)) {
      throw new ValidationError("otp_code must be a 6-digit number");
    }

    const supabase = getServiceClient();

    // Get the latest unexpired, unverified OTP for this email
    const { data: verification, error: fetchError } = await supabase
      .from("email_verifications")
      .select("*")
      .eq("email", email)
      .is("verified_at", null)
      .gt("expires_at", new Date().toISOString())
      .order("created_at", { ascending: false })
      .limit(1)
      .single();

    if (fetchError || !verification) {
      throw new ValidationError("No pending verification found. Please request a new code.");
    }

    // Check max attempts
    if (verification.attempts >= 5) {
      throw new ValidationError("Too many attempts. Please request a new code.");
    }

    // Check OTP match
    if (verification.otp_code !== otpCode) {
      // Increment attempts
      await supabase
        .from("email_verifications")
        .update({ attempts: verification.attempts + 1 })
        .eq("id", verification.id);

      const remaining = 4 - verification.attempts;
      throw new ValidationError(
        `Incorrect code. ${remaining > 0 ? `${remaining} attempt${remaining === 1 ? "" : "s"} remaining.` : "Please request a new code."}`
      );
    }

    // OTP is correct — mark as verified
    await supabase
      .from("email_verifications")
      .update({ verified_at: new Date().toISOString() })
      .eq("id", verification.id);

    return successResponse({ verified: true }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
