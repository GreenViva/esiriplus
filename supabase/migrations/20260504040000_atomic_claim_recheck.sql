-- ============================================================================
-- Tighten claim_nurse_for_med_event / claim_co_for_royal_escalation
--
-- Under high concurrency we still observed ~15% nurse-id collisions. The
-- gap: in READ COMMITTED, the NOT EXISTS subquery inside the SELECT…
-- FOR UPDATE SKIP LOCKED uses the transaction's snapshot. If transaction
-- A commits an event row stamping nurse N1 AFTER T2's snapshot was
-- taken but BEFORE T2 acquires its row lock, T2's lock attempt succeeds
-- (T1 released the lock at commit) but its snapshot still shows N1 as
-- available — so T2 picks N1 and we collide.
--
-- The fix: after locking the doctor_profiles row, run a SECOND EXISTS
-- check as its own plpgsql statement. In READ COMMITTED each statement
-- gets a fresh snapshot, so the re-check sees T1's just-committed event
-- and we bail out without claiming the nurse.
-- ============================================================================

CREATE OR REPLACE FUNCTION claim_nurse_for_med_event(
    p_event_id UUID,
    p_exclude_nurse_id UUID DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_nurse_id UUID;
    v_claimed BOOLEAN := FALSE;
BEGIN
    -- Loop because the first candidate may be claimed by a concurrent tx
    -- between our snapshot and our row lock; on detection we skip and try
    -- the next candidate.
    LOOP
        SELECT dp.doctor_id INTO v_nurse_id
        FROM doctor_profiles dp
        WHERE dp.specialty = 'nurse'
          AND dp.is_verified = true
          AND dp.is_available = true
          AND dp.in_session = false
          AND dp.is_banned = false
          AND (p_exclude_nurse_id IS NULL OR dp.doctor_id <> p_exclude_nurse_id)
          AND NOT EXISTS (
              SELECT 1 FROM medication_reminder_events e
              WHERE e.nurse_id = dp.doctor_id
                AND e.status IN ('nurse_ringing','nurse_notified','nurse_accepted','nurse_calling')
          )
        ORDER BY dp.doctor_id
        LIMIT 1
        FOR UPDATE SKIP LOCKED;

        IF v_nurse_id IS NULL THEN
            RETURN NULL;
        END IF;

        -- Fresh-snapshot recheck. If a concurrent tx committed an event
        -- with this nurse between our SELECT's snapshot and the lock
        -- acquisition, this EXISTS will see it (READ COMMITTED gives
        -- per-statement snapshots in plpgsql).
        IF EXISTS (
            SELECT 1 FROM medication_reminder_events e
            WHERE e.nurse_id = v_nurse_id
              AND e.status IN ('nurse_ringing','nurse_notified','nurse_accepted','nurse_calling')
        ) THEN
            v_nurse_id := NULL;
            CONTINUE;
        END IF;

        UPDATE medication_reminder_events
           SET nurse_id = v_nurse_id,
               status   = 'nurse_ringing'
         WHERE event_id = p_event_id
           AND status IN ('pending', 'no_nurse');

        v_claimed := TRUE;
        EXIT;
    END LOOP;

    IF NOT v_claimed THEN
        RETURN NULL;
    END IF;
    RETURN v_nurse_id;
END;
$$;

GRANT EXECUTE ON FUNCTION claim_nurse_for_med_event(UUID, UUID) TO service_role;

CREATE OR REPLACE FUNCTION claim_co_for_royal_escalation(
    p_escalation_id UUID,
    p_exclude_co_id UUID DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_co_id UUID;
    v_claimed BOOLEAN := FALSE;
BEGIN
    LOOP
        SELECT dp.doctor_id INTO v_co_id
        FROM doctor_profiles dp
        WHERE dp.specialty = 'clinical_officer'
          AND dp.is_verified = true
          AND dp.is_available = true
          AND dp.in_session = false
          AND dp.is_banned = false
          AND (p_exclude_co_id IS NULL OR dp.doctor_id <> p_exclude_co_id)
          AND NOT EXISTS (
              SELECT 1 FROM royal_checkin_escalations e
              WHERE e.co_id = dp.doctor_id
                AND e.status IN ('co_ringing','co_accepted','in_progress')
          )
        ORDER BY dp.doctor_id
        LIMIT 1
        FOR UPDATE SKIP LOCKED;

        IF v_co_id IS NULL THEN
            RETURN NULL;
        END IF;

        IF EXISTS (
            SELECT 1 FROM royal_checkin_escalations e
            WHERE e.co_id = v_co_id
              AND e.status IN ('co_ringing','co_accepted','in_progress')
        ) THEN
            v_co_id := NULL;
            CONTINUE;
        END IF;

        UPDATE royal_checkin_escalations
           SET co_id  = v_co_id,
               status = 'co_ringing'
         WHERE escalation_id = p_escalation_id
           AND status = 'pending';

        v_claimed := TRUE;
        EXIT;
    END LOOP;

    IF NOT v_claimed THEN
        RETURN NULL;
    END IF;
    RETURN v_co_id;
END;
$$;

GRANT EXECUTE ON FUNCTION claim_co_for_royal_escalation(UUID, UUID) TO service_role;
