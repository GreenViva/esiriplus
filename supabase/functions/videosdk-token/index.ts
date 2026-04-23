// functions/videosdk-token/index.ts
// Generates VideoSDK.live JWT tokens.
// Patients get 'join' permission; doctors get 'mod' permission.
// Rate limit: 30/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS, acquireConcurrencySlot } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VIDEOSDK_API_KEY = Deno.env.get("VIDEOSDK_API_KEY")!;
const VIDEOSDK_SECRET  = Deno.env.get("VIDEOSDK_SECRET")!;
const TOKEN_EXPIRY_SECS = 60 * 60 * 2; // 2 hours

interface VideoTokenRequest {
  consultation_id: string;
  room_id?: string; // optional: if already created
  call_type?: string; // "VIDEO" or "VOICE"
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
    await LIMITS.video(auth.userId ?? auth.sessionId ?? "anon");

    // Concurrency slot kept for observability only; cap set far above any
    // realistic VideoSDK plan so the effective ceiling is the VideoSDK tier.
    const releaseConcurrency = await acquireConcurrencySlot("videosdk-room", 100_000);

    const raw = await req.json();

    if (typeof raw?.consultation_id !== "string" || !raw.consultation_id) {
      throw new ValidationError("consultation_id is required");
    }

    const { consultation_id, room_id: requestRoomId, call_type } = raw as VideoTokenRequest;
    const supabase = getServiceClient();

    // Verify the caller is a participant in this consultation
    let consultation;
    if (auth.role === "doctor") {
      const { data } = await supabase
        .from("consultations")
        .select("consultation_id, status, video_room_id, patient_session_id, doctor_id, service_tier, follow_up_expiry")
        .eq("consultation_id", consultation_id)
        .eq("doctor_id", auth.userId)
        .single();
      consultation = data;
    } else {
      const { data } = await supabase
        .from("consultations")
        .select("consultation_id, status, video_room_id, doctor_id, patient_session_id, service_tier, follow_up_expiry")
        .eq("consultation_id", consultation_id)
        .eq("patient_session_id", auth.sessionId)
        .single();
      consultation = data;
    }

    if (!consultation) {
      throw new ValidationError("Consultation not found or you are not a participant");
    }

    // Royal consultations can be called within 14-day follow-up window even after completion
    const CALLABLE_STATUSES = ["active", "in_progress", "awaiting_extension", "grace_period"];
    const isRoyalFollowUp = consultation.service_tier?.toUpperCase() === "ROYAL"
      && consultation.status === "completed"
      && (!consultation.follow_up_expiry || new Date(consultation.follow_up_expiry) > new Date());

    if (!CALLABLE_STATUSES.includes(consultation.status) && !isRoyalFollowUp) {
      throw new ValidationError(`Video calls are only available for active consultations (current: ${consultation.status})`);
    }

    let roomId: string;

    if (requestRoomId) {
      // Callee path: join the caller's existing room — do NOT create a new one
      roomId = requestRoomId;
      console.log(`[videosdk-token] Callee joining existing room: ${roomId}`);
    } else {
      // Caller path: create a fresh VideoSDK room
      const adminToken = await createVideoSDKToken(VIDEOSDK_API_KEY, VIDEOSDK_SECRET, ["allow_join"]);
      const roomRes = await fetch("https://api.videosdk.live/v2/rooms", {
        method: "POST",
        headers: {
          Authorization: adminToken,
          "Content-Type": "application/json",
        },
      });

      if (!roomRes.ok) {
        const errBody = await roomRes.text().catch(() => "");
        console.error(`[videosdk-token] Room creation failed: ${roomRes.status} ${errBody}`);
        throw new Error(`Failed to create VideoSDK room (${roomRes.status})`);
      }
      const roomData = await roomRes.json();
      roomId = roomData.roomId;

      if (!roomId) {
        console.error("[videosdk-token] Room created but no roomId returned:", roomData);
        throw new Error("VideoSDK room creation returned no roomId");
      }

      // Build notification payload BEFORE DB writes so we can send everything in parallel
      const callerRole = auth.role ?? "patient";
      const callTypeLabel = (call_type ?? "VIDEO") === "AUDIO" ? "Voice" : "Video";
      const notifPayload: Record<string, unknown> = {
        title: `Incoming ${callTypeLabel} Call`,
        body: callerRole === "doctor" ? "Your doctor is calling" : "Your patient is calling",
        type: "VIDEO_CALL_INCOMING",
        consultation_id,
        data: {
          consultation_id,
          room_id: roomId,
          call_type: call_type ?? "VIDEO",
          caller_role: callerRole,
        },
      };
      if (callerRole === "doctor") {
        notifPayload.session_id = consultation.patient_session_id;
      } else {
        notifPayload.user_id = consultation.doctor_id;
      }
      const serviceKey = Deno.env.get("INTERNAL_SERVICE_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
      const supabaseUrl = Deno.env.get("SUPABASE_URL")!;

      // Run DB writes + notification ALL in parallel for minimum latency
      const notifTarget = callerRole === "doctor" ? consultation.patient_session_id : consultation.doctor_id;
      console.log(`[videosdk-token] Sending call notification: role=${callerRole} target=${notifTarget} room=${roomId}`);

      const [, , notifRes] = await Promise.all([
        // 1. Persist room_id on consultation
        supabase.from("consultations")
          .update({ video_room_id: roomId })
          .eq("consultation_id", consultation_id),
        // 2. Record in video_calls table
        supabase.from("video_calls").insert({
          consultation_id,
        }),
        // 3. Send push notification to other party
        fetch(`${supabaseUrl}/functions/v1/send-push-notification`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${serviceKey}`,
            "X-Service-Key": serviceKey,
          },
          body: JSON.stringify(notifPayload),
        }).catch((err) => {
          console.error("[videosdk-token] Notification fetch failed:", err);
          return null;
        }),
      ]);

      if (notifRes) {
        const notifBody = await notifRes.text();
        console.log(`[videosdk-token] Notification response: ${notifRes.status} ${notifBody}`);
      }
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

    await releaseConcurrency();

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
