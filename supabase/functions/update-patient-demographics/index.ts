// update-patient-demographics: Updates demographic and location data on patient_sessions.
// Called after patient completes the health profile setup (sex, age, region, etc.)
// All fields are optional — patient can skip any of them.

import { corsHeaders, handleCors } from "../_shared/cors.ts";
import { handleError } from "../_shared/errors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return handleCors(req);

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "patient");

    const sessionId = auth.sessionId;
    if (!sessionId) {
      return new Response(
        JSON.stringify({ error: "No session ID found" }),
        { status: 400, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
      );
    }

    const body = await req.json().catch(() => ({}));

    // Build update object — only include non-null fields
    const update: Record<string, unknown> = { updated_at: new Date().toISOString() };

    if (typeof body.region === "string" && body.region.trim()) {
      update.region = body.region.trim();
    }
    // Full GPS-resolved hierarchy. Each level may be null when the geocoder
    // / resolver couldn't pin it down — that's expected and fine for matching.
    if (typeof body.service_district === "string") {
      update.service_district = body.service_district.trim() || null;
    }
    if (typeof body.service_ward === "string") {
      update.service_ward = body.service_ward.trim() || null;
    }
    if (typeof body.service_street === "string") {
      update.service_street = body.service_street.trim() || null;
    }
    if (typeof body.sex === "string" && body.sex.trim()) {
      update.sex = body.sex.trim();
    }
    if (typeof body.age === "string" && body.age.trim()) {
      update.age = body.age.trim();
    }
    if (typeof body.blood_type === "string" && body.blood_type.trim()) {
      update.blood_type = body.blood_type.trim();
    }
    if (typeof body.allergies === "string") {
      update.allergies = body.allergies.trim() || null;
    }
    if (typeof body.chronic_conditions === "string") {
      update.chronic_conditions = body.chronic_conditions.trim() || null;
    }

    // Must have at least one field to update (besides updated_at)
    if (Object.keys(update).length <= 1) {
      return new Response(
        JSON.stringify({ error: "No fields to update" }),
        { status: 400, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
      );
    }

    const supabase = getServiceClient();
    const { error } = await supabase
      .from("patient_sessions")
      .update(update)
      .eq("session_id", sessionId);

    if (error) {
      console.error("[update-patient-demographics] DB error:", error.message);
      return new Response(
        JSON.stringify({ error: "Failed to update demographics" }),
        { status: 500, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
      );
    }

    console.log(`[update-patient-demographics] Updated session=${sessionId.slice(0, 8)}... fields=${Object.keys(update).filter(k => k !== "updated_at").join(",")}`);

    return new Response(
      JSON.stringify({ message: "Demographics updated" }),
      { status: 200, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
    );
  } catch (err) {
    return handleError(err, req);
  }
});
