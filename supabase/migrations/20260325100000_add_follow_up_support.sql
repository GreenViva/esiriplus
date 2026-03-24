-- Add follow-up support for Royal consultations.
-- Royal patients get unlimited free follow-ups within 14 days of consultation completion.

-- ── 1. Add follow-up fields to consultation_requests ─────────────────────────
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS is_follow_up BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS parent_consultation_id UUID REFERENCES consultations(consultation_id);

-- ── 2. Add parent + follow-up expiry to consultations ────────────────────────
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS parent_consultation_id UUID REFERENCES consultations(consultation_id);
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS follow_up_expiry TIMESTAMPTZ;

-- ── 3. Update create_consultation_with_cleanup to accept parent_consultation_id
CREATE OR REPLACE FUNCTION create_consultation_with_cleanup(
    p_patient_session_id text,
    p_doctor_id uuid,
    p_service_type text,
    p_consultation_type text DEFAULT 'chat',
    p_chief_complaint text DEFAULT '',
    p_consultation_fee numeric DEFAULT 5000,
    p_request_expires_at timestamptz DEFAULT NULL,
    p_request_id uuid DEFAULT NULL,
    p_service_tier text DEFAULT 'ECONOMY',
    p_parent_consultation_id uuid DEFAULT NULL
)
RETURNS SETOF consultations
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    closed_count integer;
    v_duration SMALLINT;
BEGIN
    v_duration := get_service_duration_minutes(p_service_type);

    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        updated_at = now()
    WHERE patient_session_id = p_patient_session_id::uuid
      AND status NOT IN ('completed'::consultation_status_enum);

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    RAISE LOG 'create_consultation_with_cleanup: patient=%, tier=%, closed=% stale consultations',
        p_patient_session_id, p_service_tier, closed_count;

    RETURN QUERY
    INSERT INTO consultations (
        patient_session_id,
        doctor_id,
        service_type,
        service_tier,
        consultation_type,
        chief_complaint,
        status,
        consultation_fee,
        request_expires_at,
        request_id,
        session_start_time,
        scheduled_end_at,
        original_duration_minutes,
        extension_count,
        parent_consultation_id,
        created_at,
        updated_at
    ) VALUES (
        p_patient_session_id::uuid,
        p_doctor_id,
        p_service_type::service_type_enum,
        p_service_tier,
        p_consultation_type,
        p_chief_complaint,
        'active'::consultation_status_enum,
        p_consultation_fee,
        p_request_expires_at,
        p_request_id,
        now(),
        now() + (v_duration || ' minutes')::interval,
        v_duration,
        0,
        p_parent_consultation_id,
        now(),
        now()
    )
    RETURNING *;
END;
$$;

-- ── 4. Stamp follow_up_expiry server-side when consultation ends ─────────────
-- Find and update the end_consultation or manage-consultation RPC to set
-- follow_up_expiry = now() + interval '14 days' for Royal consultations.
-- This is handled in the manage-consultation edge function instead (no RPC change needed).
