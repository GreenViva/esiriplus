-- ============================================================================
-- ECONOMY TIER: 60/25/15 EARNINGS SPLIT MODEL
--
-- Replaces the previous 50/30/20 Economy split with:
--   100% collected from patient upfront
--    60% → platform revenue (immediate)
--    25% → consulting doctor (released on consultation completion + report)
--    15% → follow-up escrow (released when follow-up completed within 14 days)
--
-- Follow-up scenarios (unchanged from previous model, just different %):
--   Same doctor does follow-up  → gets 25% + 15% = 40% total
--   Different doctor does FU    → original gets 25%, substitute gets 15%
--   No follow-up within 14 days → 15% moves to admin_review
--
-- Royal tier is UNCHANGED: 50% doctor / 50% platform; follow-ups free bonus.
-- Agent referral commission (10%) is UNCHANGED.
--
-- In-flight escrow handling:
--   Economy parent consultations that already completed under 50/30/20 have
--   their 20% `followup_escrow_amount` stored on the consultations row. This
--   migration switches the follow-up trigger to use that stored amount as
--   source of truth, so in-flight escrows grandfather at 20% while new
--   consultations going forward follow the 15% rule.
-- ============================================================================

-- ── 1. Update app_config with the new percentages ───────────────────────────

INSERT INTO app_config (config_key, config_value, description)
VALUES
    ('economy_consultation_pct', '25', 'Economy tier: % of fee to doctor on consultation completion'),
    ('economy_followup_pct',     '15', 'Economy tier: % of fee held in escrow for follow-up doctor')
ON CONFLICT (config_key) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description  = EXCLUDED.description;

-- doctor_earnings_split_pct (Royal's share) intentionally unchanged at 50.

-- ── 2. Rewrite the earnings trigger ─────────────────────────────────────────
-- Changes from 50/30/20 version:
--   • CASE A (follow-up release) uses parent.followup_escrow_amount instead
--     of recalculating from config. This grandfathers in-flight 20% escrows
--     and makes the ledger internally consistent (parent.held == child.paid).
--   • Falls back to config recalculation only for legacy parents where
--     followup_escrow_amount was never set (pre-50/30/20 completions).

CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger AS $$
DECLARE
    v_split_pct          INTEGER;  -- total doctor share for Royal (default 50%)
    v_consult_pct        INTEGER;  -- economy consultation pct (now 25%)
    v_followup_pct       INTEGER;  -- economy follow-up pct (now 15%)
    v_parent_fee         INTEGER;
    v_parent_tier        TEXT;
    v_parent_held        INTEGER;
    v_doctor_share       INTEGER;
    v_consult_amt        INTEGER;
    v_followup_amt       INTEGER;
BEGIN
    -- Guard: only act when transitioning INTO 'completed'
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    -- Read config (defaults match the current 60/25/15 model)
    SELECT COALESCE(config_value::INTEGER, 50) INTO v_split_pct
      FROM app_config WHERE config_key = 'doctor_earnings_split_pct';
    SELECT COALESCE(config_value::INTEGER, 25) INTO v_consult_pct
      FROM app_config WHERE config_key = 'economy_consultation_pct';
    SELECT COALESCE(config_value::INTEGER, 15) INTO v_followup_pct
      FROM app_config WHERE config_key = 'economy_followup_pct';

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE A: This is a FOLLOW-UP consultation completing
    -- Release the escrow from the PARENT consultation to the FU doctor.
    -- We USE THE STORED followup_escrow_amount on the parent as source of
    -- truth so in-flight escrows grandfather at whatever % was held at the
    -- time the parent completed.
    -- ══════════════════════════════════════════════════════════════════════
    IF NEW.parent_consultation_id IS NOT NULL THEN

        SELECT consultation_fee,
               UPPER(COALESCE(service_tier, 'ECONOMY')),
               followup_escrow_amount
          INTO v_parent_fee, v_parent_tier, v_parent_held
          FROM consultations
         WHERE consultation_id = NEW.parent_consultation_id;

        IF v_parent_fee IS NULL OR v_parent_fee <= 0 THEN
            RETURN NEW;
        END IF;

        -- ROYAL tier: follow-ups are free bonus, no additional earnings
        IF v_parent_tier = 'ROYAL' THEN
            RETURN NEW;
        END IF;

        -- ECONOMY tier: prefer the already-held amount, fall back to config
        -- recalculation only for legacy rows where it was never stored.
        IF v_parent_held IS NOT NULL AND v_parent_held > 0 THEN
            v_followup_amt := v_parent_held;
        ELSE
            v_followup_amt := FLOOR(v_parent_fee * v_followup_pct / 100.0);
        END IF;

        IF v_followup_amt <= 0 THEN
            RETURN NEW;
        END IF;

        IF NEW.is_substitute_follow_up = TRUE THEN
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
            VALUES (NEW.doctor_id, NEW.consultation_id, v_followup_amt, 'pending', 'substitute_follow_up')
            ON CONFLICT (doctor_id, consultation_id) DO NOTHING;
        ELSE
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
            VALUES (NEW.doctor_id, NEW.consultation_id, v_followup_amt, 'pending', 'follow_up')
            ON CONFLICT (doctor_id, consultation_id) DO NOTHING;
        END IF;

        UPDATE consultations
           SET followup_escrow_status = 'released'
         WHERE consultation_id = NEW.parent_consultation_id
           AND followup_escrow_status = 'held';

        INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
        VALUES (
            NEW.parent_consultation_id,
            NEW.doctor_id,
            'followup_release',
            v_followup_amt,
            'Follow-up completed by ' ||
                CASE WHEN NEW.is_substitute_follow_up THEN 'substitute' ELSE 'original' END ||
                ' doctor. FU consultation: ' || NEW.consultation_id::TEXT ||
                CASE
                    WHEN v_parent_held IS NOT NULL AND v_parent_held > 0
                    THEN ' (released from stored escrow)'
                    ELSE ' (recalculated from config at ' || v_followup_pct::TEXT || '%)'
                END
        );

        RETURN NEW;
    END IF;

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE B: This is an ORIGINAL consultation completing
    -- ══════════════════════════════════════════════════════════════════════

    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    DECLARE
        v_tier TEXT := UPPER(COALESCE(NEW.service_tier, 'ECONOMY'));
    BEGIN
        IF v_tier = 'ROYAL' THEN
            -- ── ROYAL: unchanged — full 50% to doctor ──
            v_doctor_share := FLOOR(NEW.consultation_fee * v_split_pct / 100.0);

            IF v_doctor_share > 0 THEN
                INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
                VALUES (NEW.doctor_id, NEW.consultation_id, v_doctor_share, 'pending', 'consultation')
                ON CONFLICT (doctor_id, consultation_id) DO NOTHING;

                INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (NEW.consultation_id, NEW.doctor_id, 'consultation_release', v_doctor_share,
                        'Royal tier: full ' || v_split_pct::TEXT || '% released on completion');
            END IF;

        ELSE
            -- ── ECONOMY: 25% now + 15% held in escrow ──
            v_consult_amt  := FLOOR(NEW.consultation_fee * v_consult_pct / 100.0);
            v_followup_amt := FLOOR(NEW.consultation_fee * v_followup_pct / 100.0);

            IF v_consult_amt > 0 THEN
                INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
                VALUES (NEW.doctor_id, NEW.consultation_id, v_consult_amt, 'pending', 'consultation')
                ON CONFLICT (doctor_id, consultation_id) DO NOTHING;

                INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (NEW.consultation_id, NEW.doctor_id, 'consultation_release', v_consult_amt,
                        'Economy: ' || v_consult_pct::TEXT || '% released on consultation completion');
            END IF;

            IF v_followup_amt > 0 THEN
                UPDATE consultations
                   SET followup_escrow_amount = v_followup_amt,
                       followup_escrow_status = 'held'
                 WHERE consultation_id = NEW.consultation_id;

                INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (NEW.consultation_id, NULL, 'followup_hold', v_followup_amt,
                        'Economy: ' || v_followup_pct::TEXT || '% held in escrow pending follow-up within 14 days');
            END IF;
        END IF;
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION fn_auto_create_doctor_earning() IS
    'Economy 60/25/15 split: 60% platform, 25% consulting doctor, 15% follow-up escrow. '
    'Royal unchanged at 50/50. Follow-up releases use parent.followup_escrow_amount as '
    'source of truth so in-flight escrows grandfather at their held amount.';
