-- Migration: Fix consultation timer — ensure create_consultation_with_cleanup
-- always sets session_start_time, scheduled_end_at, and original_duration_minutes.
--
-- Problem: Earlier versions of this RPC (20260228100000, 20260228110000,
-- 20260228200000) did not set the timer fields, so new consultations were
-- inserted with NULL session_start_time / scheduled_end_at and the column
-- default (15) for original_duration_minutes.  The Android client computed
-- remaining = 0 and immediately triggered timer-expired, locking the doctor
-- in the AWAITING_EXTENSION state seconds after entering the chat.
--
-- This migration is idempotent — it recreates the RPC and helper function
-- using CREATE OR REPLACE, so re-running it is safe.

-- ── 1. Ensure timer columns exist (idempotent) ────────────────────────────────

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS session_start_time     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS scheduled_end_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS original_duration_minutes SMALLINT NOT NULL DEFAULT 15,
    ADD COLUMN IF NOT EXISTS extension_count        SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS grace_period_end_at    TIMESTAMPTZ;

-- ── 2. Recreate the duration helper (idempotent) ─────────────────────────────

CREATE OR REPLACE FUNCTION get_service_duration_minutes(p_service_type text)
RETURNS SMALLINT
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
  RETURN CASE p_service_type
    WHEN 'nurse'             THEN 15
    WHEN 'clinical_officer'  THEN 15
    WHEN 'pharmacist'        THEN  5
    WHEN 'gp'                THEN 15
    WHEN 'specialist'        THEN 20
    WHEN 'psychologist'      THEN 30
    WHEN 'herbalist'         THEN 15
    ELSE 15  -- safe fallback
  END;
END;
$$;

-- ── 3. Recreate create_consultation_with_cleanup WITH timer fields ────────────
--
-- Key fix: now() + (v_duration || ' minutes')::interval sets scheduled_end_at
-- to the correct future time.  Previous versions omitted these columns entirely.

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
    v_duration SMALLINT;
BEGIN
    -- Resolve duration from service type
    v_duration := get_service_duration_minutes(p_service_type);

    -- Step 1: Close all non-completed consultations for this patient
    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        updated_at = now()
    WHERE patient_session_id = p_patient_session_id::uuid
      AND status NOT IN ('completed'::consultation_status_enum);

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    RAISE LOG 'create_consultation_with_cleanup: patient=%, closed=% stale consultations',
        p_patient_session_id, closed_count;

    -- Step 2: Insert with timer fields so the client countdown starts correctly
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

-- ── 4. Patch any existing ACTIVE consultations that are missing timer fields ──
-- Consultations stuck with NULL scheduled_end_at will never expire correctly.
-- Assume they started at created_at and give them a fresh 15-minute window.

UPDATE consultations
SET
    session_start_time     = COALESCE(session_start_time, created_at),
    scheduled_end_at       = CASE
                               WHEN scheduled_end_at IS NULL
                               THEN now() + interval '15 minutes'
                               ELSE scheduled_end_at
                             END,
    original_duration_minutes = CASE
                                  WHEN original_duration_minutes = 0
                                  THEN get_service_duration_minutes(service_type::text)
                                  ELSE original_duration_minutes
                                END,
    updated_at = now()
WHERE status IN ('active'::consultation_status_enum,
                 'awaiting_extension'::consultation_status_enum,
                 'grace_period'::consultation_status_enum)
  AND (session_start_time IS NULL
       OR scheduled_end_at IS NULL
       OR original_duration_minutes = 0);
