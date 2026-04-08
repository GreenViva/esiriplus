// list-doctors – Returns doctor profiles filtered by specialty.
// Called by patient clients after service selection.

import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const clientIp = getClientIp(req) ?? "unknown";
    await checkRateLimit(`list-doctors:${clientIp}`, 30, 60);

    const { specialty } = await req.json();

    if (!specialty || typeof specialty !== "string") {
      return new Response(
        JSON.stringify({ error: "specialty is required" }),
        { status: 400, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
      );
    }

    const supabase = getServiceClient();

    const selectFields = "doctor_id, full_name, email, phone, specialty, specialist_field, languages, bio, license_number, years_experience, profile_photo_url, average_rating, total_ratings, is_verified, is_available, services, country_code, country, can_serve_as_gp, created_at, updated_at";

    // When listing GP doctors, also include specialists who have "Serve as GP" enabled.
    // They appear alongside regular GPs but are paid at GP rates (not specialist rates).
    let query = supabase
      .from("doctor_profiles")
      .select(selectFields)
      .eq("is_verified", true)
      .eq("is_banned", false)
      .or("suspended_until.is.null,suspended_until.lte." + new Date().toISOString());

    if (specialty === "gp") {
      // GP listing: regular GPs + specialists serving as GP
      query = query.or("specialty.eq.gp,and(specialty.eq.specialist,can_serve_as_gp.eq.true)");
    } else {
      query = query.eq("specialty", specialty);
    }

    const { data, error } = await query;

    if (error) {
      console.error("list-doctors query error:", error);
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
      );
    }

    return new Response(
      JSON.stringify({ doctors: data ?? [] }),
      {
        status: 200,
        headers: {
          ...corsHeaders(req.headers.get("origin")),
          "Content-Type": "application/json",
          "Cache-Control": "no-cache",
        },
      },
    );
  } catch (err) {
    console.error("list-doctors error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
    );
  }
});
