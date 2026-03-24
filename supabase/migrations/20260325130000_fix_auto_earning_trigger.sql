-- Fix: fn_auto_create_doctor_earning must skip zero-fee follow-up consultations.
-- The existing function tries to insert into doctor_earnings with amount derived
-- from consultation_fee. For follow-ups (fee=0), the calculated amount is 0,
-- which violates CHECK (amount > 0). We already relaxed the check to >= 0 in
-- a prior migration, but better to skip the insert entirely for free consultations.

CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger AS $$
DECLARE
    v_split_pct     INTEGER;
    v_earning_amt   INTEGER;
BEGIN
    -- Guard: only act when transitioning INTO 'completed'
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    -- Skip free consultations (follow-ups have consultation_fee = 0)
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    -- Read split percentage dynamically from app_config
    SELECT COALESCE(config_value::INTEGER, 50)
      INTO v_split_pct
      FROM app_config
     WHERE config_key = 'doctor_earnings_split_pct';

    -- Floor to whole TZS (no fractional shillings)
    v_earning_amt := FLOOR(NEW.consultation_fee * v_split_pct / 100.0);

    -- Guard against zero after rounding
    IF v_earning_amt <= 0 THEN
        RETURN NEW;
    END IF;

    INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
    VALUES (NEW.doctor_id, NEW.consultation_id, v_earning_amt, 'pending')
    ON CONFLICT DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Also drop the duplicate trigger we created earlier (trg_consultation_earnings)
-- to avoid double-inserting earnings.
DROP TRIGGER IF EXISTS trg_consultation_earnings ON consultations;
