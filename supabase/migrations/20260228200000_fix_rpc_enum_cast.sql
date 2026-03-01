-- Fix: create_consultation_with_cleanup RPC fails with
--   "column service_type is of type service_type_enum but expression is of type text"
-- because the RPC parameters are text but the consultations table uses enum columns.
-- Solution: explicitly cast text parameters to the enum types.

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
    SET status = 'completed'::consultation_status_enum,
        updated_at = now()
    WHERE patient_session_id = p_patient_session_id::uuid
      AND status <> 'completed'::consultation_status_enum;

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    RAISE LOG 'create_consultation_with_cleanup: patient=%, closed=% stale consultations',
        p_patient_session_id, closed_count;

    -- Step 2: Insert and return the new consultation
    -- CRITICAL: Cast text params to enum types to match the column definitions.
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
        p_service_type::service_type_enum,
        p_consultation_type,
        p_chief_complaint,
        'active'::consultation_status_enum,
        p_consultation_fee,
        p_request_expires_at,
        p_request_id,
        now(),
        now()
    )
    RETURNING *;
END;
$$;

-- Also fix close_stale_consultations to use enum cast
CREATE OR REPLACE FUNCTION close_stale_consultations(p_patient_session_id text)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    closed_count integer;
BEGIN
    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        updated_at = now()
    WHERE patient_session_id = p_patient_session_id::uuid
      AND status <> 'completed'::consultation_status_enum;

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    RAISE LOG 'close_stale_consultations: patient_session_id=%, closed=%',
        p_patient_session_id, closed_count;

    RETURN closed_count;
EXCEPTION
    WHEN invalid_text_representation THEN
        RAISE LOG 'close_stale_consultations: invalid uuid format for %', p_patient_session_id;
        RETURN 0;
    WHEN OTHERS THEN
        RAISE LOG 'close_stale_consultations: unexpected error: %', SQLERRM;
        RETURN 0;
END;
$$;
