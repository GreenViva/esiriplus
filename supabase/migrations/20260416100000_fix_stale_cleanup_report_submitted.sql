-- ============================================================================
-- Fix: stale consultation cleanup must set report_submitted = true
--
-- fn_close_stale_consultations() was written before the report_submitted
-- requirement was added (20260404).  It sets status → completed but leaves
-- report_submitted = false, which causes fn_sync_doctor_in_session() to
-- keep in_session = true permanently — no one will ever submit a report
-- for an auto-closed session.
--
-- 1. Update fn_close_stale_consultations to also set report_submitted = true
-- 2. Fix any currently stuck doctors (completed + unreported + stale)
-- ============================================================================

-- ── 1. Patch the cleanup function ───────────────────────────────────────────

CREATE OR REPLACE FUNCTION fn_close_stale_consultations()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    closed_count integer;
BEGIN
    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        report_submitted = true,
        updated_at = now()
    WHERE (
        -- Active consultations older than 2 hours
        (status = 'active'::consultation_status_enum
         AND updated_at < now() - interval '2 hours')
        OR
        -- Awaiting extension for more than 30 minutes (both parties left)
        (status = 'awaiting_extension'::consultation_status_enum
         AND updated_at < now() - interval '30 minutes')
        OR
        -- Grace period for more than 30 minutes (payment never completed)
        (status = 'grace_period'::consultation_status_enum
         AND updated_at < now() - interval '30 minutes')
    );

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    IF closed_count > 0 THEN
        RAISE LOG 'fn_close_stale_consultations: auto-closed % stale consultations', closed_count;
    END IF;
END;
$$;

-- ── 2. Unstick currently affected doctors ───────────────────────────────────
-- Mark completed consultations as report_submitted if they are stale
-- (completed more than 2 hours ago and no report was ever generated).

UPDATE consultations
SET report_submitted = true,
    updated_at = now()
WHERE status = 'completed'::consultation_status_enum
  AND report_submitted = false
  AND updated_at < now() - interval '2 hours'
  AND NOT EXISTS (
    SELECT 1 FROM consultation_reports cr
    WHERE cr.consultation_id = consultations.consultation_id
  );

-- ── 3. Re-sync in_session for all doctors ───────────────────────────────────

UPDATE doctor_profiles dp
SET in_session = EXISTS (
  SELECT 1 FROM consultations c
  WHERE c.doctor_id = dp.doctor_id
    AND (
      c.status IN ('active', 'awaiting_extension', 'grace_period')
      OR (c.status = 'completed' AND c.report_submitted = false)
    )
);
