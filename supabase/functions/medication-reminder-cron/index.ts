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
const RING_DURATION_MS = 10 * 60 * 1000;  // 10 min — covers OEM battery-batching delivery delays. The phone's incoming-call overlay still self-dismisses at 60s, but the event row stays acceptable for 10 min so a tray-notification tap minutes later still lands on a live event.
const ACCEPT_TIMEOUT_MS = 5 * 60 * 1000; // 5 min — nurse's window to actually call after accepting

// At-scale tuning. Each per-event task is dominated by 2 HTTP round trips
// (VideoSDK room create + send-push-notification), each ~300-500 ms. With
// CONCURRENCY=20 the cron drains 100 due timetables in ~1-2 batches × ~1 s
// instead of ~100 × ~1 s sequentially.
const CONCURRENCY = 50;

// Future scale (>~7,500 events/min): introduce 4-shard sharding by adding
// `shard_idx` + `shard_count` to req body, filter due-timetable queries by
// `substring(timetable_id::text, 1, 1) IN (<hex bucket>)`, and register 4
// pg_cron jobs each passing a different shard_idx. UUIDs are uniformly
// random so a hex-prefix split partitions evenly. Not done yet — premature
// at current load; revisit when one tick's processed_count breaches ~5k.

/** Limited-parallelism runner. Spawns at most `limit` workers that drain a
 *  shared queue, so we never exceed VideoSDK / FCM concurrency budgets. */
async function runWithConcurrency<T>(
  items: T[],
  limit: number,
  fn: (item: T) => Promise<void>,
): Promise<void> {
  if (items.length === 0) return;
  let i = 0;
  const workers = Array.from(
    { length: Math.min(limit, items.length) },
    async () => {
      while (true) {
        const idx = i++;
        if (idx >= items.length) return;
        try {
          await fn(items[idx]);
        } catch (e) {
          console.error("[med-cron] worker iteration failed:", e);
        }
      }
    },
  );
  await Promise.all(workers);
}

/**
 * Atomic nurse claim. Calls a SECURITY DEFINER Postgres function that
 * picks an eligible nurse with `SELECT ... FOR UPDATE SKIP LOCKED`,
 * verifies they have no active event via NOT EXISTS, and stamps the
 * supplied event row's nurse_id + status='nurse_ringing' all in one
 * transaction. Concurrent edge-function invocations safely fan out across
 * distinct rows — replaces the previous in-memory pool which broke down
 * when pg_cron's tick exceeded 60 s and a second tick fired in parallel.
 */
async function claimNurseAtomic(
  supabase: ReturnType<typeof getServiceClient>,
  eventId: string,
  excludeNurseId?: string,
): Promise<string | null> {
  const { data, error } = await supabase.rpc("claim_nurse_for_med_event", {
    p_event_id: eventId,
    p_exclude_nurse_id: excludeNurseId ?? null,
  });
  if (error) {
    console.error("[med-cron] claim_nurse_for_med_event failed:", error.message);
    return null;
  }
  return (data as string | null) ?? null;
}

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

    // No more in-memory nurse pool — each ringNurse call now does an
    // atomic Postgres claim via FOR UPDATE SKIP LOCKED, safe across any
    // number of overlapping pg_cron invocations.

    if (dueTimetables && dueTimetables.length > 0) {
      let processedHere = 0;
      await runWithConcurrency(dueTimetables, CONCURRENCY, async (tt) => {
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
        if (!inserted) return;
        const ringed = await ringNurse(supabase, inserted.event_id, tt as TimetableRow);
        if (ringed) processedHere++;
      });
      processed += processedHere;
    }

    // ── Step 2: Retry pending events that previously had no nurse ────────
    // Read nurse_id off the event (kept by step 3 as "last attempted") and
    // pass it as exclude so we don't re-ring a nurse who already failed to
    // pick up. With only one nurse on the system, this keeps retry_count
    // climbing (toward MAX_RETRIES → fallback push) without spamming them
    // with fresh rings every minute.
    const { data: retryEvents } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id, timetable_id, retry_count, nurse_id,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .eq("status", "no_nurse")
      .eq("scheduled_date", currentDate)
      .lt("retry_count", MAX_RETRIES);

    if (retryEvents && retryEvents.length > 0) {
      let processedHere = 0;
      await runWithConcurrency(retryEvents, CONCURRENCY, async (ev) => {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as Record<string, unknown>;
        await supabase
          .from("medication_reminder_events")
          .update({ retry_count: ev.retry_count + 1 })
          .eq("event_id", ev.event_id);

        const ringed = await ringNurse(
          supabase,
          ev.event_id,
          { ...tt, timetable_id: ev.timetable_id } as TimetableRow,
          ev.nurse_id ?? undefined,
        );

        if (!ringed && ev.retry_count + 1 >= MAX_RETRIES) {
          await supabase
            .from("medication_reminder_events")
            .update({ status: "failed" })
            .eq("event_id", ev.event_id);
          await sendFallbackPushToPatient(supabase, ev.event_id, tt as TimetableRow);
          processedHere++;
        }
      });
      processed += processedHere;
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

    if (staleRings && staleRings.length > 0) {
      let processedHere = 0;
      await runWithConcurrency(staleRings, CONCURRENCY, async (ev) => {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as Record<string, unknown>;
        const previousNurseId = ev.nurse_id;

        // Keep nurse_id on the row as the "last attempted" marker so step
        // 2's retry path can also exclude this nurse. ringNurse overwrites
        // nurse_id when it picks a fresh nurse, so this is safe.
        await supabase
          .from("medication_reminder_events")
          .update({
            status: "pending",
            video_room_id: null,
            nurse_notified_at: null,
            ring_expires_at: null,
            reassign_count: ev.reassign_count + 1,
          })
          .eq("event_id", ev.event_id);

        await ringNurse(
          supabase,
          ev.event_id,
          { ...tt, timetable_id: ev.timetable_id } as TimetableRow,
          previousNurseId ?? undefined,
        );
        processedHere++;
      });
      processed += processedHere;
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

    if (deadRings && deadRings.length > 0) {
      let processedHere = 0;
      await runWithConcurrency(deadRings, CONCURRENCY, async (ev) => {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as TimetableRow;
        await supabase
          .from("medication_reminder_events")
          .update({ status: "failed" })
          .eq("event_id", ev.event_id);
        await sendFallbackPushToPatient(supabase, ev.event_id, tt);
        processedHere++;
      });
      processed += processedHere;
    }

    // ── Step 5: Accept-timeout sweep ─────────────────────────────────────
    // Nurses have 5 minutes after accepting to actually start the call.
    // If they don't, mark the event failed and push the medicine name
    // straight to the patient. The callback's start_call rejects on any
    // non-nurse_accepted status, so the nurse will see "took too long".
    const acceptCutoff = new Date(now.getTime() - ACCEPT_TIMEOUT_MS).toISOString();
    const { data: stalledAccepts } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .eq("status", "nurse_accepted")
      .lt("nurse_accepted_at", acceptCutoff);

    if (stalledAccepts && stalledAccepts.length > 0) {
      let processedHere = 0;
      await runWithConcurrency(stalledAccepts, CONCURRENCY, async (ev) => {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as TimetableRow;
        await supabase
          .from("medication_reminder_events")
          .update({ status: "failed" })
          .eq("event_id", ev.event_id);
        await sendFallbackPushToPatient(supabase, ev.event_id, tt);
        processedHere++;
      });
      processed += processedHere;
    }

    // ── Step 6: nurse_calling stuck sweep ────────────────────────────────
    // Calls that started but never settled (neither completed nor manual
    // unreachable) get auto-failed after 5 minutes. Without this they'd
    // sit forever blocking the "one nurse one patient" assignment.
    const callingCutoff = new Date(now.getTime() - ACCEPT_TIMEOUT_MS).toISOString();
    const { data: stalledCalls } = await supabase
      .from("medication_reminder_events")
      .select(`
        event_id,
        medication_timetables!inner (
          patient_session_id, medication_name, dosage, form
        )
      `)
      .eq("status", "nurse_calling")
      .lt("call_started_at", callingCutoff);

    if (stalledCalls && stalledCalls.length > 0) {
      let processedHere = 0;
      await runWithConcurrency(stalledCalls, CONCURRENCY, async (ev) => {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as TimetableRow;
        await supabase
          .from("medication_reminder_events")
          .update({ status: "failed" })
          .eq("event_id", ev.event_id);
        await sendFallbackPushToPatient(supabase, ev.event_id, tt);
        processedHere++;
      });
      processed += processedHere;
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
  // Atomic DB-level claim — no in-memory pool. Postgres' FOR UPDATE
  // SKIP LOCKED guarantees no two concurrent invocations claim the same
  // nurse, even when pg_cron's ticks overlap.
  const nurseId = await claimNurseAtomic(supabase, eventId, excludeNurseId);
  if (!nurseId) {
    await supabase
      .from("medication_reminder_events")
      .update({ status: "no_nurse" })
      .eq("event_id", eventId);
    return false;
  }

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

  // claim_nurse_for_med_event already wrote nurse_id + status='nurse_ringing'
  // atomically, so this update only fills in the post-VideoSDK details.
  await supabase
    .from("medication_reminder_events")
    .update({
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
