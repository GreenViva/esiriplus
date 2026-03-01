-- Ensure request_expires_at column exists on consultations table.
-- This column may have been missing, causing the create_consultation_with_cleanup
-- RPC to fail with "column request_expires_at does not exist".
ALTER TABLE consultations
  ADD COLUMN IF NOT EXISTS request_expires_at timestamptz;

-- Recreate the RPC now that the column is guaranteed to exist
CREATE OR REPLACE FUNCTION create_consultation_with_cleanup(
    p_patient_session_id text,
    p_doctor_id uuid,
    p_service_type text,
    p_consultation_type text DEFAULT 'chat',
    p_chief_complaint text DEFAULT '',
    p_consultation_fee numeric DEFAULT 5000,
    p_request_expires_at timestamptz DEFAULT NULL,
    p_request_id uuid DEFAULT NULL
)
RETURNS SETOF consultations
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    closed_count integer;
BEGIN
    -- Step 1: Close all non-completed consultations for this patient
    UPDATE consultations
    SET status = 'completed',
        updated_at = now()
    WHERE patient_session_id = p_patient_session_id::uuid
      AND status <> 'completed';

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    RAISE LOG 'create_consultation_with_cleanup: patient=%, closed=% stale consultations',
        p_patient_session_id, closed_count;

    -- Step 2: Insert and return the new consultation
    RETURN QUERY
    INSERT INTO consultations (
        patient_session_id,
        doctor_id,
        service_type,
        consultation_type,
        chief_complaint,
        status,
        consultation_fee,
        request_expires_at,
        request_id,
        created_at,
        updated_at
    ) VALUES (
        p_patient_session_id::uuid,
        p_doctor_id,
        p_service_type,
        p_consultation_type,
        p_chief_complaint,
        'active',
        p_consultation_fee,
        p_request_expires_at,
        p_request_id,
        now(),
        now()
    )
    RETURNING *;
END;
$$;
