-- ============================================================================
-- Surface the Royal 4-per-24h cap at REQUEST-CREATE time (not just at doctor
-- accept time) so the patient sees the polite "problem on our side" error
-- immediately, before any doctor is even notified. validate_followup_reopen
-- runs as a BEFORE INSERT trigger on consultation_requests; we extend it
-- with the Royal-specific cap check.
-- ============================================================================

CREATE OR REPLACE FUNCTION validate_followup_reopen()
RETURNS trigger AS $$
DECLARE
    v_consultation RECORD;
    v_cap jsonb;
BEGIN
    IF NOT NEW.is_follow_up OR NEW.parent_consultation_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT follow_up_count, follow_up_max, follow_up_expiry, status, service_tier
      INTO v_consultation
      FROM consultations
     WHERE consultation_id = NEW.parent_consultation_id;

    IF v_consultation IS NULL THEN
        RAISE EXCEPTION 'Consultation not found' USING ERRCODE = 'P0001';
    END IF;
    IF v_consultation.status <> 'completed' THEN
        RAISE EXCEPTION 'Consultation is not completed' USING ERRCODE = 'P0002';
    END IF;
    IF v_consultation.follow_up_expiry IS NOT NULL AND v_consultation.follow_up_expiry < NOW() THEN
        RAISE EXCEPTION 'Follow-up window has expired' USING ERRCODE = 'P0003';
    END IF;
    IF v_consultation.follow_up_max > 0 AND v_consultation.follow_up_count >= v_consultation.follow_up_max THEN
        RAISE EXCEPTION 'Follow-up limit reached' USING ERRCODE = 'P0004';
    END IF;

    -- Royal 4-per-24h cap. We raise the same ROYAL_FU_CAP_REACHED:<hours>
    -- shape used by reopen_consultation so the edge function has a single
    -- detection path.
    IF UPPER(COALESCE(v_consultation.service_tier, 'ECONOMY')) = 'ROYAL' THEN
        v_cap := fn_check_royal_followup_cap(NEW.parent_consultation_id);
        IF NOT (v_cap->>'allowed')::boolean THEN
            RAISE EXCEPTION 'ROYAL_FU_CAP_REACHED:%', (v_cap->>'hours_until_reset')
                USING ERRCODE = 'P0006';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
