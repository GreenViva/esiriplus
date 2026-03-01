-- Migration: Enforce single active consultation per doctor + in_session flag
-- Replaces the old 10-slot capacity model with a strict one-active-at-a-time model.

-- 1. Partial unique index: only ONE active consultation per doctor
CREATE UNIQUE INDEX IF NOT EXISTS idx_consultations_doctor_active
  ON consultations (doctor_id)
  WHERE status = 'active';

-- 2. Add in_session flag to doctor_profiles for fast patient-side lookups
ALTER TABLE doctor_profiles
  ADD COLUMN IF NOT EXISTS in_session BOOLEAN NOT NULL DEFAULT false;

-- 3. Add configurable max appointments per day
ALTER TABLE doctor_profiles
  ADD COLUMN IF NOT EXISTS max_appointments_per_day SMALLINT NOT NULL DEFAULT 10;

-- 4. Trigger function: auto-sync in_session when consultation status changes
CREATE OR REPLACE FUNCTION fn_sync_doctor_in_session()
RETURNS TRIGGER AS $$
BEGIN
  -- On INSERT or UPDATE, check if the doctor has any active consultation
  IF TG_OP = 'DELETE' THEN
    UPDATE doctor_profiles
    SET in_session = EXISTS (
      SELECT 1 FROM consultations
      WHERE doctor_id = OLD.doctor_id
        AND status = 'active'
    )
    WHERE doctor_id = OLD.doctor_id;
  ELSE
    UPDATE doctor_profiles
    SET in_session = EXISTS (
      SELECT 1 FROM consultations
      WHERE doctor_id = NEW.doctor_id
        AND status = 'active'
    )
    WHERE doctor_id = NEW.doctor_id;

    -- Also handle the old doctor if doctor_id changed
    IF TG_OP = 'UPDATE' AND OLD.doctor_id IS DISTINCT FROM NEW.doctor_id THEN
      UPDATE doctor_profiles
      SET in_session = EXISTS (
        SELECT 1 FROM consultations
        WHERE doctor_id = OLD.doctor_id
          AND status = 'active'
      )
      WHERE doctor_id = OLD.doctor_id;
    END IF;
  END IF;

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Attach trigger to consultations table
DROP TRIGGER IF EXISTS trg_consultation_in_session ON consultations;
CREATE TRIGGER trg_consultation_in_session
  AFTER INSERT OR UPDATE OF status OR DELETE
  ON consultations
  FOR EACH ROW
  EXECUTE FUNCTION fn_sync_doctor_in_session();

-- 6. Back-fill existing data: mark doctors who currently have active consultations
UPDATE doctor_profiles dp
SET in_session = true
WHERE EXISTS (
  SELECT 1 FROM consultations c
  WHERE c.doctor_id = dp.doctor_id
    AND c.status = 'active'
);
