// functions/check-location-offer/index.ts
// Returns the set of currently-applicable location offers for the authenticated
// patient's district + tier combination. Client uses this to preview discounts
// on the services screen *before* the patient books. Final authoritative
// discount is still applied server-side when the consultation is accepted.

import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

interface CheckOfferBody {
  service_region?: string;
  service_district?: string;
  service_ward?: string;
  service_street?: string;
  service_tier?: string;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    if (!auth.sessionId) {
      throw new ValidationError("Only patient sessions can check offers");
    }

    const raw = await req.json().catch(() => ({}));
    const body = raw as CheckOfferBody;

    const region = body.service_region?.trim() || null;
    const district = body.service_district?.trim() || null;
    const ward = body.service_ward?.trim() || null;
    const street = body.service_street?.trim() || null;

    // Need at least one location level to look anything up
    if (!region && !district && !ward && !street) {
      return successResponse({ offers: [] }, 200, origin);
    }
    const tier = (body.service_tier ?? "ECONOMY").toUpperCase();

    const supabase = getServiceClient();
    const { data, error } = await supabase.rpc("list_active_offers_for_patient", {
      p_patient_session_id: auth.sessionId,
      p_district: district,
      p_ward: ward,
      p_tier: tier,
      p_region: region,
      p_street: street,
    });

    if (error) {
      console.error("[check-location-offer] RPC error:", error.message);
      return successResponse({ offers: [] }, 200, origin);
    }

    // Normalise to the shape the client expects — strip audit columns
    const offers = (data ?? []).map((o: Record<string, unknown>) => ({
      offer_id:       o.offer_id,
      title:          o.title,
      description:    o.description,
      region:         o.region,
      district:       o.district,
      ward:           o.ward,
      street:         o.street,
      service_types:  o.service_types ?? [],
      tiers:          o.tiers ?? [],
      discount_type:  o.discount_type,
      discount_value: o.discount_value,
    }));

    return successResponse({ offers }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
