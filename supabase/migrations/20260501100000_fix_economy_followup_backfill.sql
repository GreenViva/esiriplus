-- ============================================================================
-- FIX: 20260429160000 didn't drop the old unique index, so the backfill's
-- ON CONFLICT DO NOTHING silently skipped inserting the follow_up earning
-- rows. Result: escrow ledger + consultation status released, but no
-- doctor_earnings rows. This fixes both halves: drops every prior unique
-- index on (doctor_id, consultation_id) regardless of name, ensures the
-- new (doctor_id, consultation_id, earning_type) index exists, and re-runs
-- the backfill.
-- ============================================================================

-- ── 1. Force-drop every unique index on doctor_earnings that targets just
--      (doctor_id, consultation_id), no matter what it's named.
DO $$
DECLARE
    v_idx RECORD;
BEGIN
    FOR v_idx IN
        SELECT i.relname AS index_name
          FROM pg_index ix
          JOIN pg_class i ON i.oid = ix.indexrelid
          JOIN pg_class t ON t.oid = ix.indrelid
         WHERE t.relname = 'doctor_earnings'
           AND ix.indisunique
           AND array_length(ix.indkey::int[], 1) = 2
    LOOP
        -- Verify the two indexed columns are doctor_id + consultation_id (in any order)
        IF EXISTS (
            SELECT 1
              FROM pg_attribute a
             WHERE a.attrelid = 'doctor_earnings'::regclass
               AND a.attname IN ('doctor_id', 'consultation_id')
               AND a.attnum = ANY(
                   (SELECT indkey FROM pg_index WHERE indexrelid =
                       (SELECT oid FROM pg_class WHERE relname = v_idx.index_name)
                   )::int[]
               )
            HAVING COUNT(*) = 2
        ) THEN
            RAISE NOTICE 'Dropping legacy unique index: %', v_idx.index_name;
            EXECUTE format('DROP INDEX IF EXISTS %I', v_idx.index_name);
        END IF;
    END LOOP;
END $$;

-- Also drop by known historical names for belt-and-suspenders.
DROP INDEX IF EXISTS doctor_earnings_doctor_consultation_uq;
ALTER TABLE doctor_earnings DROP CONSTRAINT IF EXISTS doctor_earnings_doctor_consultation_uq;
ALTER TABLE doctor_earnings DROP CONSTRAINT IF EXISTS doctor_earnings_consultation_id_key;

-- ── 2. Ensure the correct unique index is in place.
CREATE UNIQUE INDEX IF NOT EXISTS doctor_earnings_doctor_consultation_type_uq
    ON doctor_earnings (doctor_id, consultation_id, earning_type)
    WHERE earning_type NOT IN ('medication_reminder');

-- ── 3. Re-run backfill: now that the constraint allows it, insert the
--      follow_up earning rows that were silently skipped last time.
--
--      Detection: Economy consultations where escrow was released by the
--      previous backfill (status='released', release ledger entry written)
--      but no follow_up earning exists yet.
DO $$
DECLARE
    v_row RECORD;
    v_inserted_count INTEGER := 0;
BEGIN
    FOR v_row IN
        SELECT c.consultation_id, c.doctor_id, c.followup_escrow_amount
          FROM consultations c
         WHERE UPPER(COALESCE(c.service_tier, 'ECONOMY')) = 'ECONOMY'
           AND c.status = 'completed'
           AND c.last_reopened_at IS NOT NULL
           AND c.followup_escrow_status = 'released'
           AND COALESCE(c.followup_escrow_amount, 0) > 0
           AND c.doctor_id IS NOT NULL
           AND NOT EXISTS (
               SELECT 1 FROM doctor_earnings de
                WHERE de.doctor_id = c.doctor_id
                  AND de.consultation_id = c.consultation_id
                  AND de.earning_type = 'follow_up'
           )
    LOOP
        INSERT INTO doctor_earnings
            (doctor_id, consultation_id, amount, status, earning_type)
        VALUES
            (v_row.doctor_id, v_row.consultation_id,
             v_row.followup_escrow_amount, 'pending', 'follow_up');

        v_inserted_count := v_inserted_count + 1;
    END LOOP;

    RAISE NOTICE 'Re-backfill inserted % missing follow_up earning rows', v_inserted_count;
END $$;
