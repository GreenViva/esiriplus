// functions/extend-session/index.ts
// Extends a patient's anonymous session expiry.
// Rate limit: 5/min (sensitive operation).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const MAX_EXTENSION_HOURS = 72;
const MIN_EXTENSION_HOURS = 1;

interface ExtendSessionRequest {
  extend_hours: number;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.sensitive(auth.sessionId ?? auth.userId ?? "anon");

    if (!auth.sessionId) {
      throw new ValidationError("Only patient sessions can be extended");
    }

    const raw = await req.json();
    if (typeof raw?.extend_hours !== "number") {
      throw new ValidationError("extend_hours must be a number");
    }

    const hours = raw.extend_hours as number;
    if (hours < MIN_EXTENSION_HOURS || hours > MAX_EXTENSION_HOURS) {
      throw new ValidationError(
        `extend_hours must be between ${MIN_EXTENSION_HOURS} and ${MAX_EXTENSION_HOURS}`
      );
    }

    const supabase = getServiceClient();

    // Fetch current session
    const { data: session, error: fetchErr } = await supabase
      .from("patient_sessions")
      .select("session_id, expires_at, is_active")
      .eq("session_id", auth.sessionId)
      .single();

    if (fetchErr || !session) {
      throw new ValidationError("Session not found");
    }

    if (!session.is_active) {
      throw new ValidationError("Cannot extend an inactive session");
    }

    // Calculate new expiry from current expiry (not now, to stack extensions)
    const currentExpiry = new Date(session.expires_at);
    const newExpiry = new Date(currentExpiry.getTime() + hours * 60 * 60 * 1000);

    // Cap total extension at 30 days from now
    const maxExpiry = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
    const finalExpiry = newExpiry > maxExpiry ? maxExpiry : newExpiry;

    await supabase
      .from("patient_sessions")
      .update({
        expires_at: finalExpiry.toISOString(),
        last_extended_at: new Date().toISOString(),
      })
      .eq("session_id", auth.sessionId);

    await logEvent({
      function_name: "extend-session",
      level: "info",
      session_id: auth.sessionId,
      action: "session_extended",
      metadata: {
        extended_by_hours: hours,
        new_expiry: finalExpiry.toISOString(),
        previous_expiry: session.expires_at,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: "Session extended successfully",
      expires_at: finalExpiry.toISOString(),
      extended_by_hours: hours,
    }, 200, origin);

  } catch (err) {
    return errorResponse(err, origin);
  }
});
