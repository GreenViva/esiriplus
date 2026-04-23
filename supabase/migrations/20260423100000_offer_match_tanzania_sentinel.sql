-- ============================================================================
-- Fix: location-offer matcher was producing wrong ancestors when a patient
-- session carried an inconsistent hierarchy (e.g. district='Kinondoni',
-- ward='Ipala' — 'Ipala' is a ward in Dodoma, not Kinondoni). The name-only
-- ward lookup would pick Dodoma's 'Ipala' and overwrite v_region with
-- 'Dodoma', so Dar es Salaam-scoped offers never matched, no redemption row
-- was inserted, and `redemption_count` stayed at 0 — capped offers could
-- never auto-terminate.
--
-- Also drops the obsolete 5-arg overloads of the matcher functions so every
-- caller binds to the hierarchy-aware 7-arg version (including the older
-- create-consultation edge function, which passes 5 named args).
--
-- Strategy:
--   * Hardens resolve_patient_location so a requested-but-unresolvable ward
--     cancels street resolution (otherwise a globally-unscoped street match
--     could silently re-introduce a wrong region).
--   * Rewrites match_location_offer / list_active_offers_for_patient to
--     canonicalise inputs through resolve_patient_location. After that,
--     ward/street are guaranteed to live under district.
--   * Backfills patient_sessions through the same resolver so already-dirty
--     rows become consistent immediately (no waiting for client re-resolve).
-- ============================================================================

DROP FUNCTION IF EXISTS match_location_offer(UUID, TEXT, TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS list_active_offers_for_patient(UUID, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION resolve_patient_location(
    p_region   TEXT,
    p_district TEXT,
    p_ward     TEXT,
    p_street   TEXT
)
RETURNS TABLE (region TEXT, district TEXT, ward TEXT, street TEXT)
LANGUAGE plpgsql STABLE SECURITY DEFINER AS $$
DECLARE
    v_region_id   UUID; v_district_id UUID; v_ward_id UUID; v_street_id UUID;
    v_region_n    TEXT; v_district_n TEXT; v_ward_n TEXT; v_street_n TEXT;
    v_ward_requested   BOOLEAN := p_ward   IS NOT NULL AND length(trim(p_ward))   > 0;
    v_street_requested BOOLEAN := p_street IS NOT NULL AND length(trim(p_street)) > 0;
BEGIN
    -- Region: ignore the TANZANIA sentinel (client default for "unknown").
    IF p_region IS NOT NULL AND length(trim(p_region)) > 0
       AND lower(trim(p_region)) <> 'tanzania' THEN
        SELECT l.id, l.name INTO v_region_id, v_region_n
          FROM tz_locations l
         WHERE l.level = 'region' AND lower(l.name) = lower(trim(p_region))
         LIMIT 1;
    END IF;

    IF p_district IS NOT NULL AND length(trim(p_district)) > 0 THEN
        SELECT l.id, l.name INTO v_district_id, v_district_n
          FROM tz_locations l
         WHERE l.level = 'district'
           AND lower(l.name) = lower(trim(p_district))
           AND (v_region_id IS NULL OR l.parent_id = v_region_id)
         LIMIT 1;
        IF v_district_id IS NOT NULL AND v_region_id IS NULL THEN
            SELECT r.id, r.name INTO v_region_id, v_region_n
              FROM tz_locations d JOIN tz_locations r ON r.id = d.parent_id
             WHERE d.id = v_district_id;
        END IF;
    END IF;

    IF v_ward_requested THEN
        SELECT l.id, l.name INTO v_ward_id, v_ward_n
          FROM tz_locations l
         WHERE l.level = 'ward'
           AND lower(l.name) = lower(trim(p_ward))
           AND (v_district_id IS NULL OR l.parent_id = v_district_id)
         LIMIT 1;
        IF v_ward_id IS NOT NULL THEN
            IF v_district_id IS NULL THEN
                SELECT d.id, d.name INTO v_district_id, v_district_n
                  FROM tz_locations w JOIN tz_locations d ON d.id = w.parent_id
                 WHERE w.id = v_ward_id;
            END IF;
            IF v_region_id IS NULL AND v_district_id IS NOT NULL THEN
                SELECT r.id, r.name INTO v_region_id, v_region_n
                  FROM tz_locations d JOIN tz_locations r ON r.id = d.parent_id
                 WHERE d.id = v_district_id;
            END IF;
        END IF;
    END IF;

    -- If ward was explicitly requested but couldn't resolve under the known
    -- district, skip street resolution entirely. An unscoped street match
    -- could otherwise backfill v_region with a wrong region (the Ipala bug).
    IF v_street_requested AND (NOT v_ward_requested OR v_ward_id IS NOT NULL) THEN
        SELECT l.id, l.name INTO v_street_id, v_street_n
          FROM tz_locations l
         WHERE l.level = 'street'
           AND lower(l.name) = lower(trim(p_street))
           AND (v_ward_id IS NULL OR l.parent_id = v_ward_id)
         LIMIT 1;
        IF v_street_id IS NOT NULL THEN
            IF v_ward_id IS NULL THEN
                SELECT w.id, w.name INTO v_ward_id, v_ward_n
                  FROM tz_locations s JOIN tz_locations w ON w.id = s.parent_id
                 WHERE s.id = v_street_id;
            END IF;
            IF v_district_id IS NULL AND v_ward_id IS NOT NULL THEN
                SELECT d.id, d.name INTO v_district_id, v_district_n
                  FROM tz_locations w JOIN tz_locations d ON d.id = w.parent_id
                 WHERE w.id = v_ward_id;
            END IF;
            IF v_region_id IS NULL AND v_district_id IS NOT NULL THEN
                SELECT r.id, r.name INTO v_region_id, v_region_n
                  FROM tz_locations d JOIN tz_locations r ON r.id = d.parent_id
                 WHERE d.id = v_district_id;
            END IF;
        END IF;
    END IF;

    region := v_region_n; district := v_district_n;
    ward   := v_ward_n;   street   := v_street_n;
    RETURN NEXT;
END;
$$;

GRANT EXECUTE ON FUNCTION resolve_patient_location(TEXT, TEXT, TEXT, TEXT)
    TO service_role, authenticated, anon;

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
    v_region TEXT; v_district TEXT; v_ward TEXT; v_street TEXT;
    v_offer  location_offers;
BEGIN
    SELECT r.region, r.district, r.ward, r.street
      INTO v_region, v_district, v_ward, v_street
      FROM resolve_patient_location(p_region, p_district, p_ward, p_street) r;

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
    v_region TEXT; v_district TEXT; v_ward TEXT; v_street TEXT;
BEGIN
    SELECT r.region, r.district, r.ward, r.street
      INTO v_region, v_district, v_ward, v_street
      FROM resolve_patient_location(p_region, p_district, p_ward, p_street) r;

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

GRANT EXECUTE ON FUNCTION match_location_offer(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO service_role, authenticated;
GRANT EXECUTE ON FUNCTION list_active_offers_for_patient(UUID, TEXT, TEXT, TEXT, TEXT, TEXT) TO service_role, authenticated;

-- ── Backfill: re-canonicalise every patient_sessions row. Only touches rows
-- whose current tuple disagrees with the canonical form, so idempotent.
-- Uses a CTE because PostgreSQL disallows LATERAL references to the UPDATE
-- target in the same statement's FROM clause.
WITH canonical AS (
    SELECT ps.session_id,
           r.region   AS c_region,
           r.district AS c_district,
           r.ward     AS c_ward,
           r.street   AS c_street
      FROM patient_sessions ps,
           LATERAL resolve_patient_location(
               ps.region, ps.service_district, ps.service_ward, ps.service_street
           ) r
)
UPDATE patient_sessions ps
   SET region           = c.c_region,
       service_district = c.c_district,
       service_ward     = c.c_ward,
       service_street   = c.c_street,
       updated_at       = now()
  FROM canonical c
 WHERE c.session_id = ps.session_id
   AND (ps.region           IS DISTINCT FROM c.c_region
     OR ps.service_district IS DISTINCT FROM c.c_district
     OR ps.service_ward     IS DISTINCT FROM c.c_ward
     OR ps.service_street   IS DISTINCT FROM c.c_street);
