-- ============================================================================
-- Optional expiry on location offers.
--
-- Admins can set `expires_at` so an offer auto-deactivates after a duration
-- (hours or days). Matching RPCs filter expired offers out immediately; no
-- background job is required for correctness. The admin list shows time
-- remaining / "Expired Xh ago" based on this column.
-- ============================================================================

ALTER TABLE location_offers
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

-- Expiry must be in the future at insert time (small nuance: on UPDATE we
-- allow it to be in the past so the offer can be retroactively "expired").
ALTER TABLE location_offers
    DROP CONSTRAINT IF EXISTS location_offers_expires_in_future_on_insert;

-- Re-create the matcher functions with an additional expires_at filter. The
-- signatures are unchanged so the edge functions and admin panel don't need
-- updates for this part.
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

-- Also guard the redemption-cap trigger so a race between "expired" and
-- "just accepted" can't record a redemption against a dead offer.
CREATE OR REPLACE FUNCTION enforce_offer_cap()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_max     INTEGER;
    v_count   INTEGER;
    v_active  BOOLEAN;
    v_expires TIMESTAMPTZ;
BEGIN
    SELECT max_redemptions, redemption_count, is_active, expires_at
      INTO v_max, v_count, v_active, v_expires
      FROM location_offers
     WHERE offer_id = NEW.offer_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Offer % does not exist', NEW.offer_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF NOT v_active THEN
        RAISE EXCEPTION 'Offer % is not active', NEW.offer_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_expires IS NOT NULL AND v_expires <= now() THEN
        RAISE EXCEPTION 'Offer % has expired', NEW.offer_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_max IS NOT NULL AND v_count >= v_max THEN
        RAISE EXCEPTION 'Offer % redemption cap reached', NEW.offer_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_max IS NOT NULL AND v_count + 1 >= v_max THEN
        UPDATE location_offers
           SET redemption_count = v_count + 1,
               is_active        = FALSE,
               terminated_at    = COALESCE(terminated_at, now())
         WHERE offer_id = NEW.offer_id;
    ELSE
        UPDATE location_offers
           SET redemption_count = v_count + 1
         WHERE offer_id = NEW.offer_id;
    END IF;

    RETURN NEW;
END;
$$;
