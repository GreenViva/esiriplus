// functions/send-doctor-otp/index.ts
// Sends a 6-digit OTP to the doctor's email for registration verification.
// POST: { email }

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { sendOtpEmail } from "../_shared/resend.ts";

function generateOtp(): string {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const body = await req.json();
    const email = (body.email ?? "").trim().toLowerCase();

    if (!email) {
      throw new ValidationError("email is required");
    }

    const emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    if (!emailRegex.test(email)) {
      throw new ValidationError("Invalid email format");
    }

    // Rate limit: 10 OTPs per hour per email, 20 per hour per IP
    await checkRateLimit(`otp2:${email}`, 10, 3600);
    await checkRateLimit(`otp2-ip:${clientIp}`, 20, 3600);

    const supabase = getServiceClient();

    // Note: duplicate email check is handled by register-doctor at creation time

    // Invalidate any previous unexpired OTPs for this email
    await supabase
      .from("email_verifications")
      .update({ expires_at: new Date().toISOString() })
      .eq("email", email)
      .is("verified_at", null)
      .gt("expires_at", new Date().toISOString());

    // Generate and store new OTP (10 min expiry)
    const otp = generateOtp();
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

    const { error: insertError } = await supabase
      .from("email_verifications")
      .insert({ email, otp_code: otp, expires_at: expiresAt });

    if (insertError) {
      console.error("Failed to insert OTP:", insertError);
      throw new Error("Failed to create verification");
    }

    // Send OTP via Resend (non-fatal if email fails — OTP is in DB)
    try {
      await sendOtpEmail(email, otp);
    } catch (emailErr) {
      console.error("Email send failed (OTP still saved in DB):", emailErr);
    }

    return successResponse({ sent: true }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
