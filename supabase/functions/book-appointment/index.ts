// book-appointment – Handles appointment booking lifecycle:
//   action: "book"           — patient books a time slot
//   action: "cancel"         — patient or doctor cancels
//   action: "get_slots"      — returns availability + booked times for a doctor
//   action: "get_appointments" — returns appointments for the caller
//
// Rate limit: 10/min per user.

import { handlePreflight, corsHeaders } from "../_shared/cors.ts";
import { validateAuth, type AuthResult } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

// Service tier fees (TZS) — must match client-side service_tiers
const SERVICE_FEES: Record<string, number> = {
  nurse: 5000,
  clinical_officer: 7000,
  pharmacist: 3000,
  gp: 10000,
  specialist: 30000,
  psychologist: 50000,
};

// ── Types ────────────────────────────────────────────────────────────────────

interface BookRequest {
  action: "book";
  doctor_id: string;
  scheduled_at: string; // ISO-8601
  duration_minutes?: number;
  service_type: string;
  consultation_type?: string;
  chief_complaint?: string;
}

interface CancelRequest {
  action: "cancel";
  appointment_id: string;
}

interface GetSlotsRequest {
  action: "get_slots";
  doctor_id: string;
  date: string; // YYYY-MM-DD
}

interface GetAppointmentsRequest {
  action: "get_appointments";
  status?: string;
  limit?: number;
  offset?: number;
}

type RequestBody = BookRequest | CancelRequest | GetSlotsRequest | GetAppointmentsRequest;

// ── Validation ───────────────────────────────────────────────────────────────

function validate(body: unknown): RequestBody {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("Request body must be JSON object");
  }
  const b = body as Record<string, unknown>;

  const action = b.action as string;
  if (!["book", "cancel", "get_slots", "get_appointments"].includes(action)) {
    throw new ValidationError(
      'action must be one of: book, cancel, get_slots, get_appointments'
    );
  }

  if (action === "book") {
    if (typeof b.doctor_id !== "string" || !b.doctor_id) {
      throw new ValidationError("doctor_id is required for booking");
    }
    if (typeof b.scheduled_at !== "string" || !b.scheduled_at) {
      throw new ValidationError("scheduled_at is required for booking");
    }
    if (typeof b.service_type !== "string" || !b.service_type) {
      throw new ValidationError("service_type is required for booking");
    }
  } else if (action === "cancel") {
    if (typeof b.appointment_id !== "string" || !b.appointment_id) {
      throw new ValidationError("appointment_id is required for cancellation");
    }
  } else if (action === "get_slots") {
    if (typeof b.doctor_id !== "string" || !b.doctor_id) {
      throw new ValidationError("doctor_id is required for get_slots");
    }
    if (typeof b.date !== "string" || !b.date) {
      throw new ValidationError("date (YYYY-MM-DD) is required for get_slots");
    }
  }

  return b as unknown as RequestBody;
}

// ── Handlers ─────────────────────────────────────────────────────────────────

async function handleBook(
  body: BookRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  if (!auth.sessionId) {
    throw new ValidationError("Only patients can book appointments");
  }

  const supabase = getServiceClient();
  const consultationFee = SERVICE_FEES[body.service_type] ?? 5000;

  const { data, error } = await supabase.rpc("book_appointment", {
    p_doctor_id: body.doctor_id,
    p_patient_session_id: auth.sessionId,
    p_scheduled_at: body.scheduled_at,
    p_duration_minutes: body.duration_minutes ?? 15,
    p_service_type: body.service_type,
    p_consultation_type: body.consultation_type ?? "chat",
    p_chief_complaint: body.chief_complaint ?? "",
    p_consultation_fee: consultationFee,
    p_grace_period_minutes: 5,
  });

  if (error) {
    console.error("book_appointment RPC error:", JSON.stringify(error));
    throw new ValidationError(error.message || "Failed to book appointment");
  }

  const appointment = Array.isArray(data) ? data[0] : data;
  if (!appointment) {
    throw new ValidationError("Booking failed — no appointment returned");
  }

  // Notify doctor via push
  try {
    await supabase.functions.invoke("send-push-notification", {
      body: {
        user_id: body.doctor_id,
        title: "New Appointment Booked",
        body: `A patient has booked an appointment for ${new Date(body.scheduled_at).toLocaleString("en-GB", { timeZone: "Africa/Nairobi" })}`,
        type: "appointment_confirmed",
        data: {
          appointment_id: appointment.appointment_id,
          scheduled_at: body.scheduled_at,
        },
      },
    });
  } catch (e) {
    console.error("Failed to send booking notification:", e);
  }

  // Notify patient via in-app notification
  try {
    await supabase.from("notifications").insert({
      user_id: auth.sessionId,
      title: "Appointment Booked",
      body: `Your appointment is confirmed for ${new Date(body.scheduled_at).toLocaleString("en-GB", { timeZone: "Africa/Nairobi" })}`,
      type: "appointment_confirmed",
      data: JSON.stringify({
        appointment_id: appointment.appointment_id,
        doctor_id: body.doctor_id,
        scheduled_at: body.scheduled_at,
      }),
    });
  } catch (e) {
    console.error("Failed to insert notification:", e);
  }

  await logEvent({
    function_name: "book-appointment",
    level: "info",
    session_id: auth.sessionId,
    action: "appointment_booked",
    metadata: {
      appointment_id: appointment.appointment_id,
      doctor_id: body.doctor_id,
      scheduled_at: body.scheduled_at,
      service_type: body.service_type,
    },
    ip_address: null,
  });

  return successResponse(
    {
      appointment_id: appointment.appointment_id,
      doctor_id: appointment.doctor_id,
      scheduled_at: appointment.scheduled_at,
      status: appointment.status,
      created_at: appointment.created_at,
    },
    201,
    origin
  );
}

async function handleCancel(
  body: CancelRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  // Fetch the appointment
  const { data: appointment } = await supabase
    .from("appointments")
    .select("*")
    .eq("appointment_id", body.appointment_id)
    .single();

  if (!appointment) {
    throw new ValidationError("Appointment not found");
  }

  // Verify caller owns this appointment
  const isPatient = auth.sessionId === appointment.patient_session_id;
  const isDoctor = auth.userId === appointment.doctor_id;
  if (!isPatient && !isDoctor) {
    throw new ValidationError("Not authorized to cancel this appointment");
  }

  // Only booked or confirmed appointments can be cancelled
  if (!["booked", "confirmed"].includes(appointment.status)) {
    throw new ValidationError(`Cannot cancel an appointment with status: ${appointment.status}`);
  }

  const { error } = await supabase
    .from("appointments")
    .update({ status: "cancelled" })
    .eq("appointment_id", body.appointment_id);

  if (error) throw error;

  // Notify the other party
  try {
    if (isPatient) {
      await supabase.functions.invoke("send-push-notification", {
        body: {
          user_id: appointment.doctor_id,
          title: "Appointment Cancelled",
          body: "A patient has cancelled their upcoming appointment.",
          type: "appointment_cancelled",
          data: { appointment_id: body.appointment_id },
        },
      });
    } else {
      await supabase.functions.invoke("send-push-notification", {
        body: {
          session_id: appointment.patient_session_id,
          title: "Appointment Cancelled",
          body: "Your doctor has cancelled the appointment. You can book a new time.",
          type: "appointment_cancelled",
          data: { appointment_id: body.appointment_id },
        },
      });
    }
  } catch (e) {
    console.error("Failed to send cancellation notification:", e);
  }

  await logEvent({
    function_name: "book-appointment",
    level: "info",
    user_id: auth.userId,
    session_id: auth.sessionId,
    action: "appointment_cancelled",
    metadata: { appointment_id: body.appointment_id },
    ip_address: null,
  });

  return successResponse(
    { appointment_id: body.appointment_id, status: "cancelled" },
    200,
    origin
  );
}

async function handleGetSlots(
  body: GetSlotsRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();

  // Parse the target date — use UTC to avoid timezone shift issues
  const [year, month, day] = body.date.split("-").map(Number);
  const targetDate = new Date(Date.UTC(year, month - 1, day));
  const dayOfWeek = targetDate.getUTCDay(); // 0=Sunday

  // 1. Get doctor's availability slots for this day of week
  const { data: availabilitySlots, error: slotsError } = await supabase
    .from("doctor_availability_slots")
    .select("slot_id, start_time, end_time, buffer_minutes")
    .eq("doctor_id", body.doctor_id)
    .eq("day_of_week", dayOfWeek)
    .eq("is_active", true)
    .order("start_time", { ascending: true });

  if (slotsError) throw slotsError;

  // 2. Get booked appointments for this date
  const dayStart = new Date(body.date + "T00:00:00+03:00").toISOString();
  const dayEnd = new Date(body.date + "T23:59:59+03:00").toISOString();

  const { data: bookedAppointments, error: bookedError } = await supabase
    .from("appointments")
    .select("appointment_id, scheduled_at, duration_minutes, status")
    .eq("doctor_id", body.doctor_id)
    .in("status", ["booked", "confirmed", "in_progress"])
    .gte("scheduled_at", dayStart)
    .lte("scheduled_at", dayEnd)
    .order("scheduled_at", { ascending: true });

  if (bookedError) throw bookedError;

  // 3. Get doctor's in_session status and max appointments
  const { data: profile } = await supabase
    .from("doctor_profiles")
    .select("in_session, max_appointments_per_day")
    .eq("doctor_id", body.doctor_id)
    .single();

  return successResponse(
    {
      doctor_id: body.doctor_id,
      date: body.date,
      day_of_week: dayOfWeek,
      availability_slots: availabilitySlots ?? [],
      booked_appointments: (bookedAppointments ?? []).map((a) => ({
        appointment_id: a.appointment_id,
        scheduled_at: a.scheduled_at,
        duration_minutes: a.duration_minutes,
        status: a.status,
      })),
      in_session: profile?.in_session ?? false,
      max_appointments_per_day: profile?.max_appointments_per_day ?? 10,
    },
    200,
    origin
  );
}

async function handleGetAppointments(
  body: GetAppointmentsRequest,
  auth: AuthResult,
  origin: string | null
): Promise<Response> {
  const supabase = getServiceClient();
  const limit = Math.min(body.limit ?? 50, 100);
  const offset = body.offset ?? 0;

  let query = supabase
    .from("appointments")
    .select(`
      appointment_id, doctor_id, patient_session_id, scheduled_at,
      duration_minutes, status, service_type, consultation_type,
      chief_complaint, consultation_fee, consultation_id,
      rescheduled_from, reminders_sent, grace_period_minutes,
      created_at, updated_at
    `)
    .order("scheduled_at", { ascending: false })
    .range(offset, offset + limit - 1);

  // Filter by role
  if (auth.userId) {
    // Doctor
    query = query.eq("doctor_id", auth.userId);
  } else if (auth.sessionId) {
    // Patient
    query = query.eq("patient_session_id", auth.sessionId);
  } else {
    throw new ValidationError("Authentication required");
  }

  // Optional status filter
  if (body.status) {
    query = query.eq("status", body.status);
  }

  const { data, error } = await query;
  if (error) throw error;

  return successResponse(
    { appointments: data ?? [], count: (data ?? []).length },
    200,
    origin
  );
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const identifier = auth.userId ?? auth.sessionId ?? "anon";
    await LIMITS.payment(identifier);

    const raw = await req.json();
    const body = validate(raw);

    switch (body.action) {
      case "book":
        return await handleBook(body as BookRequest, auth, origin);
      case "cancel":
        return await handleCancel(body as CancelRequest, auth, origin);
      case "get_slots":
        return await handleGetSlots(body as GetSlotsRequest, auth, origin);
      case "get_appointments":
        return await handleGetAppointments(body as GetAppointmentsRequest, auth, origin);
      default:
        throw new ValidationError("Unknown action");
    }
  } catch (err) {
    await logEvent({
      function_name: "book-appointment",
      level: "error",
      action: "request_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
