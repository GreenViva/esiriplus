-- ============================================================================
-- Atomic nurse / clinical-officer assignment via row-locked SQL functions.
--
-- Replaces the in-memory pool used by medication-reminder-cron and
-- royal-checkin-cron. The pool was correct within a single edge-function
-- invocation but broke down when pg_cron's tick took >60 s and a second
-- tick fired in parallel — both isolates built independent pools and
-- could hand out the same nurse twice.
--
-- The new pattern uses `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` against
-- doctor_profiles, with a NOT EXISTS sub-query against the events table to
-- exclude busy nurses. SKIP LOCKED makes any number of concurrent
-- invocations fan out across distinct rows; the lock is released at
-- transaction commit, by which point the nurse_id has been written and
-- subsequent NOT EXISTS checks will find them busy.
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
BEGIN
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

    -- Reserve the nurse atomically: stamping nurse_id + status here means
    -- the very next claim call (across any number of parallel invocations)
    -- sees this nurse as busy via the NOT EXISTS check above.
    UPDATE medication_reminder_events
       SET nurse_id = v_nurse_id,
           status   = 'nurse_ringing'
     WHERE event_id = p_event_id
       AND status IN ('pending', 'no_nurse');

    RETURN v_nurse_id;
END;
$$;

GRANT EXECUTE ON FUNCTION claim_nurse_for_med_event(UUID, UUID) TO service_role;

-- ──────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION claim_co_for_royal_escalation(
    p_escalation_id UUID,
    p_exclude_co_id UUID DEFAULT NULL
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_co_id UUID;
BEGIN
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

    UPDATE royal_checkin_escalations
       SET co_id  = v_co_id,
           status = 'co_ringing'
     WHERE escalation_id = p_escalation_id
       AND status = 'pending';

    RETURN v_co_id;
END;
$$;

GRANT EXECUTE ON FUNCTION claim_co_for_royal_escalation(UUID, UUID) TO service_role;
