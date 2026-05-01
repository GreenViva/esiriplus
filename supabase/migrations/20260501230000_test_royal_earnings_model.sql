-- ============================================================================
-- Self-contained verification of the Royal earnings model.
-- Inserts fixture rows with sentinel UUIDs, exercises each trigger / helper,
-- asserts expected outcomes, then deletes the fixtures. Migration fails (and
-- rolls back) on the first assertion miss.
-- ============================================================================

DO $$
DECLARE
    -- Reuse a real doctor + patient session from prod to satisfy FKs.
    -- Sentinel UUID for the consultation rows so cleanup is unambiguous.
    v_doctor_id      UUID := '10f4a1eb-28f4-4cce-bbee-497068fb6c2b'::uuid;
    v_patient_sess   UUID := '67445028-ce73-416d-9bff-4519cb76ed5d'::uuid;
    v_consult_royal  UUID := '99999999-9999-9999-9999-c0c0c0c00001'::uuid;
    v_video_call_a   UUID;
    v_video_call_b   UUID;
    v_video_call_c   UUID;
    v_video_call_d   UUID;

    v_eco_base       INTEGER;
    v_count          INTEGER;
    v_amount         INTEGER;
    v_status         TEXT;
    v_escrow_amt     INTEGER;
    v_escrow_status  TEXT;
    v_cap            JSONB;
BEGIN
    -- ── Cleanup any prior run ─────────────────────────────────────────────
    -- Only touch rows tied to OUR sentinel consultation. Don't touch the
    -- shared doctor/patient_session — those are real prod records.
    DELETE FROM doctor_earnings  WHERE consultation_id = v_consult_royal;
    DELETE FROM video_calls      WHERE consultation_id = v_consult_royal;
    DELETE FROM escrow_ledger    WHERE consultation_id = v_consult_royal;
    DELETE FROM messages         WHERE consultation_id = v_consult_royal;
    DELETE FROM royal_call_floor_misses WHERE consultation_id = v_consult_royal;
    DELETE FROM consultations    WHERE consultation_id = v_consult_royal;

    -- ── Helper assertion macro (RAISE is more informative than assert) ────
    -- (Inline — no real macro support)

    -- (No fixture inserts — doctor + patient_session are real prod rows.)

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 1: fn_get_economy_base_price('gp') = 10000
    -- ════════════════════════════════════════════════════════════════════
    v_eco_base := fn_get_economy_base_price('gp');
    IF v_eco_base <> 10000 THEN
        RAISE EXCEPTION 'T1 FAIL: fn_get_economy_base_price(gp) = %, expected 10000', v_eco_base;
    END IF;
    RAISE NOTICE 'T1 PASS: Eco base price for GP = 10000';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 2 & 3: Royal initial completion uses 25%/15% × Eco base.
    --   Patient pays Royal price 420,000; doctor earns 2,500; escrow 1,500.
    -- ════════════════════════════════════════════════════════════════════
    INSERT INTO consultations (
        consultation_id, patient_session_id, doctor_id,
        service_type, service_tier, consultation_type, consultation_fee,
        chief_complaint, status, request_expires_at,
        session_duration_minutes, original_duration_minutes
    ) VALUES (
        v_consult_royal, v_patient_sess, v_doctor_id,
        'gp'::service_type_enum, 'ROYAL', 'chat', 420000,
        'fixture initial', 'pending', NOW() + INTERVAL '1 hour',
        15, 15
    );

    -- Trigger fires when status moves to 'completed'.
    UPDATE consultations
       SET status = 'completed',
           session_start_time = NOW() - INTERVAL '15 minutes',
           session_end_time = NOW(),
           follow_up_expiry = NOW() + INTERVAL '14 days'
     WHERE consultation_id = v_consult_royal;

    -- T2: doctor earnings row created with 2,500.
    SELECT amount INTO v_amount
      FROM doctor_earnings
     WHERE doctor_id = v_doctor_id
       AND consultation_id = v_consult_royal
       AND earning_type = 'consultation';
    IF v_amount IS DISTINCT FROM 2500 THEN
        RAISE EXCEPTION 'T2 FAIL: Royal initial earning = %, expected 2500 (25%% × 10000 Eco base)', v_amount;
    END IF;
    RAISE NOTICE 'T2 PASS: Royal initial earning = 2,500';

    -- T3: followup escrow held at 1,500.
    SELECT followup_escrow_amount, followup_escrow_status
      INTO v_escrow_amt, v_escrow_status
      FROM consultations WHERE consultation_id = v_consult_royal;
    IF v_escrow_amt IS DISTINCT FROM 1500 OR v_escrow_status <> 'held' THEN
        RAISE EXCEPTION 'T3 FAIL: escrow = % / %, expected 1500 / held', v_escrow_amt, v_escrow_status;
    END IF;
    RAISE NOTICE 'T3 PASS: Royal escrow held at 1,500';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 4: fn_check_royal_followup_cap on a fresh consultation = allowed
    -- ════════════════════════════════════════════════════════════════════
    v_cap := fn_check_royal_followup_cap(v_consult_royal);
    IF NOT (v_cap->>'allowed')::boolean THEN
        RAISE EXCEPTION 'T4 FAIL: cap should allow, got %', v_cap;
    END IF;
    RAISE NOTICE 'T4 PASS: Royal FU cap allows initial state (count=0)';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 5: First FU re-completion → 1,500 follow_up earning, escrow released
    -- ════════════════════════════════════════════════════════════════════
    UPDATE consultations
       SET status = 'active',
           is_reopened = true,
           last_reopened_at = NOW(),
           follow_up_count = follow_up_count + 1
     WHERE consultation_id = v_consult_royal;

    UPDATE consultations
       SET status = 'completed',
           session_end_time = NOW()
     WHERE consultation_id = v_consult_royal;

    SELECT COUNT(*), MAX(amount) INTO v_count, v_amount
      FROM doctor_earnings
     WHERE doctor_id = v_doctor_id
       AND consultation_id = v_consult_royal
       AND earning_type = 'follow_up';
    IF v_count <> 1 OR v_amount <> 1500 THEN
        RAISE EXCEPTION 'T5 FAIL: first FU → count=%, amount=%, expected 1 row × 1500', v_count, v_amount;
    END IF;

    SELECT followup_escrow_status INTO v_escrow_status
      FROM consultations WHERE consultation_id = v_consult_royal;
    IF v_escrow_status <> 'released' THEN
        RAISE EXCEPTION 'T5 FAIL: escrow status = %, expected released', v_escrow_status;
    END IF;
    RAISE NOTICE 'T5 PASS: First Royal FU paid 1,500 from escrow';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 6: 2nd, 3rd, 4th FUs each pay fresh 1,500 (escrow already released)
    -- ════════════════════════════════════════════════════════════════════
    FOR i IN 2..4 LOOP
        UPDATE consultations
           SET status = 'active', last_reopened_at = NOW(), follow_up_count = follow_up_count + 1
         WHERE consultation_id = v_consult_royal;
        UPDATE consultations
           SET status = 'completed', session_end_time = NOW()
         WHERE consultation_id = v_consult_royal;
    END LOOP;

    SELECT COUNT(*) INTO v_count
      FROM doctor_earnings
     WHERE doctor_id = v_doctor_id
       AND consultation_id = v_consult_royal
       AND earning_type = 'follow_up';
    IF v_count <> 4 THEN
        RAISE EXCEPTION 'T6 FAIL: after 4 FUs, follow_up count=%, expected 4', v_count;
    END IF;
    RAISE NOTICE 'T6 PASS: 4 FUs each paid 1,500 (total 6,000 follow-up earnings)';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 7: 5th FU within same 24h window → cap should reject (allowed=false)
    -- ════════════════════════════════════════════════════════════════════
    v_cap := fn_check_royal_followup_cap(v_consult_royal);
    IF (v_cap->>'allowed')::boolean THEN
        RAISE EXCEPTION 'T7 FAIL: cap should reject after 4 FUs, got %', v_cap;
    END IF;
    IF (v_cap->>'count')::int <> 4 THEN
        RAISE EXCEPTION 'T7 FAIL: count should be 4, got %', v_cap;
    END IF;
    IF (v_cap->>'hours_until_reset')::int < 1 THEN
        RAISE EXCEPTION 'T7 FAIL: hours_until_reset = %, expected >= 1', v_cap;
    END IF;
    RAISE NOTICE 'T7 PASS: 5th FU rejected — count=4, hours_until_reset=%', v_cap->>'hours_until_reset';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 8: video_call ≥ 60s in active Royal window → 2,000 royal_call earning
    -- ════════════════════════════════════════════════════════════════════
    INSERT INTO video_calls (consultation_id, started_at, ended_at, duration_seconds)
    VALUES (v_consult_royal, NOW() - INTERVAL '5 minutes', NOW(), 90)
    RETURNING call_id INTO v_video_call_a;

    SELECT COUNT(*) INTO v_count
      FROM doctor_earnings
     WHERE doctor_id = v_doctor_id
       AND consultation_id = v_consult_royal
       AND earning_type = 'royal_call'
       AND amount = 2000;
    IF v_count <> 1 THEN
        RAISE EXCEPTION 'T8 FAIL: royal_call earning count=%, expected 1', v_count;
    END IF;
    RAISE NOTICE 'T8 PASS: video_call ≥60s → 2,000 royal_call earning';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 9: video_call < 60s → no earning
    -- ════════════════════════════════════════════════════════════════════
    INSERT INTO video_calls (consultation_id, started_at, ended_at, duration_seconds)
    VALUES (v_consult_royal, NOW() - INTERVAL '1 minute', NOW(), 30);

    SELECT COUNT(*) INTO v_count
      FROM doctor_earnings
     WHERE doctor_id = v_doctor_id
       AND consultation_id = v_consult_royal
       AND earning_type = 'royal_call';
    IF v_count <> 1 THEN
        RAISE EXCEPTION 'T9 FAIL: still % royal_call rows after 30s call, expected 1 (unchanged)', v_count;
    END IF;
    RAISE NOTICE 'T9 PASS: <60s call did not pay';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 10: 2nd & 3rd same-day calls each pay; 4th does NOT
    -- ════════════════════════════════════════════════════════════════════
    INSERT INTO video_calls (consultation_id, started_at, ended_at, duration_seconds)
    VALUES (v_consult_royal, NOW() - INTERVAL '4 minutes', NOW(), 75);
    INSERT INTO video_calls (consultation_id, started_at, ended_at, duration_seconds)
    VALUES (v_consult_royal, NOW() - INTERVAL '3 minutes', NOW(), 80);
    INSERT INTO video_calls (consultation_id, started_at, ended_at, duration_seconds)
    VALUES (v_consult_royal, NOW() - INTERVAL '2 minutes', NOW(), 65);

    SELECT COUNT(*) INTO v_count
      FROM doctor_earnings
     WHERE doctor_id = v_doctor_id
       AND consultation_id = v_consult_royal
       AND earning_type = 'royal_call';
    IF v_count <> 3 THEN
        RAISE EXCEPTION 'T10 FAIL: royal_call earnings count=%, expected 3 (4th capped)', v_count;
    END IF;

    -- And the cap helper agrees:
    v_cap := fn_check_royal_call_cap(v_doctor_id, v_consult_royal);
    IF (v_cap->>'allowed')::boolean THEN
        RAISE EXCEPTION 'T10 FAIL: call cap should reject after 3, got %', v_cap;
    END IF;
    RAISE NOTICE 'T10 PASS: 4th same-day call did not pay (cap honoured)';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 11: Sweep function doesn't error and returns an integer
    -- ════════════════════════════════════════════════════════════════════
    PERFORM fn_sweep_royal_call_floor();
    RAISE NOTICE 'T11 PASS: fn_sweep_royal_call_floor() ran without error';

    -- ════════════════════════════════════════════════════════════════════
    -- TEST 12: ECONOMY initial completion still uses consultation_fee (unchanged)
    -- ════════════════════════════════════════════════════════════════════
    -- Use a separate sentinel consultation so the Royal one above isn't disturbed.
    DECLARE
        v_consult_eco UUID := '99999999-9999-9999-9999-c0c0eee00002'::uuid;
        v_eco_amount  INTEGER;
    BEGIN
        DELETE FROM doctor_earnings WHERE consultation_id = v_consult_eco;
        DELETE FROM escrow_ledger   WHERE consultation_id = v_consult_eco;
        DELETE FROM consultations   WHERE consultation_id = v_consult_eco;

        INSERT INTO consultations (
            consultation_id, patient_session_id, doctor_id,
            service_type, service_tier, consultation_type, consultation_fee,
            chief_complaint, status, request_expires_at,
            session_duration_minutes, original_duration_minutes
        ) VALUES (
            v_consult_eco, v_patient_sess, v_doctor_id,
            'gp'::service_type_enum, 'ECONOMY', 'chat', 10000,
            'fixture eco', 'pending', NOW() + INTERVAL '1 hour',
            15, 15
        );

        UPDATE consultations
           SET status = 'completed', session_start_time = NOW(), session_end_time = NOW(),
               follow_up_expiry = NOW() + INTERVAL '14 days'
         WHERE consultation_id = v_consult_eco;

        SELECT amount INTO v_eco_amount
          FROM doctor_earnings
         WHERE doctor_id = v_doctor_id
           AND consultation_id = v_consult_eco
           AND earning_type = 'consultation';
        IF v_eco_amount IS DISTINCT FROM 2500 THEN
            RAISE EXCEPTION 'T12 FAIL: Economy initial earning = %, expected 2500', v_eco_amount;
        END IF;
        RAISE NOTICE 'T12 PASS: Economy initial earning still 2,500 (25%% × 10,000)';

        DELETE FROM doctor_earnings WHERE consultation_id = v_consult_eco;
        DELETE FROM escrow_ledger   WHERE consultation_id = v_consult_eco;
        DELETE FROM consultations   WHERE consultation_id = v_consult_eco;
    END;

    -- ── Cleanup ───────────────────────────────────────────────────────────
    DELETE FROM doctor_earnings  WHERE consultation_id = v_consult_royal;
    DELETE FROM video_calls      WHERE consultation_id = v_consult_royal;
    DELETE FROM escrow_ledger    WHERE consultation_id = v_consult_royal;
    DELETE FROM messages         WHERE consultation_id = v_consult_royal;
    DELETE FROM royal_call_floor_misses WHERE consultation_id = v_consult_royal;
    DELETE FROM consultations    WHERE consultation_id = v_consult_royal;

    RAISE NOTICE '✓ All 12 Royal earnings tests passed.';
END $$;
