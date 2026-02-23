// list-doctors – Returns doctor profiles filtered by specialty.
// Called by patient clients after service selection.

import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const { specialty } = await req.json();

    if (!specialty || typeof specialty !== "string") {
      return new Response(
        JSON.stringify({ error: "specialty is required" }),
        { status: 400, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
      );
    }

    const supabase = getServiceClient();

    const { data, error } = await supabase
      .from("doctor_profiles")
      .select("*")
      .eq("specialty", specialty)
      .eq("is_verified", true);

    if (error) {
      console.error("list-doctors query error:", error);
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
      );
    }

    return new Response(
      JSON.stringify({ doctors: data ?? [] }),
      { status: 200, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
    );
  } catch (err) {
    console.error("list-doctors error:", err);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { ...corsHeaders(req.headers.get("origin")), "Content-Type": "application/json" } },
    );
  }
});
