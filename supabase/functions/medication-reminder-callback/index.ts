// functions/medication-reminder-callback/index.ts
//
// Actions:
//   complete / patient_unreachable — Nurse marks a reminder call as done
//   create_timetable — Doctor creates a medication timetable for a Royal patient
//   get_schedules — Patient fetches their active timetables

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

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

    // ── Action: complete / patient_unreachable (nurse callback) ──────────
    const event_id = body.event_id;
    const status = action;

    if (!event_id || typeof event_id !== "string") {
      throw new ValidationError("event_id is required");
    }
    if (!["completed", "patient_unreachable"].includes(status)) {
      throw new ValidationError("Invalid action. Use: create_timetable, get_schedules, completed, or patient_unreachable");
    }

    if (!auth.userId) throw new ValidationError("Authentication required");

    const { data: event } = await supabase
      .from("medication_reminder_events")
      .select("event_id, nurse_id, timetable_id, status")
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

    // Credit nurse 1000 TZS for completed medication reminder calls
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
            amount: 1000,
            status: "pending",
            earning_type: "medication_reminder",
          });
          console.log(`[med-callback] Credited nurse ${auth.userId} 1000 TZS for event ${event_id}`);
        } catch (e) {
          // May conflict if same nurse+consultation already has an earning row —
          // that's fine, they still completed the call.
          console.warn(`[med-callback] Earnings insert skipped (likely duplicate):`, e);
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
