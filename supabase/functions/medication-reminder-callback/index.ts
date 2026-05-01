// functions/medication-reminder-callback/index.ts
//
// Actions (2026-05-01 redesign):
//
//   accept_ring             — Nurse accepts the 60s ringing invitation
//                              (status nurse_ringing → nurse_accepted).
//   decline_ring            — Nurse declines (status → pending so cron
//                              re-rings a different nurse).
//   start_call              — Nurse taps Call from their Medical Reminder
//                              list (status → nurse_calling, push VideoSDK
//                              incoming call to the patient).
//   completed               — Nurse marks the call done; nurse earns 2,000.
//   patient_unreachable     — Nurse marks the call as unanswered.
//   list_accepted_reminders — Nurse fetches their pending list (accepted
//                              reminders awaiting Call).
//   create_timetable        — Doctor creates a medication timetable.
//   get_schedules           — Patient fetches their active timetables.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const NURSE_REMINDER_EARNING = 2000;  // TZS per completed nurse reminder

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const body = await req.json();
    const action = body.action ?? body.status ?? "";

    const supabase = getServiceClient();

    // ── Action: create_timetable (doctor) ────────────────────────────────
    if (action === "create_timetable") {
      if (!auth.userId) throw new ValidationError("Only doctors can create timetables");

      const { consultation_id, patient_session_id, medication_name, times_per_day, scheduled_times, duration_days, dosage, form } = body;
      if (!consultation_id || !patient_session_id || !medication_name || !scheduled_times?.length || !duration_days) {
        throw new ValidationError("Missing required fields");
      }

      const today = new Date().toISOString().split("T")[0];
      const endDate = new Date(Date.now() + (duration_days - 1) * 86400000).toISOString().split("T")[0];

      const { data: inserted, error } = await supabase
        .from("medication_timetables")
        .insert({
          consultation_id,
          patient_session_id,
          doctor_id: auth.userId,
          medication_name,
          dosage: dosage ?? null,
          form: form ?? "Tablets",
          times_per_day,
          scheduled_times,
          duration_days,
          start_date: today,
          end_date: endDate,
        })
        .select("timetable_id")
        .single();

      if (error) throw error;

      await logEvent({
        function_name: "medication-reminder-callback",
        level: "info",
        user_id: auth.userId,
        action: "timetable_created",
        metadata: { timetable_id: inserted.timetable_id, medication_name },
      });

      return successResponse({ ok: true, timetable_id: inserted.timetable_id }, 201, origin);
    }

    // ── Action: get_schedules (patient) ──────────────────────────────────
    if (action === "get_schedules") {
      if (!auth.sessionId) throw new ValidationError("Only patients can view schedules");

      const { data: timetables } = await supabase
        .from("medication_timetables")
        .select("timetable_id, medication_name, dosage, form, times_per_day, scheduled_times, duration_days, start_date, end_date, is_active")
        .eq("patient_session_id", auth.sessionId)
        .eq("is_active", true)
        .order("created_at", { ascending: false });

      return successResponse({ timetables: timetables ?? [] }, 200, origin);
    }

    // ── Action: list_accepted_reminders (nurse) ─────────────────────────
    // Returns reminders ringing-OR-accepted-OR-already-calling for the nurse.
    // The nurse's Medical Reminder badge opens this list. Including
    // nurse_ringing here means the nurse can simply tap Call on a ringing
    // entry and the screen will accept_ring → start_call atomically.
    if (action === "list_accepted_reminders") {
      if (!auth.userId) throw new ValidationError("Authentication required");

      const { data: rows } = await supabase
        .from("medication_reminder_events")
        .select(`
          event_id, status, scheduled_date, scheduled_time, video_room_id,
          ring_expires_at, nurse_accepted_at,
          medication_timetables!inner (
            patient_session_id, medication_name, dosage, form
          )
        `)
        .eq("nurse_id", auth.userId)
        .in("status", ["nurse_ringing", "nurse_notified", "nurse_accepted", "nurse_calling"])
        .order("nurse_notified_at", { ascending: true });

      return successResponse({ reminders: rows ?? [] }, 200, origin);
    }

    // ── Action: accept_ring (nurse) ─────────────────────────────────────
    if (action === "accept_ring") {
      if (!auth.userId) throw new ValidationError("Authentication required");
      const event_id = body.event_id;
      if (!event_id) throw new ValidationError("event_id is required");

      const { data: ev } = await supabase
        .from("medication_reminder_events")
        .select("event_id, nurse_id, status, ring_expires_at")
        .eq("event_id", event_id)
        .single();

      if (!ev) throw new ValidationError("Event not found");
      if (ev.nurse_id !== auth.userId) {
        throw new ValidationError("You are not the nurse rung for this reminder");
      }
      if (!["nurse_ringing", "nurse_notified"].includes(ev.status)) {
        // Already accepted / declined / expired — no-op for idempotency.
        return successResponse({ ok: true, status: ev.status }, 200, origin);
      }
      if (ev.ring_expires_at && new Date(ev.ring_expires_at) < new Date()) {
        // Ring window already elapsed — cron will reassign.
        return successResponse({ ok: false, reason: "ring_expired" }, 200, origin);
      }

      await supabase
        .from("medication_reminder_events")
        .update({
          status: "nurse_accepted",
          nurse_accepted_at: new Date().toISOString(),
        })
        .eq("event_id", event_id);

      return successResponse({ ok: true }, 200, origin);
    }

    // ── Action: decline_ring (nurse) ────────────────────────────────────
    if (action === "decline_ring") {
      if (!auth.userId) throw new ValidationError("Authentication required");
      const event_id = body.event_id;
      if (!event_id) throw new ValidationError("event_id is required");

      const { data: ev } = await supabase
        .from("medication_reminder_events")
        .select("event_id, nurse_id, status, reassign_count")
        .eq("event_id", event_id)
        .single();

      if (!ev) throw new ValidationError("Event not found");
      if (ev.nurse_id !== auth.userId) {
        throw new ValidationError("You are not the nurse rung for this reminder");
      }
      if (!["nurse_ringing", "nurse_notified"].includes(ev.status)) {
        return successResponse({ ok: true, already_settled: true }, 200, origin);
      }

      // Reset to pending; cron will pick a different nurse on next tick.
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
        .eq("event_id", event_id);

      return successResponse({ ok: true }, 200, origin);
    }

    // ── Action: start_call (nurse taps Call from list) ──────────────────
    // Pushes a VideoSDK incoming call to the patient. This is where the
    // patient first hears about the reminder — never before nurse acceptance.
    if (action === "start_call") {
      if (!auth.userId) throw new ValidationError("Authentication required");
      const event_id = body.event_id;
      if (!event_id) throw new ValidationError("event_id is required");

      const { data: ev } = await supabase
        .from("medication_reminder_events")
        .select(`
          event_id, nurse_id, status, video_room_id, timetable_id,
          medication_timetables!inner (
            consultation_id, patient_session_id, medication_name, dosage, form
          )
        `)
        .eq("event_id", event_id)
        .single();

      if (!ev) throw new ValidationError("Event not found");
      if (ev.nurse_id !== auth.userId) {
        throw new ValidationError("You are not assigned to this reminder");
      }
      // Idempotent: if the call has already been started (status=nurse_calling)
      // — for example, the nurse left the call screen and came back — just
      // return the existing room info so they can rejoin without seeing an
      // error. Only reject if the status is unexpected (completed, ringing,
      // patient_unreachable, etc.).
      if (ev.status === "nurse_calling") {
        const tt = (ev as unknown as Record<string, unknown>).medication_timetables as {
          consultation_id: string; patient_session_id: string;
        };
        return successResponse({
          ok: true,
          room_id: ev.video_room_id,
          patient_session_id: tt.patient_session_id,
          consultation_id: tt.consultation_id,
          rejoined: true,
        }, 200, origin);
      }
      if (ev.status !== "nurse_accepted") {
        throw new ValidationError(`Cannot start call from status ${ev.status}`);
      }

      const tt = (ev as unknown as Record<string, unknown>).medication_timetables as {
        consultation_id: string; patient_session_id: string; medication_name: string; dosage?: string | null;
      };
      const medLabel = `${tt.medication_name}${tt.dosage ? ` (${tt.dosage})` : ""}`;

      await supabase
        .from("medication_reminder_events")
        .update({
          status: "nurse_calling",
          call_started_at: new Date().toISOString(),
        })
        .eq("event_id", event_id);

      // Patient now gets the actual incoming call push. We reuse the existing
      // VIDEO_CALL_INCOMING route — it's already data-only allowlisted in
      // send-push-notification and the Android handler already starts the
      // IncomingCallService + overlay from its data fields. caller_role tags
      // it so the overlay can show "Medical Reminder request".
      // Direct fetch (not supabase.functions.invoke) so we can attach
      // X-Service-Key — send-push-notification's doctor gate would otherwise
      // reject the nurse, who isn't the consultation's owner doctor.
      try {
        const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
        const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const BEARER_JWT = Deno.env.get("EDGE_FN_BEARER_JWT") ?? SERVICE_ROLE;
        const res = await fetch(`${SUPABASE_URL}/functions/v1/send-push-notification`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${BEARER_JWT}`,
            "X-Service-Key": SERVICE_ROLE,
          },
          body: JSON.stringify({
            session_id: tt.patient_session_id,
            title: "Medication Reminder Call",
            body: `A nurse is calling you to remind you about ${medLabel}.`,
            type: "VIDEO_CALL_INCOMING",
            data: {
              event_id,
              consultation_id: tt.consultation_id,
              room_id: ev.video_room_id,
              call_type: "AUDIO",
              caller_role: "medication_reminder_nurse",
              medication_name: tt.medication_name,
              dosage: tt.dosage ?? "",
            },
          }),
        });
        if (!res.ok) {
          const txt = await res.text().catch(() => "");
          console.error(`[med-callback] send-push-notification ${res.status}: ${txt}`);
        }
      } catch (e) {
        console.error("[med-callback] Failed to push patient incoming call:", e);
      }

      return successResponse({
        ok: true,
        room_id: ev.video_room_id,
        patient_session_id: tt.patient_session_id,
        consultation_id: tt.consultation_id,
      }, 200, origin);
    }

    // ── Action: completed / patient_unreachable (call ended) ────────────
    const event_id = body.event_id;
    const status = action;

    if (!event_id || typeof event_id !== "string") {
      throw new ValidationError("event_id is required");
    }
    if (!["completed", "patient_unreachable"].includes(status)) {
      throw new ValidationError(
        "Invalid action. Use: create_timetable, get_schedules, list_accepted_reminders, accept_ring, decline_ring, start_call, completed, patient_unreachable"
      );
    }
    if (!auth.userId) throw new ValidationError("Authentication required");

    const { data: event } = await supabase
      .from("medication_reminder_events")
      .select("event_id, nurse_id, timetable_id, status, nurse_notified_at")
      .eq("event_id", event_id)
      .single();

    if (!event) throw new ValidationError("Event not found");
    if (event.nurse_id !== auth.userId) throw new ValidationError("You are not assigned to this reminder");
    if (event.status === "completed") {
      return successResponse({ ok: true, already_completed: true }, 200, origin);
    }

    const now = new Date().toISOString();
    await supabase
      .from("medication_reminder_events")
      .update({
        status,
        call_ended_at: now,
        ...(status === "completed" ? { call_started_at: event.nurse_notified_at } : {}),
      })
      .eq("event_id", event_id);

    // Pay nurse 2,000 TZS per completed reminder. Multiple medication_reminder
    // earnings on the same consultation are allowed (the unique index excludes
    // this earning_type), so each completed event credits independently.
    if (status === "completed") {
      const { data: tt } = await supabase
        .from("medication_timetables")
        .select("consultation_id")
        .eq("timetable_id", event.timetable_id)
        .single();

      if (tt) {
        try {
          await supabase.from("doctor_earnings").insert({
            doctor_id: auth.userId,
            consultation_id: tt.consultation_id,
            amount: NURSE_REMINDER_EARNING,
            status: "pending",
            earning_type: "medication_reminder",
            notes: `Medication reminder. event_id=${event_id}`,
          });
          console.log(`[med-callback] Credited nurse ${auth.userId} ${NURSE_REMINDER_EARNING} TZS for event ${event_id}`);
        } catch (e) {
          console.warn(`[med-callback] Earnings insert skipped:`, e);
        }
      }
    }

    if (status === "patient_unreachable") {
      const { data: tt } = await supabase
        .from("medication_timetables")
        .select("patient_session_id, medication_name, dosage")
        .eq("timetable_id", event.timetable_id)
        .single();

      if (tt) {
        const medLabel = `${tt.medication_name}${tt.dosage ? ` (${tt.dosage})` : ""}`;
        try {
          await supabase.functions.invoke("send-push-notification", {
            body: {
              session_id: tt.patient_session_id,
              title: "Medication Reminder",
              body: `You missed a nurse call for your medication reminder. Please take ${medLabel} now.`,
              type: "MEDICATION_REMINDER_PATIENT",
              data: { event_id },
            },
          });
        } catch (e) {
          console.error("Failed to send missed-call notification:", e);
        }
      }
    }

    await logEvent({
      function_name: "medication-reminder-callback",
      level: "info",
      user_id: auth.userId,
      action: `reminder_${status}`,
      metadata: { event_id },
    });

    return successResponse({ ok: true }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
