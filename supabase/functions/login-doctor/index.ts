// functions/login-doctor/index.ts
// Authenticates a doctor via Supabase Auth (email + password).
//
// Expects JSON body: { email, password }
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

    await checkRateLimit(`login-doctor:${clientIp}`, 5, 60);

    const body = await req.json();

    if (!body.email || !body.password) {
      throw new ValidationError("Email and password are required");
    }

    const supabase = getServiceClient();

    // 1. Authenticate with Supabase Auth
    const { data: signInData, error: signInError } =
      await supabase.auth.signInWithPassword({
        email: body.email,
        password: body.password,
      });

    if (signInError) {
      const msg = signInError.message?.toLowerCase() ?? "";
      if (msg.includes("invalid") || msg.includes("credentials")) {
        return errorResponse(
          Object.assign(new Error("Invalid email or password"), {
            status: 401,
          }),
          origin,
        );
      }
      throw signInError;
    }

    if (!signInData.session || !signInData.user) {
      return errorResponse(
        Object.assign(new Error("Invalid email or password"), { status: 401 }),
        origin,
      );
    }

    const session = signInData.session;
    const user = signInData.user;

    // 2. Verify this user is actually a doctor
    const role = user.user_metadata?.role;
    if (role !== "DOCTOR") {
      return errorResponse(
        Object.assign(new Error("This account is not a doctor account"), {
          status: 403,
        }),
        origin,
      );
    }

    // 3. Fetch doctor profile for response
    const { data: profile } = await supabase
      .from("doctor_profiles")
      .select("full_name, phone, email, is_verified")
      .eq("doctor_id", user.id)
      .single();

    // 4. Audit log
    await logEvent({
      function_name: "login-doctor",
      level: "info",
      user_id: user.id,
      action: "doctor_logged_in",
      metadata: { email: body.email },
      ip_address: clientIp,
    });

    // 5. Return SessionResponse format
    return successResponse(
      {
        access_token: session.access_token,
        refresh_token: session.refresh_token,
        expires_at: session.expires_at,
        user: {
          id: user.id,
          full_name: profile?.full_name ?? user.user_metadata?.full_name ?? "",
          phone: profile?.phone ?? "",
          email: profile?.email ?? body.email,
          role: "DOCTOR",
          is_verified: profile?.is_verified ?? false,
        },
      },
      200,
      origin,
    );
  } catch (err) {
    await logEvent({
      function_name: "login-doctor",
      level: "warn",
      action: "doctor_login_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address: clientIp,
    });
    return errorResponse(err, origin);
  }
});
