// functions/medication-reminder-cron/index.ts
//
// Cron job (every minute): Checks medication timetables for due reminders,
// assigns an available online nurse, creates a VideoSDK room, and pushes
// call notifications to both nurse and patient.
//
// Retry: no-nurse events retry up to 3 times (3 minutes).
// Timeout: nurse_notified events older than 2 min get reassigned (max 2).
// Fallback: after all retries, patient gets a text-only push reminder.
//
// Auth: X-Cron-Secret header (same as appointment-reminder).

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const VIDEOSDK_API_KEY = Deno.env.get("VIDEOSDK_API_KEY")!;
const VIDEOSDK_SECRET  = Deno.env.get("VIDEOSDK_SECRET")!;
const TOKEN_EXPIRY_SECS = 7200;
const MAX_RETRIES = 3;
const MAX_REASSIGNS = 2;
const NURSE_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes

// ── VideoSDK helpers (reused from videosdk-token) ────────────────────────────

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
    // Auth: cron secret
    const cronSecret = req.headers.get("X-Cron-Secret");
    if (!cronSecret || cronSecret !== Deno.env.get("CRON_SECRET")) {
      return new Response("Unauthorized", { status: 401 });
    }

    const supabase = getServiceClient();
    const now = new Date();
    let processed = 0;

    // Current time in EAT (Africa/Nairobi = UTC+3)
    const eat = new Date(now.getTime() + 3 * 60 * 60 * 1000);
    const currentHHMM = eat.toISOString().slice(11, 16); // "HH:MM"
    const currentDate = eat.toISOString().slice(0, 10);   // "YYYY-MM-DD"

    // ── Step 1: Find active timetables with a due scheduled_time ─────────
    const { data: dueTimetables } = await supabase
      .from("medication_timetables")
      .select("timetable_id, patient_session_id, doctor_id, medication_name, dosage, form, scheduled_times")
      .eq("is_active", true)
      .lte("start_date", currentDate)
      .gte("end_date", currentDate)
      .contains("scheduled_times", [currentHHMM]);

    if (dueTimetables && dueTimetables.length > 0) {
      for (const tt of dueTimetables) {
        // INSERT event — ON CONFLICT DO NOTHING prevents duplicates
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

        if (!inserted) continue; // Already exists — skip

        const assigned = await assignNurseAndNotify(
          supabase, inserted.event_id, tt, origin,
        );
        if (assigned) processed++;
      }
    }

    // ── Step 2: Retry events with status='no_nurse' and retry_count < MAX ─
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

        const assigned = await assignNurseAndNotify(
          supabase, ev.event_id,
          { ...tt, timetable_id: ev.timetable_id } as unknown as typeof dueTimetables extends (infer T)[] ? T : never,
          origin,
        );

        // If still no nurse after max retries, send text fallback
        if (!assigned && ev.retry_count + 1 >= MAX_RETRIES) {
          await supabase
            .from("medication_reminder_events")
            .update({ status: "failed" })
            .eq("event_id", ev.event_id);

          // Fallback: text-only push to patient
          try {
            await supabase.functions.invoke("send-push-notification", {
              body: {
                session_id: tt.patient_session_id,
                title: "Medication Reminder",
                body: `Time to take your medication: ${tt.medication_name}${tt.dosage ? ` (${tt.dosage})` : ""}. We couldn't reach a nurse — please remember to take it now.`,
                type: "MEDICATION_REMINDER_PATIENT",
                data: { event_id: ev.event_id },
              },
            });
          } catch (e) {
            console.error("Failed to send fallback patient notification:", e);
          }
          processed++;
        }
      }
    }

    // ── Step 3: Timeout stale nurse_notified events (>2 min) ─────────────
    const timeoutThreshold = new Date(now.getTime() - NURSE_TIMEOUT_MS).toISOString();
    const { data: staleEvents } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id, timetable_id, nurse_id, reassign_count,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .eq("status", "nurse_notified")
      .eq("scheduled_date", currentDate)
      .lt("nurse_notified_at", timeoutThreshold)
      .lt("reassign_count", MAX_REASSIGNS);

    if (staleEvents) {
      for (const ev of staleEvents) {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as Record<string, unknown>;
        const previousNurseId = ev.nurse_id;

        // Reset to pending for reassignment, excluding previous nurse
        await supabase
          .from("medication_reminder_events")
          .update({
            status: "pending",
            nurse_id: null,
            video_room_id: null,
            nurse_notified_at: null,
            reassign_count: ev.reassign_count + 1,
          })
          .eq("event_id", ev.event_id);

        await assignNurseAndNotify(
          supabase, ev.event_id,
          { ...tt, timetable_id: ev.timetable_id } as unknown as typeof dueTimetables extends (infer T)[] ? T : never,
          origin,
          previousNurseId ?? undefined,
        );
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

    return successResponse({ message: "OK", processed }, 200, origin);
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

// ── Assign nurse + notify ────────────────────────────────────────────────────

async function assignNurseAndNotify(
  supabase: ReturnType<typeof getServiceClient>,
  eventId: string,
  timetable: { patient_session_id: string; medication_name: string; dosage?: string; form?: string; timetable_id?: string },
  origin: string | null,
  excludeNurseId?: string,
): Promise<boolean> {
  // Find available nurse
  let nurseQuery = supabase
    .from("doctor_profiles")
    .select("doctor_id")
    .eq("specialty", "nurse")
    .eq("is_verified", true)
    .eq("is_available", true)
    .eq("in_session", false)
    .eq("is_banned", false)
    .limit(1);

  if (excludeNurseId) {
    nurseQuery = nurseQuery.neq("doctor_id", excludeNurseId);
  }

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

  // Create VideoSDK room
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

  // Update event
  await supabase
    .from("medication_reminder_events")
    .update({
      status: "nurse_notified",
      nurse_id: nurseId,
      video_room_id: roomId,
      nurse_notified_at: new Date().toISOString(),
    })
    .eq("event_id", eventId);

  const medLabel = `${timetable.medication_name}${timetable.dosage ? ` (${timetable.dosage})` : ""}`;

  // Push to nurse: incoming call for medication reminder
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        user_id: nurseId,
        title: "Medication Reminder Call",
        body: `Please call patient for medication reminder: ${medLabel}`,
        type: "MEDICATION_REMINDER_CALL",
        data: {
          event_id: eventId,
          room_id: roomId,
          patient_session_id: timetable.patient_session_id,
          medication_name: timetable.medication_name,
          dosage: timetable.dosage ?? "",
        },
      },
    });
  } catch (e) {
    console.error(`[med-reminder] Failed to notify nurse ${nurseId}:`, e);
  }

  // Push to patient: heads-up that nurse will call
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        session_id: timetable.patient_session_id,
        title: "Medication Reminder",
        body: `A nurse will call you shortly to remind you about ${medLabel}.`,
        type: "MEDICATION_REMINDER_PATIENT",
        data: {
          event_id: eventId,
          room_id: roomId,
          medication_name: timetable.medication_name,
        },
      },
    });
    await supabase
      .from("medication_reminder_events")
      .update({ patient_notified: true })
      .eq("event_id", eventId);
  } catch (e) {
    console.error(`[med-reminder] Failed to notify patient:`, e);
  }

  console.log(`[med-reminder] Assigned nurse=${nurseId} room=${roomId} for event=${eventId} med=${medLabel}`);
  return true;
}
