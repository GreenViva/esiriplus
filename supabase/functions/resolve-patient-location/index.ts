// resolve-patient-location: Takes raw GPS-geocoded strings, canonicalises them
// against tz_locations, and persists the resulting region/district/ward/street
// tuple on the caller's patient_sessions row. Returns the canonical tuple so
// the client can mirror it locally.
//
// The Android app calls this from its LocationResolver after FusedLocation +
// Geocoder produce raw admin-area names. Centralising resolution here keeps
// the client free of any tz_locations cache and means future seed updates
// (new wards, renamed districts) take effect without an app release.

import { handlePreflight } from "../_shared/cors.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface RawLocationBody {
  region?: string;
  district?: string;
  ward?: string;
  street?: string;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    if (!auth.sessionId) {
      throw new ValidationError("Only patient sessions can resolve location");
    }

    const body = (await req.json().catch(() => ({}))) as RawLocationBody;

    const region   = body.region?.trim()   || null;
    const district = body.district?.trim() || null;
    const ward     = body.ward?.trim()     || null;
    const street   = body.street?.trim()   || null;

    if (!region && !district && !ward && !street) {
      throw new ValidationError("At least one location level required");
    }

    const supabase = getServiceClient();

    // Canonicalise via RPC — returns one row with possibly-null fields per level.
    const { data, error: rpcError } = await supabase.rpc(
      "resolve_patient_location",
      {
        p_region:   region,
        p_district: district,
        p_ward:     ward,
        p_street:   street,
      },
    );

    if (rpcError) {
      console.error("[resolve-patient-location] RPC error:", rpcError.message);
      return errorResponse(new Error("Failed to resolve location"), origin);
    }

    const resolved = (data?.[0] ?? {}) as {
      region?: string | null;
      district?: string | null;
      ward?: string | null;
      street?: string | null;
    };

    // Persist canonical form on the session
    const { error: updateError } = await supabase
      .from("patient_sessions")
      .update({
        region:           resolved.region   ?? null,
        service_district: resolved.district ?? null,
        service_ward:     resolved.ward     ?? null,
        service_street:   resolved.street   ?? null,
        updated_at:       new Date().toISOString(),
      })
      .eq("session_id", auth.sessionId);

    if (updateError) {
      console.error("[resolve-patient-location] Update error:", updateError.message);
      return errorResponse(new Error("Failed to save location"), origin);
    }

    console.log(
      `[resolve-patient-location] session=${auth.sessionId.slice(0, 8)}... ` +
      `raw=(${region}/${district}/${ward}/${street}) ` +
      `canonical=(${resolved.region}/${resolved.district}/${resolved.ward}/${resolved.street})`,
    );

    return successResponse(
      {
        region:   resolved.region   ?? null,
        district: resolved.district ?? null,
        ward:     resolved.ward     ?? null,
        street:   resolved.street   ?? null,
      },
      200,
      origin,
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
