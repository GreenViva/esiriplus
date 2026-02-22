// functions/register-doctor/index.ts
// Creates a Supabase Auth account for a doctor and inserts their profile.
//
// Expects JSON body:
//   { email, password, full_name, country_code, phone, specialty, country,
//     languages, license_number, years_experience, bio, services,
//     profile_photo_url?, license_document_url?, certificates_url? }
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

    if (body.password.length < 8) {
      throw new ValidationError("Password must be at least 8 characters");
    }

    // Map specialty display name to Postgres enum value
    const specialtyEnum = SPECIALTY_TO_ENUM[body.specialty] ?? body.specialty.toLowerCase().replace(/\s+/g, "_");
    const supabase = getServiceClient();

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
          profile_photo_url: body.profile_photo_url ?? null,
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
