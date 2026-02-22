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

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    await checkRateLimit(`register-doctor:${clientIp}`, 3, 60);

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

    const supabase = getServiceClient();

    // 1. Create Supabase Auth user
    const { data: authData, error: authError } =
      await supabase.auth.admin.createUser({
        email: body.email,
        password: body.password,
        email_confirm: true,
        user_metadata: {
          role: "DOCTOR",
          full_name: body.full_name,
        },
      });

    if (authError) {
      if (
        authError.message?.includes("already") ||
        authError.message?.includes("duplicate")
      ) {
        return errorResponse(
          Object.assign(new Error("An account with this email already exists"), {
            status: 409,
          }),
          origin,
        );
      }
      throw authError;
    }

    const userId = authData.user.id;

    // 2. Insert doctor profile row
    const { error: profileError } = await supabase
      .from("doctor_profiles")
      .insert({
        doctor_id: userId,
        full_name: body.full_name,
        email: body.email,
        country_code: body.country_code ?? "+255",
        phone: body.phone,
        specialty: body.specialty,
        country: body.country ?? "",
        languages: body.languages ?? [],
        license_number: body.license_number,
        years_experience: body.years_experience ?? 0,
        bio: body.bio ?? "",
        services: body.services ?? [],
        profile_photo_url: body.profile_photo_url ?? null,
        license_document_url: body.license_document_url ?? null,
        certificates_url: body.certificates_url ?? null,
        is_verified: false,
        is_available: false,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      });

    if (profileError) {
      // Rollback: delete the auth user if profile insert fails
      await supabase.auth.admin.deleteUser(userId);
      throw profileError;
    }

    // 3. Sign in to get a real session (access + refresh tokens)
    const { data: signInData, error: signInError } =
      await supabase.auth.signInWithPassword({
        email: body.email,
        password: body.password,
      });

    if (signInError || !signInData.session) {
      throw new Error("Account created but sign-in failed. Please log in.");
    }

    const session = signInData.session;

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
    await logEvent({
      function_name: "register-doctor",
      level: "warn",
      action: "doctor_registration_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address: clientIp,
    });
    return errorResponse(err, origin);
  }
});
