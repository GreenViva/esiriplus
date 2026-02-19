// functions/send-appointment-notification/index.ts
// Sends appointment confirmation notifications to patient & doctor.
// Called after appointment creation. Rate limit: 20/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface AppointmentNotificationRequest {
  appointment_id: string;
  notification_type: "confirmed" | "rescheduled" | "cancelled";
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    await LIMITS.notification(auth.userId ?? auth.sessionId ?? "anon");

    const raw = await req.json();
    if (!raw?.appointment_id || !raw?.notification_type) {
      throw new ValidationError("appointment_id and notification_type are required");
    }

    const validTypes = ["confirmed", "rescheduled", "cancelled"];
    if (!validTypes.includes(raw.notification_type)) {
      throw new ValidationError(`notification_type must be one of: ${validTypes.join(", ")}`);
    }

    const { appointment_id, notification_type } = raw as AppointmentNotificationRequest;
    const supabase = getServiceClient();

    // Fetch appointment with doctor and patient info
    const { data: appointment } = await supabase
      .from("appointments")
      .select(`
        appointment_id,
        scheduled_at,
        service_type,
        patient_session_id,
        doctor_id,
        doctor_profiles:doctor_id (full_name)
      `)
      .eq("appointment_id", appointment_id)
      .single();

    if (!appointment) {
      throw new ValidationError("Appointment not found");
    }

    const scheduledDate = new Date(appointment.scheduled_at).toLocaleString("en-KE", {
      timeZone: "Africa/Nairobi",
      dateStyle: "medium",
      timeStyle: "short",
    });

    const doctorName = (appointment as unknown as Record<string, unknown>)
      ?.doctor_profiles?.full_name ?? "your doctor";

    const messages: Record<string, { patient: { title: string; body: string }, doctor: { title: string; body: string } }> = {
      confirmed: {
        patient: {
          title: "Appointment Confirmed ✓",
          body: `Your appointment with Dr. ${doctorName} is confirmed for ${scheduledDate}.`,
        },
        doctor: {
          title: "New Appointment",
          body: `You have a new appointment scheduled for ${scheduledDate}.`,
        },
      },
      rescheduled: {
        patient: {
          title: "Appointment Rescheduled",
          body: `Your appointment has been rescheduled to ${scheduledDate}.`,
        },
        doctor: {
          title: "Appointment Rescheduled",
          body: `An appointment has been rescheduled to ${scheduledDate}.`,
        },
      },
      cancelled: {
        patient: {
          title: "Appointment Cancelled",
          body: `Your appointment with Dr. ${doctorName} has been cancelled.`,
        },
        doctor: {
          title: "Appointment Cancelled",
          body: `An appointment scheduled for ${scheduledDate} has been cancelled.`,
        },
      },
    };

    const msgs = messages[notification_type];

    // Send to patient
    await supabase.functions.invoke("send-push-notification", {
      body: {
        session_id: appointment.patient_session_id,
        title: msgs.patient.title,
        body: msgs.patient.body,
        type: `appointment_${notification_type}`,
        consultation_id: appointment_id,
      },
    });

    // Send to doctor
    await supabase.functions.invoke("send-push-notification", {
      body: {
        user_id: appointment.doctor_id,
        title: msgs.doctor.title,
        body: msgs.doctor.body,
        type: `appointment_${notification_type}`,
        consultation_id: appointment_id,
      },
    });

    // Also write to notifications table for in-app notification centre
    await supabase.from("notifications").insert([
      {
        user_id: appointment.patient_session_id,
        title: msgs.patient.title,
        body: msgs.patient.body,
        type: `appointment_${notification_type}`,
        reference_id: appointment_id,
        is_read: false,
        created_at: new Date().toISOString(),
      },
      {
        user_id: appointment.doctor_id,
        title: msgs.doctor.title,
        body: msgs.doctor.body,
        type: `appointment_${notification_type}`,
        reference_id: appointment_id,
        is_read: false,
        created_at: new Date().toISOString(),
      },
    ]);

    await logEvent({
      function_name: "send-appointment-notification",
      level: "info",
      action: "appointment_notification_sent",
      metadata: { appointment_id, notification_type },
      ip_address: getClientIp(req),
    });

    return successResponse({ message: "Appointment notifications sent" }, 200, origin);

  } catch (err) {
    await logEvent({
      function_name: "send-appointment-notification",
      level: "error",
      action: "notification_error",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
