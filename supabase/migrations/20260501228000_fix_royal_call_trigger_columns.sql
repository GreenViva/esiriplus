-- ============================================================================
-- Fix: fn_auto_create_royal_call_earning referenced video_calls.id and
-- video_calls.status, neither of which exist on the actual table. Real
-- columns (verified): call_id (PK), consultation_id, started_at, ended_at,
-- duration_seconds, call_quality, created_at.
--
-- Without this fix the trigger throws on every video_calls UPDATE/INSERT.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_auto_create_royal_call_earning()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_consultation RECORD;
    v_cap jsonb;
    v_already_paid INTEGER;
BEGIN
    -- Only act when a call has just completed with sufficient duration.
    IF NEW.duration_seconds IS NULL OR NEW.duration_seconds < 60 THEN
        RETURN NEW;
    END IF;
    IF NEW.ended_at IS NULL THEN
        RETURN NEW;
    END IF;
    -- On UPDATE, only act when ended_at transitioned to non-null
    -- (or duration_seconds crossed the 60s threshold for the first time).
    IF TG_OP = 'UPDATE' THEN
        IF OLD.ended_at IS NOT NULL AND OLD.duration_seconds IS NOT NULL
           AND OLD.duration_seconds >= 60 THEN
            RETURN NEW;  -- already counted on previous trigger fire
        END IF;
    END IF;

    SELECT consultation_id, doctor_id, service_tier, follow_up_expiry, status
      INTO v_consultation
      FROM consultations
     WHERE consultation_id = NEW.consultation_id;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    -- Royal-only feature.
    IF UPPER(COALESCE(v_consultation.service_tier, 'ECONOMY')) <> 'ROYAL' THEN
        RETURN NEW;
    END IF;

    -- Within active follow-up window.
    IF v_consultation.follow_up_expiry IS NULL
       OR v_consultation.follow_up_expiry < NOW() THEN
        RETURN NEW;
    END IF;

    -- Doctor→Royal-client calls happen between consultations during this
    -- window, when the parent consultation is in 'completed' state.
    IF v_consultation.status NOT IN ('completed') THEN
        RETURN NEW;
    END IF;

    -- Cap check: 3 per Africa/Dar_es_Salaam calendar day per (doctor, patient).
    v_cap := fn_check_royal_call_cap(v_consultation.doctor_id, NEW.consultation_id);
    IF NOT (v_cap->>'allowed')::boolean THEN
        RETURN NEW;
    END IF;

    -- De-dupe: already issued an earning for THIS specific call?
    -- video_calls primary key is `call_id` (verified 2026-05-01 against the
    -- actual remote schema; an earlier migration spec showed `id`, but the
    -- live table uses `call_id`).
    SELECT COUNT(*) INTO v_already_paid
      FROM doctor_earnings
     WHERE doctor_id = v_consultation.doctor_id
       AND consultation_id = NEW.consultation_id
       AND earning_type = 'royal_call'
       AND notes LIKE '%call_id=' || NEW.call_id::text || '%';

    IF v_already_paid > 0 THEN
        RETURN NEW;
    END IF;

    INSERT INTO doctor_earnings
        (doctor_id, consultation_id, amount, status, earning_type, notes)
    VALUES (
        v_consultation.doctor_id,
        NEW.consultation_id,
        2000,
        'pending',
        'royal_call',
        'Royal call earning. call_id=' || NEW.call_id::text
            || ', duration_seconds=' || NEW.duration_seconds::text
    );

    RETURN NEW;
END;
$$;
