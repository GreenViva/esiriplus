-- ============================================================================
-- Allow location_offers to target at any level of the hierarchy.
--
-- Before this migration, an offer had to specify a district. Now admins can
-- create offers at region, district, ward, or street granularity. The offer
-- matching RPCs infer the patient's ancestors from tz_locations so a request
-- that only carries a district can still match a region-level offer.
-- ============================================================================

-- ── 1. Schema changes ───────────────────────────────────────────────────────
ALTER TABLE location_offers
    ALTER COLUMN district DROP NOT NULL;

ALTER TABLE location_offers
    ADD COLUMN IF NOT EXISTS street TEXT;

-- At least one location field must be specified (region default of TANZANIA
-- doesn't count as a real target — treat it as sentinel).
-- We DON'T add a CHECK here because region has a default; we enforce in RPCs.

-- Add service_street to requests + consultations for per-street targeting
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS service_street TEXT;

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS service_street TEXT;

-- ── 2. Replace match_location_offer with hierarchy-aware version ────────────
-- Matching rules: for every level the OFFER specifies, the patient's
-- corresponding level must match (case-insensitive). Ancestors are derived
-- from tz_locations if the request doesn't carry them directly.
CREATE OR REPLACE FUNCTION match_location_offer(
    p_patient_session_id UUID,
    p_district           TEXT,    -- may be null; e.g. only region known
    p_ward               TEXT,
    p_service_type       TEXT,
    p_tier               TEXT,
    p_region             TEXT DEFAULT NULL,
    p_street             TEXT DEFAULT NULL
)
RETURNS location_offers
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
    v_region   TEXT := p_region;
    v_district TEXT := p_district;
    v_ward     TEXT := p_ward;
    v_street   TEXT := p_street;
    v_anc      RECORD;
    v_offer    location_offers;
BEGIN
    -- Infer missing ancestors from the most specific level the caller gave us
    IF v_street IS NOT NULL AND (v_region IS NULL OR v_district IS NULL OR v_ward IS NULL) THEN
        SELECT * INTO v_anc FROM tz_location_ancestors(v_street, 'street');
        v_region   := COALESCE(v_region,   v_anc.region);
        v_district := COALESCE(v_district, v_anc.district);
        v_ward     := COALESCE(v_ward,     v_anc.ward);
    END IF;
    IF v_ward IS NOT NULL AND (v_region IS NULL OR v_district IS NULL) THEN
        SELECT * INTO v_anc FROM tz_location_ancestors(v_ward, 'ward');
        v_region   := COALESCE(v_region,   v_anc.region);
        v_district := COALESCE(v_district, v_anc.district);
    END IF;
    IF v_district IS NOT NULL AND v_region IS NULL THEN
        SELECT * INTO v_anc FROM tz_location_ancestors(v_district, 'district');
        v_region := COALESCE(v_region, v_anc.region);
    END IF;

    SELECT o.*
      INTO v_offer
      FROM location_offers o
     WHERE o.is_active = TRUE
       -- For every level the OFFER specifies, patient must match
       AND (o.region   IS NULL OR o.region = 'TANZANIA'
            OR (v_region IS NOT NULL AND lower(o.region)   = lower(v_region)))
       AND (o.district IS NULL
            OR (v_district IS NOT NULL AND lower(o.district) = lower(v_district)))
       AND (o.ward     IS NULL
            OR (v_ward IS NOT NULL AND lower(o.ward)     = lower(v_ward)))
       AND (o.street   IS NULL
            OR (v_street IS NOT NULL AND lower(o.street)   = lower(v_street)))
       AND (cardinality(o.service_types) = 0 OR p_service_type = ANY(o.service_types))
       AND (cardinality(o.tiers)         = 0 OR p_tier         = ANY(o.tiers))
       AND NOT EXISTS (
           SELECT 1 FROM location_offer_redemptions r
            WHERE r.offer_id = o.offer_id
              AND r.patient_session_id = p_patient_session_id
       )
     ORDER BY
        -- Prefer more specific offers (more non-null location levels first)
        ((CASE WHEN o.street   IS NOT NULL THEN 1 ELSE 0 END) +
         (CASE WHEN o.ward     IS NOT NULL THEN 1 ELSE 0 END) +
         (CASE WHEN o.district IS NOT NULL THEN 1 ELSE 0 END)) DESC,
        o.created_at DESC
     LIMIT 1;

    RETURN v_offer;
END;
$$;

-- ── 3. Replace list_active_offers_for_patient with hierarchy-aware version ──
CREATE OR REPLACE FUNCTION list_active_offers_for_patient(
    p_patient_session_id UUID,
    p_district           TEXT,
    p_ward               TEXT,
    p_tier               TEXT,
    p_region             TEXT DEFAULT NULL,
    p_street             TEXT DEFAULT NULL
)
RETURNS SETOF location_offers
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
    v_region   TEXT := p_region;
    v_district TEXT := p_district;
    v_ward     TEXT := p_ward;
    v_street   TEXT := p_street;
    v_anc      RECORD;
BEGIN
    IF v_street IS NOT NULL AND (v_region IS NULL OR v_district IS NULL OR v_ward IS NULL) THEN
        SELECT * INTO v_anc FROM tz_location_ancestors(v_street, 'street');
        v_region   := COALESCE(v_region,   v_anc.region);
        v_district := COALESCE(v_district, v_anc.district);
        v_ward     := COALESCE(v_ward,     v_anc.ward);
    END IF;
    IF v_ward IS NOT NULL AND (v_region IS NULL OR v_district IS NULL) THEN
        SELECT * INTO v_anc FROM tz_location_ancestors(v_ward, 'ward');
        v_region   := COALESCE(v_region,   v_anc.region);
        v_district := COALESCE(v_district, v_anc.district);
    END IF;
    IF v_district IS NOT NULL AND v_region IS NULL THEN
        SELECT * INTO v_anc FROM tz_location_ancestors(v_district, 'district');
        v_region := COALESCE(v_region, v_anc.region);
    END IF;

    RETURN QUERY
    SELECT o.*
      FROM location_offers o
     WHERE o.is_active = TRUE
       AND (o.region   IS NULL OR o.region = 'TANZANIA'
            OR (v_region IS NOT NULL AND lower(o.region)   = lower(v_region)))
       AND (o.district IS NULL
            OR (v_district IS NOT NULL AND lower(o.district) = lower(v_district)))
       AND (o.ward     IS NULL
            OR (v_ward IS NOT NULL AND lower(o.ward)     = lower(v_ward)))
       AND (o.street   IS NULL
            OR (v_street IS NOT NULL AND lower(o.street)   = lower(v_street)))
       AND (cardinality(o.tiers) = 0 OR p_tier = ANY(o.tiers))
       AND NOT EXISTS (
           SELECT 1 FROM location_offer_redemptions r
            WHERE r.offer_id = o.offer_id
              AND r.patient_session_id = p_patient_session_id
       )
     ORDER BY
        ((CASE WHEN o.street   IS NOT NULL THEN 1 ELSE 0 END) +
         (CASE WHEN o.ward     IS NOT NULL THEN 1 ELSE 0 END) +
         (CASE WHEN o.district IS NOT NULL THEN 1 ELSE 0 END)) DESC,
        o.created_at DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION match_location_offer(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO service_role, authenticated;
GRANT EXECUTE ON FUNCTION list_active_offers_for_patient(UUID, TEXT, TEXT, TEXT, TEXT, TEXT) TO service_role, authenticated;
