// functions/medication-reminder-cron/index.ts
//
// Cron job (every minute). Two-stage flow (2026-05-01 redesign):
//
//   1. Find due timetables → create medication_reminder_events (status=pending).
//   2. Ring an online nurse with a 60s window:
//        nurse_id set, video_room_id created, ring_expires_at = now + 60s,
//        status = 'nurse_ringing'. Push goes to the nurse only.
//      The patient does NOT get a heads-up push here — they only get a real
//      VideoSDK incoming call AFTER the nurse explicitly accepts and taps
//      Call from their Medical Reminder list (handled in the callback fn).
//   3. Stale ring (status=nurse_ringing AND ring_expires_at < now): reassign
//      to a different nurse, up to MAX_REASSIGNS times.
//   4. After MAX_REASSIGNS exhausted with no acceptance, fall through to
//      no_nurse retry. After MAX_RETRIES no_nurse cycles, send the patient a
//      plain text fallback push that NAMES the medicine ("take Amoxicillin
//      500mg now").
//
// Auth: X-Cron-Secret header.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VIDEOSDK_API_KEY = Deno.env.get("VIDEOSDK_API_KEY")!;
const VIDEOSDK_SECRET  = Deno.env.get("VIDEOSDK_SECRET")!;
const TOKEN_EXPIRY_SECS = 7200;
const MAX_RETRIES = 3;          // pending → nurse-search retries (no nurse online)
const MAX_REASSIGNS = 3;        // nurse declined / didn't pick up the ring
const RING_DURATION_MS = 60 * 1000;  // 60s — nurse's window to accept

// ── VideoSDK helpers ─────────────────────────────────────────────────────────

async function createVideoSDKToken(
  apiKey: string, secret: string, permissions: string[]
): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const payload: Record<string, unknown> = {
    apikey: apiKey, permissions, iat: now, exp: now + TOKEN_EXPIRY_SECS, version: 2,
  };
  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
  const headerB64 = encode(header);
  const payloadB64 = encode(payload);
  const sigInput = `${headerB64}.${payloadB64}`;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(sigInput));
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
  return `${sigInput}.${sigB64}`;
}

async function createRoom(): Promise<string> {
  const adminToken = await createVideoSDKToken(VIDEOSDK_API_KEY, VIDEOSDK_SECRET, ["allow_join"]);
  const res = await fetch("https://api.videosdk.live/v2/rooms", {
    method: "POST",
    headers: { Authorization: adminToken, "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`VideoSDK room creation failed: ${res.status}`);
  const data = await res.json();
  if (!data.roomId) throw new Error("No roomId returned");
  return data.roomId;
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const cronSecret = req.headers.get("X-Cron-Secret");
    if (!cronSecret || cronSecret !== Deno.env.get("CRON_SECRET")) {
      return new Response("Unauthorized", { status: 401 });
    }

    const supabase = getServiceClient();
    const now = new Date();
    let processed = 0;

    // EAT (Africa/Dar_es_Salaam) — matches what doctors set. We use Intl with
    // an explicit timeZone so the conversion is correct regardless of what
    // the Edge runtime's local clock is set to. (Manually adding 3h to
    // now.getTime() and toISOString-ing produced "23:38" when EAT was
    // actually "20:38" because the runtime wasn't on UTC.)
    const fmt = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "Africa/Dar_es_Salaam",
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit", hour12: false,
    });
    const eatStr = fmt.format(now); // "2026-05-01 20:38"
    const currentDate = eatStr.slice(0, 10);
    const currentHHMM = eatStr.slice(11, 16);

    // ── Step 1: Find due timetables and create events ───────────────────
    const { data: dueTimetables } = await supabase
      .from("medication_timetables")
      .select("timetable_id, patient_session_id, doctor_id, medication_name, dosage, form, scheduled_times")
      .eq("is_active", true)
      .lte("start_date", currentDate)
      .gte("end_date", currentDate)
      .contains("scheduled_times", [currentHHMM]);

    console.log(`[med-cron-debug] currentHHMM=${currentHHMM} currentDate=${currentDate} due=${dueTimetables?.length ?? 0}`);

    if (dueTimetables && dueTimetables.length > 0) {
      for (const tt of dueTimetables) {
        const { data: inserted } = await supabase
          .from("medication_reminder_events")
          .insert({
            timetable_id: tt.timetable_id,
            scheduled_date: currentDate,
            scheduled_time: currentHHMM,
            status: "pending",
          })
          .select("event_id")
          .maybeSingle();

        if (!inserted) continue;

        const ringed = await ringNurse(supabase, inserted.event_id, tt);
        if (ringed) processed++;
      }
    }

    // ── Step 2: Retry pending events that previously had no nurse ────────
    const { data: retryEvents } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id, timetable_id, retry_count,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .eq("status", "no_nurse")
      .eq("scheduled_date", currentDate)
      .lt("retry_count", MAX_RETRIES);

    if (retryEvents) {
      for (const ev of retryEvents) {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as Record<string, unknown>;
        await supabase
          .from("medication_reminder_events")
          .update({ retry_count: ev.retry_count + 1 })
          .eq("event_id", ev.event_id);

        const ringed = await ringNurse(
          supabase, ev.event_id,
          { ...tt, timetable_id: ev.timetable_id } as TimetableRow,
        );

        // After exhausting nurse retries, fallback push to patient — naming
        // the medicine so they know what they were supposed to take.
        if (!ringed && ev.retry_count + 1 >= MAX_RETRIES) {
          await supabase
            .from("medication_reminder_events")
            .update({ status: "failed" })
            .eq("event_id", ev.event_id);

          await sendFallbackPushToPatient(supabase, ev.event_id, tt as TimetableRow);
          processed++;
        }
      }
    }

    // ── Step 3: Reassign ringing events whose 60s window has elapsed ─────
    // (Nurse didn't accept or decline within the window.)
    const ringExpired = new Date(now.getTime()).toISOString();
    const { data: staleRings } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id, timetable_id, nurse_id, reassign_count,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .in("status", ["nurse_ringing", "nurse_notified"])  // legacy state included
      .eq("scheduled_date", currentDate)
      .lt("ring_expires_at", ringExpired)
      .lt("reassign_count", MAX_REASSIGNS);

    if (staleRings) {
      for (const ev of staleRings) {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as Record<string, unknown>;
        const previousNurseId = ev.nurse_id;

        await supabase
          .from("medication_reminder_events")
          .update({
            status: "pending",
            nurse_id: null,
            video_room_id: null,
            nurse_notified_at: null,
            ring_expires_at: null,
            reassign_count: ev.reassign_count + 1,
          })
          .eq("event_id", ev.event_id);

        await ringNurse(
          supabase, ev.event_id,
          { ...tt, timetable_id: ev.timetable_id } as TimetableRow,
          previousNurseId ?? undefined,
        );
        processed++;
      }
    }

    // ── Step 4: Hard-fail rings that exceeded reassign budget ───────────
    const { data: deadRings } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .in("status", ["nurse_ringing", "nurse_notified"])
      .eq("scheduled_date", currentDate)
      .lt("ring_expires_at", ringExpired)
      .gte("reassign_count", MAX_REASSIGNS);

    if (deadRings) {
      for (const ev of deadRings) {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as TimetableRow;
        await supabase
          .from("medication_reminder_events")
          .update({ status: "failed" })
          .eq("event_id", ev.event_id);
        await sendFallbackPushToPatient(supabase, ev.event_id, tt);
        processed++;
      }
    }

    if (processed > 0) {
      await logEvent({
        function_name: "medication-reminder-cron",
        level: "info",
        action: "reminders_processed",
        metadata: { total: processed, time: currentHHMM, date: currentDate },
      });
    }

    return successResponse({
      message: "OK",
      processed,
      debug: {
        currentHHMM,
        currentDate,
        dueCount: dueTimetables?.length ?? 0,
        runtime_now_iso: new Date().toISOString(),
        runtime_now_ms: Date.now(),
        runtime_tz_env: Deno.env.get("TZ") ?? "(unset)",
        eat_via_intl: eatStr,
        pushErrors,
        pushDebug,
      },
    }, 200, origin);
  } catch (err) {
    await logEvent({
      function_name: "medication-reminder-cron",
      level: "error",
      action: "cron_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});

/**
 * Invokes send-push-notification with the explicit X-Service-Key header so
 * the push function's role-check (which rejects service_role JWTs) gets
 * bypassed. The default supabase.functions.invoke() forwards only the JWT
 * → push function rejects → try/catch swallows the error → event stays in
 * nurse_ringing but no FCM is actually sent.
 */
async function invokeSendPush(args: { body: Record<string, unknown> }): Promise<void> {
  const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
  // SUPABASE_SERVICE_ROLE_KEY is now the new sb_secret_… format (not a JWT).
  // The Edge Functions gateway only accepts JWTs in Authorization, so we
  // store the legacy service-role JWT as a custom secret EDGE_FN_BEARER_JWT
  // and use it just for the gateway pass. X-Service-Key still compares
  // against SERVICE_ROLE in the receiving function (which is also the new
  // sb_secret format on its end), so that bypass continues to work.
  const BEARER_JWT = Deno.env.get("EDGE_FN_BEARER_JWT")!;
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const res = await fetch(`${SUPABASE_URL}/functions/v1/send-push-notification`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${BEARER_JWT}`,
      "X-Service-Key": SERVICE_ROLE,
    },
    body: JSON.stringify(args.body),
  });
  const text = await res.text().catch(() => "");
  if (!res.ok) {
    throw new Error(`send-push-notification ${res.status}: ${text}`);
  }
  // Log successful response so we can see sent/failed counts in cron debug.
  pushDebug.push(`OK ${res.status}: ${text.slice(0, 120)}`);
}

// ── Ring nurse (sets state to nurse_ringing, pushes nurse, NO patient push) ─

interface TimetableRow {
  patient_session_id: string;
  medication_name: string;
  dosage?: string | null;
  form?: string | null;
  timetable_id?: string;
}

// Captures push errors / debug across the cron tick so we can surface in the
// response and stop staring at silent try/catch swallows.
const pushErrors: string[] = [];
const pushDebug: string[] = [];

async function ringNurse(
  supabase: ReturnType<typeof getServiceClient>,
  eventId: string,
  timetable: TimetableRow,
  excludeNurseId?: string,
): Promise<boolean> {
  // Find an online nurse not currently in a session.
  let nurseQuery = supabase
    .from("doctor_profiles")
    .select("doctor_id")
    .eq("specialty", "nurse")
    .eq("is_verified", true)
    .eq("is_available", true)
    .eq("in_session", false)
    .eq("is_banned", false)
    .limit(1);

  if (excludeNurseId) nurseQuery = nurseQuery.neq("doctor_id", excludeNurseId);

  const { data: nurses } = await nurseQuery;

  if (!nurses || nurses.length === 0) {
    await supabase
      .from("medication_reminder_events")
      .update({ status: "no_nurse" })
      .eq("event_id", eventId);
    console.warn(`[med-reminder] No nurse available for event ${eventId}`);
    return false;
  }

  const nurseId = nurses[0].doctor_id;

  // Pre-create the room so the nurse-side accept-then-call has it ready.
  let roomId: string;
  try {
    roomId = await createRoom();
  } catch (e) {
    console.error(`[med-reminder] Failed to create room for event ${eventId}:`, e);
    await supabase
      .from("medication_reminder_events")
      .update({ status: "no_nurse" })
      .eq("event_id", eventId);
    return false;
  }

  const ringExpiresAt = new Date(Date.now() + RING_DURATION_MS).toISOString();
  const notifiedAt = new Date().toISOString();

  await supabase
    .from("medication_reminder_events")
    .update({
      status: "nurse_ringing",
      nurse_id: nurseId,
      video_room_id: roomId,
      nurse_notified_at: notifiedAt,
      ring_expires_at: ringExpiresAt,
    })
    .eq("event_id", eventId);

  const medLabel = `${timetable.medication_name}${timetable.dosage ? ` (${timetable.dosage})` : ""}`;

  // Push to nurse only. The body is the tagged invitation per spec.
  try {
    await invokeSendPush({
      body: {
        user_id: nurseId,
        title: "Medication Reminder",
        body: `We would like if you would help to remind a patient to take their medicines: ${medLabel}`,
        type: "MEDICATION_REMINDER_RING",
        data: {
          event_id: eventId,
          room_id: roomId,
          patient_session_id: timetable.patient_session_id,
          medication_name: timetable.medication_name,
          dosage: timetable.dosage ?? "",
          ring_expires_at: ringExpiresAt,
        },
      },
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error(`[med-reminder] Failed to ring nurse ${nurseId}:`, msg);
    pushErrors.push(`ring nurse ${nurseId}: ${msg}`);
  }

  console.log(`[med-reminder] Ringing nurse=${nurseId} room=${roomId} event=${eventId} med=${medLabel}`);
  return true;
}

// ── Fallback push to patient when no nurse comes through ───────────────────

async function sendFallbackPushToPatient(
  supabase: ReturnType<typeof getServiceClient>,
  eventId: string,
  tt: TimetableRow,
): Promise<void> {
  const medLabel = `${tt.medication_name}${tt.dosage ? ` (${tt.dosage})` : ""}`;
  try {
    await invokeSendPush({
      body: {
        session_id: tt.patient_session_id,
        title: "Medication Reminder",
        body: `You are reminded to take these medicines: ${medLabel}.`,
        type: "MEDICATION_REMINDER_PATIENT",
        data: { event_id: eventId, medication_name: tt.medication_name },
      },
    });
  } catch (e) {
    console.error("[med-reminder] Fallback push failed:", e);
  }
}
