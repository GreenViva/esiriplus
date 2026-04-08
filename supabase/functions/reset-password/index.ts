// functions/reset-password/index.ts
// OTP-based password reset for doctors. Three actions:
//   action: "send_otp"      — sends 6-digit OTP to doctor's email
//   action: "verify_otp"    — verifies the OTP code
//   action: "set_password"  — sets the new password (requires verified OTP)
//
// Rate limited: 5/min per IP for send, 20/min for verify/set.

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
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
    const action = body?.action;
    const email = (body?.email ?? "").trim().toLowerCase();

    if (!email || !email.includes("@")) {
      throw new ValidationError("A valid email address is required");
    }

    const supabase = getServiceClient();

    // ── SEND OTP ──────────────────────────────────────────────────────────────
    if (action === "send_otp") {
      await checkRateLimit(`reset-otp:${clientIp}`, 10, 60);
      await checkRateLimit(`reset-otp:${email}`, 5, 300);

      // Verify email belongs to a doctor
      const { data: doctor } = await supabase
        .from("doctor_profiles")
        .select("doctor_id")
        .eq("email", email)
        .maybeSingle();

      if (!doctor) {
        // Don't reveal whether the email exists
        return successResponse({ sent: true }, 200, origin);
      }

      // Invalidate previous OTPs
      await supabase
        .from("email_verifications")
        .update({ expires_at: new Date().toISOString() })
        .eq("email", email)
        .is("verified_at", null)
        .gt("expires_at", new Date().toISOString());

      // Generate and store OTP (10 min expiry)
      const otp = generateOtp();
      const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

      await supabase
        .from("email_verifications")
        .insert({ email, otp_code: otp, expires_at: expiresAt });

      try {
        await sendOtpEmail(email, otp);
      } catch (e) {
        console.error("[reset-password] Email send failed:", e);
      }

      await logEvent({
        function_name: "reset-password",
        level: "info",
        action: "reset_otp_sent",
        metadata: { email },
        ip_address: clientIp,
      });

      return successResponse({ sent: true }, 200, origin);
    }

    // ── VERIFY OTP ────────────────────────────────────────────────────────────
    if (action === "verify_otp") {
      await checkRateLimit(`reset-verify:${clientIp}`, 20, 3600);

      const otpCode = (body?.otp_code ?? "").trim();
      if (!otpCode || !/^\d{6}$/.test(otpCode)) {
        throw new ValidationError("OTP must be a 6-digit number");
      }

      const { data: verification } = await supabase
        .from("email_verifications")
        .select("*")
        .eq("email", email)
        .is("verified_at", null)
        .gt("expires_at", new Date().toISOString())
        .order("created_at", { ascending: false })
        .limit(1)
        .single();

      if (!verification) {
        throw new ValidationError("No pending verification found. Please request a new code.");
      }

      if (verification.attempts >= 5) {
        throw new ValidationError("Too many attempts. Please request a new code.");
      }

      if (verification.otp_code !== otpCode) {
        await supabase
          .from("email_verifications")
          .update({ attempts: verification.attempts + 1 })
          .eq("id", verification.id);

        const remaining = 4 - verification.attempts;
        throw new ValidationError(
          `Incorrect code. ${remaining > 0 ? `${remaining} attempt${remaining === 1 ? "" : "s"} remaining.` : "Please request a new code."}`
        );
      }

      // Mark as verified
      await supabase
        .from("email_verifications")
        .update({ verified_at: new Date().toISOString() })
        .eq("id", verification.id);

      return successResponse({ verified: true }, 200, origin);
    }

    // ── SET NEW PASSWORD ──────────────────────────────────────────────────────
    if (action === "set_password") {
      await checkRateLimit(`reset-set:${clientIp}`, 10, 3600);

      const newPassword = body?.new_password;
      if (!newPassword || typeof newPassword !== "string" || newPassword.length < 6) {
        throw new ValidationError("Password must be at least 6 characters");
      }

      // Verify there's a recently verified OTP for this email (within 15 minutes)
      const fifteenMinAgo = new Date(Date.now() - 15 * 60 * 1000).toISOString();
      const { data: verification } = await supabase
        .from("email_verifications")
        .select("id, verified_at")
        .eq("email", email)
        .not("verified_at", "is", null)
        .gt("verified_at", fifteenMinAgo)
        .order("verified_at", { ascending: false })
        .limit(1)
        .single();

      if (!verification) {
        throw new ValidationError("OTP verification expired. Please start over.");
      }

      // Find the doctor's auth user
      const { data: doctor } = await supabase
        .from("doctor_profiles")
        .select("doctor_id")
        .eq("email", email)
        .single();

      if (!doctor) {
        throw new ValidationError("Doctor account not found");
      }

      // Update password via Postgres RPC (SECURITY DEFINER — bypasses RLS)
      const { error: updateErr } = await supabase.rpc("reset_doctor_password", {
        p_doctor_id: doctor.doctor_id,
        p_new_password: newPassword,
      });

      if (updateErr) {
        console.error("[reset-password] Password update failed:", updateErr.message);
        throw new Error("Failed to update password");
      }

      // Invalidate the used verification
      await supabase
        .from("email_verifications")
        .update({ expires_at: new Date().toISOString() })
        .eq("id", verification.id);

      await logEvent({
        function_name: "reset-password",
        level: "info",
        action: "password_reset_completed",
        metadata: { email, doctor_id: doctor.doctor_id },
        ip_address: clientIp,
      });

      return successResponse({ reset: true }, 200, origin);
    }

    throw new ValidationError("action must be one of: send_otp, verify_otp, set_password");
  } catch (err) {
    return errorResponse(err, origin);
  }
});
