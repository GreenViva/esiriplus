-- ============================================================================
-- Patient location hierarchy persistence + canonical-name resolver.
--
-- Until now patient_sessions only stored a free-text "region" string captured
-- at setup. Service flows then re-asked the patient to pick a district every
-- time, which created the bug where region-only offers (e.g. "50% off across
-- all of Dar es Salaam") never reached patients whose hierarchy wasn't fully
-- captured.
--
-- This migration:
--   1. Adds service_district / service_ward / service_street columns so the
--      full GPS-resolved hierarchy lives on the session — single source of
--      truth, no more nav-arg juggling on the client.
--   2. Introduces resolve_patient_location(...) which takes raw geocoder
--      output (potentially fuzzy: "Kinondoni Municipal", "Sinza B" …) and
--      returns the canonical tz_locations names for each level. The client
--      stores the canonical form, so all downstream offer/pricing matchers
--      compare like-for-like.
-- ============================================================================

-- ── 1. Schema additions ────────────────────────────────────────────────────
ALTER TABLE patient_sessions
    ADD COLUMN IF NOT EXISTS service_district TEXT,
    ADD COLUMN IF NOT EXISTS service_ward     TEXT,
    ADD COLUMN IF NOT EXISTS service_street   TEXT;

-- ── 2. Canonical-name resolver ─────────────────────────────────────────────
-- Strategy per level: case-insensitive exact match against tz_locations,
-- scoped by the previously-resolved ancestor where possible. If a level
-- doesn't match (e.g. geocoder returned a sub-village name we don't seed),
-- it returns NULL for that level — broader levels still come back so offer
-- matching at region/district works even when ward/street are unknown.
CREATE OR REPLACE FUNCTION resolve_patient_location(
    p_region   TEXT,
    p_district TEXT,
    p_ward     TEXT,
    p_street   TEXT
)
RETURNS TABLE (
    region   TEXT,
    district TEXT,
    ward     TEXT,
    street   TEXT
)
LANGUAGE plpgsql STABLE SECURITY DEFINER AS $$
DECLARE
    v_region_id   UUID;
    v_district_id UUID;
    v_ward_id     UUID;
    v_street_id   UUID;
    v_region_n    TEXT;
    v_district_n  TEXT;
    v_ward_n      TEXT;
    v_street_n    TEXT;
BEGIN
    -- Region: top-level, no parent constraint
    IF p_region IS NOT NULL AND length(trim(p_region)) > 0 THEN
        SELECT l.id, l.name INTO v_region_id, v_region_n
          FROM tz_locations l
         WHERE l.level = 'region'
           AND lower(l.name) = lower(trim(p_region))
         LIMIT 1;
    END IF;

    -- District: scoped to resolved region if we have one, otherwise global
    IF p_district IS NOT NULL AND length(trim(p_district)) > 0 THEN
        SELECT l.id, l.name INTO v_district_id, v_district_n
          FROM tz_locations l
         WHERE l.level = 'district'
           AND lower(l.name) = lower(trim(p_district))
           AND (v_region_id IS NULL OR l.parent_id = v_region_id)
         LIMIT 1;

        -- If district resolved but region didn't, derive region from district
        IF v_district_id IS NOT NULL AND v_region_id IS NULL THEN
            SELECT r.id, r.name INTO v_region_id, v_region_n
              FROM tz_locations d
              JOIN tz_locations r ON r.id = d.parent_id
             WHERE d.id = v_district_id;
        END IF;
    END IF;

    -- Ward: scoped to resolved district if we have one
    IF p_ward IS NOT NULL AND length(trim(p_ward)) > 0 THEN
        SELECT l.id, l.name INTO v_ward_id, v_ward_n
          FROM tz_locations l
         WHERE l.level = 'ward'
           AND lower(l.name) = lower(trim(p_ward))
           AND (v_district_id IS NULL OR l.parent_id = v_district_id)
         LIMIT 1;

        -- Backfill ancestors from ward if needed
        IF v_ward_id IS NOT NULL THEN
            IF v_district_id IS NULL THEN
                SELECT d.id, d.name INTO v_district_id, v_district_n
                  FROM tz_locations w
                  JOIN tz_locations d ON d.id = w.parent_id
                 WHERE w.id = v_ward_id;
            END IF;
            IF v_region_id IS NULL AND v_district_id IS NOT NULL THEN
                SELECT r.id, r.name INTO v_region_id, v_region_n
                  FROM tz_locations d
                  JOIN tz_locations r ON r.id = d.parent_id
                 WHERE d.id = v_district_id;
            END IF;
        END IF;
    END IF;

    -- Street: scoped to resolved ward if we have one
    IF p_street IS NOT NULL AND length(trim(p_street)) > 0 THEN
        SELECT l.id, l.name INTO v_street_id, v_street_n
          FROM tz_locations l
         WHERE l.level = 'street'
           AND lower(l.name) = lower(trim(p_street))
           AND (v_ward_id IS NULL OR l.parent_id = v_ward_id)
         LIMIT 1;

        IF v_street_id IS NOT NULL THEN
            IF v_ward_id IS NULL THEN
                SELECT w.id, w.name INTO v_ward_id, v_ward_n
                  FROM tz_locations s
                  JOIN tz_locations w ON w.id = s.parent_id
                 WHERE s.id = v_street_id;
            END IF;
            IF v_district_id IS NULL AND v_ward_id IS NOT NULL THEN
                SELECT d.id, d.name INTO v_district_id, v_district_n
                  FROM tz_locations w
                  JOIN tz_locations d ON d.id = w.parent_id
                 WHERE w.id = v_ward_id;
            END IF;
            IF v_region_id IS NULL AND v_district_id IS NOT NULL THEN
                SELECT r.id, r.name INTO v_region_id, v_region_n
                  FROM tz_locations d
                  JOIN tz_locations r ON r.id = d.parent_id
                 WHERE d.id = v_district_id;
            END IF;
        END IF;
    END IF;

    region   := v_region_n;
    district := v_district_n;
    ward     := v_ward_n;
    street   := v_street_n;
    RETURN NEXT;
END;
$$;

GRANT EXECUTE ON FUNCTION resolve_patient_location(TEXT, TEXT, TEXT, TEXT)
    TO service_role, authenticated, anon;
