-- ============================================================================
-- ROYAL EARNINGS MODEL — full rework (2026-05-01)
--
-- 1. Initial Royal consultation:
--      Patient pays Royal price (consultation_fee = e.g. 420k for GP).
--      Doctor earns 60/25/15 against ECONOMY BASE PRICE
--      (app_config.price_<service>), not the Royal price.
--      GP example: doctor 25% × 10k = 2,500; escrow 15% × 10k = 1,500.
--
-- 2. Royal follow-up consultations (within 14-day follow_up_expiry window):
--      Each FU pays the doctor 15% of Economy base (1,500 for GP).
--      First FU releases the held escrow as that earning.
--      Subsequent FUs in the window create fresh follow_up earning rows.
--      CAP: max 4 paying FUs per rolling 24h window keyed off the first
--      FU of that window. 5th+ blocked at the reopen RPC; patient sees
--      a polite "problem on our side" error with hours-until-reset.
--
-- 3. Royal doctor→patient calls (within follow_up window):
--      Each connected call ≥ 60s pays the doctor 2,000.
--      Cap: max 3 paid calls per CALENDAR DAY (Africa/Dar_es_Salaam) per
--      patient. 4th+ call earns no row (silently capped on doctor side).
--      Floor: doctor *should* make 3 calls/day on each active Royal
--      patient. Each calendar day the floor is missed = +1 to
--      doctor_profiles.royal_call_misses_count. At >= 3, flagged_at is
--      set for admin review (no auto-suspension).
--
-- The 24h FU cap and the calendar-day call cap deliberately use
-- different windows per the business rule.
-- ============================================================================

-- ── 1. Schema additions ─────────────────────────────────────────────────────

ALTER TABLE doctor_profiles
    ADD COLUMN IF NOT EXISTS royal_call_misses_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flagged_at TIMESTAMPTZ DEFAULT NULL;

-- royal_call_floor_misses: idempotency log for the daily floor sweep.
CREATE TABLE IF NOT EXISTS royal_call_floor_misses (
    miss_id BIGSERIAL PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctor_profiles(doctor_id) ON DELETE CASCADE,
    consultation_id UUID NOT NULL REFERENCES consultations(consultation_id) ON DELETE CASCADE,
    miss_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doctor_id, consultation_id, miss_date)
);

-- doctor_earnings unique index update: only 'consultation' and
-- 'substitute_follow_up' stay one-per-(doctor, consultation). 'follow_up'
-- and 'royal_call' can repeat.
DROP INDEX IF EXISTS doctor_earnings_doctor_consultation_type_uq;
DROP INDEX IF EXISTS doctor_earnings_doctor_consultation_uq;
ALTER TABLE doctor_earnings DROP CONSTRAINT IF EXISTS doctor_earnings_doctor_consultation_uq;

CREATE UNIQUE INDEX IF NOT EXISTS doctor_earnings_consultation_strict_uq
    ON doctor_earnings (doctor_id, consultation_id)
    WHERE earning_type IN ('consultation', 'substitute_follow_up');

-- Helpful index for the call cap query (hot path).
CREATE INDEX IF NOT EXISTS idx_doctor_earnings_royal_call_lookup
    ON doctor_earnings (doctor_id, consultation_id, earning_type, created_at)
    WHERE earning_type IN ('royal_call', 'follow_up');

-- ── 2. Helper: Economy base price lookup ────────────────────────────────────

CREATE OR REPLACE FUNCTION fn_get_economy_base_price(p_service_type TEXT)
RETURNS INTEGER
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(config_value::INTEGER, 0)
      FROM app_config
     WHERE config_key = 'price_' || lower(p_service_type);
$$;

COMMENT ON FUNCTION fn_get_economy_base_price(TEXT) IS
    'Returns the Economy base price for the given service type from app_config.price_<service>. Used to compute Royal earnings against Economy base since the 10× multiplier was dropped 2026-05-01.';

-- ── 3. Helper: Royal follow-up 24h cap check ────────────────────────────────

CREATE OR REPLACE FUNCTION fn_check_royal_followup_cap(p_consultation_id UUID)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_window_start TIMESTAMPTZ;
    v_count INTEGER;
    v_hours_until_reset INTEGER;
BEGIN
    -- The "current window" is anchored at the earliest follow_up earning
    -- whose created_at is within the last 24 hours. If no such earning
    -- exists, the next FU starts a fresh window.
    SELECT MIN(created_at) INTO v_window_start
      FROM doctor_earnings
     WHERE consultation_id = p_consultation_id
       AND earning_type = 'follow_up'
       AND created_at > NOW() - INTERVAL '24 hours';

    IF v_window_start IS NULL THEN
        RETURN jsonb_build_object(
            'allowed', true,
            'count', 0,
            'hours_until_reset', 0
        );
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM doctor_earnings
     WHERE consultation_id = p_consultation_id
       AND earning_type = 'follow_up'
       AND created_at >= v_window_start;

    v_hours_until_reset := GREATEST(
        1,
        CEIL(EXTRACT(EPOCH FROM (v_window_start + INTERVAL '24 hours' - NOW())) / 3600.0)::INTEGER
    );

    RETURN jsonb_build_object(
        'allowed', v_count < 4,
        'count', v_count,
        'hours_until_reset', v_hours_until_reset
    );
END;
$$;

COMMENT ON FUNCTION fn_check_royal_followup_cap(UUID) IS
    '4 follow-ups per rolling 24h window for Royal consultations. Window starts at the earliest follow_up earning within last 24h.';

-- ── 4. Helper: Royal call calendar-day cap check ────────────────────────────

CREATE OR REPLACE FUNCTION fn_check_royal_call_cap(
    p_doctor_id UUID,
    p_consultation_id UUID
)
RETURNS jsonb
LANGUAGE sql
STABLE
AS $$
    SELECT jsonb_build_object(
        'allowed', count(*) < 3,
        'count_today', count(*)
    )
    FROM doctor_earnings
    WHERE doctor_id = p_doctor_id
      AND consultation_id = p_consultation_id
      AND earning_type = 'royal_call'
      AND (created_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date
          = (NOW() AT TIME ZONE 'Africa/Dar_es_Salaam')::date;
$$;

COMMENT ON FUNCTION fn_check_royal_call_cap(UUID, UUID) IS
    '3 paid calls per (doctor, consultation) per Africa/Dar_es_Salaam calendar day.';

-- ── 5. Trigger: auto-create royal_call earning on video_calls completion ────

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

    -- Must be within active follow-up window (i.e. consultation completed
    -- and follow_up_expiry not yet passed). Doctor→Royal-client calls
    -- happen between consultations during this window.
    IF v_consultation.follow_up_expiry IS NULL
       OR v_consultation.follow_up_expiry < NOW() THEN
        RETURN NEW;
    END IF;

    -- The Royal Clients calling feature only invokes calls when the parent
    -- consultation is in a 'completed' or related post-session state.
    -- Skip if currently active (this is an in-consultation call, not a
    -- doctor-initiated post-consultation call).
    IF v_consultation.status NOT IN ('completed') THEN
        RETURN NEW;
    END IF;

    -- Cap check: 3 per Africa/Dar_es_Salaam calendar day per (doctor, patient).
    v_cap := fn_check_royal_call_cap(v_consultation.doctor_id, NEW.consultation_id);
    IF NOT (v_cap->>'allowed')::boolean THEN
        RETURN NEW;
    END IF;

    -- De-dupe: already issued an earning for THIS specific call?
    SELECT COUNT(*) INTO v_already_paid
      FROM doctor_earnings
     WHERE doctor_id = v_consultation.doctor_id
       AND consultation_id = NEW.consultation_id
       AND earning_type = 'royal_call'
       AND notes LIKE '%call_id=' || NEW.id::text || '%';

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
        'Royal call earning. call_id=' || NEW.id::text
            || ', duration_seconds=' || NEW.duration_seconds::text
    );

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS auto_create_royal_call_earning ON video_calls;
CREATE TRIGGER auto_create_royal_call_earning
    AFTER INSERT OR UPDATE ON video_calls
    FOR EACH ROW EXECUTE FUNCTION fn_auto_create_royal_call_earning();

-- ── 6. Rewrite fn_auto_create_doctor_earning for Royal model ────────────────

CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_consult_pct        INTEGER;
    v_followup_pct       INTEGER;
    v_parent_fee         INTEGER;
    v_parent_tier        TEXT;
    v_parent_held        INTEGER;
    v_consult_amt        INTEGER;
    v_followup_amt       INTEGER;
    v_tier               TEXT;
    v_existing_earning   INTEGER;
    v_eco_base           INTEGER;
    v_cap                jsonb;
BEGIN
    -- Guard: only act when transitioning INTO 'completed'
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(config_value::INTEGER, 25) INTO v_consult_pct
      FROM app_config WHERE config_key = 'economy_consultation_pct';
    SELECT COALESCE(config_value::INTEGER, 15) INTO v_followup_pct
      FROM app_config WHERE config_key = 'economy_followup_pct';

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE 0: REOPEN-MODEL RE-COMPLETION (same row, was reopened, now done)
    -- ══════════════════════════════════════════════════════════════════════
    IF NEW.parent_consultation_id IS NULL
       AND NEW.last_reopened_at IS NOT NULL THEN

        SELECT COUNT(*) INTO v_existing_earning
          FROM doctor_earnings
         WHERE doctor_id = NEW.doctor_id
           AND consultation_id = NEW.consultation_id
           AND earning_type = 'consultation';

        IF v_existing_earning > 0 THEN
            v_tier := UPPER(COALESCE(NEW.service_tier, 'ECONOMY'));
            v_eco_base := fn_get_economy_base_price(NEW.service_type::TEXT);
            v_followup_amt := FLOOR(v_eco_base * v_followup_pct / 100.0);

            -- Royal: each FU pays a flat 15% × Economy base, capped at 4/24h.
            IF v_tier = 'ROYAL' THEN
                -- The reopen RPC should have rejected this if the cap was
                -- already at 4. As a defensive belt-and-suspenders check:
                v_cap := fn_check_royal_followup_cap(NEW.consultation_id);
                IF NOT (v_cap->>'allowed')::boolean THEN
                    RETURN NEW;  -- silently skip earning; reopen already happened
                END IF;

                IF v_followup_amt <= 0 THEN
                    RETURN NEW;
                END IF;

                -- First FU of this consultation: release held escrow as the
                -- earning. Subsequent FUs: fresh earning row.
                IF NEW.followup_escrow_status = 'held'
                   AND COALESCE(NEW.followup_escrow_amount, 0) > 0 THEN

                    INSERT INTO doctor_earnings
                        (doctor_id, consultation_id, amount, status, earning_type, notes)
                    VALUES (
                        NEW.doctor_id, NEW.consultation_id,
                        NEW.followup_escrow_amount, 'pending', 'follow_up',
                        'Royal first follow-up: 15% × Economy base released from escrow'
                    );

                    UPDATE consultations
                       SET followup_escrow_status = 'released'
                     WHERE consultation_id = NEW.consultation_id
                       AND followup_escrow_status = 'held';

                    INSERT INTO escrow_ledger
                        (consultation_id, doctor_id, entry_type, amount, notes)
                    VALUES (
                        NEW.consultation_id, NEW.doctor_id, 'followup_release',
                        NEW.followup_escrow_amount,
                        'Royal first FU: 15% × Eco base released from escrow'
                    );
                ELSE
                    -- Fresh follow-up earning (escrow already released or never held).
                    INSERT INTO doctor_earnings
                        (doctor_id, consultation_id, amount, status, earning_type, notes)
                    VALUES (
                        NEW.doctor_id, NEW.consultation_id,
                        v_followup_amt, 'pending', 'follow_up',
                        'Royal follow-up: 15% × Economy base (within 24h cap)'
                    );

                    INSERT INTO escrow_ledger
                        (consultation_id, doctor_id, entry_type, amount, notes)
                    VALUES (
                        NEW.consultation_id, NEW.doctor_id, 'royal_followup_payout',
                        v_followup_amt,
                        'Royal subsequent FU: 15% × Eco base paid from platform pocket'
                    );
                END IF;

                RETURN NEW;
            END IF;

            -- Economy: release held escrow on the (single allowed) follow-up.
            IF NEW.followup_escrow_status = 'held'
               AND COALESCE(NEW.followup_escrow_amount, 0) > 0 THEN

                INSERT INTO doctor_earnings
                    (doctor_id, consultation_id, amount, status, earning_type)
                VALUES
                    (NEW.doctor_id, NEW.consultation_id,
                     NEW.followup_escrow_amount, 'pending', 'follow_up');

                UPDATE consultations
                   SET followup_escrow_status = 'released'
                 WHERE consultation_id = NEW.consultation_id
                   AND followup_escrow_status = 'held';

                IF NOT EXISTS (
                    SELECT 1 FROM escrow_ledger
                     WHERE consultation_id = NEW.consultation_id
                       AND entry_type = 'followup_release'
                ) THEN
                    INSERT INTO escrow_ledger
                        (consultation_id, doctor_id, entry_type, amount, notes)
                    VALUES (
                        NEW.consultation_id, NEW.doctor_id, 'followup_release',
                        NEW.followup_escrow_amount,
                        'Economy reopen: 15% follow-up escrow released to same doctor on re-completion'
                    );
                END IF;
            END IF;

            RETURN NEW;
        END IF;
    END IF;

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE A: LEGACY CHILD CONSULTATION (pre-Apr-9 follow-up model)
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

        IF v_parent_tier = 'ROYAL' THEN
            RETURN NEW;
        END IF;

        IF v_parent_held IS NOT NULL AND v_parent_held > 0 THEN
            v_followup_amt := v_parent_held;
        ELSE
            v_followup_amt := FLOOR(v_parent_fee * v_followup_pct / 100.0);
        END IF;

        IF v_followup_amt <= 0 THEN
            RETURN NEW;
        END IF;

        IF NEW.is_substitute_follow_up = TRUE THEN
            INSERT INTO doctor_earnings
                (doctor_id, consultation_id, amount, status, earning_type)
            VALUES
                (NEW.doctor_id, NEW.consultation_id, v_followup_amt,
                 'pending', 'substitute_follow_up')
            ON CONFLICT DO NOTHING;
        ELSE
            INSERT INTO doctor_earnings
                (doctor_id, consultation_id, amount, status, earning_type)
            VALUES
                (NEW.doctor_id, NEW.consultation_id, v_followup_amt,
                 'pending', 'follow_up');
        END IF;

        UPDATE consultations
           SET followup_escrow_status = 'released'
         WHERE consultation_id = NEW.parent_consultation_id
           AND followup_escrow_status = 'held';

        IF NOT EXISTS (
            SELECT 1 FROM escrow_ledger
             WHERE consultation_id = NEW.parent_consultation_id
               AND entry_type = 'followup_release'
        ) THEN
            INSERT INTO escrow_ledger
                (consultation_id, doctor_id, entry_type, amount, notes)
            VALUES (
                NEW.parent_consultation_id, NEW.doctor_id, 'followup_release',
                v_followup_amt,
                'Legacy child follow-up release. FU consultation: ' || NEW.consultation_id::TEXT
            );
        END IF;

        RETURN NEW;
    END IF;

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE B: ORIGINAL CONSULTATION FIRST COMPLETION
    --   ECONOMY: 25% / 15% / 60% against consultation_fee.
    --   ROYAL:   25% / 15% against ECONOMY BASE PRICE; rest is platform.
    --            Patient paid Royal price (consultation_fee), but the
    --            doctor split is computed against the Economy base.
    -- ══════════════════════════════════════════════════════════════════════
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    v_tier := UPPER(COALESCE(NEW.service_tier, 'ECONOMY'));

    IF v_tier = 'ROYAL' THEN
        v_eco_base := fn_get_economy_base_price(NEW.service_type::TEXT);
        IF v_eco_base <= 0 THEN
            RAISE WARNING 'fn_auto_create_doctor_earning: no Economy base price found for service_type=%', NEW.service_type;
            RETURN NEW;
        END IF;

        v_consult_amt  := FLOOR(v_eco_base * v_consult_pct / 100.0);
        v_followup_amt := FLOOR(v_eco_base * v_followup_pct / 100.0);

        -- Doctor consultation share (25% × Eco base).
        IF v_consult_amt > 0 THEN
            INSERT INTO doctor_earnings
                (doctor_id, consultation_id, amount, status, earning_type, notes)
            VALUES (
                NEW.doctor_id, NEW.consultation_id, v_consult_amt,
                'pending', 'consultation',
                'Royal initial: 25% × Economy base (' || v_eco_base::TEXT || ')'
            )
            ON CONFLICT DO NOTHING;

            IF NOT EXISTS (
                SELECT 1 FROM escrow_ledger
                 WHERE consultation_id = NEW.consultation_id
                   AND doctor_id = NEW.doctor_id
                   AND entry_type = 'consultation_release'
            ) THEN
                INSERT INTO escrow_ledger
                    (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (
                    NEW.consultation_id, NEW.doctor_id, 'consultation_release',
                    v_consult_amt,
                    'Royal initial: 25% × Eco base released on completion'
                );
            END IF;
        END IF;

        -- Follow-up escrow (15% × Eco base).
        IF v_followup_amt > 0
           AND COALESCE(NEW.followup_escrow_status, 'none') NOT IN ('held', 'released', 'admin_review') THEN

            UPDATE consultations
               SET followup_escrow_amount = v_followup_amt,
                   followup_escrow_status = 'held'
             WHERE consultation_id = NEW.consultation_id;

            IF NOT EXISTS (
                SELECT 1 FROM escrow_ledger
                 WHERE consultation_id = NEW.consultation_id
                   AND entry_type = 'followup_hold'
            ) THEN
                INSERT INTO escrow_ledger
                    (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (
                    NEW.consultation_id, NULL, 'followup_hold', v_followup_amt,
                    'Royal initial: 15% × Eco base held in escrow for first FU'
                );
            END IF;
        END IF;

    ELSE
        -- ECONOMY (unchanged from previous model).
        v_consult_amt  := FLOOR(NEW.consultation_fee * v_consult_pct / 100.0);
        v_followup_amt := FLOOR(NEW.consultation_fee * v_followup_pct / 100.0);

        IF v_consult_amt > 0 THEN
            INSERT INTO doctor_earnings
                (doctor_id, consultation_id, amount, status, earning_type)
            VALUES
                (NEW.doctor_id, NEW.consultation_id, v_consult_amt,
                 'pending', 'consultation')
            ON CONFLICT DO NOTHING;

            IF NOT EXISTS (
                SELECT 1 FROM escrow_ledger
                 WHERE consultation_id = NEW.consultation_id
                   AND doctor_id = NEW.doctor_id
                   AND entry_type = 'consultation_release'
            ) THEN
                INSERT INTO escrow_ledger
                    (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (
                    NEW.consultation_id, NEW.doctor_id, 'consultation_release',
                    v_consult_amt,
                    'Economy: ' || v_consult_pct::TEXT || '% released on consultation completion'
                );
            END IF;
        END IF;

        IF v_followup_amt > 0
           AND COALESCE(NEW.followup_escrow_status, 'none') NOT IN ('held', 'released', 'admin_review') THEN

            UPDATE consultations
               SET followup_escrow_amount = v_followup_amt,
                   followup_escrow_status = 'held'
             WHERE consultation_id = NEW.consultation_id;

            IF NOT EXISTS (
                SELECT 1 FROM escrow_ledger
                 WHERE consultation_id = NEW.consultation_id
                   AND entry_type = 'followup_hold'
            ) THEN
                INSERT INTO escrow_ledger
                    (consultation_id, doctor_id, entry_type, amount, notes)
                VALUES (
                    NEW.consultation_id, NULL, 'followup_hold', v_followup_amt,
                    'Economy: ' || v_followup_pct::TEXT || '% held in escrow pending follow-up within 14 days'
                );
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION fn_auto_create_doctor_earning() IS
    'Royal-aware earnings: initial uses 25/15 against Economy base; FU pays 15% × Eco base on each re-completion (cap-aware). Economy unchanged 25/15/60.';

-- ── 7. Update reopen_consultation RPC: Royal cap + drop is_reopened block ───

CREATE OR REPLACE FUNCTION reopen_consultation(
    p_consultation_id UUID,
    p_doctor_id UUID,
    p_service_type TEXT DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_consultation RECORD;
    v_duration INTEGER;
    v_now TIMESTAMPTZ := NOW();
    v_stype TEXT;
    v_cap jsonb;
BEGIN
    SELECT * INTO v_consultation
      FROM consultations
     WHERE consultation_id = p_consultation_id
     FOR UPDATE;

    IF v_consultation IS NULL THEN
        RAISE EXCEPTION 'Consultation not found' USING ERRCODE = 'P0001';
    END IF;
    IF v_consultation.status <> 'completed' THEN
        RAISE EXCEPTION 'Consultation is not completed (status: %)', v_consultation.status USING ERRCODE = 'P0002';
    END IF;
    -- Note: previous "is_reopened" check dropped — it blocked Royal's
    -- multi-reopen flow even though follow_up_max=-1 should permit it.
    -- The status='completed' check above already guards against reopening
    -- a consultation that's currently active.
    IF v_consultation.follow_up_expiry IS NOT NULL AND v_consultation.follow_up_expiry < v_now THEN
        RAISE EXCEPTION 'Follow-up window has expired' USING ERRCODE = 'P0004';
    END IF;
    IF v_consultation.follow_up_max > 0 AND v_consultation.follow_up_count >= v_consultation.follow_up_max THEN
        RAISE EXCEPTION 'Follow-up limit reached (% of %)', v_consultation.follow_up_count, v_consultation.follow_up_max USING ERRCODE = 'P0005';
    END IF;

    -- Royal: 4-per-24h cap. Block here so the patient sees the polite
    -- "problem on our side" message before any session starts.
    IF UPPER(COALESCE(v_consultation.service_tier, 'ECONOMY')) = 'ROYAL' THEN
        v_cap := fn_check_royal_followup_cap(p_consultation_id);
        IF NOT (v_cap->>'allowed')::boolean THEN
            RAISE EXCEPTION 'ROYAL_FU_CAP_REACHED:%', (v_cap->>'hours_until_reset')
                USING ERRCODE = 'P0006';
        END IF;
    END IF;

    v_stype := COALESCE(p_service_type, v_consultation.service_type::TEXT);

    v_duration := CASE v_stype
        WHEN 'nurse' THEN 15
        WHEN 'clinical_officer' THEN 15
        WHEN 'pharmacist' THEN 5
        WHEN 'gp' THEN 15
        WHEN 'specialist' THEN 20
        WHEN 'psychologist' THEN 30
        WHEN 'herbalist' THEN 15
        WHEN 'drug_interaction' THEN 5
        ELSE 15
    END;

    UPDATE consultations SET
        status = 'active',
        doctor_id = p_doctor_id,
        session_start_time = v_now,
        session_end_time = NULL,
        scheduled_end_at = v_now + (v_duration || ' minutes')::INTERVAL,
        grace_period_end_at = NULL,
        extension_count = 0,
        is_reopened = true,
        last_reopened_at = v_now,
        follow_up_count = follow_up_count + 1,
        updated_at = v_now
    WHERE consultation_id = p_consultation_id;

    UPDATE doctor_profiles SET in_session = true WHERE doctor_id = p_doctor_id;

    INSERT INTO messages (consultation_id, sender_type, sender_id, message_text, message_type, is_read, created_at)
    VALUES (p_consultation_id, 'system', 'system', 'Follow-up session started', 'text', false, v_now);
END;
$$;

-- ── 8. Daily floor sweep for missed Royal call days ─────────────────────────

CREATE OR REPLACE FUNCTION fn_sweep_royal_call_floor()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_yesterday DATE;
    v_misses INTEGER := 0;
BEGIN
    v_yesterday := ((NOW() AT TIME ZONE 'Africa/Dar_es_Salaam') - INTERVAL '1 day')::date;

    -- Insert one miss row per (doctor, consultation) where the patient's
    -- Royal window covered yesterday and the doctor made <3 paying calls.
    WITH eligible AS (
        SELECT DISTINCT c.doctor_id, c.consultation_id, c.follow_up_expiry
          FROM consultations c
         WHERE UPPER(COALESCE(c.service_tier, 'ECONOMY')) = 'ROYAL'
           AND c.follow_up_expiry IS NOT NULL
           AND c.follow_up_expiry >= ((v_yesterday)::timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')
           AND c.created_at < ((v_yesterday + 1)::timestamp AT TIME ZONE 'Africa/Dar_es_Salaam')
    ),
    call_counts AS (
        SELECT e.doctor_id,
               e.consultation_id,
               (SELECT COUNT(*) FROM doctor_earnings de
                 WHERE de.doctor_id = e.doctor_id
                   AND de.consultation_id = e.consultation_id
                   AND de.earning_type = 'royal_call'
                   AND (de.created_at AT TIME ZONE 'Africa/Dar_es_Salaam')::date = v_yesterday
               ) AS call_count
          FROM eligible e
    ),
    inserted_misses AS (
        INSERT INTO royal_call_floor_misses (doctor_id, consultation_id, miss_date)
        SELECT doctor_id, consultation_id, v_yesterday
          FROM call_counts
         WHERE call_count < 3
        ON CONFLICT (doctor_id, consultation_id, miss_date) DO NOTHING
        RETURNING doctor_id
    )
    SELECT COUNT(*) INTO v_misses FROM inserted_misses;

    -- Bump doctors' miss counters based on TODAY's inserted miss rows
    -- (idempotent because of the UNIQUE on royal_call_floor_misses).
    WITH today_misses AS (
        SELECT doctor_id, COUNT(*) AS new_misses
          FROM royal_call_floor_misses
         WHERE miss_date = v_yesterday
         GROUP BY doctor_id
    )
    UPDATE doctor_profiles dp
       SET royal_call_misses_count = dp.royal_call_misses_count + tm.new_misses
      FROM today_misses tm
     WHERE dp.doctor_id = tm.doctor_id
       AND NOT EXISTS (
           -- Don't double-bump if this sweep ran already for v_yesterday.
           -- We detect that by checking if every miss row for v_yesterday
           -- exists at created_at older than NOW() - 1 minute.
           SELECT 1 FROM royal_call_floor_misses rcfm
            WHERE rcfm.doctor_id = dp.doctor_id
              AND rcfm.miss_date = v_yesterday
              AND rcfm.created_at < NOW() - INTERVAL '1 minute'
       );

    -- Flag doctors who've crossed the threshold.
    UPDATE doctor_profiles
       SET flagged_at = NOW()
     WHERE flagged_at IS NULL
       AND royal_call_misses_count >= 3;

    RETURN v_misses;
END;
$$;

COMMENT ON FUNCTION fn_sweep_royal_call_floor() IS
    'Daily sweep: counts Royal patients whose doctor missed the 3-calls/day floor yesterday (Dar es Salaam time). Increments doctor_profiles.royal_call_misses_count and flags at >= 3.';
