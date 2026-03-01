// functions/send-push-notification/index.ts
// Sends FCM push notifications to patients or doctors.
// Uses FCM V1 API with service account authentication.
// Rate limit: 20/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole, type AuthResult } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const FCM_PROJECT_ID  = Deno.env.get("FCM_PROJECT_ID")!;
const FCM_CLIENT_EMAIL = Deno.env.get("FCM_CLIENT_EMAIL")!;
const FCM_PRIVATE_KEY  = Deno.env.get("FCM_PRIVATE_KEY")!;
const FCM_V1_ENDPOINT  = `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`;

// ── FCM V1 Auth — generate OAuth2 access token from service account ──────────
async function getFCMAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  const header  = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: FCM_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=/g, "");

  const headerB64  = encode(header);
  const payloadB64 = encode(payload);
  const sigInput   = `${headerB64}.${payloadB64}`;

  // Import the RSA private key
  const pemBody = FCM_PRIVATE_KEY
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\\n/g, "")
    .replace(/\n/g, "")
    .trim();

  const keyData = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyData,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(sigInput)
  );

  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=/g, "");

  const jwt = `${sigInput}.${sigB64}`;

  // Exchange JWT for OAuth2 access token
  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  if (!tokenRes.ok) {
    const errText = await tokenRes.text();
    throw new Error(`Failed to get FCM access token (${tokenRes.status}): ${errText}`);
  }
  const tokenData = await tokenRes.json();
  return tokenData.access_token;
}

// ── Send single FCM message ───────────────────────────────────────────────────
// Medical privacy: push payload contains no patient data.
// Only notification_id and type are sent so the app can fetch
// full content securely from Supabase after wake.
async function sendFCM(
  fcmToken: string,
  notification: PushRequest,
  accessToken: string
): Promise<boolean> {
  const message = {
    message: {
      token: fcmToken,
      data: {
        notification_id: notification.notification_id ?? "",
        type: notification.type,
        user_id: notification.user_id ?? notification.session_id ?? "",
        // Fallback title/body for inline display (used by admin direct pushes)
        title: notification.title,
        body: notification.body,
      },
      android: {
        priority: "high",
        notification: {
          sound: "default",
          channel_id: "esiri_main",
        },
      },
    },
  };

  const res = await fetch(FCM_V1_ENDPOINT, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(message),
  });

  if (!res.ok) {
    const errBody = await res.text();
    console.error(`FCM send failed (${res.status}): ${errBody}`);
  }

  return res.ok;
}

interface PushRequest {
  // Target by session_id (patient) OR user_id (doctor) OR "doctors" broadcast
  session_id?: string;
  user_id?: string;
  target?: "doctors";
  service_type?: string;   // filter doctors by service type if target=doctors
  title: string;
  body: string;
  type: string;            // notification type for client-side routing
  consultation_id?: string;
  notification_id?: string | null; // set after inserting into notifications table
  data?: Record<string, string>; // extra payload
}

function validate(body: unknown): PushRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  if (typeof b.title !== "string" || b.title.trim().length === 0) {
    throw new ValidationError("title is required");
  }
  if (typeof b.body !== "string" || b.body.trim().length === 0) {
    throw new ValidationError("body is required");
  }
  if (typeof b.type !== "string") {
    throw new ValidationError("type is required");
  }
  if (!b.session_id && !b.user_id && b.target !== "doctors") {
    throw new ValidationError("One of session_id, user_id, or target:'doctors' is required");
  }

  return b as unknown as PushRequest;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    let auth: AuthResult;
    const internalKey = req.headers.get("X-Service-Key");
    const serviceRoleKey = Deno.env.get("INTERNAL_SERVICE_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    if (internalKey && internalKey === serviceRoleKey) {
      // Internal call from admin panel — skip JWT auth
      auth = { userId: "service-role", sessionToken: null, sessionId: null, role: "admin" as const, jwt: "" };
    } else {
      auth = await validateAuth(req);
      if (auth.role !== "doctor" && auth.role !== "admin" && auth.role !== "hr") {
        requireRole(auth, "admin");
      }
    }

    await LIMITS.notification(auth.userId ?? auth.sessionId ?? "system");

    const raw = await req.json();
    const notification = validate(raw);
    const supabase = getServiceClient();

    let fcmTokens: string[] = [];

    if (notification.session_id) {
      // Patient notification
      const { data } = await supabase
        .from("patient_sessions")
        .select("fcm_token")
        .eq("session_id", notification.session_id)
        .not("fcm_token", "is", null)
        .single();
      if (data?.fcm_token) fcmTokens = [data.fcm_token];

    } else if (notification.user_id) {
      // Specific user — look up FCM token from fcm_tokens table
      const { data: tokenRow } = await supabase
        .from("fcm_tokens")
        .select("token")
        .eq("user_id", notification.user_id)
        .single();
      if (tokenRow?.token) {
        fcmTokens = [tokenRow.token];
      }

    } else if (notification.target === "doctors") {
      // Broadcast to all available doctors — look up from fcm_tokens joined with doctor_profiles
      const { data: doctors } = await supabase
        .from("doctor_profiles")
        .select("doctor_id")
        .eq("is_verified", true)
        .eq("is_available", true);

      if (doctors && doctors.length > 0) {
        const doctorIds = doctors.map((d) => d.doctor_id);
        const { data: tokens } = await supabase
          .from("fcm_tokens")
          .select("token")
          .in("user_id", doctorIds);
        fcmTokens = (tokens ?? []).map((t) => t.token).filter(Boolean);
      }
    }

    // Determine target user_id for the notification record
    const targetUserId = notification.user_id ?? notification.session_id ?? null;

    // Insert notification record into Supabase (source of truth)
    let notificationId: string | null = null;
    if (targetUserId) {
      const { data: insertedNotif } = await supabase
        .from("notifications")
        .insert({
          user_id: targetUserId,
          title: notification.title,
          body: notification.body,
          type: notification.type,
          data: JSON.stringify(notification.data ?? {}),
        })
        .select("notification_id")
        .single();
      notificationId = insertedNotif?.notification_id ?? null;
    }

    if (fcmTokens.length === 0) {
      return successResponse({ message: "No FCM tokens found, notification stored only", notification_id: notificationId }, 200, origin);
    }

    // Get OAuth2 access token once, reuse for all messages
    const accessToken = await getFCMAccessToken();

    // Send to all tokens (in parallel, max 20)
    // Medical privacy: push payload contains no patient data —
    // only notification_id and type for secure server-side fetch.
    const results = await Promise.allSettled(
      fcmTokens.slice(0, 20).map((token) => sendFCM(token, { ...notification, notification_id: notificationId }, accessToken))
    );

    const sent = results.filter((r) => r.status === "fulfilled" && r.value).length;
    const failed = results.length - sent;

    // Record in notification_history
    await supabase.from("notification_history").insert({
      notification_type: notification.type,
      title: notification.title,
      body: notification.body,
      target_session_id: notification.session_id ?? null,
      target_user_id: notification.user_id ?? null,
      tokens_sent: sent,
      tokens_failed: failed,
      consultation_id: notification.consultation_id ?? null,
      sent_at: new Date().toISOString(),
    });

    await logEvent({
      function_name: "send-push-notification",
      level: "info",
      user_id: auth.userId,
      action: "notification_sent",
      metadata: {
        type: notification.type,
        tokens_sent: sent,
        tokens_failed: failed,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({ sent, failed }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "send-push-notification",
      level: "error",
      action: "notification_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
