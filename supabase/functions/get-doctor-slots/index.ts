// get-doctor-slots – Returns appointment slot availability per doctor.
// Accepts { doctor_ids: string[] } (max 50). Uses service client (no JWT required).

import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { getClientIp } from "../_shared/logger.ts";

const MAX_DOCTOR_SLOTS = 10;
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

    // Count active consultations per doctor in a single query
    const { data, error } = await supabase
      .from("consultations")
      .select("doctor_id")
      .in("doctor_id", doctor_ids)
      .in("status", ["pending", "active", "in_progress"]);

    if (error) {
      console.error("get-doctor-slots query error:", error);
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers },
      );
    }

    // Tally used slots per doctor
    const usedMap: Record<string, number> = {};
    for (const row of data ?? []) {
      usedMap[row.doctor_id] = (usedMap[row.doctor_id] ?? 0) + 1;
    }

    // Build response with all requested doctor IDs
    const slots: Record<string, { used: number; available: number; total: number }> = {};
    for (const id of doctor_ids) {
      const used = usedMap[id] ?? 0;
      slots[id] = {
        used,
        available: Math.max(0, MAX_DOCTOR_SLOTS - used),
        total: MAX_DOCTOR_SLOTS,
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
