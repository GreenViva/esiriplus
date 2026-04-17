-- ============================================================================
-- Soft-delete for location offers.
--
-- Clicking "Delete" on an offer sets `deleted_at = now()` (and also flips
-- `is_active = false`). The main admin list hides soft-deleted offers; a
-- recycle-bin view surfaces them with Restore / Delete-permanently actions.
--
-- Both matcher RPCs filter out deleted offers as a safety net — even if
-- something restores is_active = true without clearing deleted_at, a deleted
-- offer never matches.
-- ============================================================================

ALTER TABLE location_offers
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_location_offers_deleted_at
    ON location_offers (deleted_at) WHERE deleted_at IS NOT NULL;

-- ── Re-create matchers to exclude soft-deleted offers ───────────────────────
CREATE OR REPLACE FUNCTION match_location_offer(
    p_patient_session_id UUID,
    p_district           TEXT,
    p_ward               TEXT,
    p_service_type       TEXT,
    p_tier               TEXT,
    p_region             TEXT DEFAULT NULL,
    p_street             TEXT DEFAULT NULL
)
RETURNS location_offers
LANGUAGE plpgsql STABLE SECURITY DEFINER AS $$
DECLARE
    v_region   TEXT := p_region;
    v_district TEXT := p_district;
    v_ward     TEXT := p_ward;
    v_street   TEXT := p_street;
    v_anc      RECORD;
    v_offer    location_offers;
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

    SELECT o.*
      INTO v_offer
      FROM location_offers o
     WHERE o.is_active = TRUE
       AND o.deleted_at IS NULL
       AND (o.expires_at IS NULL OR o.expires_at > now())
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
        ((CASE WHEN o.street   IS NOT NULL THEN 1 ELSE 0 END) +
         (CASE WHEN o.ward     IS NOT NULL THEN 1 ELSE 0 END) +
         (CASE WHEN o.district IS NOT NULL THEN 1 ELSE 0 END)) DESC,
        o.created_at DESC
     LIMIT 1;

    RETURN v_offer;
END;
$$;

CREATE OR REPLACE FUNCTION list_active_offers_for_patient(
    p_patient_session_id UUID,
    p_district           TEXT,
    p_ward               TEXT,
    p_tier               TEXT,
    p_region             TEXT DEFAULT NULL,
    p_street             TEXT DEFAULT NULL
)
RETURNS SETOF location_offers
LANGUAGE plpgsql STABLE SECURITY DEFINER AS $$
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
       AND o.deleted_at IS NULL
       AND (o.expires_at IS NULL OR o.expires_at > now())
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
