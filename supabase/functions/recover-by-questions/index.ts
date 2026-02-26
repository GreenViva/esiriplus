// functions/recover-by-questions/index.ts
// Recovers a patient session by answering recovery questions.
// Used when patient forgot their Patient ID — fully anonymous.
//
// Flow:
//   1. Patient provides answers to all 5 security questions
//   2. Answers are normalized, sorted by key, concatenated, and SHA-256 hashed
//   3. Hash is used to look up patient_sessions.recovery_hash
//   4. If found, individual answers are verified via PBKDF2 (need 3+ correct)
//   5. On success → reveals Patient ID + issues new JWT
//
// Security:
//   - Brute force protected (10 attempts per 30 min, keyed on recovery_hash)
//   - Answers normalized (lowercase, trimmed) before comparison
//   - Timing-attack resistant PBKDF2 comparison
//   - Failed attempts logged

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const JWT_SECRET    = Deno.env.get("JWT_SECRET") ?? Deno.env.get("SUPABASE_JWT_SECRET")!;
const SESSION_TTL_H = 24;
const REFRESH_TTL_D = 7;
const MIN_CORRECT   = 3; // must answer 3 of 5 correctly

const ALLOWED_QUESTIONS = [
  "first_pet",
  "favorite_city",
  "birth_city",
  "primary_school",
  "favorite_teacher",
];

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

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
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

// Timing-attack resistant answer comparison using PBKDF2
async function compareAnswer(rawAnswer: string, storedHash: string, storedSaltB64: string): Promise<boolean> {
  try {
    const normalized = rawAnswer.toLowerCase().trim();
    const salt       = Uint8Array.from(atob(storedSaltB64), (c) => c.charCodeAt(0));

    const keyMaterial = await crypto.subtle.importKey(
      "raw", new TextEncoder().encode(normalized), "PBKDF2", false, ["deriveBits"]
    );
    const hashBits = await crypto.subtle.deriveBits(
      { name: "PBKDF2", hash: "SHA-256", salt, iterations: 100_000 },
      keyMaterial, 256
    );
    const actualHash = btoa(String.fromCharCode(...new Uint8Array(hashBits)));

    // Constant-time comparison
    if (actualHash.length !== storedHash.length) return false;
    let diff = 0;
    for (let i = 0; i < actualHash.length; i++) {
      diff |= actualHash.charCodeAt(i) ^ storedHash.charCodeAt(i);
    }
    return diff === 0;
  } catch {
    return false;
  }
}

// Combined answers hash — must match the same logic in setup-recovery
async function computeRecoveryHash(answers: Record<string, string>): Promise<string> {
  const combined = ALLOWED_QUESTIONS
    .map((key) => (answers[key] || "").toLowerCase().trim())
    .join("|");
  const data = new TextEncoder().encode(combined);
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
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

    const answers = body?.answers as Record<string, string> | undefined;

    if (!answers || typeof answers !== "object") {
      throw new ValidationError("answers object is required");
    }

    // Must provide all 5 answers (they form the lookup key)
    if (Object.keys(answers).length !== ALLOWED_QUESTIONS.length) {
      throw new ValidationError("All 5 security question answers are required");
    }

    // Compute the recovery hash from answers to find the session
    const recoveryHash = await computeRecoveryHash(answers);
    const supabase     = getServiceClient();

    // Check brute force lockout (keyed on recovery_hash)
    const { data: locked } = await supabase
      .rpc("is_recovery_locked", {
        p_patient_id_hash: recoveryHash,
        p_ip_address:      clientIp,
      });

    if (locked) {
      throw new ValidationError("Too many failed attempts. Please try again in 30 minutes.");
    }

    // Look up session by recovery_hash
    const { data: session } = await supabase
      .from("patient_sessions")
      .select("session_id, is_locked, recovery_setup, patient_id")
      .eq("recovery_hash", recoveryHash)
      .single();

    if (!session || !session.recovery_setup) {
      await supabase.from("recovery_attempts").insert({
        patient_id_hash: recoveryHash,
        ip_address:      clientIp,
        success:         false,
      });
      throw new ValidationError("No account found matching these answers, or recovery not set up");
    }

    if (session.is_locked) {
      throw new ValidationError("This account has been locked. Please contact support.");
    }

    // Fetch stored recovery questions for individual answer verification
    const { data: storedQuestions } = await supabase
      .from("recovery_questions")
      .select("question_key, answer_hash, answer_salt")
      .eq("session_id", session.session_id);

    if (!storedQuestions || storedQuestions.length === 0) {
      throw new ValidationError("Recovery questions not found for this account");
    }

    // Compare answers — check all provided answers individually
    let correctCount = 0;
    for (const stored of storedQuestions) {
      const providedAnswer = answers[stored.question_key];
      if (!providedAnswer) continue;

      const isCorrect = await compareAnswer(
        providedAnswer,
        stored.answer_hash,
        stored.answer_salt
      );
      if (isCorrect) correctCount++;
    }

    // Need at least 3 correct
    if (correctCount < MIN_CORRECT) {
      await supabase.from("recovery_attempts").insert({
        patient_id_hash: recoveryHash,
        ip_address:      clientIp,
        success:         false,
      });

      throw new ValidationError(
        `Incorrect answers. You got ${correctCount} of ${MIN_CORRECT} required correct.`
      );
    }

    // ── Success — issue new JWT ───────────────────────────────────────────────
    const newSessionToken       = generateSecureToken();
    const newSessionTokenHash   = await sha256Hex(newSessionToken);
    const newSessionTokenBcrypt = await hashToken(newSessionToken);
    const newRefreshToken       = generateRefreshToken();
    const newRefreshTokenHash   = await sha256Hex(newRefreshToken);
    const newRefreshTokenBcrypt = await hashToken(newRefreshToken);

    const now            = new Date();
    const expiresAt      = new Date(now.getTime() + SESSION_TTL_H * 60 * 60 * 1000);
    const refreshExpires = new Date(now.getTime() + REFRESH_TTL_D * 24 * 60 * 60 * 1000);

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

    // Log success
    await supabase.from("recovery_attempts").insert({
      patient_id_hash: recoveryHash,
      ip_address:      clientIp,
      success:         true,
    });

    const nowSecs = Math.floor(Date.now() / 1000);
    const jwt = await signJWT({
      session_id:    session.session_id,
      session_token: newSessionToken,
      role:          "patient",
      iat:           nowSecs,
      exp:           nowSecs + SESSION_TTL_H * 60 * 60,
    });

    await logEvent({
      function_name: "recover-by-questions",
      level:         "info",
      session_id:    session.session_id,
      action:        "session_recovered_by_questions",
      metadata:      { correct_answers: correctCount },
      ip_address:    clientIp,
    });

    return successResponse({
      message:            "Account recovered successfully. Welcome back!",
      patient_id:         session.patient_id,
      session_id:         session.session_id,
      access_token:       jwt,
      refresh_token:      newRefreshToken,
      token_type:         "Bearer",
      expires_in:         SESSION_TTL_H * 60 * 60,
      expires_at:         expiresAt.toISOString(),
      refresh_expires_at: refreshExpires.toISOString(),
      reminder:           "Please save your Patient ID somewhere safe: " + session.patient_id,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "recover-by-questions",
      level:         "warn",
      action:        "recovery_by_questions_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address:    clientIp,
    });
    return errorResponse(err, origin);
  }
});
