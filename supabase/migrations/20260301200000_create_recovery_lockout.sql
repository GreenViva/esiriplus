-- Create the recovery_attempts table (referenced by recover-by-id and recover-by-questions).
-- Tracks failed/successful recovery attempts for brute-force lockout.
-- NOTE: Table may already exist from dashboard setup.

CREATE TABLE IF NOT EXISTS recovery_attempts (
  attempt_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id_hash text NOT NULL,
  ip_address text NOT NULL,
  success boolean NOT NULL DEFAULT false,
  attempted_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recovery_attempts_hash_time ON recovery_attempts (patient_id_hash, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_recovery_attempts_ip_time ON recovery_attempts (ip_address, attempted_at DESC);

-- Enable RLS — service_role only (edge functions use service client)
ALTER TABLE recovery_attempts ENABLE ROW LEVEL SECURITY;

-- The is_recovery_locked RPC: returns true if 10+ failed attempts in last 30 minutes
-- for the given hash OR IP address.
CREATE OR REPLACE FUNCTION is_recovery_locked(
  p_patient_id_hash text,
  p_ip_address text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  attempt_count integer;
BEGIN
  SELECT COUNT(*) INTO attempt_count
  FROM recovery_attempts
  WHERE (patient_id_hash = p_patient_id_hash OR ip_address = p_ip_address)
    AND success = false
    AND attempted_at > now() - interval '30 minutes';

  RETURN attempt_count >= 10;
END;
$$;
