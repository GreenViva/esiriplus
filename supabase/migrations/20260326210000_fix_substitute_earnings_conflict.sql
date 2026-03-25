-- Fix: the fn_auto_create_doctor_earning trigger uses ON CONFLICT (consultation_id)
-- but we changed the unique constraint to (doctor_id, consultation_id).
-- Update the trigger to use the correct conflict target.

CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger AS $$
DECLARE
    v_split_pct     INTEGER;
    v_parent_fee    INTEGER;
    v_doctor_share  INTEGER;
    v_original_amt  INTEGER;
    v_substitute_amt INTEGER;
BEGIN
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(config_value::INTEGER, 50)
      INTO v_split_pct
      FROM app_config
     WHERE config_key = 'doctor_earnings_split_pct';

    -- ── Substitute follow-up: split earnings 60/40
    IF NEW.is_substitute_follow_up = TRUE AND NEW.original_doctor_id IS NOT NULL
       AND NEW.parent_consultation_id IS NOT NULL THEN

        SELECT consultation_fee INTO v_parent_fee
          FROM consultations
         WHERE consultation_id = NEW.parent_consultation_id;

        IF v_parent_fee IS NULL OR v_parent_fee <= 0 THEN
            RETURN NEW;
        END IF;

        v_doctor_share := FLOOR(v_parent_fee * v_split_pct / 100.0);
        IF v_doctor_share <= 0 THEN
            RETURN NEW;
        END IF;

        v_original_amt := FLOOR(v_doctor_share * 60 / 100.0);
        v_substitute_amt := FLOOR(v_doctor_share * 40 / 100.0);

        IF v_original_amt > 0 THEN
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
            VALUES (NEW.original_doctor_id, NEW.consultation_id, v_original_amt, 'pending')
            ON CONFLICT (doctor_id, consultation_id) DO NOTHING;
        END IF;

        IF v_substitute_amt > 0 THEN
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
            VALUES (NEW.doctor_id, NEW.consultation_id, v_substitute_amt, 'pending')
            ON CONFLICT (doctor_id, consultation_id) DO NOTHING;
        END IF;

        RETURN NEW;
    END IF;

    -- ── Normal flow
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    v_doctor_share := FLOOR(NEW.consultation_fee * v_split_pct / 100.0);
    IF v_doctor_share <= 0 THEN
        RETURN NEW;
    END IF;

    INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
    VALUES (NEW.doctor_id, NEW.consultation_id, v_doctor_share, 'pending')
    ON CONFLICT (doctor_id, consultation_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
