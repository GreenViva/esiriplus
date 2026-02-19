// functions/refresh-patient-session/index.ts
//
// Extends a patient session using the refresh token.
// Allows up to 7 days of total session life without re-onboarding.
// Issues a fresh JWT and rotates the refresh token (prevents replay attacks).
//
// Security:
//   • Refresh token is single-use (rotated on every refresh)
//   • Cannot refresh beyond 7-day absolute window
//   • Rate limited: 10/min per IP
//   • Old refresh token immediately invalidated after use

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { hashToken, compareToken, sha256Hex } from "../_shared/bcrypt.ts";

const JWT_SECRET    = Deno.env.get("JWT_SECRET") ?? Deno.env.get("SUPABASE_JWT_SECRET")!;;
const SESSION_TTL_H = 24;   // each refresh gives 24 more hours
const REFRESH_TTL_D = 7;    // absolute max from creation


async function signJWT(payload: Record<string, unknown>): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");

  const headerB64  = encode(header);
  const payloadB64 = encode(payload);
  const sigInput   = `${headerB64}.${payloadB64}`;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(JWT_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "HMAC", key, new TextEncoder().encode(sigInput)
  );

  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");

  return `${sigInput}.${sigB64}`;
}

function generateRefreshToken(): string {
  const bytes = new Uint8Array(48);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function generateSessionToken(): string {
  const uuidPart   = crypto.randomUUID().replace(/-/g, "");
  const randomBytes = new Uint8Array(32);
  crypto.getRandomValues(randomBytes);
  const randomPart = Array.from(randomBytes)
    .map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${uuidPart}${randomPart}`;
}

Deno.serve(async (req: Request) => {
  const origin   = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    await checkRateLimit(`session-refresh:${clientIp}`, 10, 60);

    let body: Record<string, unknown> = {};
    try { body = await req.json(); } catch { /* optional */ }

    const refreshToken = body?.refresh_token as string | undefined;
    const sessionId    = body?.session_id as string | undefined;

    if (!refreshToken || !sessionId) {
      throw new ValidationError("refresh_token and session_id are required");
    }

    if (typeof refreshToken !== "string" || refreshToken.length < 32) {
      throw new ValidationError("Invalid refresh token format");
    }

    const supabase         = getServiceClient();
    const refreshTokenHash = await sha256Hex(refreshToken);

    // Find session by ID + sha256 hash (fast indexed lookup)
    const { data: session, error: fetchErr } = await supabase
      .from("patient_sessions")
      .select("session_id, is_active, expires_at, refresh_expires_at, created_at, refresh_token_bcrypt")
      .eq("session_id", sessionId)
      .eq("refresh_token_hash", refreshTokenHash)
      .single();

    // Bcrypt comparison — timing-attack resistant even if sha256 lookup matched
    const tokenValid = session?.refresh_token_bcrypt
      ? await compareToken(refreshToken, session.refresh_token_bcrypt)
      : false;

    if (fetchErr || !session || !tokenValid) {
      // Log this — could be a stolen refresh token attempt
      await logEvent({
        function_name: "refresh-patient-session",
        level:         "warn",
        action:        "invalid_refresh_token",
        metadata:      { session_id: sessionId },
        ip_address:    clientIp,
      });
      throw new ValidationError("Invalid or expired refresh token");
    }

    if (!session.is_active) {
      throw new ValidationError("Session is no longer active");
    }

    // Check absolute 7-day refresh window has not expired
    const refreshExpiry = new Date(session.refresh_expires_at);
    if (refreshExpiry < new Date()) {
      await supabase
        .from("patient_sessions")
        .update({ is_active: false })
        .eq("session_id", sessionId);

      throw new ValidationError(
        "Session has exceeded the maximum 7-day window. Please start a new session."
      );
    }

    // ── Rotate tokens (prevents replay attacks) ───────────────────────────────
    // New session token + new refresh token issued
    // Old refresh token is immediately invalidated by overwriting the hash
    const newSessionToken       = generateSessionToken();
    const newSessionTokenHash   = await sha256Hex(newSessionToken);
    const newSessionTokenBcrypt = await hashToken(newSessionToken);
    const newRefreshToken       = generateRefreshToken();
    const newRefreshTokenHash   = await sha256Hex(newRefreshToken);
    const newRefreshTokenBcrypt = await hashToken(newRefreshToken);

    const now       = new Date();
    const expiresAt = new Date(now.getTime() + SESSION_TTL_H * 60 * 60 * 1000);

    // Update session with new hashes — old tokens immediately invalidated
    await supabase
      .from("patient_sessions")
      .update({
        session_token_hash:   newSessionTokenHash,
        session_token_bcrypt: newSessionTokenBcrypt,
        refresh_token_hash:   newRefreshTokenHash,
        refresh_token_bcrypt: newRefreshTokenBcrypt,
        expires_at:         expiresAt.toISOString(),
        last_refreshed_at:  now.toISOString(),
      })
      .eq("session_id", sessionId);

    // Issue new JWT
    const nowSecs = Math.floor(Date.now() / 1000);
    const jwt = await signJWT({
      session_id:    sessionId,
      session_token: newSessionToken,
      role:          "patient",
      iat:           nowSecs,
      exp:           nowSecs + SESSION_TTL_H * 60 * 60,
    });

    await logEvent({
      function_name: "refresh-patient-session",
      level:         "info",
      session_id:    sessionId,
      action:        "session_refreshed",
      metadata: {
        new_expires_at:   expiresAt.toISOString(),
        refresh_expires_at: session.refresh_expires_at,
      },
      ip_address: clientIp,
    });

    return successResponse({
      message:       "Session refreshed successfully",
      session_id:    sessionId,
      access_token:  jwt,
      refresh_token: newRefreshToken,   // NEW refresh token — store this, discard old one
      token_type:    "Bearer",
      expires_in:    SESSION_TTL_H * 60 * 60,
      expires_at:    expiresAt.toISOString(),
      refresh_expires_at: session.refresh_expires_at, // absolute deadline unchanged
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "refresh-patient-session",
      level:         "warn",
      action:        "refresh_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address:    clientIp,
    });
    return errorResponse(err, origin);
  }
});
