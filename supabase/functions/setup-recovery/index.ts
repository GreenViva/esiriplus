// functions/setup-recovery/index.ts
// Sets up recovery for an existing patient session.
// Called after onboarding — patient provides answers to 5 questions.
// Answers are hashed with PBKDF2 before storage — never stored in plaintext.
//
// Also generates and returns the friendly Patient ID (ESR-XXXX-XXXX)
// This is shown to the patient ONCE with a strong warning to save it.
//
// Flow:
//   1. Patient completes onboarding
//   2. App calls this endpoint with JWT + 5 question answers
//   3. Function generates friendly Patient ID
//   4. Hashes all 5 answers with individual salts
//   5. Stores hashes in recovery_questions table
//   6. Returns Patient ID to display to patient

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { sha256Hex } from "../_shared/bcrypt.ts";

// ── Allowed question keys ─────────────────────────────────────────────────────
const ALLOWED_QUESTIONS = [
  "first_pet",
  "favorite_city",
  "birth_city",
  "primary_school",
  "favorite_teacher",
];

// ── Generate friendly Patient ID ──────────────────────────────────────────────
// Format: ESR-XXXX-XXXX
// Characters: A-Z and 2-9 (removed 0,1,O,I to avoid confusion)
// Combinations: 32^8 = 1,099,511,627,776 (1 trillion+)
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

// ── PBKDF2 hash for recovery answers ─────────────────────────────────────────
// Same approach as session token hashing — timing attack resistant
async function hashAnswer(answer: string, salt: Uint8Array): Promise<string> {
  const normalized = answer.toLowerCase().trim(); // normalize before hashing
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(normalized),
    "PBKDF2",
    false,
    ["deriveBits"]
  );
  const hashBits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: 100_000 },
    keyMaterial,
    256
  );
  return btoa(String.fromCharCode(...new Uint8Array(hashBits)));
}

// ── Validate request body ─────────────────────────────────────────────────────
interface SetupRequest {
  answers: Record<string, string>; // { first_pet: "Fluffy", favorite_city: "Dar" ... }
}

function validate(body: unknown): SetupRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be a JSON object");
  }
  const b = body as Record<string, unknown>;

  if (typeof b.answers !== "object" || b.answers === null) {
    throw new ValidationError("answers object is required");
  }

  const answers = b.answers as Record<string, unknown>;
  const providedKeys = Object.keys(answers);

  // Must answer all 5 questions
  if (providedKeys.length !== 5) {
    throw new ValidationError("All 5 recovery questions must be answered");
  }

  // All keys must be valid
  for (const key of providedKeys) {
    if (!ALLOWED_QUESTIONS.includes(key)) {
      throw new ValidationError(`Invalid question key: ${key}`);
    }
    if (typeof answers[key] !== "string" || (answers[key] as string).trim().length < 2) {
      throw new ValidationError(`Answer for ${key} must be at least 2 characters`);
    }
    if ((answers[key] as string).trim().length > 100) {
      throw new ValidationError(`Answer for ${key} must be under 100 characters`);
    }
  }

  // Must have all 5 allowed questions
  for (const key of ALLOWED_QUESTIONS) {
    if (!answers[key]) {
      throw new ValidationError(`Missing answer for: ${key}`);
    }
  }

  return { answers: answers as Record<string, string> };
}

// ── Handler ───────────────────────────────────────────────────────────────────
Deno.serve(async (req: Request) => {
  const origin   = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // 1. Auth — must be a valid patient session
    const auth = await validateAuth(req);
    if (auth.role !== "patient" || !auth.sessionId) {
      throw new ValidationError("Patient session required");
    }

    // 2. Rate limit — 3/min (prevent brute force answer testing)
    await LIMITS.sensitive(auth.sessionId);

    const supabase = getServiceClient();

    // 3. Check if recovery already set up
    const { data: existing } = await supabase
      .from("patient_sessions")
      .select("recovery_setup, patient_id")
      .eq("session_id", auth.sessionId)
      .single();

    if (existing?.recovery_setup) {
      // Recovery already set up — return existing patient ID
      return successResponse({
        message: "Recovery already configured",
        patient_id: existing.patient_id,
        already_setup: true,
      }, 200, origin);
    }

    // 4. Parse and validate body
    const rawBody = await req.json();
    const body    = validate(rawBody);

    // 5. Generate friendly Patient ID
    let patientId: string;
    let patientIdHash: string;
    let collision = true;

    // Collision check loop (astronomically unlikely but safe)
    do {
      patientId     = generatePatientId();
      patientIdHash = await sha256Hex(patientId);

      const { data: existing } = await supabase
        .from("patient_sessions")
        .select("session_id")
        .eq("patient_id_hash", patientIdHash)
        .single();

      collision = !!existing;
    } while (collision);

    // 6. Hash all 5 answers with individual salts
    const questionRecords = [];

    for (const questionKey of ALLOWED_QUESTIONS) {
      const answer = body.answers[questionKey];
      const salt   = new Uint8Array(32);
      crypto.getRandomValues(salt);

      const answerHash = await hashAnswer(answer, salt);
      const saltB64    = btoa(String.fromCharCode(...salt));

      questionRecords.push({
        session_id:   auth.sessionId,
        question_key: questionKey,
        answer_hash:  answerHash,
        answer_salt:  saltB64,
      });
    }

    // 7. Store everything in a transaction-like sequence
    // Insert recovery questions
    const { error: questionsErr } = await supabase
      .from("recovery_questions")
      .insert(questionRecords);

    if (questionsErr) throw questionsErr;

    // Update session with patient ID and mark recovery as set up
    const { error: updateErr } = await supabase
      .from("patient_sessions")
      .update({
        patient_id:        patientId,
        patient_id_hash:   patientIdHash,
        recovery_setup:    true,
        recovery_setup_at: new Date().toISOString(),
      })
      .eq("session_id", auth.sessionId);

    if (updateErr) throw updateErr;

    // 8. Audit log
    await logEvent({
      function_name: "setup-recovery",
      level:         "info",
      session_id:    auth.sessionId,
      action:        "recovery_setup_complete",
      metadata: {
        session_id: auth.sessionId,
        patient_id: patientId, // log for audit — never returned in errors
      },
      ip_address: clientIp,
    });

    // 9. Return Patient ID — shown to patient with strong warning
    return successResponse({
      message:    "Recovery setup complete",
      patient_id: patientId,  // e.g. "ESR-4K9X-M2PQ"
      warning:    "IMPORTANT: Save your Patient ID. It is the only way to recover your account if you lose access. We cannot recover it for you.",
      questions_set: ALLOWED_QUESTIONS.length,
    }, 201, origin);

  } catch (err) {
    await logEvent({
      function_name: "setup-recovery",
      level:         "warn",
      action:        "recovery_setup_failed",
      error_message: err instanceof Error ? err.message : String(err),
      ip_address:    clientIp,
    });
    return errorResponse(err, origin);
  }
});
