// functions/videosdk-token/index.ts
// Generates VideoSDK.live JWT tokens.
// Patients get 'join' permission; doctors get 'mod' permission.
// Rate limit: 30/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VIDEOSDK_API_KEY = Deno.env.get("VIDEOSDK_API_KEY")!;
const VIDEOSDK_SECRET  = Deno.env.get("VIDEOSDK_SECRET")!;
const TOKEN_EXPIRY_SECS = 60 * 60 * 2; // 2 hours

interface VideoTokenRequest {
  consultation_id: string;
  room_id?: string; // optional: if already created
}

/** Create a VideoSDK JWT using HS256 */
async function createVideoSDKToken(
  apiKey: string,
  secret: string,
  permissions: string[],
  roomId?: string
): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);

  const payload: Record<string, unknown> = {
    apikey: apiKey,
    permissions,
    iat: now,
    exp: now + TOKEN_EXPIRY_SECS,
    version: 2,
  };

  if (roomId) {
    payload.roomId = roomId;
  }

  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=/g, "");

  const headerB64  = encode(header);
  const payloadB64 = encode(payload);
  const sigInput   = `${headerB64}.${payloadB64}`;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(sigInput)
  );

  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=/g, "");

  return `${sigInput}.${sigB64}`;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.read(auth.userId ?? auth.sessionId ?? "anon");

    const raw = await req.json();

    if (typeof raw?.consultation_id !== "string" || !raw.consultation_id) {
      throw new ValidationError("consultation_id is required");
    }

    const { consultation_id } = raw as VideoTokenRequest;
    const supabase = getServiceClient();

    // Verify the caller is a participant in this consultation
    let consultation;
    if (auth.role === "doctor") {
      const { data } = await supabase
        .from("consultations")
        .select("consultation_id, status, video_room_id, patient_session_id")
        .eq("consultation_id", consultation_id)
        .eq("doctor_id", auth.userId)
        .single();
      consultation = data;
    } else {
      const { data } = await supabase
        .from("consultations")
        .select("consultation_id, status, video_room_id, doctor_id")
        .eq("consultation_id", consultation_id)
        .eq("patient_session_id", auth.sessionId)
        .single();
      consultation = data;
    }

    if (!consultation) {
      throw new ValidationError("Consultation not found or you are not a participant");
    }

    if (!["active", "in_progress"].includes(consultation.status)) {
      throw new ValidationError("Video calls are only available for active consultations");
    }

    // Get or create a VideoSDK room
    let roomId = consultation.video_room_id ?? raw.room_id;

    if (!roomId) {
      // Create a new room via VideoSDK API
      const adminToken = await createVideoSDKToken(VIDEOSDK_API_KEY, VIDEOSDK_SECRET, ["allow_join"]);
      const roomRes = await fetch("https://api.videosdk.live/v2/rooms", {
        method: "POST",
        headers: {
          Authorization: adminToken,
          "Content-Type": "application/json",
        },
      });

      if (!roomRes.ok) throw new Error("Failed to create VideoSDK room");
      const roomData = await roomRes.json();
      roomId = roomData.roomId;

      // Persist room_id on the consultation
      await supabase
        .from("consultations")
        .update({ video_room_id: roomId })
        .eq("consultation_id", consultation_id);

      // Also record in video_calls table
      await supabase.from("video_calls").insert({
        consultation_id,
        room_id: roomId,
        started_at: new Date().toISOString(),
        status: "active",
      });
    }

    // Assign permissions based on role
    // Doctors: moderator (can end call, mute participants)
    // Patients: join only
    const permissions = auth.role === "doctor"
      ? ["allow_join", "allow_mod"]
      : ["allow_join"];

    const token = await createVideoSDKToken(
      VIDEOSDK_API_KEY,
      VIDEOSDK_SECRET,
      permissions,
      roomId
    );

    await logEvent({
      function_name: "videosdk-token",
      level: "info",
      user_id: auth.userId,
      session_id: auth.sessionId,
      action: "video_token_issued",
      metadata: {
        consultation_id,
        room_id: roomId,
        role: auth.role,
        permissions,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      token,
      room_id: roomId,
      permissions,
      expires_in: TOKEN_EXPIRY_SECS,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "videosdk-token",
      level: "error",
      action: "token_generation_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
