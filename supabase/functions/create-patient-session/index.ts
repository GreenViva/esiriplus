// functions/create-patient-session/index.ts
// Creates a cryptographically secure anonymous patient session.
//
// Security:
//   • 96-char token (UUID + 32 random bytes)
//   • SHA-256 stored for fast DB lookup
//   • PBKDF2 hash stored for timing-attack resistant comparison
//   • JWT signed with HS256, expires 24h
//   • Refresh token valid 7 days
//   • Rate limited: 5/min per IP

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { hashToken, sha256Hex } from "../_shared/bcrypt.ts";

const JWT_SECRET    = Deno.env.get("JWT_SECRET") ?? Deno.env.get("SUPABASE_JWT_SECRET")!;
const SESSION_TTL_H = 24;
const REFRESH_TTL_D = 7;

// Format: ESR-XXXX-XXXX (A-Z sans O/I, 2-9 → 32^8 ≈ 1 trillion combinations)
function generatePatientId(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const randomBytes = new Uint8Array(8);
  crypto.getRandomValues(randomBytes);
  const part1 = Array.from(randomBytes.slice(0, 4))
    .map((b) => chars[b % chars.length])
    .join("");
  const part2 = Array.from(randomBytes.slice(4, 8))
    .map((b) => chars[b % chars.length])
    .join("");
  return `ESR-${part1}-${part2}`;
}

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

    await checkRateLimit(`session-create:${clientIp}`, 5, 60);

    const supabase = getServiceClient();

    let body: Record<string, unknown> = {};
    try { body = await req.json(); } catch { /* optional */ }

    const legacyPatientId = body?.legacy_patient_id as string | undefined;
    const deviceInfo      = body?.device_info as Record<string, string> | undefined;
    const fcmToken        = body?.fcm_token as string | undefined;

    // 1. Generate tokens
    const sessionToken       = generateSecureToken();
    const sessionTokenHash   = await sha256Hex(sessionToken);    // fast lookup
    const sessionTokenBcrypt = await hashToken(sessionToken);    // secure compare

    const refreshToken       = generateRefreshToken();
    const refreshTokenHash   = await sha256Hex(refreshToken);    // fast lookup
    const refreshTokenBcrypt = await hashToken(refreshToken);    // secure compare

    // 2. Collision check
    const { data: collision } = await supabase
      .from("patient_sessions")
      .select("session_id")
      .eq("session_token_hash", sessionTokenHash)
      .single();

    if (collision) throw new Error("Token collision — please retry");

    // 3. Legacy PWA migration
    let legacyDataLinked = false;
    let migratedFromId: string | null = null;

    if (legacyPatientId && /^[A-Z0-9]{6}$/i.test(legacyPatientId)) {
      const legacyHash = await sha256Hex(legacyPatientId.toUpperCase());
      const { data: legacySession } = await supabase
        .from("patient_sessions")
        .select("session_id, is_migrated")
        .eq("legacy_id_hash", legacyHash)
        .single();

      if (legacySession && !legacySession.is_migrated) {
        migratedFromId   = legacySession.session_id;
        legacyDataLinked = true;
        await supabase
          .from("patient_sessions")
          .update({ is_migrated: true, migrated_at: new Date().toISOString() })
          .eq("session_id", legacySession.session_id);
      }
    }

    // 4. Generate friendly Patient ID with collision check
    let patientId: string;
    let patientIdHash: string;
    let pidCollision = true;

    do {
      patientId     = generatePatientId();
      patientIdHash = await sha256Hex(patientId);

      const { data: existing } = await supabase
        .from("patient_sessions")
        .select("session_id")
        .eq("patient_id_hash", patientIdHash)
        .single();

      pidCollision = !!existing;
    } while (pidCollision);

    // 5. Calculate expiry
    const now            = new Date();
    const expiresAt      = new Date(now.getTime() + SESSION_TTL_H * 60 * 60 * 1000);
    const refreshExpires = new Date(now.getTime() + REFRESH_TTL_D * 24 * 60 * 60 * 1000);

    // 6. Insert session — hashes only, never plaintext
    const { data: session, error: insertErr } = await supabase
      .from("patient_sessions")
      .insert({
        session_token_hash:   sessionTokenHash,
        session_token_bcrypt: sessionTokenBcrypt,
        refresh_token_hash:   refreshTokenHash,
        refresh_token_bcrypt: refreshTokenBcrypt,
        patient_id:           patientId,
        patient_id_hash:      patientIdHash,
        is_active:            true,
        expires_at:           expiresAt.toISOString(),
        refresh_expires_at:   refreshExpires.toISOString(),
        migrated_from_id:     migratedFromId,
        is_legacy:            false,
        fcm_token:            fcmToken ?? null,
        device_info:          deviceInfo ?? null,
        ip_address:           clientIp,
        created_at:           now.toISOString(),
      })
      .select("session_id")
      .single();

    if (insertErr || !session) {
      throw new Error(`DB insert failed: ${insertErr?.message}`);
    }

    // 7. Issue JWT
    const nowSecs = Math.floor(Date.now() / 1000);
    const jwt = await signJWT({
      sub:           session.session_id,    // Supabase auth.uid() for RLS
      session_id:    session.session_id,
      session_token: sessionToken,
      role:          "authenticated",       // Supabase Postgres role
      app_role:      "patient",             // Application-level role
      iat:           nowSecs,
      exp:           nowSecs + SESSION_TTL_H * 60 * 60,
    });

    // 8. Audit log
    await logEvent({
      function_name: "create-patient-session",
      level:         "info",
      session_id:    session.session_id,
      action:        "session_created",
      metadata:      { patient_id: patientId, expires_at: expiresAt.toISOString(), legacy_data_linked: legacyDataLinked },
      ip_address:    clientIp,
    });

    // 9. Return — raw token sent ONCE, never stored server-side
    return successResponse({
      message:            "Session created successfully",
      session_id:         session.session_id,
      patient_id:         patientId,
      access_token:       jwt,
      refresh_token:      refreshToken,
      token_type:         "Bearer",
      expires_in:         SESSION_TTL_H * 60 * 60,
      expires_at:         expiresAt.toISOString(),
      refresh_expires_at: refreshExpires.toISOString(),
      legacy_data_linked: legacyDataLinked,
    }, 201, origin);

  } catch (err) {
    await logEvent({
      function_name: "create-patient-session",
      level:         "warn",
      action:        "session_creation_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address:    clientIp,
    });
    return errorResponse(err, origin);
  }
});
