// get-doctor-slots – Returns appointment slot availability per doctor.
// Accepts { doctor_ids: string[] } (max 50). Uses service client (no JWT required).
// Now uses the in_session boolean from doctor_profiles instead of counting consultations.

import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { getClientIp } from "../_shared/logger.ts";

const MAX_IDS_PER_REQUEST = 50;

Deno.serve(async (req) => {
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  const origin = req.headers.get("origin");
  const headers = {
    ...corsHeaders(origin),
    "Content-Type": "application/json",
  };

  try {
    // Rate limit by IP (30 req/min)
    const clientIp = getClientIp(req) ?? "unknown";
    await checkRateLimit(`get-doctor-slots:${clientIp}`, 30, 60);

    const { doctor_ids } = await req.json();

    if (
      !Array.isArray(doctor_ids) ||
      doctor_ids.length === 0 ||
      doctor_ids.length > MAX_IDS_PER_REQUEST
    ) {
      return new Response(
        JSON.stringify({
          error: `doctor_ids must be an array of 1–${MAX_IDS_PER_REQUEST} strings`,
        }),
        { status: 400, headers },
      );
    }

    const supabase = getServiceClient();

    // Fetch in_session status and max_appointments_per_day from doctor_profiles
    const { data: profiles, error: profileError } = await supabase
      .from("doctor_profiles")
      .select("doctor_id, in_session, max_appointments_per_day")
      .in("doctor_id", doctor_ids);

    if (profileError) {
      console.error("get-doctor-slots profile query error:", profileError);
      return new Response(
        JSON.stringify({ error: profileError.message }),
        { status: 500, headers },
      );
    }

    // Count today's booked/confirmed/in_progress appointments per doctor
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    const todayEnd = new Date();
    todayEnd.setHours(23, 59, 59, 999);

    const { data: todayAppointments, error: aptError } = await supabase
      .from("appointments")
      .select("doctor_id")
      .in("doctor_id", doctor_ids)
      .in("status", ["booked", "confirmed", "in_progress"])
      .gte("scheduled_at", todayStart.toISOString())
      .lte("scheduled_at", todayEnd.toISOString());

    if (aptError) {
      console.error("get-doctor-slots appointments query error:", aptError);
    }

    // Tally today's appointments per doctor
    const todayCountMap: Record<string, number> = {};
    for (const row of todayAppointments ?? []) {
      todayCountMap[row.doctor_id] = (todayCountMap[row.doctor_id] ?? 0) + 1;
    }

    // Build profile map
    const profileMap: Record<string, { in_session: boolean; max_appointments_per_day: number }> = {};
    for (const p of profiles ?? []) {
      profileMap[p.doctor_id] = {
        in_session: p.in_session ?? false,
        max_appointments_per_day: p.max_appointments_per_day ?? 10,
      };
    }

    // Build response
    const slots: Record<string, {
      in_session: boolean;
      today_booked: number;
      max_per_day: number;
      available_today: number;
    }> = {};

    for (const id of doctor_ids) {
      const profile = profileMap[id] ?? { in_session: false, max_appointments_per_day: 10 };
      const todayBooked = todayCountMap[id] ?? 0;
      slots[id] = {
        in_session: profile.in_session,
        today_booked: todayBooked,
        max_per_day: profile.max_appointments_per_day,
        available_today: Math.max(0, profile.max_appointments_per_day - todayBooked),
      };
    }

    return new Response(
      JSON.stringify({ slots }),
      { status: 200, headers },
    );
  } catch (err) {
    console.error("get-doctor-slots error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers },
    );
  }
});
