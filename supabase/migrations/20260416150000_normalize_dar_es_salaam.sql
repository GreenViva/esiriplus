-- ============================================================================
-- Merge duplicate "Dar-es-salaam" region into canonical "Dar es Salaam".
--
-- The mtaa dataset seeded region name "Dar-es-salaam" (hyphenated), which
-- didn't match the canonical "Dar es Salaam" from the earlier hierarchy
-- migration. The result was TWO Dar es Salaam regions in the picker with
-- their own districts/wards/streets. This migration collapses them into one.
--
-- Safe to re-run: once merge is complete, the duplicate region no longer
-- exists and the procedure short-circuits.
-- ============================================================================

DO $merge$
DECLARE
    v_mtaa         UUID;
    v_canonical    UUID;
    v_dist_row     RECORD;
    v_canon_dist   UUID;
    v_ward_row     RECORD;
    v_canon_ward   UUID;
BEGIN
    SELECT id INTO v_mtaa      FROM tz_locations WHERE level = 'region' AND name = 'Dar-es-salaam'  LIMIT 1;
    SELECT id INTO v_canonical FROM tz_locations WHERE level = 'region' AND name = 'Dar es Salaam' LIMIT 1;

    -- Nothing to merge
    IF v_mtaa IS NULL THEN
        RETURN;
    END IF;

    -- No canonical region exists yet — just rename the duplicate
    IF v_canonical IS NULL THEN
        UPDATE tz_locations SET name = 'Dar es Salaam' WHERE id = v_mtaa;
        RETURN;
    END IF;

    -- ── Walk every district under the mtaa region ──────────────────────────
    FOR v_dist_row IN
        SELECT id, name FROM tz_locations
         WHERE parent_id = v_mtaa AND level = 'district'
    LOOP
        SELECT id INTO v_canon_dist FROM tz_locations
         WHERE parent_id = v_canonical
           AND level = 'district'
           AND lower(name) = lower(v_dist_row.name)
         LIMIT 1;

        IF v_canon_dist IS NULL THEN
            -- No canonical equivalent — just re-parent the district
            UPDATE tz_locations SET parent_id = v_canonical WHERE id = v_dist_row.id;
            CONTINUE;
        END IF;

        -- Canonical district exists — merge wards
        FOR v_ward_row IN
            SELECT id, name FROM tz_locations
             WHERE parent_id = v_dist_row.id AND level = 'ward'
        LOOP
            SELECT id INTO v_canon_ward FROM tz_locations
             WHERE parent_id = v_canon_dist
               AND level = 'ward'
               AND lower(name) = lower(v_ward_row.name)
             LIMIT 1;

            IF v_canon_ward IS NULL THEN
                -- No canonical equivalent — re-parent the whole ward subtree
                UPDATE tz_locations SET parent_id = v_canon_dist WHERE id = v_ward_row.id;
                CONTINUE;
            END IF;

            -- Move non-colliding streets to canonical ward
            UPDATE tz_locations AS mtaa_street
               SET parent_id = v_canon_ward
             WHERE mtaa_street.parent_id = v_ward_row.id
               AND mtaa_street.level = 'street'
               AND NOT EXISTS (
                   SELECT 1 FROM tz_locations c
                    WHERE c.parent_id = v_canon_ward
                      AND c.level = 'street'
                      AND lower(c.name) = lower(mtaa_street.name)
               );
            -- Drop any remaining (colliding) streets under the mtaa ward
            DELETE FROM tz_locations
             WHERE parent_id = v_ward_row.id AND level = 'street';
            -- Drop the now-empty mtaa ward
            DELETE FROM tz_locations WHERE id = v_ward_row.id;
        END LOOP;

        -- Delete the now-empty mtaa district
        DELETE FROM tz_locations WHERE id = v_dist_row.id;
    END LOOP;

    -- Finally remove the duplicate region
    DELETE FROM tz_locations WHERE id = v_mtaa;
END
$merge$;
