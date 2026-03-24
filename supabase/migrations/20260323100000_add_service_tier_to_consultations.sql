-- Migration: Add service_tier to consultation_requests and consultations tables.
--
-- service_tier was tracked only on the Android client (via navigation route state)
-- but never persisted server-side. This caused:
--   1. manage-consultation sync to fail after service_tier was added to the SELECT query
--   2. Royal follow-up window logic to never activate (Room always stored 'ECONOMY')
--
-- This migration is idempotent (ADD COLUMN IF NOT EXISTS).

-- ── 1. Add service_tier to consultation_requests ──────────────────────────────
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS service_tier TEXT NOT NULL DEFAULT 'ECONOMY';

-- ── 2. Add service_tier to consultations ─────────────────────────────────────
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS service_tier TEXT NOT NULL DEFAULT 'ECONOMY';

-- ── 3. Recreate create_consultation_with_cleanup with service_tier ────────────
CREATE OR REPLACE FUNCTION create_consultation_with_cleanup(
    p_patient_session_id text,
    p_doctor_id uuid,
    p_service_type text,
    p_consultation_type text DEFAULT 'chat',
    p_chief_complaint text DEFAULT '',
    p_consultation_fee numeric DEFAULT 5000,
    p_request_expires_at timestamptz DEFAULT NULL,
    p_request_id uuid DEFAULT NULL,
    p_service_tier text DEFAULT 'ECONOMY'
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
        now(),
        now()
    )
    RETURNING *;
END;
$$;
