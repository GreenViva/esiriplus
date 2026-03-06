// functions/register-doctor/index.ts
// Creates a Supabase Auth account for a doctor and inserts their profile.
//
// Expects JSON body:
//   { email, password, full_name, country_code, phone, specialty, country,
//     languages, license_number, years_experience, bio, services,
//     specialist_field?, profile_photo_url?, license_document_url?, certificates_url? }
//
// Returns SessionResponse: { access_token, refresh_token, expires_at, user }

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// Map display names (from the app) → Postgres service_type_enum values (lowercase)
const SPECIALTY_TO_ENUM: Record<string, string> = {
  "Nurse": "nurse",
  "Clinical Officer": "clinical_officer",
  "Pharmacist": "pharmacist",
  "General Practitioner": "gp",
  "Specialist": "specialist",
  "Psychologist": "psychologist",
  "Herbalist": "herbalist",
};

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    await checkRateLimit(`register-doctor:${clientIp}`, 10, 60);

    const body = await req.json();

    // Validate required fields
    const required = [
      "email",
      "password",
      "full_name",
      "phone",
      "specialty",
      "license_number",
    ];
    const missing = required.filter((f) => !body[f]);
    if (missing.length > 0) {
      throw new ValidationError(`Missing required fields: ${missing.join(", ")}`);
    }

    // Email format
    const emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    if (!emailRegex.test(body.email.trim())) {
      throw new ValidationError("Invalid email address");
    }

    // Verify email domain has MX records (proves the domain can receive email)
    const emailDomain = body.email.trim().split("@")[1];
    try {
      const mxRecords = await Deno.resolveDns(emailDomain, "MX");
      if (!mxRecords || mxRecords.length === 0) {
        throw new ValidationError("This email domain cannot receive emails. Please use a valid email address.");
      }
    } catch (err) {
      if (err instanceof ValidationError) throw err;
      // DNS lookup failed — domain doesn't exist
      throw new ValidationError("This email domain does not exist. Please use a valid email address.");
    }

    // Password complexity
    if (body.password.length < 8) {
      throw new ValidationError("Password must be at least 8 characters");
    }
    if (!/[A-Z]/.test(body.password)) {
      throw new ValidationError("Password must contain at least one uppercase letter");
    }
    if (!/[0-9]/.test(body.password)) {
      throw new ValidationError("Password must contain at least one digit");
    }

    // Phone: digits only, 7-15 chars
    const phoneDigits = (body.phone ?? "").replace(/\D/g, "");
    if (phoneDigits.length < 7 || phoneDigits.length > 15) {
      throw new ValidationError("Phone number must be 7-15 digits");
    }

    // Full name length
    const fullName = (body.full_name ?? "").trim();
    if (fullName.length < 2 || fullName.length > 100) {
      throw new ValidationError("Full name must be 2-100 characters");
    }

    // Years experience bounds
    const yearsExp = Number(body.years_experience ?? 0);
    if (!Number.isInteger(yearsExp) || yearsExp < 0 || yearsExp > 70) {
      throw new ValidationError("Years of experience must be 0-70");
    }

    // Sanitize inputs
    body.email = body.email.trim().toLowerCase();
    body.full_name = fullName;
    body.phone = phoneDigits;
    body.years_experience = yearsExp;

    // Map specialty display name to Postgres enum value
    const specialtyEnum = SPECIALTY_TO_ENUM[body.specialty] ?? body.specialty.toLowerCase().replace(/\s+/g, "_");
    const supabase = getServiceClient();

    // OTP verification disabled until DNS is verified for Resend
    // TODO: Re-enable once esiri.africa DNS records are set up
    // const { data: otpVerification } = await supabase
    //   .from("email_verifications")
    //   .select("id, verified_at, created_at")
    //   .eq("email", body.email)
    //   .not("verified_at", "is", null)
    //   .order("created_at", { ascending: false })
    //   .limit(1)
    //   .single();
    //
    // if (!otpVerification) {
    //   throw new ValidationError("Email not verified. Please complete OTP verification first.");
    // }

    // 1. Create Supabase Auth user — store extra fields in user_metadata
    let authData;
    try {
      const result = await supabase.auth.admin.createUser({
        email: body.email,
        password: body.password,
        email_confirm: true,
        user_metadata: {
          role: "DOCTOR",
          full_name: body.full_name,
          country_code: body.country_code ?? "+255",
          country: body.country ?? "",
          services: body.services ?? [],
          license_document_url: body.license_document_url ?? null,
          certificates_url: body.certificates_url ?? null,
        },
      });

      if (result.error) {
        console.error("Step 1 - Auth user creation error:", JSON.stringify(result.error));
        if (
          result.error.message?.includes("already") ||
          result.error.message?.includes("duplicate")
        ) {
          throw new ValidationError("An account with this email already exists");
        }
        throw new ValidationError(
          "Failed to create account. Please try again later."
        );
      }
      authData = result.data;
    } catch (e) {
      if (e instanceof ValidationError) throw e;
      console.error("Step 1 - createUser threw:", e?.message ?? e);
      throw new ValidationError(
        "Failed to create account. Please try again later."
      );
    }

    const userId = authData.user.id;

    // 2. Insert doctor profile row (only columns that exist in Postgres table)
    try {
      const { error: profileError } = await supabase
        .from("doctor_profiles")
        .insert({
          doctor_id: userId,
          full_name: body.full_name,
          email: body.email,
          phone: body.phone,
          specialty: specialtyEnum,
          languages: body.languages ?? [],
          license_number: body.license_number,
          years_experience: body.years_experience ?? 0,
          bio: body.bio ?? "",
          specialist_field: body.specialist_field ?? null,
          profile_photo_url: body.profile_photo_url ?? null,
          license_document_url: body.license_document_url ?? null,
          certificates_url: body.certificates_url ?? null,
          country: body.country ?? null,
          country_code: body.country_code ?? "+255",
          services: body.services ? JSON.stringify(body.services) : null,
          is_verified: false,
          is_available: false,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        });

      if (profileError) {
        console.error("Step 2 - Profile insert error:", JSON.stringify(profileError));
        await supabase.auth.admin.deleteUser(userId);
        throw new ValidationError(
          "Failed to create doctor profile. Please check your details and try again."
        );
      }
    } catch (e) {
      if (e instanceof ValidationError) throw e;
      console.error("Step 2 - insert threw:", e?.message ?? e);
      await supabase.auth.admin.deleteUser(userId).catch(() => {});
      throw new ValidationError(
        "Failed to create doctor profile. Please try again later."
      );
    }
    // 3. Sign in to get a real session (access + refresh tokens)
    let session;
    try {
      const { data: signInData, error: signInError } =
        await supabase.auth.signInWithPassword({
          email: body.email,
          password: body.password,
        });

      if (signInError || !signInData.session) {
        console.error("Step 3 - Sign-in error:", JSON.stringify(signInError));
        throw new ValidationError(
          "Account created but auto-login failed. Please sign in manually."
        );
      }
      session = signInData.session;
    } catch (e) {
      if (e instanceof ValidationError) throw e;
      console.error("Step 3 - signIn threw:", e?.message ?? e);
      throw new ValidationError(
        "Account created but auto-login failed. Please sign in manually."
      );
    }
    // 4. Audit log
    await logEvent({
      function_name: "register-doctor",
      level: "info",
      user_id: userId,
      action: "doctor_registered",
      metadata: {
        email: body.email,
        specialty: body.specialty,
      },
      ip_address: clientIp,
    });

    // 5. Return SessionResponse format
    return successResponse(
      {
        access_token: session.access_token,
        refresh_token: session.refresh_token,
        expires_at: session.expires_at,
        user: {
          id: userId,
          full_name: body.full_name,
          phone: body.phone,
          email: body.email,
          role: "DOCTOR",
          is_verified: false,
        },
      },
      201,
      origin,
    );
  } catch (err) {
    console.error("[register-doctor] error:", err instanceof Error ? err.message : String(err));
    try {
      await logEvent({
        function_name: "register-doctor",
        level: "warn",
        action: "doctor_registration_failed",
        error_message: err instanceof Error ? err.message : String(err),
        ip_address: clientIp,
      });
    } catch (_logErr) {
      // logging must not crash the response
    }
    return errorResponse(err, origin);
  }
});
