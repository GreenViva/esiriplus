-- Add service_type to payments table so mpesa-callback knows which service was purchased.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS service_type text;

-- RPC to atomically add video call minutes to a consultation.
-- Uses the existing call_minutes_remaining column on consultations.
CREATE OR REPLACE FUNCTION add_call_minutes(
  p_consultation_id uuid,
  p_minutes integer
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE consultations
  SET call_minutes_remaining = call_minutes_remaining + p_minutes,
      updated_at = now()
  WHERE consultation_id = p_consultation_id;
END;
$$;
