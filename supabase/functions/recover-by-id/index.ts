// functions/recover-by-id/index.ts
// Recovers a patient session using their Patient ID (ESR-XXXX-XXXX).
// Issues a fresh JWT for the recovered session.
// Rate limited and brute-force protected.
//
// Flow:
//   1. Patient enters their Patient ID (e.g. ESR-4K9X-M2PQ)
//   2. Function looks up session by SHA-256 hash of ID
//   3. If found and active → issues new JWT
//   4. All medical history and reports are accessible again

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { sha256Hex } from "../_shared/bcrypt.ts";

const JWT_SECRET    = Deno.env.get("JWT_SECRET") ?? Deno.env.get("SUPABASE_JWT_SECRET")!;
const SESSION_TTL_H = 24;
const REFRESH_TTL_D = 7;

function generateSecureToken(): string {
  const uuidPart    = crypto.randomUUID().replace(/-/g, "");
  const randomBytes = new Uint8Array(32);
  crypto.getRandomValues(randomBytes);
  const randomPart  = Array.from(randomBytes).map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${uuidPart}${randomPart}`;
}

function generateRefreshToken(): string {
  const bytes = new Uint8Array(48);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function hashToken(token: string): Promise<string> {
  const salt = new Uint8Array(32);
  crypto.getRandomValues(salt);
  const keyMaterial = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(token), "PBKDF2", false, ["deriveBits"]
  );
  const hashBits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: 100_000 },
    keyMaterial, 256
  );
  const saltB64 = btoa(String.fromCharCode(...salt));
  const hashB64 = btoa(String.fromCharCode(...new Uint8Array(hashBits)));
  return `${saltB64}:${hashB64}`;
}

async function signJWT(payload: Record<string, unknown>): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");

  const headerB64  = encode(header);
  const payloadB64 = encode(payload);
  const sigInput   = `${headerB64}.${payloadB64}`;

  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(JWT_SECRET),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(sigInput));
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");

  return `${sigInput}.${sigB64}`;
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

    let body: Record<string, unknown> = {};
    try { body = await req.json(); } catch { /* required */ }

    const patientId = body?.patient_id as string | undefined;

    if (!patientId || typeof patientId !== "string") {
      throw new ValidationError("patient_id is required");
    }

    // Validate format: ESR-XXXX-XXXX
    if (!/^ESR-[A-Z2-9]{4}-[A-Z2-9]{4}$/.test(patientId.toUpperCase())) {
      throw new ValidationError("Invalid Patient ID format. Should look like: ESR-4K9X-M2PQ");
    }

    const patientIdHash = await sha256Hex(patientId.toUpperCase());
    const supabase      = getServiceClient();

    // Check brute force lockout
    const { data: locked } = await supabase
      .rpc("is_recovery_locked", {
        p_patient_id_hash: patientIdHash,
        p_ip_address:      clientIp,
      });

    if (locked) {
      await logEvent({
        function_name: "recover-by-id",
        level:         "warn",
        action:        "recovery_locked",
        metadata:      { ip: clientIp },
        ip_address:    clientIp,
      });
      throw new ValidationError("Too many failed attempts. Please try again in 30 minutes.");
    }

    // Look up session by patient ID hash
    const { data: session } = await supabase
      .from("patient_sessions")
      .select("session_id, is_active, is_locked, expires_at, recovery_setup")
      .eq("patient_id_hash", patientIdHash)
      .single();

    if (!session) {
      // Log failed attempt
      await supabase.from("recovery_attempts").insert({
        patient_id_hash: patientIdHash,
        ip_address:      clientIp,
        success:         false,
      });

      // Generic error — don't reveal if ID exists or not
      throw new ValidationError("Invalid Patient ID or account not found");
    }

    if (session.is_locked) {
      throw new ValidationError("This account has been locked. Please use recovery questions.");
    }

    if (!session.recovery_setup) {
      throw new ValidationError("Recovery not set up for this account");
    }

    // Generate new session token and refresh token
    const newSessionToken       = generateSecureToken();
    const newSessionTokenHash   = await sha256Hex(newSessionToken);
    const newSessionTokenBcrypt = await hashToken(newSessionToken);
    const newRefreshToken       = generateRefreshToken();
    const newRefreshTokenHash   = await sha256Hex(newRefreshToken);
    const newRefreshTokenBcrypt = await hashToken(newRefreshToken);

    const now            = new Date();
    const expiresAt      = new Date(now.getTime() + SESSION_TTL_H * 60 * 60 * 1000);
    const refreshExpires = new Date(now.getTime() + REFRESH_TTL_D * 24 * 60 * 60 * 1000);

    // Reactivate session with new tokens
    await supabase
      .from("patient_sessions")
      .update({
        session_token_hash:   newSessionTokenHash,
        session_token_bcrypt: newSessionTokenBcrypt,
        refresh_token_hash:   newRefreshTokenHash,
        refresh_token_bcrypt: newRefreshTokenBcrypt,
        is_active:            true,
        expires_at:           expiresAt.toISOString(),
        refresh_expires_at:   refreshExpires.toISOString(),
        last_seen_at:         now.toISOString(),
      })
      .eq("session_id", session.session_id);

    // Log successful recovery
    await supabase.from("recovery_attempts").insert({
      patient_id_hash: patientIdHash,
      ip_address:      clientIp,
      success:         true,
    });

    // Issue new JWT
    const nowSecs = Math.floor(Date.now() / 1000);
    const jwt = await signJWT({
      session_id:    session.session_id,
      session_token: newSessionToken,
      role:          "patient",
      iat:           nowSecs,
      exp:           nowSecs + SESSION_TTL_H * 60 * 60,
    });

    await logEvent({
      function_name: "recover-by-id",
      level:         "info",
      session_id:    session.session_id,
      action:        "session_recovered_by_id",
      ip_address:    clientIp,
    });

    return successResponse({
      message:            "Account recovered successfully. Welcome back!",
      session_id:         session.session_id,
      access_token:       jwt,
      refresh_token:      newRefreshToken,
      token_type:         "Bearer",
      expires_in:         SESSION_TTL_H * 60 * 60,
      expires_at:         expiresAt.toISOString(),
      refresh_expires_at: refreshExpires.toISOString(),
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "recover-by-id",
      level:         "warn",
      action:        "recovery_by_id_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address:    clientIp,
    });
    return errorResponse(err, origin);
  }
});
