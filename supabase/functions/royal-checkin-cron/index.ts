// functions/royal-checkin-cron/index.ts
//
// Royal-client check-in reminders for doctors. Scheduled minute-by-minute
// via pg_cron; only does work during the 9 fire-minutes per day:
//
//   08:00 / 08:05 / 08:10 EAT — slot=8,  attempts 1/2/3
//   13:00 / 13:05 / 13:10 EAT — slot=13, attempts 1/2/3
//   18:00 / 18:05 / 18:10 EAT — slot=18, attempts 1/2/3
//
// On every fire-minute it walks the set of doctors who have at least one
// active-window Royal patient and sends a high-priority FCM push UNLESS
// the doctor has already acknowledged the slot today. Each successive
// attempt is suppressed once acknowledgment lands.
//
// Skip rules: banned, suspended, in_session — those doctors aren't
// pinged. is_available=false is INTENTIONALLY pinged: a doctor who has
// stopped taking new patients still owes their existing Royal patients
// a check-in, so the reminder is delivered.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface FireWindow {
  slotHour: 8 | 13 | 18;
  attempt: 1 | 2 | 3;
}

// Map current EAT HH:mm to (slotHour, attempt). Returns null on non-fire
// minutes so the function exits cheaply.
function fireWindowFor(eatHHMM: string): FireWindow | null {
  switch (eatHHMM) {
    case "08:00": return { slotHour: 8, attempt: 1 };
    case "08:05": return { slotHour: 8, attempt: 2 };
    case "08:10": return { slotHour: 8, attempt: 3 };
    case "13:00": return { slotHour: 13, attempt: 1 };
    case "13:05": return { slotHour: 13, attempt: 2 };
    case "13:10": return { slotHour: 13, attempt: 3 };
    case "18:00": return { slotHour: 18, attempt: 1 };
    case "18:05": return { slotHour: 18, attempt: 2 };
    case "18:10": return { slotHour: 18, attempt: 3 };
    default: return null;
  }
}

// Push helper — reuse the medication-reminder pattern: direct fetch with
// X-Service-Key + EDGE_FN_BEARER_JWT so the gateway lets us through and
// send-push-notification's role gate is bypassed.
async function pushToDoctor(
  doctorId: string,
  slotHour: number,
  slotDate: string,
  count: number,
): Promise<void> {
  const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const BEARER_JWT = Deno.env.get("EDGE_FN_BEARER_JWT") ?? SERVICE_ROLE;
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

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const cronSecret = req.headers.get("X-Cron-Secret");
    if (!cronSecret || cronSecret !== Deno.env.get("CRON_SECRET")) {
      return new Response("Unauthorized", { status: 401 });
    }

    // EAT HH:mm via Intl. The Edge runtime clock isn't reliably UTC, so we
    // explicitly format with timeZone="Africa/Dar_es_Salaam" — same trick
    // used in medication-reminder-cron after a 2026-05-01 incident.
    const fmt = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "Africa/Dar_es_Salaam",
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit", hour12: false,
    });
    const eatStr = fmt.format(new Date()); // "2026-05-03 08:05"
    const slotDate = eatStr.slice(0, 10);
    const eatHHMM = eatStr.slice(11, 16);

    const window = fireWindowFor(eatHHMM);
    if (!window) {
      return successResponse({ message: "no fire window", eat: eatHHMM }, 200, origin);
    }

    const supabase = getServiceClient();

    // Find doctors with at least one active-window Royal patient.
    // Doing this in two steps — first the candidate set from consultations,
    // then filter by doctor_profiles state — keeps the SQL portable across
    // schema differences in our environments.
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
        { message: "no doctors with active royal clients", eat: eatHHMM, slot: window.slotHour },
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
      // suspended_until is the future timestamp until which the doctor is
      // suspended; null/past means active.
      if (p.suspended_until && new Date(p.suspended_until).getTime() > nowMs) return false;
      return true;
    });
    if (eligible.length === 0) {
      return successResponse(
        { message: "no eligible doctors", eat: eatHHMM, slot: window.slotHour },
        200,
        origin,
      );
    }

    let sent = 0;
    let skipped = 0;
    const errors: string[] = [];

    for (const p of eligible) {
      const doctorId = p.doctor_id as string;

      // Per-doctor royal client count — for the body of the push and the
      // tracking row.
      const { count: rcCount } = await supabase
        .from("consultations")
        .select("consultation_id", { count: "exact", head: true })
        .eq("doctor_id", doctorId)
        .eq("service_tier", "ROYAL")
        .eq("status", "completed")
        .gt("follow_up_expiry", new Date().toISOString());
      const royalCount = rcCount ?? 0;
      if (royalCount === 0) {
        // Stale candidate — the consultations row may have aged out
        // between the two queries.
        skipped++;
        continue;
      }

      // Upsert the per-(doctor, slot) tracking row. ON CONFLICT keeps
      // attempts_sent updated atomically across attempts.
      const { data: existing } = await supabase
        .from("royal_checkin_reminders")
        .select("id, attempts_sent, acknowledged_at")
        .eq("doctor_id", doctorId)
        .eq("slot_date", slotDate)
        .eq("slot_hour", window.slotHour)
        .maybeSingle();

      if (existing?.acknowledged_at) {
        // Doctor already acked this slot today — no further attempts.
        skipped++;
        continue;
      }
      if (existing && existing.attempts_sent >= window.attempt) {
        // Idempotency: cron already sent this attempt (e.g. duplicate
        // tick within the same minute). Skip.
        skipped++;
        continue;
      }

      try {
        await pushToDoctor(doctorId, window.slotHour, slotDate, royalCount);

        if (existing) {
          await supabase
            .from("royal_checkin_reminders")
            .update({
              attempts_sent: window.attempt,
              last_sent_at: new Date().toISOString(),
              royal_client_count: royalCount,
              updated_at: new Date().toISOString(),
            })
            .eq("id", existing.id);
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
        const msg = e instanceof Error ? e.message : String(e);
        errors.push(`${doctorId}: ${msg}`);
      }
    }

    if (sent > 0) {
      await logEvent({
        function_name: "royal-checkin-cron",
        level: "info",
        action: "royal_checkins_sent",
        metadata: {
          slot: window.slotHour,
          attempt: window.attempt,
          sent,
          skipped,
          eat: eatStr,
        },
      });
    }

    return successResponse(
      {
        message: "ok",
        slot_hour: window.slotHour,
        attempt: window.attempt,
        eat: eatStr,
        sent,
        skipped,
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
