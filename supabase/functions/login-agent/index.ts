// login-agent — Authenticates an agent via email/password.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const body = await req.json();

    if (typeof body.email !== "string" || !body.email.includes("@")) {
      throw new ValidationError("Valid email is required");
    }
    if (typeof body.password !== "string" || !body.password) {
      throw new ValidationError("Password is required");
    }

    const supabase = getServiceClient();

    // Sign in
    const { data: sessionData, error: signInError } = await supabase.auth.signInWithPassword({
      email: body.email.toLowerCase(),
      password: body.password,
    });

    if (signInError || !sessionData.session) {
      throw new ValidationError(signInError?.message ?? "Invalid credentials");
    }

    const userId = sessionData.user.id;

    // Verify this user is an agent
    const { data: profile } = await supabase
      .from("agent_profiles")
      .select("full_name, email, mobile_number, place_of_residence, is_active")
      .eq("agent_id", userId)
      .maybeSingle();

    if (!profile) {
      await supabase.auth.signOut();
      throw new ValidationError("No agent account found for this email. Please register first.");
    }

    if (!profile.is_active) {
      await supabase.auth.signOut();
      throw new ValidationError("Your agent account has been deactivated. Contact support.");
    }

    await logEvent({
      function_name: "login-agent",
      level: "info",
      user_id: userId,
      action: "agent_login",
    });

    return successResponse({
      access_token: sessionData.session.access_token,
      refresh_token: sessionData.session.refresh_token,
      expires_at: sessionData.session.expires_at,
      expires_in: sessionData.session.expires_in,
      user: {
        id: userId,
        email: profile.email,
        full_name: profile.full_name,
        role: "AGENT",
      },
    }, 200, origin);

  } catch (err) {
    return errorResponse(err, origin);
  }
});
