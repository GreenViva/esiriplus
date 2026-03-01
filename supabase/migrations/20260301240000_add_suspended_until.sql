-- Add suspended_until column for timed doctor suspensions
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMPTZ;

-- Partial index for the cron job that lifts expired suspensions
CREATE INDEX IF NOT EXISTS idx_doctor_profiles_suspended_until
  ON doctor_profiles (suspended_until)
  WHERE suspended_until IS NOT NULL;
