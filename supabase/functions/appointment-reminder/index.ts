// functions/appointment-reminder/index.ts
// Sends reminders for upcoming appointments.
// Designed to be called by a Supabase CRON job (pg_cron) every 15 minutes.
// Auth: service role or admin only.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// Reminder windows in minutes
const REMINDER_WINDOWS = [
  { minutes: 60 * 24, label: "24 hours" },  // 24h reminder
  { minutes: 60,      label: "1 hour" },     // 1h reminder
  { minutes: 15,      label: "15 minutes" }, // 15min reminder
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
      const windowStart = new Date(now.getTime() + (window.minutes - 5) * 60 * 1000);
      const windowEnd   = new Date(now.getTime() + (window.minutes + 5) * 60 * 1000);

      // Find appointments falling within this reminder window
      // that haven't been reminded yet for this window
      const { data: appointments } = await supabase
        .from("appointments")
        .select(`
          appointment_id,
          scheduled_at,
          service_type,
          patient_session_id,
          doctor_id,
          reminder_sent_at,
          reminders_sent,
          doctor_profiles:doctor_id (full_name)
        `)
        .eq("status", "confirmed")
        .gte("scheduled_at", windowStart.toISOString())
        .lte("scheduled_at", windowEnd.toISOString());

      if (!appointments || appointments.length === 0) continue;

      for (const appt of appointments) {
        const alreadySent = (appt.reminders_sent ?? []) as string[];
        if (alreadySent.includes(window.label)) continue;

        const doctorName = (appt as unknown as Record<string, unknown>)
          ?.doctor_profiles?.full_name ?? "your doctor";

        const scheduledTime = new Date(appt.scheduled_at).toLocaleString("en-KE", {
          timeZone: "Africa/Nairobi",
          timeStyle: "short",
        });

        // Notify patient
        await supabase.functions.invoke("send-push-notification", {
          body: {
            session_id: appt.patient_session_id,
            title: `Appointment in ${window.label}`,
            body: `Reminder: Your consultation with Dr. ${doctorName} is at ${scheduledTime}.`,
            type: "appointment_reminder",
            consultation_id: appt.appointment_id,
          },
        });

        // Notify doctor
        await supabase.functions.invoke("send-push-notification", {
          body: {
            user_id: appt.doctor_id,
            title: `Appointment in ${window.label}`,
            body: `Reminder: You have a consultation at ${scheduledTime}.`,
            type: "appointment_reminder",
            consultation_id: appt.appointment_id,
          },
        });

        // Mark this reminder window as sent
        await supabase
          .from("appointments")
          .update({
            reminders_sent: [...alreadySent, window.label],
            reminder_sent_at: now.toISOString(),
          })
          .eq("appointment_id", appt.appointment_id);

        totalSent++;
      }
    }

    await logEvent({
      function_name: "appointment-reminder",
      level: "info",
      action: "reminders_processed",
      metadata: { total_sent: totalSent, processed_at: now.toISOString() },
    });

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
