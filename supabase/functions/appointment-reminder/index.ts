// functions/appointment-reminder/index.ts
// Sends reminders for upcoming appointments at 20/10/5/0 min marks.
// Designed to be called by a Supabase CRON job (pg_cron) every minute.
// Auth: service role (via cron) or admin.
// Tracks sent reminders in reminders_sent[] to prevent duplicates.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// Reminder windows in minutes before appointment
const REMINDER_WINDOWS = [
  { minutes: 20, label: "20min", title: "Appointment in 20 minutes", body: "Your appointment starts in 20 minutes. Please be ready." },
  { minutes: 10, label: "10min", title: "Appointment in 10 minutes", body: "Your appointment starts in 10 minutes." },
  { minutes: 5,  label: "5min",  title: "Appointment in 5 minutes",  body: "Your appointment starts in 5 minutes. Get ready!" },
  { minutes: 0,  label: "0min",  title: "Appointment Starting Now",  body: "Your appointment is starting now. Please join." },
];

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // Accept both admin auth and cron invocations (via service role header)
    let isAuthorized = false;

    const cronSecret = req.headers.get("X-Cron-Secret");
    if (cronSecret && cronSecret === Deno.env.get("CRON_SECRET")) {
      isAuthorized = true;
    } else {
      const auth = await validateAuth(req);
      requireRole(auth, "admin");
      isAuthorized = true;
    }

    if (!isAuthorized) {
      return new Response("Unauthorized", { status: 401 });
    }

    const supabase = getServiceClient();
    const now = new Date();
    let totalSent = 0;

    for (const window of REMINDER_WINDOWS) {
      // Calculate the time window: appointments starting in [minutes, minutes+1) from now
      const windowStart = new Date(now.getTime() + window.minutes * 60 * 1000);
      const windowEnd = new Date(windowStart.getTime() + 60 * 1000); // +1 minute

      // Find booked/confirmed appointments in this window that haven't received this reminder
      const { data: appointments } = await supabase
        .from("appointments")
        .select(`
          appointment_id,
          scheduled_at,
          service_type,
          patient_session_id,
          doctor_id,
          reminders_sent,
          doctor_profiles:doctor_id (full_name)
        `)
        .in("status", ["booked", "confirmed"])
        .gte("scheduled_at", windowStart.toISOString())
        .lt("scheduled_at", windowEnd.toISOString());

      if (!appointments || appointments.length === 0) continue;

      for (const appt of appointments) {
        const alreadySent = (appt.reminders_sent ?? []) as string[];
        if (alreadySent.includes(window.label)) continue;

        const doctorName = (appt as unknown as Record<string, unknown>)
          ?.doctor_profiles?.full_name ?? "your doctor";

        const scheduledTime = new Date(appt.scheduled_at).toLocaleString("en-GB", {
          timeZone: "Africa/Nairobi",
          hour: "2-digit",
          minute: "2-digit",
        });

        // Notify patient
        try {
          await supabase.functions.invoke("send-push-notification", {
            body: {
              session_id: appt.patient_session_id,
              title: window.title,
              body: `${window.body} Your consultation with Dr. ${doctorName} is at ${scheduledTime}.`,
              type: "appointment_reminder",
              data: {
                appointment_id: appt.appointment_id,
                scheduled_at: appt.scheduled_at,
                minutes_until: window.minutes,
              },
            },
          });
        } catch (e) {
          console.error(`Failed to notify patient ${appt.patient_session_id}:`, e);
        }

        // Notify doctor
        try {
          await supabase.functions.invoke("send-push-notification", {
            body: {
              user_id: appt.doctor_id,
              title: window.title,
              body: `${window.body} You have a consultation at ${scheduledTime}.`,
              type: "appointment_reminder",
              data: {
                appointment_id: appt.appointment_id,
                scheduled_at: appt.scheduled_at,
                minutes_until: window.minutes,
              },
            },
          });
        } catch (e) {
          console.error(`Failed to notify doctor ${appt.doctor_id}:`, e);
        }

        // Mark this reminder as sent
        const updatedReminders = [...alreadySent, window.label];
        await supabase
          .from("appointments")
          .update({ reminders_sent: updatedReminders })
          .eq("appointment_id", appt.appointment_id);

        totalSent++;
      }
    }

    if (totalSent > 0) {
      await logEvent({
        function_name: "appointment-reminder",
        level: "info",
        action: "reminders_processed",
        metadata: { total_sent: totalSent, processed_at: now.toISOString() },
      });
    }

    return successResponse({
      message: "Reminder job completed",
      reminders_sent: totalSent,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "appointment-reminder",
      level: "error",
      action: "reminder_job_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
