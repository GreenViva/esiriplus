-- ============================================================================
-- ECONOMY TIER: 50/30/20 EARNINGS SPLIT MODEL
--
-- Payment breakdown (Economy only — Royal keeps existing 50/50):
--   100% collected from patient upfront
--    50% → platform revenue (immediate)
--    30% → consulting doctor (released on consultation completion + report)
--    20% → follow-up escrow (released when follow-up completed within 14 days)
--
-- Follow-up scenarios:
--   Same doctor does follow-up  → gets 30% + 20% = 50% total
--   Different doctor does FU    → original gets 30%, substitute gets 20%
--   No follow-up within 14 days → 20% moves to admin_review
--
-- Royal tier keeps existing behavior:
--   50% to doctor on completion, follow-ups are free bonus
-- ============================================================================

-- ── 1. Escrow ledger for financial audit trail ───────────────────────────────

CREATE TABLE IF NOT EXISTS escrow_ledger (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultations(consultation_id),
    doctor_id       UUID REFERENCES auth.users(id),
    entry_type      TEXT NOT NULL CHECK (entry_type IN (
        'consultation_hold',    -- 30% held at payment
        'followup_hold',        -- 20% held at payment
        'consultation_release', -- 30% released to doctor
        'followup_release',     -- 20% released to follow-up doctor
        'followup_expired',     -- 20% expired (no follow-up in 14 days)
        'admin_review',         -- funds moved to admin review
        'refund'                -- funds refunded to patient
    )),
    amount          INTEGER NOT NULL CHECK (amount >= 0),
    currency        TEXT NOT NULL DEFAULT 'TZS',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_escrow_ledger_consultation
    ON escrow_ledger (consultation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_escrow_ledger_doctor
    ON escrow_ledger (doctor_id, created_at DESC);

-- RLS: portal users can read, service role can write
ALTER TABLE escrow_ledger ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Portal users can read escrow_ledger"
    ON escrow_ledger FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM user_roles
        WHERE user_id = auth.uid()
        AND role_name IN ('admin', 'finance', 'audit')
    ));

CREATE POLICY "Doctors read own escrow entries"
    ON escrow_ledger FOR SELECT TO authenticated
    USING (doctor_id = auth.uid());

-- ── 2. Add earning_type to doctor_earnings for 30/20 distinction ─────────────

ALTER TABLE doctor_earnings
    ADD COLUMN IF NOT EXISTS earning_type TEXT NOT NULL DEFAULT 'consultation'
        CHECK (earning_type IN ('consultation', 'follow_up', 'substitute_consultation', 'substitute_follow_up'));

-- ── 3. Add escrow tracking to consultations ──────────────────────────────────

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS followup_escrow_amount INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS followup_escrow_status TEXT DEFAULT 'none'
        CHECK (followup_escrow_status IN ('none', 'held', 'released', 'expired', 'admin_review'));

-- ── 4. App config: new split percentages ─────────────────────────────────────

INSERT INTO app_config (config_key, config_value, description)
VALUES
    ('economy_consultation_pct', '30', 'Economy tier: % of fee to doctor on consultation completion'),
    ('economy_followup_pct', '20', 'Economy tier: % of fee held in escrow for follow-up doctor')
ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value;

-- ── 5. Rewrite the earnings trigger with 50/30/20 logic ─────────────────────

CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger AS $$
DECLARE
    v_split_pct          INTEGER;  -- total doctor share (default 50%)
    v_consult_pct        INTEGER;  -- economy consultation pct (default 30%)
    v_followup_pct       INTEGER;  -- economy follow-up pct (default 20%)
    v_parent_fee         INTEGER;
    v_parent_tier        TEXT;
    v_doctor_share       INTEGER;
    v_consult_amt        INTEGER;
    v_followup_amt       INTEGER;
    v_original_amt       INTEGER;
    v_substitute_amt     INTEGER;
BEGIN
    -- Guard: only act when transitioning INTO 'completed'
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    -- Read config
    SELECT COALESCE(config_value::INTEGER, 50) INTO v_split_pct
      FROM app_config WHERE config_key = 'doctor_earnings_split_pct';
    SELECT COALESCE(config_value::INTEGER, 30) INTO v_consult_pct
      FROM app_config WHERE config_key = 'economy_consultation_pct';
    SELECT COALESCE(config_value::INTEGER, 20) INTO v_followup_pct
      FROM app_config WHERE config_key = 'economy_followup_pct';

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE A: This is a FOLLOW-UP consultation completing
    -- Release the 20% escrow from the PARENT consultation to the FU doctor
    -- ══════════════════════════════════════════════════════════════════════
    IF NEW.parent_consultation_id IS NOT NULL THEN

        -- Get parent consultation details
        SELECT consultation_fee, UPPER(COALESCE(service_tier, 'ECONOMY'))
          INTO v_parent_fee, v_parent_tier
          FROM consultations
         WHERE consultation_id = NEW.parent_consultation_id;

        IF v_parent_fee IS NULL OR v_parent_fee <= 0 THEN
            RETURN NEW;
        END IF;

        -- ROYAL tier: follow-ups are free bonus, no additional earnings
        IF v_parent_tier = 'ROYAL' THEN
            RETURN NEW;
        END IF;

        -- ECONOMY tier: release the 20% escrow to the follow-up doctor
        v_followup_amt := FLOOR(v_parent_fee * v_followup_pct / 100.0);

        IF v_followup_amt <= 0 THEN
            RETURN NEW;
        END IF;

        -- Determine earning type based on whether it's a substitute
        IF NEW.is_substitute_follow_up = TRUE THEN
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
            VALUES (NEW.doctor_id, NEW.consultation_id, v_followup_amt, 'pending', 'substitute_follow_up')
            ON CONFLICT (doctor_id, consultation_id) DO NOTHING;
        ELSE
            INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
            VALUES (NEW.doctor_id, NEW.consultation_id, v_followup_amt, 'pending', 'follow_up')
            ON CONFLICT (doctor_id, consultation_id) DO NOTHING;
        END IF;

        -- Mark parent's escrow as released
        UPDATE consultations
           SET followup_escrow_status = 'released'
         WHERE consultation_id = NEW.parent_consultation_id
           AND followup_escrow_status = 'held';

        -- Audit trail
        INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
        VALUES (
            NEW.parent_consultation_id,
            NEW.doctor_id,
            'followup_release',
            v_followup_amt,
            'Follow-up completed by ' ||
                CASE WHEN NEW.is_substitute_follow_up THEN 'substitute' ELSE 'original' END ||
                ' doctor. FU consultation: ' || NEW.consultation_id::TEXT
        );

        RETURN NEW;
    END IF;

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE B: This is an ORIGINAL consultation completing
    -- ══════════════════════════════════════════════════════════════════════

    -- Skip free consultations
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    DECLARE
        v_tier TEXT := UPPER(COALESCE(NEW.service_tier, 'ECONOMY'));
    BEGIN
        IF v_tier = 'ROYAL' THEN
            -- ── ROYAL: existing behavior — full 50% to doctor ──
            v_doctor_share := FLOOR(NEW.consultation_fee * v_split_pct / 100.0);

            IF v_doctor_share > 0 THEN
                INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
                VALUES (NEW.doctor_id, NEW.consultation_id, v_doctor_share, 'pending', 'consultation')
                ON CONFLICT (doctor_id, consultation_id) DO NOTHING;

                -- Audit
                INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (NEW.consultation_id, NEW.doctor_id, 'consultation_release', v_doctor_share,
                        'Royal tier: full 50% released on completion');
            END IF;

        ELSE
            -- ── ECONOMY: 30% now + 20% held in escrow ──
            v_consult_amt  := FLOOR(NEW.consultation_fee * v_consult_pct / 100.0);
            v_followup_amt := FLOOR(NEW.consultation_fee * v_followup_pct / 100.0);

            -- Release 30% to consulting doctor immediately
            IF v_consult_amt > 0 THEN
                INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status, earning_type)
                VALUES (NEW.doctor_id, NEW.consultation_id, v_consult_amt, 'pending', 'consultation')
                ON CONFLICT (doctor_id, consultation_id) DO NOTHING;

                INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (NEW.consultation_id, NEW.doctor_id, 'consultation_release', v_consult_amt,
                        'Economy: 30% released on consultation completion');
            END IF;

            -- Hold 20% in escrow for follow-up
            IF v_followup_amt > 0 THEN
                UPDATE consultations
                   SET followup_escrow_amount = v_followup_amt,
                       followup_escrow_status = 'held'
                 WHERE consultation_id = NEW.consultation_id;

                INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (NEW.consultation_id, NULL, 'followup_hold', v_followup_amt,
                        'Economy: 20% held in escrow pending follow-up within 14 days');
            END IF;
        END IF;
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── 6. Function to expire unclaimed follow-up escrow after 14 days ───────────
-- Called by a cron job or edge function on a schedule

CREATE OR REPLACE FUNCTION fn_expire_followup_escrow()
RETURNS INTEGER AS $$
DECLARE
    v_expired_count INTEGER := 0;
    v_row RECORD;
BEGIN
    -- Find Economy consultations where:
    --   follow_up_expiry has passed
    --   escrow is still 'held' (no follow-up was completed)
    FOR v_row IN
        SELECT consultation_id, followup_escrow_amount, doctor_id
          FROM consultations
         WHERE followup_escrow_status = 'held'
           AND follow_up_expiry IS NOT NULL
           AND follow_up_expiry < NOW()
           AND followup_escrow_amount > 0
    LOOP
        -- Move to admin review
        UPDATE consultations
           SET followup_escrow_status = 'admin_review'
         WHERE consultation_id = v_row.consultation_id;

        -- Audit trail
        INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
        VALUES (
            v_row.consultation_id,
            v_row.doctor_id,
            'followup_expired',
            v_row.followup_escrow_amount,
            'Follow-up window expired (14 days). Funds moved to admin review.'
        );

        INSERT INTO escrow_ledger (consultation_id, doctor_id, entry_type, amount, notes)
        VALUES (
            v_row.consultation_id,
            NULL,
            'admin_review',
            v_row.followup_escrow_amount,
            'Expired follow-up escrow pending admin decision (refund/retain/split).'
        );

        v_expired_count := v_expired_count + 1;
    END LOOP;

    RETURN v_expired_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── 7. Back-fill existing completed Economy consultations ────────────────────
-- For consultations that already completed before this migration,
-- we DON'T retroactively change their earnings. Only new consultations
-- will use the 50/30/20 model.

-- ── 8. Grant service_role full access to escrow_ledger ───────────────────────

CREATE POLICY "service_role_full_access_escrow"
    ON escrow_ledger FOR ALL
    USING (true) WITH CHECK (true);
