// functions/acknowledge-royal-checkin/index.ts
//
// Doctor taps "Open Royal Clients" on the check-in notification → client
// fires this endpoint to mark the slot acknowledged. Suppresses any
// remaining attempts for that slot today.
//
// Idempotent: subsequent calls (e.g. user taps the notification, then the
// snooze attempt for the same slot lands and they tap that one too) are
// no-ops on already-acknowledged rows.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface AcknowledgeBody {
  slot_date: string;   // "YYYY-MM-DD" in EAT
  slot_hour: number;   // 8 | 13 | 18
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    if (!auth.userId || auth.role !== "doctor") {
      throw new ValidationError("Only doctors can acknowledge Royal check-ins");
    }

    const raw = await req.json();
    const body = raw as AcknowledgeBody;
    if (typeof body.slot_date !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(body.slot_date)) {
      throw new ValidationError("slot_date must be YYYY-MM-DD");
    }
    if (![8, 13, 18].includes(body.slot_hour)) {
      throw new ValidationError("slot_hour must be 8, 13, or 18");
    }

    const supabase = getServiceClient();

    // Upsert pattern: if the row exists, mark acknowledged; if it doesn't
    // (rare — the doctor could have tapped a stale notification before
    // the cron created the row), insert the row already-acknowledged so
    // the next attempt no-ops.
    const nowIso = new Date().toISOString();
    const { data: existing } = await supabase
      .from("royal_checkin_reminders")
      .select("id, acknowledged_at, attempts_sent")
      .eq("doctor_id", auth.userId)
      .eq("slot_date", body.slot_date)
      .eq("slot_hour", body.slot_hour)
      .maybeSingle();

    if (existing) {
      if (!existing.acknowledged_at) {
        await supabase
          .from("royal_checkin_reminders")
          .update({ acknowledged_at: nowIso, updated_at: nowIso })
          .eq("id", existing.id);
      }
    } else {
      await supabase.from("royal_checkin_reminders").insert({
        doctor_id: auth.userId,
        slot_date: body.slot_date,
        slot_hour: body.slot_hour,
        attempts_sent: 0,
        acknowledged_at: nowIso,
        royal_client_count: 0,
      });
    }

    await logEvent({
      function_name: "acknowledge-royal-checkin",
      level: "info",
      user_id: auth.userId,
      action: "royal_checkin_acknowledged",
      metadata: { slot_date: body.slot_date, slot_hour: body.slot_hour },
    });

    return successResponse({ ok: true }, 200, origin);
  } catch (err) {
    await logEvent({
      function_name: "acknowledge-royal-checkin",
      level: "error",
      action: "acknowledge_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
