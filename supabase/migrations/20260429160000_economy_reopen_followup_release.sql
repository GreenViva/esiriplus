-- ============================================================================
-- ECONOMY REOPEN-MODEL FOLLOW-UP RELEASE
--
-- Bug context (discovered 2026-04-29):
--   When the Apr 9 follow-up reopen model replaced child consultations with
--   same-row reopens, the earnings trigger was never updated. So when an
--   Economy consultation is reopened for follow-up and re-completed, the
--   trigger re-runs CASE B (parent_consultation_id is null), which:
--     - Tries to insert a duplicate consultation earning (blocked by uq idx)
--     - Writes a duplicate consultation_release ledger entry
--     - Resets followup_escrow_status back to 'held'
--     - NEVER releases the 15% escrow to the doctor → money sits in escrow
--       until fn_expire_followup_escrow sweeps it to admin_review.
--
--   Concrete example: consultation f26983c2 (Economy, fee 10000)
--     15:31  original completion → 2500 to doctor, 1500 escrow held    (OK)
--     15:38  reopen for follow-up                                       (OK)
--     15:39  re-completion → trigger re-runs CASE B
--            ledger gets duplicate consultation_release(2500) + followup_hold(1500)
--            doctor never gets the 1500 follow-up share owed
--
-- Fix:
--   1. Replace unique index (doctor_id, consultation_id) with
--      (doctor_id, consultation_id, earning_type) so a doctor can hold both a
--      'consultation' and a 'follow_up' earning on the same consultation
--      under the reopen model.
--   2. Trigger detects re-completion (last_reopened_at set + existing
--      consultation earning) and:
--        Economy → release held escrow as a follow_up earning, mark released
--        Royal   → no-op (Royal follow-ups are free bonus by policy)
--   3. All ledger writes guarded by IF NOT EXISTS to be idempotent under any
--      number of trigger fires.
--   4. Backfill: for Economy consultations already stuck (last_reopened_at
--      not null, status completed, escrow still held), release escrow now.
--
-- Royal handoff/double-payout (separate bug observed on consultation
-- edf93779) is intentionally NOT addressed here — pending a separate fix.
-- ============================================================================

-- ── 1. Replace doctor_earnings unique index ─────────────────────────────────
-- Old: (doctor_id, consultation_id)             — too narrow for reopen model
-- New: (doctor_id, consultation_id, earning_type) — allows consultation + follow_up

DROP INDEX IF EXISTS doctor_earnings_doctor_consultation_uq;

CREATE UNIQUE INDEX IF NOT EXISTS doctor_earnings_doctor_consultation_type_uq
    ON doctor_earnings (doctor_id, consultation_id, earning_type)
    WHERE earning_type NOT IN ('medication_reminder');

-- ── 2. Replace trigger function ─────────────────────────────────────────────

CREATE OR REPLACE FUNCTION fn_auto_create_doctor_earning()
RETURNS trigger AS $$
DECLARE
    v_split_pct          INTEGER;
    v_consult_pct        INTEGER;
    v_followup_pct       INTEGER;
    v_parent_fee         INTEGER;
    v_parent_tier        TEXT;
    v_parent_held        INTEGER;
    v_doctor_share       INTEGER;
    v_consult_amt        INTEGER;
    v_followup_amt       INTEGER;
    v_tier               TEXT;
    v_existing_earning   INTEGER;
BEGIN
    -- Guard: only act when transitioning INTO 'completed'
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;

    -- Read config (defaults match the 60/25/15 model)
    SELECT COALESCE(config_value::INTEGER, 50) INTO v_split_pct
      FROM app_config WHERE config_key = 'doctor_earnings_split_pct';
    SELECT COALESCE(config_value::INTEGER, 25) INTO v_consult_pct
      FROM app_config WHERE config_key = 'economy_consultation_pct';
    SELECT COALESCE(config_value::INTEGER, 15) INTO v_followup_pct
      FROM app_config WHERE config_key = 'economy_followup_pct';

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE 0: REOPEN-MODEL RE-COMPLETION
    -- Same row was previously completed, then reopened, now re-completed.
    -- Detected by last_reopened_at + an existing consultation earning.
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

            -- Royal: follow-ups are free bonus, nothing additional to release
            IF v_tier = 'ROYAL' THEN
                RETURN NEW;
            END IF;

            -- Economy: release the held escrow as a follow_up earning to the
            -- same doctor (40% total: 25% consultation + 15% follow-up).
            IF NEW.followup_escrow_status = 'held'
               AND COALESCE(NEW.followup_escrow_amount, 0) > 0 THEN

                INSERT INTO doctor_earnings
                    (doctor_id, consultation_id, amount, status, earning_type)
                VALUES
                    (NEW.doctor_id, NEW.consultation_id,
                     NEW.followup_escrow_amount, 'pending', 'follow_up')
                ON CONFLICT DO NOTHING;

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
        -- v_existing_earning = 0 falls through. Should be unreachable since
        -- reopen requires a previous completion — but be defensive.
    END IF;

    -- ══════════════════════════════════════════════════════════════════════
    -- CASE A: LEGACY CHILD CONSULTATION (pre-Apr-9 follow-up model)
    -- A separate row with parent_consultation_id set — release escrow from
    -- the parent.
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
                 'pending', 'follow_up')
            ON CONFLICT DO NOTHING;
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
    -- ══════════════════════════════════════════════════════════════════════
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    v_tier := UPPER(COALESCE(NEW.service_tier, 'ECONOMY'));

    IF v_tier = 'ROYAL' THEN
        v_doctor_share := FLOOR(NEW.consultation_fee * v_split_pct / 100.0);
        IF v_doctor_share > 0 THEN
            INSERT INTO doctor_earnings
                (doctor_id, consultation_id, amount, status, earning_type)
            VALUES
                (NEW.doctor_id, NEW.consultation_id, v_doctor_share,
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
                    v_doctor_share,
                    'Royal tier: full ' || v_split_pct::TEXT || '% released on completion'
                );
            END IF;
        END IF;
    ELSE
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

        -- Only initialize escrow if it isn't already held/released. Prevents
        -- a stray re-fire from resetting state.
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
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION fn_auto_create_doctor_earning() IS
    'Reopen-aware Economy 60/25/15 split with idempotent ledger writes. '
    'On re-completion of a reopened Economy consultation, releases held '
    'escrow as a follow_up earning to the same doctor (40% total). '
    'Royal follow-ups remain free bonus. Legacy child-consultation path '
    'preserved for in-flight rows from before Apr 9 reopen model.';

-- ── 3. Backfill: release stuck Economy escrows from the bug period ──────────
-- Any Economy consultation that was reopened and re-completed before this
-- migration has its 15% (or grandfathered 20%) escrow stuck in 'held'. The
-- doctor performed the follow-up but never got paid for it. Release now.

DO $$
DECLARE
    v_row RECORD;
    v_released_count INTEGER := 0;
BEGIN
    FOR v_row IN
        SELECT consultation_id, doctor_id, followup_escrow_amount
          FROM consultations
         WHERE UPPER(COALESCE(service_tier, 'ECONOMY')) = 'ECONOMY'
           AND status = 'completed'
           AND last_reopened_at IS NOT NULL
           AND followup_escrow_status = 'held'
           AND COALESCE(followup_escrow_amount, 0) > 0
           AND doctor_id IS NOT NULL
    LOOP
        INSERT INTO doctor_earnings
            (doctor_id, consultation_id, amount, status, earning_type)
        VALUES
            (v_row.doctor_id, v_row.consultation_id,
             v_row.followup_escrow_amount, 'pending', 'follow_up')
        ON CONFLICT DO NOTHING;

        UPDATE consultations
           SET followup_escrow_status = 'released'
         WHERE consultation_id = v_row.consultation_id;

        IF NOT EXISTS (
            SELECT 1 FROM escrow_ledger
             WHERE consultation_id = v_row.consultation_id
               AND entry_type = 'followup_release'
        ) THEN
            INSERT INTO escrow_ledger
                (consultation_id, doctor_id, entry_type, amount, notes)
            VALUES (
                v_row.consultation_id, v_row.doctor_id, 'followup_release',
                v_row.followup_escrow_amount,
                'Backfill 2026-04-29: escrow stuck due to reopen-model trigger bug. Released to doctor.'
            );
        END IF;

        v_released_count := v_released_count + 1;
    END LOOP;

    RAISE NOTICE 'Backfill released % stuck Economy follow-up escrows', v_released_count;
END $$;
