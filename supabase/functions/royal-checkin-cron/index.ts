// functions/royal-checkin-cron/index.ts
//
// Royal-client check-in reminders for doctors. Scheduled minute-by-minute
// via pg_cron; only does work during fire-minutes per day:
//
//   08:00 / 08:05 / 08:10 EAT — slot=8,  attempts 1/2/3 → push doctor
//   08:15                    EAT — slot=8,  escalation    → ring CO
//   13:00 / 13:05 / 13:10 EAT — slot=13, attempts 1/2/3 → push doctor
//   13:15                    EAT — slot=13, escalation    → ring CO
//   18:00 / 18:05 / 18:10 EAT — slot=18, attempts 1/2/3 → push doctor
//   18:15                    EAT — slot=18, escalation    → ring CO
//
// On every tick (regardless of fire window), the cron also sweeps stale CO
// rings — rings that have been sitting in `co_ringing` past their
// `ring_expires_at` — and reassigns them to a different CO, up to
// MAX_REASSIGNS times. After the budget is exhausted the escalation is
// marked failed (the doctor still gets warned later only if a CO actually
// completes a check-in, per spec).
//
// Skip rules for doctor pings: banned, suspended, in_session — those
// doctors aren't pinged. is_available=false is INTENTIONALLY pinged: a
// doctor who has stopped taking new patients still owes their existing
// Royal patients a check-in.
//
// Skip rules for CO ring: banned, suspended, in_session — only fully-active
// COs get rung. is_available=false is also skipped here (a CO who has
// stopped accepting work shouldn't be auto-assigned to cover for a doctor).

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const RING_DURATION_MS = 60 * 1000;
const MAX_REASSIGNS = 3;
const CONCURRENCY = 50;

/** Limited-parallelism runner. Same shape as in medication-reminder-cron;
 *  drains an item queue with at most `limit` workers in flight so the cron
 *  scales cleanly to hundreds of doctors / patients per slot. */
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
          console.error("[royal-cron] worker iteration failed:", e);
        }
      }
    },
  );
  await Promise.all(workers);
}

interface ReminderWindow {
  kind: "reminder";
  slotHour: 8 | 13 | 18;
  attempt: 1 | 2 | 3;
}
interface EscalationWindow {
  kind: "escalation";
  slotHour: 8 | 13 | 18;
}
type FireWindow = ReminderWindow | EscalationWindow;

function fireWindowFor(eatHHMM: string): FireWindow | null {
  switch (eatHHMM) {
    case "08:00": return { kind: "reminder", slotHour: 8,  attempt: 1 };
    case "08:05": return { kind: "reminder", slotHour: 8,  attempt: 2 };
    case "08:10": return { kind: "reminder", slotHour: 8,  attempt: 3 };
    case "08:15": return { kind: "escalation", slotHour: 8 };
    case "13:00": return { kind: "reminder", slotHour: 13, attempt: 1 };
    case "13:05": return { kind: "reminder", slotHour: 13, attempt: 2 };
    case "13:10": return { kind: "reminder", slotHour: 13, attempt: 3 };
    case "13:15": return { kind: "escalation", slotHour: 13 };
    case "18:00": return { kind: "reminder", slotHour: 18, attempt: 1 };
    case "18:05": return { kind: "reminder", slotHour: 18, attempt: 2 };
    case "18:10": return { kind: "reminder", slotHour: 18, attempt: 3 };
    case "18:15": return { kind: "escalation", slotHour: 18 };
    default: return null;
  }
}

const SUPABASE_URL  = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE  = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const BEARER_JWT    = Deno.env.get("EDGE_FN_BEARER_JWT") ?? SERVICE_ROLE;

// ── Push helpers ─────────────────────────────────────────────────────────────

async function pushToDoctor(
  doctorId: string,
  slotHour: number,
  slotDate: string,
  count: number,
): Promise<void> {
  const slotLabel = `${String(slotHour).padStart(2, "0")}:00`;
  await fetch(`${SUPABASE_URL}/functions/v1/send-push-notification`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${BEARER_JWT}`,
      "X-Service-Key": SERVICE_ROLE,
    },
    body: JSON.stringify({
      user_id: doctorId,
      title: "Royal client check-in",
      body: count === 1
        ? "1 Royal client is waiting on your check-in."
        : `${count} Royal clients are waiting on your check-in.`,
      type: "ROYAL_CHECKIN",
      data: {
        slot_date: slotDate,
        slot_hour: String(slotHour),
        slot_label: slotLabel,
        royal_client_count: String(count),
      },
    }),
  });
}

async function pushRingToCO(
  coId: string,
  doctorName: string,
  slotHour: number,
  slotDate: string,
  escalationId: string,
  ringExpiresAt: string,
): Promise<void> {
  const slotLabel = `${String(slotHour).padStart(2, "0")}:00`;
  await fetch(`${SUPABASE_URL}/functions/v1/send-push-notification`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${BEARER_JWT}`,
      "X-Service-Key": SERVICE_ROLE,
    },
    body: JSON.stringify({
      user_id: coId,
      title: "Royal check-in coverage",
      body: `Help cover Dr ${doctorName}'s ${slotLabel} Royal check-in for one client.`,
      type: "ROYAL_CHECKIN_ESCALATION_RING",
      data: {
        escalation_id: escalationId,
        slot_date: slotDate,
        slot_hour: String(slotHour),
        slot_label: slotLabel,
        ring_expires_at: ringExpiresAt,
      },
    }),
  });
}

// ── CO selection ─────────────────────────────────────────────────────────────

type SbClient = ReturnType<typeof getServiceClient>;

/**
 * Atomic CO claim — same pattern as the medication-reminder cron's nurse
 * claim. Calls a Postgres function that picks an eligible CO with
 * FOR UPDATE SKIP LOCKED, verifies they have no active escalation, and
 * stamps the supplied escalation row's co_id + status='co_ringing' all
 * in one transaction. Replaces the in-memory pool which broke when
 * pg_cron ticks overlapped under load.
 */
async function claimCOAtomic(
  supabase: SbClient,
  escalationId: string,
  excludeCoId?: string,
): Promise<string | null> {
  const { data, error } = await supabase.rpc("claim_co_for_royal_escalation", {
    p_escalation_id: escalationId,
    p_exclude_co_id: excludeCoId ?? null,
  });
  if (error) {
    console.error("[royal-cron] claim_co_for_royal_escalation failed:", error.message);
    return null;
  }
  return (data as string | null) ?? null;
}

async function getDoctorDisplayName(supabase: SbClient, doctorId: string): Promise<string> {
  const { data } = await supabase
    .from("doctor_profiles")
    .select("full_name")
    .eq("doctor_id", doctorId)
    .maybeSingle();
  const name = (data?.full_name as string | null)?.trim();
  if (!name) return "your colleague";
  // Strip leading "Dr " if present so the push body reads "Dr Gem", not "Dr Dr Gem".
  return name.replace(/^Dr\s+/i, "");
}

async function ringCO(
  supabase: SbClient,
  escalationId: string,
  doctorId: string,
  slotHour: number,
  slotDate: string,
  excludeCoId?: string,
): Promise<{ ok: boolean; co_id?: string }> {
  const coId = await claimCOAtomic(supabase, escalationId, excludeCoId);
  if (!coId) {
    await supabase
      .from("royal_checkin_escalations")
      .update({ status: "pending", co_id: null, ring_expires_at: null, co_notified_at: null })
      .eq("escalation_id", escalationId);
    return { ok: false };
  }

  // claim_co_for_royal_escalation already wrote co_id + status='co_ringing'
  // atomically; this update only fills in ring window + notified timestamp.
  const ringExpiresAt = new Date(Date.now() + RING_DURATION_MS).toISOString();
  const notifiedAt = new Date().toISOString();
  await supabase
    .from("royal_checkin_escalations")
    .update({
      ring_expires_at: ringExpiresAt,
      co_notified_at: notifiedAt,
    })
    .eq("escalation_id", escalationId);

  const doctorName = await getDoctorDisplayName(supabase, doctorId);
  try {
    await pushRingToCO(coId, doctorName, slotHour, slotDate, escalationId, ringExpiresAt);
  } catch (e) {
    console.error(`[royal-cron] Ring push to CO ${coId} failed:`, e);
  }
  return { ok: true, co_id: coId };
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

    const fmt = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "Africa/Dar_es_Salaam",
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit", hour12: false,
    });
    const eatStr = fmt.format(new Date());
    const slotDate = eatStr.slice(0, 10);
    const eatHHMM = eatStr.slice(11, 16);

    const supabase = getServiceClient();

    // No more in-memory CO pool — each ringCO call atomically claims via
    // FOR UPDATE SKIP LOCKED, safe across overlapping pg_cron invocations.

    // ── Phase A: Stale-ring sweep (always) ───────────────────────────────────
    // Reassign rings whose 60s window has elapsed without an accept/decline.
    let reassigned = 0;
    let escalationsFailed = 0;
    {
      const nowIso = new Date().toISOString();
      const { data: stale } = await supabase
        .from("royal_checkin_escalations")
        .select("escalation_id, doctor_id, co_id, slot_hour, slot_date, reassign_count, status")
        .eq("status", "co_ringing")
        .lt("ring_expires_at", nowIso);

      let reassignedHere = 0;
      let failedHere = 0;
      await runWithConcurrency(stale ?? [], CONCURRENCY, async (row) => {
        const escalationId = row.escalation_id as string;
        const previousCoId = row.co_id as string | null;
        const reassignCount = (row.reassign_count as number) ?? 0;

        if (reassignCount >= MAX_REASSIGNS) {
          await supabase
            .from("royal_checkin_escalations")
            .update({ status: "failed" })
            .eq("escalation_id", escalationId);
          failedHere++;
          return;
        }

        await supabase
          .from("royal_checkin_escalations")
          .update({
            reassign_count: reassignCount + 1,
            status: "pending",
            co_id: null,
            ring_expires_at: null,
            co_notified_at: null,
          })
          .eq("escalation_id", escalationId);

        const result = await ringCO(
          supabase,
          escalationId,
          row.doctor_id as string,
          row.slot_hour as number,
          row.slot_date as string,
          previousCoId ?? undefined,
        );
        if (result.ok) reassignedHere++;
      });
      reassigned += reassignedHere;
      escalationsFailed += failedHere;
    }

    const window = fireWindowFor(eatHHMM);
    if (!window) {
      return successResponse(
        { message: "no fire window", eat: eatHHMM, reassigned, escalations_failed: escalationsFailed },
        200,
        origin,
      );
    }

    // Doctors with at least one active-window Royal patient.
    const { data: rawDoctors } = await supabase
      .from("consultations")
      .select("doctor_id")
      .eq("service_tier", "ROYAL")
      .eq("status", "completed")
      .gt("follow_up_expiry", new Date().toISOString());

    const candidateIds = Array.from(
      new Set((rawDoctors ?? []).map((r) => r.doctor_id as string).filter(Boolean)),
    );
    if (candidateIds.length === 0) {
      return successResponse(
        { message: "no doctors with active royal clients", eat: eatHHMM, slot: window.slotHour, reassigned },
        200,
        origin,
      );
    }

    const { data: profiles } = await supabase
      .from("doctor_profiles")
      .select("doctor_id, is_banned, suspended_until, in_session")
      .in("doctor_id", candidateIds);

    const nowMs = Date.now();
    const eligible = (profiles ?? []).filter((p) => {
      if (p.is_banned) return false;
      if (p.in_session) return false;
      if (p.suspended_until && new Date(p.suspended_until).getTime() > nowMs) return false;
      return true;
    });
    if (eligible.length === 0) {
      return successResponse(
        { message: "no eligible doctors", eat: eatHHMM, slot: window.slotHour, reassigned },
        200,
        origin,
      );
    }

    let sent = 0;
    let skipped = 0;
    const errors: string[] = [];

    await runWithConcurrency(eligible, CONCURRENCY, async (p) => {
      const doctorId = p.doctor_id as string;

      const { count: rcCount } = await supabase
        .from("consultations")
        .select("consultation_id", { count: "exact", head: true })
        .eq("doctor_id", doctorId)
        .eq("service_tier", "ROYAL")
        .eq("status", "completed")
        .gt("follow_up_expiry", new Date().toISOString());
      const royalCount = rcCount ?? 0;
      if (royalCount === 0) {
        skipped++;
        return;
      }

      // Per-(doctor, slot) reminder row — required for both reminder and
      // escalation paths (escalation references reminder_id).
      const { data: reminder } = await supabase
        .from("royal_checkin_reminders")
        .select("id, attempts_sent, acknowledged_at")
        .eq("doctor_id", doctorId)
        .eq("slot_date", slotDate)
        .eq("slot_hour", window.slotHour)
        .maybeSingle();

      if (reminder?.acknowledged_at) {
        skipped++;
        return;
      }

      // ── Reminder phase: push the doctor, record attempt ─────────────────
      if (window.kind === "reminder") {
        if (reminder && (reminder.attempts_sent as number) >= window.attempt) {
          skipped++;
          return;
        }
        try {
          await pushToDoctor(doctorId, window.slotHour, slotDate, royalCount);
          if (reminder) {
            await supabase
              .from("royal_checkin_reminders")
              .update({
                attempts_sent: window.attempt,
                last_sent_at: new Date().toISOString(),
                royal_client_count: royalCount,
                updated_at: new Date().toISOString(),
              })
              .eq("id", reminder.id);
          } else {
            await supabase.from("royal_checkin_reminders").insert({
              doctor_id: doctorId,
              slot_date: slotDate,
              slot_hour: window.slotHour,
              attempts_sent: window.attempt,
              last_sent_at: new Date().toISOString(),
              royal_client_count: royalCount,
            });
          }
          sent++;
        } catch (e) {
          errors.push(`${doctorId}: ${e instanceof Error ? e.message : String(e)}`);
        }
        return;
      }

      // ── Escalation phase ────────────────────────────────────────────────
      // Only escalate slots that exhausted all 3 attempts and weren't acked.
      if (!reminder || (reminder.attempts_sent as number) < 3) {
        skipped++;
        return;
      }

      // One escalation per Royal patient. Look up the doctor's active
      // patients and skip ones that already have an escalation row for this
      // slot — keeps the cron idempotent across ticks.
      const { data: royalRows } = await supabase
        .from("consultations")
        .select("consultation_id, patient_session_id")
        .eq("doctor_id", doctorId)
        .eq("service_tier", "ROYAL")
        .eq("status", "completed")
        .gt("follow_up_expiry", new Date().toISOString());

      // Dedup by patient_session_id — a patient may have multiple active
      // Royal consultations with the same doctor; we only need to call them
      // once per slot.
      const patientMap = new Map<string, string>();
      for (const r of royalRows ?? []) {
        const psid = r.patient_session_id as string | null;
        const cid = r.consultation_id as string | null;
        if (psid && cid && !patientMap.has(psid)) patientMap.set(psid, cid);
      }
      if (patientMap.size === 0) {
        skipped++;
        return;
      }

      const { data: existingEscs } = await supabase
        .from("royal_checkin_escalations")
        .select("patient_session_id")
        .eq("doctor_id", doctorId)
        .eq("slot_date", slotDate)
        .eq("slot_hour", window.slotHour);
      const alreadyEscalated = new Set(
        (existingEscs ?? []).map((r) => r.patient_session_id as string),
      );

      const todo: Array<{ patientSessionId: string; consultationId: string }> = [];
      for (const [patientSessionId, consultationId] of patientMap) {
        if (alreadyEscalated.has(patientSessionId)) continue;
        todo.push({ patientSessionId, consultationId });
      }

      // Per-patient escalation creation runs in parallel within this
      // doctor's bucket (caps via the outer per-doctor concurrency).
      await runWithConcurrency(todo, CONCURRENCY, async ({ patientSessionId, consultationId }) => {
        try {
          const { data: created, error: insErr } = await supabase
            .from("royal_checkin_escalations")
            .insert({
              reminder_id: reminder!.id,
              doctor_id: doctorId,
              slot_date: slotDate,
              slot_hour: window.slotHour,
              patient_session_id: patientSessionId,
              consultation_id: consultationId,
              status: "pending",
            })
            .select("escalation_id")
            .single();
          if (insErr || !created) {
            errors.push(`${doctorId}/${patientSessionId}: escalation insert ${insErr?.message ?? "unknown"}`);
            return;
          }

          const result = await ringCO(
            supabase,
            created.escalation_id,
            doctorId,
            window.slotHour,
            slotDate,
          );
          if (result.ok) sent++;
          else skipped++;
        } catch (e) {
          errors.push(`${doctorId}/${patientSessionId}: ${e instanceof Error ? e.message : String(e)}`);
        }
      });
    });

    if (sent > 0) {
      await logEvent({
        function_name: "royal-checkin-cron",
        level: "info",
        action: window.kind === "reminder" ? "royal_checkins_sent" : "royal_escalations_rung",
        metadata: {
          slot: window.slotHour,
          attempt: window.kind === "reminder" ? window.attempt : null,
          sent,
          skipped,
          reassigned,
          eat: eatStr,
        },
      });
    }

    return successResponse(
      {
        message: "ok",
        kind: window.kind,
        slot_hour: window.slotHour,
        attempt: window.kind === "reminder" ? window.attempt : null,
        eat: eatStr,
        sent,
        skipped,
        reassigned,
        escalations_failed: escalationsFailed,
        errors,
      },
      200,
      origin,
    );
  } catch (err) {
    await logEvent({
      function_name: "royal-checkin-cron",
      level: "error",
      action: "cron_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
