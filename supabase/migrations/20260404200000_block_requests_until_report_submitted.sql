-- ============================================================================
-- Doctors stay "in session" until their report is submitted.
-- This prevents new consultation requests from arriving while the doctor
-- is still filling out the report for the previous consultation.
--
-- 1. Add report_submitted flag to consultations
-- 2. Update fn_sync_doctor_in_session to include completed+unreported
-- 3. Back-fill existing completed consultations that already have reports
-- ============================================================================

-- ── 1. Add column ────────────────────────────────────────────────────────────

ALTER TABLE consultations
  ADD COLUMN IF NOT EXISTS report_submitted BOOLEAN NOT NULL DEFAULT false;

-- ── 2. Update the in_session sync trigger ────────────────────────────────────
-- Doctor is "in session" when they have a consultation that is:
--   active / awaiting_extension / grace_period  (mid-consultation)
--   OR  completed + report NOT yet submitted    (writing report)

CREATE OR REPLACE FUNCTION fn_sync_doctor_in_session()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    UPDATE doctor_profiles
    SET in_session = EXISTS (
      SELECT 1 FROM consultations
      WHERE doctor_id = OLD.doctor_id
        AND (
          status IN ('active'::consultation_status_enum,
                     'awaiting_extension'::consultation_status_enum,
                     'grace_period'::consultation_status_enum)
          OR (status = 'completed'::consultation_status_enum AND report_submitted = false)
        )
    )
    WHERE doctor_id = OLD.doctor_id;
  ELSE
    UPDATE doctor_profiles
    SET in_session = EXISTS (
      SELECT 1 FROM consultations
      WHERE doctor_id = NEW.doctor_id
        AND (
          status IN ('active'::consultation_status_enum,
                     'awaiting_extension'::consultation_status_enum,
                     'grace_period'::consultation_status_enum)
          OR (status = 'completed'::consultation_status_enum AND report_submitted = false)
        )
    )
    WHERE doctor_id = NEW.doctor_id;

    IF TG_OP = 'UPDATE' AND OLD.doctor_id IS DISTINCT FROM NEW.doctor_id THEN
      UPDATE doctor_profiles
      SET in_session = EXISTS (
        SELECT 1 FROM consultations
        WHERE doctor_id = OLD.doctor_id
          AND (
            status IN ('active'::consultation_status_enum,
                       'awaiting_extension'::consultation_status_enum,
                       'grace_period'::consultation_status_enum)
            OR (status = 'completed'::consultation_status_enum AND report_submitted = false)
          )
      )
      WHERE doctor_id = OLD.doctor_id;
    END IF;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── 3. Recreate trigger to also fire on report_submitted changes ──────────────
-- The original trigger only fires on UPDATE OF status, so updating
-- report_submitted alone would NOT re-sync in_session.

DROP TRIGGER IF EXISTS trg_consultation_in_session ON consultations;
CREATE TRIGGER trg_consultation_in_session
  AFTER INSERT OR DELETE OR UPDATE OF status, report_submitted
  ON consultations
  FOR EACH ROW
  EXECUTE FUNCTION fn_sync_doctor_in_session();

-- ── 4. Back-fill: mark completed consultations that already have reports ──────

UPDATE consultations c
SET report_submitted = true
WHERE c.status = 'completed'
  AND c.report_submitted = false
  AND EXISTS (
    SELECT 1 FROM consultation_reports cr
    WHERE cr.consultation_id = c.consultation_id
  );

-- ── 4. Re-sync in_session for all doctors (pick up new logic) ────────────────

UPDATE doctor_profiles dp
SET in_session = EXISTS (
  SELECT 1 FROM consultations c
  WHERE c.doctor_id = dp.doctor_id
    AND (
      c.status IN ('active', 'awaiting_extension', 'grace_period')
      OR (c.status = 'completed' AND c.report_submitted = false)
    )
);
