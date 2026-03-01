-- Migration: Consultation timer enforcement + extension workflow
-- Adds timed sessions, awaiting_extension/grace_period statuses, and RPCs for the extension flow.

-- NOTE: Enum values 'awaiting_extension' and 'grace_period' are added in
-- 20260302090000_add_consultation_enum_values.sql (must be separate transaction).

-- ─── 1. New columns on consultations ─────────────────────────────────────────
ALTER TABLE consultations
  ADD COLUMN IF NOT EXISTS scheduled_end_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS extension_count SMALLINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS grace_period_end_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS original_duration_minutes SMALLINT NOT NULL DEFAULT 15;

-- ─── 3. Helper: service-type → duration mapping ─────────────────────────────
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
    ELSE 15  -- safe fallback
  END;
END;
$$;

-- ─── 4. Update unique index to cover all active-like statuses ────────────────
DROP INDEX IF EXISTS idx_consultations_doctor_active;
CREATE UNIQUE INDEX idx_consultations_doctor_active
  ON consultations (doctor_id)
  WHERE status IN ('active'::consultation_status_enum,
                   'awaiting_extension'::consultation_status_enum,
                   'grace_period'::consultation_status_enum);

-- ─── 5. Update trigger: treat all 3 statuses as "in session" ────────────────
CREATE OR REPLACE FUNCTION fn_sync_doctor_in_session()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    UPDATE doctor_profiles
    SET in_session = EXISTS (
      SELECT 1 FROM consultations
      WHERE doctor_id = OLD.doctor_id
        AND status IN ('active'::consultation_status_enum,
                       'awaiting_extension'::consultation_status_enum,
                       'grace_period'::consultation_status_enum)
    )
    WHERE doctor_id = OLD.doctor_id;
  ELSE
    UPDATE doctor_profiles
    SET in_session = EXISTS (
      SELECT 1 FROM consultations
      WHERE doctor_id = NEW.doctor_id
        AND status IN ('active'::consultation_status_enum,
                       'awaiting_extension'::consultation_status_enum,
                       'grace_period'::consultation_status_enum)
    )
    WHERE doctor_id = NEW.doctor_id;

    IF TG_OP = 'UPDATE' AND OLD.doctor_id IS DISTINCT FROM NEW.doctor_id THEN
      UPDATE doctor_profiles
      SET in_session = EXISTS (
        SELECT 1 FROM consultations
        WHERE doctor_id = OLD.doctor_id
          AND status IN ('active'::consultation_status_enum,
                         'awaiting_extension'::consultation_status_enum,
                         'grace_period'::consultation_status_enum)
      )
      WHERE doctor_id = OLD.doctor_id;
    END IF;
  END IF;

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ─── 6. Replace create_consultation_with_cleanup to set timer fields ─────────
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

    -- Step 2: Insert with timer fields
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

-- ─── 7a. RPC: mark_awaiting_extension ────────────────────────────────────────
CREATE OR REPLACE FUNCTION mark_awaiting_extension(
    p_consultation_id uuid,
    p_doctor_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE consultations
    SET status = 'awaiting_extension'::consultation_status_enum,
        updated_at = now()
    WHERE consultation_id = p_consultation_id
      AND doctor_id = p_doctor_id
      AND status = 'active'::consultation_status_enum
      AND scheduled_end_at <= now();

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cannot mark awaiting_extension: consultation not found, not active, or timer has not expired'
            USING ERRCODE = 'P0001';
    END IF;
END;
$$;

-- ─── 7b. RPC: start_grace_period ─────────────────────────────────────────────
CREATE OR REPLACE FUNCTION start_grace_period(
    p_consultation_id uuid,
    p_patient_session_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE consultations
    SET status = 'grace_period'::consultation_status_enum,
        grace_period_end_at = now() + interval '5 minutes',
        updated_at = now()
    WHERE consultation_id = p_consultation_id
      AND patient_session_id = p_patient_session_id
      AND status = 'awaiting_extension'::consultation_status_enum;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cannot start grace period: consultation not found or not in awaiting_extension status'
            USING ERRCODE = 'P0001';
    END IF;
END;
$$;

-- ─── 7c. RPC: extend_consultation ────────────────────────────────────────────
CREATE OR REPLACE FUNCTION extend_consultation(
    p_consultation_id uuid,
    p_payment_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_duration SMALLINT;
BEGIN
    -- Get the original duration to add
    SELECT original_duration_minutes INTO v_duration
    FROM consultations
    WHERE consultation_id = p_consultation_id
      AND status = 'grace_period'::consultation_status_enum;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cannot extend: consultation not found or not in grace_period status'
            USING ERRCODE = 'P0001';
    END IF;

    UPDATE consultations
    SET status = 'active'::consultation_status_enum,
        scheduled_end_at = now() + (v_duration || ' minutes')::interval,
        extension_count = extension_count + 1,
        grace_period_end_at = NULL,
        updated_at = now()
    WHERE consultation_id = p_consultation_id;
END;
$$;

-- ─── 7d. RPC: end_consultation ───────────────────────────────────────────────
CREATE OR REPLACE FUNCTION end_consultation(
    p_consultation_id uuid,
    p_doctor_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_status consultation_status_enum;
BEGIN
    SELECT status INTO v_status
    FROM consultations
    WHERE consultation_id = p_consultation_id
      AND doctor_id = p_doctor_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Consultation not found for this doctor'
            USING ERRCODE = 'P0001';
    END IF;

    -- Block ending during grace_period (patient is paying)
    IF v_status = 'grace_period'::consultation_status_enum THEN
        RAISE EXCEPTION 'Cannot end consultation while patient is processing payment'
            USING ERRCODE = 'P0001';
    END IF;

    -- Allow ending from active or awaiting_extension
    IF v_status NOT IN ('active'::consultation_status_enum,
                         'awaiting_extension'::consultation_status_enum) THEN
        RAISE EXCEPTION 'Consultation is not in an endable status'
            USING ERRCODE = 'P0001';
    END IF;

    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        session_end_time = now(),
        grace_period_end_at = NULL,
        updated_at = now()
    WHERE consultation_id = p_consultation_id;
END;
$$;

-- ─── 7e. RPC: cancel_extension ───────────────────────────────────────────────
CREATE OR REPLACE FUNCTION cancel_extension(
    p_consultation_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE consultations
    SET status = 'awaiting_extension'::consultation_status_enum,
        grace_period_end_at = NULL,
        updated_at = now()
    WHERE consultation_id = p_consultation_id
      AND status = 'grace_period'::consultation_status_enum;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cannot cancel extension: consultation not found or not in grace_period status'
            USING ERRCODE = 'P0001';
    END IF;
END;
$$;

-- ─── 7f. RPC: get_server_time ────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION get_server_time()
RETURNS timestamptz
LANGUAGE sql
STABLE
AS $$
    SELECT now();
$$;

-- ─── 8. Enable realtime for consultations table ──────────────────────────────
-- (Idempotent — Postgres ignores if already in the publication)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND tablename = 'consultations'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE consultations;
    END IF;
END;
$$;
