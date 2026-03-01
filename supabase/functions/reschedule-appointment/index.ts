// reschedule-appointment – Doctor reschedules an existing appointment.
// Calls the reschedule_appointment RPC, notifies patient via FCM.
//
// Rate limit: 10/min per user.

import { handlePreflight, corsHeaders } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface RescheduleRequest {
  appointment_id: string;
  new_scheduled_at: string; // ISO-8601
  reason?: string;
}

function validate(body: unknown): RescheduleRequest {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  if (typeof b.appointment_id !== "string" || !b.appointment_id) {
    throw new ValidationError("appointment_id is required");
  }
  if (typeof b.new_scheduled_at !== "string" || !b.new_scheduled_at) {
    throw new ValidationError("new_scheduled_at is required");
  }

  return b as unknown as RescheduleRequest;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);

    // Only doctors can reschedule
    if (!auth.userId) {
      throw new ValidationError("Only doctors can reschedule appointments");
    }

    const identifier = auth.userId;
    await LIMITS.payment(identifier);

    const raw = await req.json();
    const body = validate(raw);

    const supabase = getServiceClient();

    // Verify the doctor owns this appointment
    const { data: existing } = await supabase
      .from("appointments")
      .select("doctor_id, patient_session_id, scheduled_at")
      .eq("appointment_id", body.appointment_id)
      .single();

    if (!existing) {
      throw new ValidationError("Appointment not found");
    }
    if (existing.doctor_id !== auth.userId) {
      throw new ValidationError("You are not assigned to this appointment");
    }

    // Call the atomic reschedule RPC
    const { data, error } = await supabase.rpc("reschedule_appointment", {
      p_appointment_id: body.appointment_id,
      p_new_scheduled_at: body.new_scheduled_at,
      p_reschedule_reason: body.reason ?? "",
    });

    if (error) {
      console.error("reschedule_appointment RPC error:", JSON.stringify(error));
      throw new ValidationError(error.message || "Failed to reschedule appointment");
    }

    const result = Array.isArray(data) ? data[0] : data;
    if (!result) {
      throw new ValidationError("Reschedule failed — no result returned");
    }

    // Notify patient via push
    try {
      const formattedTime = new Date(body.new_scheduled_at).toLocaleString("en-GB", {
        timeZone: "Africa/Nairobi",
      });

      await supabase.functions.invoke("send-push-notification", {
        body: {
          session_id: existing.patient_session_id,
          title: "Appointment Rescheduled",
          body: `Your appointment has been moved to ${formattedTime}. ${body.reason ? `Reason: ${body.reason}` : ""}`.trim(),
          type: "appointment_rescheduled",
          data: {
            old_appointment_id: body.appointment_id,
            new_appointment_id: result.new_appointment_id,
            new_scheduled_at: body.new_scheduled_at,
          },
        },
      });
    } catch (e) {
      console.error("Failed to send reschedule notification:", e);
    }

    // Insert in-app notification
    try {
      await supabase.from("notifications").insert({
        user_id: existing.patient_session_id,
        title: "Appointment Rescheduled",
        body: `Your appointment has been rescheduled to ${new Date(body.new_scheduled_at).toLocaleString("en-GB", { timeZone: "Africa/Nairobi" })}`,
        type: "appointment_rescheduled",
        data: JSON.stringify({
          old_appointment_id: body.appointment_id,
          new_appointment_id: result.new_appointment_id,
          new_scheduled_at: body.new_scheduled_at,
        }),
      });
    } catch (e) {
      console.error("Failed to insert notification:", e);
    }

    await logEvent({
      function_name: "reschedule-appointment",
      level: "info",
      user_id: auth.userId,
      action: "appointment_rescheduled",
      metadata: {
        old_appointment_id: body.appointment_id,
        new_appointment_id: result.new_appointment_id,
        new_scheduled_at: body.new_scheduled_at,
        reason: body.reason,
      },
      ip_address: null,
    });

    return successResponse(
      {
        new_appointment_id: result.new_appointment_id,
        old_appointment_id: result.old_appointment_id,
        doctor_id: result.doctor_id,
        scheduled_at: result.scheduled_at,
        status: result.status,
        created_at: result.created_at,
      },
      200,
      origin
    );
  } catch (err) {
    await logEvent({
      function_name: "reschedule-appointment",
      level: "error",
      action: "reschedule_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
