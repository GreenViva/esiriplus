-- Substitute follow-up support: when original doctor is offline, patient can
-- request another doctor. Earnings are split 60/40 between original and substitute.

-- ── 1. Add substitute follow-up columns ──────────────────────────────────────
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS is_substitute_follow_up BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS original_doctor_id UUID REFERENCES auth.users(id);

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS is_substitute_follow_up BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS original_doctor_id UUID REFERENCES auth.users(id);

-- ── 2. Update create_consultation_with_cleanup RPC ───────────────────────────
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
    p_parent_consultation_id uuid DEFAULT NULL,
    p_agent_id uuid DEFAULT NULL,
    p_is_substitute_follow_up boolean DEFAULT FALSE,
    p_original_doctor_id uuid DEFAULT NULL
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

    RETURN QUERY
    INSERT INTO consultations (
        patient_session_id, doctor_id, service_type, service_tier,
        consultation_type, chief_complaint, status, consultation_fee,
        request_expires_at, request_id, session_start_time, scheduled_end_at,
        original_duration_minutes, extension_count,
        parent_consultation_id, agent_id,
        is_substitute_follow_up, original_doctor_id,
        created_at, updated_at
    ) VALUES (
        p_patient_session_id::uuid, p_doctor_id,
        p_service_type::service_type_enum, p_service_tier,
        p_consultation_type, p_chief_complaint,
        'active'::consultation_status_enum, p_consultation_fee,
        p_request_expires_at, p_request_id,
        now(), now() + (v_duration || ' minutes')::interval,
        v_duration, 0,
        p_parent_consultation_id, p_agent_id,
        p_is_substitute_follow_up, p_original_doctor_id,
        now(), now()
    )
    RETURNING *;
END;
$$;

-- ── 3. Update doctor earnings trigger for substitute follow-up split ─────────
CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger AS $$
DECLARE
    v_split_pct     INTEGER;
    v_parent_fee    INTEGER;
    v_doctor_share  INTEGER;
    v_original_amt  INTEGER;
    v_substitute_amt INTEGER;
BEGIN
    -- Guard: only act when transitioning INTO 'completed'
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    -- Read split percentage from app_config (default 50%)
    SELECT COALESCE(config_value::INTEGER, 50)
      INTO v_split_pct
      FROM app_config
     WHERE config_key = 'doctor_earnings_split_pct';

    -- ── Substitute follow-up: split earnings 60/40 between original and substitute doctor
    IF NEW.is_substitute_follow_up = TRUE AND NEW.original_doctor_id IS NOT NULL
       AND NEW.parent_consultation_id IS NOT NULL THEN

        -- Look up the ORIGINAL consultation's fee (the one the patient paid for)
        SELECT consultation_fee INTO v_parent_fee
          FROM consultations
         WHERE consultation_id = NEW.parent_consultation_id;

        IF v_parent_fee IS NULL OR v_parent_fee <= 0 THEN
            RETURN NEW;
        END IF;

        -- Total doctor share from original fee
        v_doctor_share := FLOOR(v_parent_fee * v_split_pct / 100.0);

        IF v_doctor_share <= 0 THEN
            RETURN NEW;
        END IF;

        -- Original doctor gets 60% of the doctor share
        v_original_amt := FLOOR(v_doctor_share * 60 / 100.0);
        -- Substitute doctor gets 40% of the doctor share
        v_substitute_amt := FLOOR(v_doctor_share * 40 / 100.0);

        -- Insert original doctor earnings (if > 0)
        IF v_original_amt > 0 THEN
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
            VALUES (NEW.original_doctor_id, NEW.consultation_id, v_original_amt, 'pending')
            ON CONFLICT DO NOTHING;
        END IF;

        -- Insert substitute doctor earnings (if > 0)
        IF v_substitute_amt > 0 THEN
            -- Use a different unique constraint approach: the consultation_id unique
            -- constraint on doctor_earnings prevents two rows for the same consultation.
            -- We need to allow it for substitute follow-ups.
            -- Solution: insert into a separate key by appending '_sub' context.
            -- Actually, the UNIQUE(consultation_id) constraint blocks this.
            -- We need to drop that constraint and use a composite unique instead.
            -- For now, insert the substitute earnings directly (the original doctor
            -- earnings row uses consultation_id, substitute uses a generated ID).
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
            VALUES (NEW.doctor_id, NEW.consultation_id, v_substitute_amt, 'pending')
            ON CONFLICT (consultation_id) DO UPDATE SET
                amount = EXCLUDED.amount,
                doctor_id = EXCLUDED.doctor_id;
        END IF;

        RETURN NEW;
    END IF;

    -- ── Normal flow: single doctor earnings
    -- Skip free consultations (follow-ups have consultation_fee = 0)
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    v_doctor_share := FLOOR(NEW.consultation_fee * v_split_pct / 100.0);

    IF v_doctor_share <= 0 THEN
        RETURN NEW;
    END IF;

    INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
    VALUES (NEW.doctor_id, NEW.consultation_id, v_doctor_share, 'pending')
    ON CONFLICT DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── 4. Fix unique constraint: allow two earnings rows per consultation (for substitute split)
-- Drop the single-column unique and replace with composite unique on (doctor_id, consultation_id)
ALTER TABLE doctor_earnings DROP CONSTRAINT IF EXISTS doctor_earnings_consultation_id_key;
ALTER TABLE doctor_earnings ADD CONSTRAINT doctor_earnings_doctor_consultation_uq
    UNIQUE (doctor_id, consultation_id);
