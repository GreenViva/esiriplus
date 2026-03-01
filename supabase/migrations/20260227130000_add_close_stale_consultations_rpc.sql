-- RPC function to close stale open consultations for a patient.
-- Called by handle-consultation-request edge function before creating a new consultation.
-- Uses explicit uuid cast to avoid text↔uuid type mismatch.
-- Returns the count of closed consultations.

CREATE OR REPLACE FUNCTION close_stale_consultations(p_patient_session_id text)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  closed_count integer;
BEGIN
  UPDATE consultations
  SET status = 'completed',
      updated_at = now()
  WHERE patient_session_id = p_patient_session_id::uuid
    AND status NOT IN ('completed');

  GET DIAGNOSTICS closed_count = ROW_COUNT;

  RAISE LOG 'close_stale_consultations: patient_session_id=%, closed=%',
    p_patient_session_id, closed_count;

  RETURN closed_count;
END;
$$;
