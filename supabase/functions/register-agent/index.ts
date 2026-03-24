// register-agent — Creates a new agent account with Supabase Auth + agent_profiles row.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface RegisterAgentRequest {
  full_name: string;
  mobile_number: string;
  email: string;
  place_of_residence: string;
  password: string;
}

function validate(body: unknown): RegisterAgentRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;
  if (typeof b.full_name !== "string" || !b.full_name.trim()) {
    throw new ValidationError("full_name is required");
  }
  if (typeof b.mobile_number !== "string" || !b.mobile_number.trim()) {
    throw new ValidationError("mobile_number is required");
  }
  if (typeof b.email !== "string" || !b.email.includes("@")) {
    throw new ValidationError("Valid email is required");
  }
  if (typeof b.place_of_residence !== "string" || !b.place_of_residence.trim()) {
    throw new ValidationError("place_of_residence is required");
  }
  if (typeof b.password !== "string" || b.password.length < 6) {
    throw new ValidationError("Password must be at least 6 characters");
  }
  return b as unknown as RegisterAgentRequest;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const raw = await req.json();
    const body = validate(raw);

    const supabase = getServiceClient();

    // Check if email is already registered
    const { data: existing } = await supabase
      .from("agent_profiles")
      .select("id")
      .eq("email", body.email.toLowerCase())
      .maybeSingle();

    if (existing) {
      throw new ValidationError("An agent with this email already exists");
    }

    // Create Supabase Auth user
    const { data: authData, error: authError } = await supabase.auth.admin.createUser({
      email: body.email.toLowerCase(),
      password: body.password,
      email_confirm: true,
      user_metadata: {
        full_name: body.full_name.trim(),
        role: "AGENT",
      },
    });

    if (authError || !authData.user) {
      throw new ValidationError(authError?.message ?? "Failed to create account");
    }

    const userId = authData.user.id;

    // Insert agent profile
    const { error: profileError } = await supabase
      .from("agent_profiles")
      .insert({
        agent_id: userId,
        full_name: body.full_name.trim(),
        mobile_number: body.mobile_number.trim(),
        email: body.email.toLowerCase(),
        place_of_residence: body.place_of_residence.trim(),
      });

    if (profileError) {
      // Rollback: delete the auth user
      await supabase.auth.admin.deleteUser(userId);
      throw new ValidationError(profileError.message);
    }

    // Assign agent role
    const { error: roleError } = await supabase
      .from("user_roles")
      .insert({ user_id: userId, role_name: "agent" });

    if (roleError) {
      console.warn("Failed to insert agent role:", roleError.message);
    }

    // Sign in to get session tokens
    const { data: signInData, error: signInError } = await supabase.auth.admin.generateLink({
      type: "magiclink",
      email: body.email.toLowerCase(),
    });

    // Direct sign-in with password
    const { data: sessionData, error: sessionError } = await supabase.auth.signInWithPassword({
      email: body.email.toLowerCase(),
      password: body.password,
    });

    if (sessionError || !sessionData.session) {
      // Account created but sign-in failed — client can sign in manually
      return successResponse({
        message: "Account created. Please sign in.",
        user_id: userId,
      }, 201, origin);
    }

    await logEvent({
      function_name: "register-agent",
      level: "info",
      user_id: userId,
      action: "agent_registered",
      metadata: { email: body.email.toLowerCase() },
    });

    return successResponse({
      access_token: sessionData.session.access_token,
      refresh_token: sessionData.session.refresh_token,
      expires_at: sessionData.session.expires_at,
      expires_in: sessionData.session.expires_in,
      user: {
        id: userId,
        email: body.email.toLowerCase(),
        full_name: body.full_name.trim(),
        role: "AGENT",
      },
    }, 201, origin);

  } catch (err) {
    return errorResponse(err, origin);
  }
});
