-- Add warning_count (cumulative) and warning_acknowledged (dismiss tracking)
ALTER TABLE doctor_profiles
  ADD COLUMN IF NOT EXISTS warning_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS warning_acknowledged BOOLEAN NOT NULL DEFAULT true;

-- Back-fill: doctors with an existing warning_message get count=1, unacknowledged
UPDATE doctor_profiles
SET warning_count = 1, warning_acknowledged = false
WHERE warning_message IS NOT NULL AND warning_count = 0;

-- Auto-increment warning_count when admin sets a new warning_message.
-- Also resets warning_acknowledged to false so the heartbeat restarts.
CREATE OR REPLACE FUNCTION fn_increment_warning_count()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.warning_message IS DISTINCT FROM OLD.warning_message
     AND NEW.warning_message IS NOT NULL THEN
    NEW.warning_count := COALESCE(OLD.warning_count, 0) + 1;
    NEW.warning_acknowledged := false;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_increment_warning_count ON doctor_profiles;
CREATE TRIGGER trg_increment_warning_count
  BEFORE UPDATE OF warning_message ON doctor_profiles
  FOR EACH ROW
  EXECUTE FUNCTION fn_increment_warning_count();
