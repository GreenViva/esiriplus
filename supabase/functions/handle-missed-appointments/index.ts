// handle-missed-appointments – Cron-triggered function that detects overdue
// confirmed appointments past their grace period and marks them as missed.
// Notifies both doctor and patient.
// Called every minute by pg_cron.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // Accept cron or admin auth
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
    let totalMissed = 0;

    // Find confirmed/booked appointments where:
    //   scheduled_at + grace_period_minutes < now
    // This means the grace period has elapsed and the session was never started.
    const { data: overdueAppointments, error } = await supabase
      .from("appointments")
      .select(`
        appointment_id,
        doctor_id,
        patient_session_id,
        scheduled_at,
        grace_period_minutes,
        service_type,
        doctor_profiles:doctor_id (full_name)
      `)
      .in("status", ["booked", "confirmed"]);

    if (error) {
      console.error("Error fetching overdue appointments:", error);
      throw error;
    }

    for (const appt of overdueAppointments ?? []) {
      const scheduledAt = new Date(appt.scheduled_at);
      const gracePeriodMs = (appt.grace_period_minutes ?? 5) * 60 * 1000;
      const deadline = new Date(scheduledAt.getTime() + gracePeriodMs);

      if (now < deadline) continue; // Not yet overdue

      // Mark as missed
      const { error: updateError } = await supabase
        .from("appointments")
        .update({ status: "missed" })
        .eq("appointment_id", appt.appointment_id)
        .in("status", ["booked", "confirmed"]); // Optimistic lock

      if (updateError) {
        console.error(`Failed to mark ${appt.appointment_id} as missed:`, updateError);
        continue;
      }

      const doctorName = (appt as unknown as Record<string, unknown>)
        ?.doctor_profiles?.full_name ?? "Doctor";

      const scheduledTime = scheduledAt.toLocaleString("en-GB", {
        timeZone: "Africa/Nairobi",
        hour: "2-digit",
        minute: "2-digit",
      });

      // Notify doctor
      try {
        await supabase.functions.invoke("send-push-notification", {
          body: {
            user_id: appt.doctor_id,
            title: "Missed Appointment",
            body: `The appointment at ${scheduledTime} was missed. You can reschedule it from your dashboard.`,
            type: "appointment_missed",
            data: {
              appointment_id: appt.appointment_id,
              scheduled_at: appt.scheduled_at,
            },
          },
        });
      } catch (e) {
        console.error(`Failed to notify doctor ${appt.doctor_id}:`, e);
      }

      // Notify patient
      try {
        await supabase.functions.invoke("send-push-notification", {
          body: {
            session_id: appt.patient_session_id,
            title: "Missed Appointment",
            body: `Your appointment with Dr. ${doctorName} at ${scheduledTime} was missed. The doctor may reschedule it for you.`,
            type: "appointment_missed",
            data: {
              appointment_id: appt.appointment_id,
              scheduled_at: appt.scheduled_at,
            },
          },
        });
      } catch (e) {
        console.error(`Failed to notify patient ${appt.patient_session_id}:`, e);
      }

      // Insert in-app notifications
      try {
        await supabase.from("notifications").insert([
          {
            user_id: appt.doctor_id,
            title: "Missed Appointment",
            body: `Appointment at ${scheduledTime} was missed.`,
            type: "appointment_missed",
            data: JSON.stringify({ appointment_id: appt.appointment_id }),
          },
          {
            user_id: appt.patient_session_id,
            title: "Missed Appointment",
            body: `Your appointment with Dr. ${doctorName} was missed.`,
            type: "appointment_missed",
            data: JSON.stringify({ appointment_id: appt.appointment_id }),
          },
        ]);
      } catch (e) {
        console.error("Failed to insert missed notifications:", e);
      }

      totalMissed++;
    }

    if (totalMissed > 0) {
      await logEvent({
        function_name: "handle-missed-appointments",
        level: "info",
        action: "missed_appointments_processed",
        metadata: { total_missed: totalMissed, processed_at: now.toISOString() },
      });
    }

    return successResponse({
      message: "Missed appointments job completed",
      missed_count: totalMissed,
    }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "handle-missed-appointments",
      level: "error",
      action: "missed_job_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
