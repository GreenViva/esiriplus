// functions/handle-messages/index.ts
// Proxy for message operations — works with both patient custom JWTs and doctor Supabase JWTs.
// Actions: get, send, typing, mark_read

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, type AuthResult } from "../_shared/auth.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { LIMITS } from "../_shared/rateLimit.ts";

// ── Validation ───────────────────────────────────────────────────────────────

function validate(body: unknown): Record<string, unknown> {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;
  const action = b.action as string;
  if (!["get", "send", "typing", "mark_read"].includes(action)) {
    throw new ValidationError(
      'action must be one of: get, send, typing, mark_read'
    );
  }
  return b;
}

// ── Authorization helper (3.5) ──────────────────────────────────────────────

/** Verify the caller is a participant in the given consultation. */
async function verifyConsultationParticipant(
  consultationId: string,
  auth: AuthResult
): Promise<void> {
  const supabase = getServiceClient();
  const { data: consultation, error } = await supabase
    .from("consultations")
    .select("doctor_id, patient_session_id")
    .eq("consultation_id", consultationId)
    .single();

  if (error || !consultation) {
    throw new ValidationError("Consultation not found");
  }

  // Doctor: auth.userId must match doctor_id
  if (auth.userId && consultation.doctor_id === auth.userId) return;

  // Patient: auth.sessionId must match patient_session_id
  if (auth.sessionId && consultation.patient_session_id === auth.sessionId) return;

  throw new ValidationError("You are not a participant in this consultation");
}

// ── Rate limit key ──────────────────────────────────────────────────────────

function getRateLimitKey(auth: AuthResult): string {
  return auth.userId ?? auth.sessionId ?? "unknown";
}

// ── Handlers ─────────────────────────────────────────────────────────────────

async function handleGet(
  body: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const consultationId = body.consultation_id as string;
  if (!consultationId) throw new ValidationError("consultation_id is required");

  // Authorization: verify caller is a participant
  await verifyConsultationParticipant(consultationId, auth);

  const since = body.since as string | undefined;
  const limit = Math.min(Number(body.limit) || 100, 500);

  const supabase = getServiceClient();
  let query = supabase
    .from("messages")
    .select("*")
    .eq("consultation_id", consultationId);

  if (since) {
    query = query.gt("created_at", since);
  }

  const { data, error } = await query
    .order("created_at", { ascending: true })
    .limit(limit);

  if (error) throw error;
  return successResponse(data ?? [], 200, origin);
}

async function handleSend(
  body: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const messageId = body.message_id as string;
  const consultationId = body.consultation_id as string;
  const senderType = body.sender_type as string;
  const senderId = body.sender_id as string;
  const messageText = body.message_text as string;
  const messageType = (body.message_type as string) ?? "text";

  if (!consultationId) throw new ValidationError("consultation_id is required");
  if (!senderType) throw new ValidationError("sender_type is required");
  if (!senderId) throw new ValidationError("sender_id is required");
  if (!messageText) throw new ValidationError("message_text is required");

  // Authorization: verify caller is a participant
  await verifyConsultationParticipant(consultationId, auth);

  // Enforce sender identity matches authenticated user (prevents spoofing)
  if (auth.sessionId) {
    // Patient auth (custom JWT)
    if (senderType !== "patient" || senderId !== auth.sessionId) {
      throw new ValidationError("sender_type/sender_id must match your session");
    }
  } else if (auth.userId) {
    // Doctor auth (Supabase JWT)
    if (senderType !== "doctor" || senderId !== auth.userId) {
      throw new ValidationError("sender_type/sender_id must match your account");
    }
  }

  const supabase = getServiceClient();

  const insertPayload: Record<string, unknown> = {
    consultation_id: consultationId,
    sender_type: senderType,
    sender_id: senderId,
    message_text: messageText,
    message_type: messageType,
  };
  if (messageId) insertPayload.message_id = messageId;

  const { data, error } = await supabase
    .from("messages")
    .insert(insertPayload)
    .select("*")
    .single();

  if (error) throw error;
  return successResponse(data, 201, origin);
}

async function handleTyping(
  body: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const consultationId = body.consultation_id as string;
  const userId = body.user_id as string;
  const isTyping = body.is_typing as boolean;

  if (!consultationId) throw new ValidationError("consultation_id is required");
  if (!userId) throw new ValidationError("user_id is required");

  // Enforce user_id matches authenticated caller
  const expectedUserId = auth.userId ?? auth.sessionId;
  if (userId !== expectedUserId) {
    throw new ValidationError("user_id must match your authenticated identity");
  }

  // Authorization: verify caller is a participant
  await verifyConsultationParticipant(consultationId, auth);

  const supabase = getServiceClient();
  const { error } = await supabase.from("typing_indicators").upsert(
    {
      consultation_id: consultationId,
      user_id: userId,
      is_typing: isTyping ?? false,
      updated_at: new Date().toISOString(),
    },
    { onConflict: "consultation_id,user_id" }
  );

  if (error) throw error;
  return successResponse({ ok: true }, 200, origin);
}

async function handleMarkRead(
  body: Record<string, unknown>,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const messageId = body.message_id as string;
  if (!messageId) throw new ValidationError("message_id is required");

  const supabase = getServiceClient();

  // Fetch message to get consultation_id for authorization
  const { data: message, error: fetchErr } = await supabase
    .from("messages")
    .select("consultation_id")
    .eq("message_id", messageId)
    .single();

  if (fetchErr || !message) throw new ValidationError("Message not found");

  // Verify caller is a participant in this consultation
  await verifyConsultationParticipant(message.consultation_id, auth);

  const { error } = await supabase
    .from("messages")
    .update({ is_read: true })
    .eq("message_id", messageId);

  if (error) throw error;
  return successResponse({ ok: true }, 200, origin);
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const raw = await req.json();
    const body = validate(raw);

    // Rate limiting (3.6)
    const rateKey = getRateLimitKey(auth);
    const action = body.action as string;
    if (action === "get") {
      await LIMITS.read(rateKey);
    } else if (action === "send") {
      await LIMITS.notification(rateKey);
    } else if (action === "typing" || action === "mark_read") {
      await LIMITS.notification(rateKey);
    }

    switch (action) {
      case "get":
        return await handleGet(body, auth, origin);
      case "send":
        return await handleSend(body, auth, origin);
      case "typing":
        return await handleTyping(body, auth, origin);
      case "mark_read":
        return await handleMarkRead(body, auth, origin);
      default:
        throw new ValidationError("Unknown action");
    }
  } catch (err) {
    return errorResponse(err, origin);
  }
});
