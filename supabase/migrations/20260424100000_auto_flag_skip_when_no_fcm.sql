-- ============================================================================
-- Guard: auto-flag trigger must NOT flag doctors who had no FCM token
-- registered at the time of the missed requests. "Our bug, not theirs."
--
-- Backstory: migration 20260420100000 added a trigger that flags a doctor
-- after 3 consecutive non-accepted (rejected/expired) requests. The
-- business rule is sound, but it assumes the doctor actually had a chance
-- to accept — i.e. their device received the push. If the doctor's
-- fcm_tokens row is missing (login race condition, token-sync silently
-- failed, etc.) no push ever fired. Flagging them in that case punishes
-- them for an infrastructure bug.
--
-- This migration changes the trigger to short-circuit when the doctor has
-- no fcm_tokens row right now. If the row existed during the missed
-- window and was later deleted, that's a rare enough edge case to accept
-- false-negatives; flagging will resume as soon as a new miss occurs
-- after token registration.
--
-- Also un-flags existing doctors whose flag_reason is the auto-flag
-- message AND who currently have no fcm_tokens row — they're the exact
-- cohort that got bitten by this bug.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_auto_flag_doctor_rejection_streak()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_three_non_accepts INT;
    v_has_fcm_token          BOOLEAN;
BEGIN
    -- Fire only on transitions INTO rejected/expired.
    IF NEW.status NOT IN ('rejected', 'expired') THEN
        RETURN NEW;
    END IF;
    IF COALESCE(OLD.status, '') = NEW.status THEN
        RETURN NEW;
    END IF;

    -- Guard: skip if the doctor has no registered FCM token right now.
    -- Without a token, the push could not have been delivered; a non-accept
    -- is not evidence of ghosting.
    SELECT EXISTS (
        SELECT 1 FROM fcm_tokens
         WHERE user_id = NEW.doctor_id::text
           AND token IS NOT NULL
           AND length(token) > 0
    ) INTO v_has_fcm_token;

    IF NOT v_has_fcm_token THEN
        RETURN NEW;
    END IF;

    -- Of this doctor's three most-recent requests, how many were non-accepts?
    SELECT COUNT(*)
      INTO v_last_three_non_accepts
      FROM (
          SELECT status
            FROM consultation_requests
           WHERE doctor_id = NEW.doctor_id
           ORDER BY created_at DESC
           LIMIT 3
      ) AS recent
     WHERE status IN ('rejected', 'expired');

    IF v_last_three_non_accepts >= 3 THEN
        UPDATE doctor_profiles
           SET flagged     = TRUE,
               flag_reason = 'Did not accept 3 consecutive requests',
               flagged_at  = COALESCE(flagged_at, NOW())
         WHERE doctor_id = NEW.doctor_id
           AND flagged   = FALSE;
    END IF;

    RETURN NEW;
END;
$$;

-- Un-flag the cohort currently bitten by the bug: auto-flagged doctors
-- who STILL have no fcm_tokens row at migration time. Admin-set flags
-- (anything with a different flag_reason) are left alone — those are
-- deliberate and not related to this bug.
UPDATE doctor_profiles d
   SET flagged     = FALSE,
       flag_reason = NULL,
       flagged_at  = NULL,
       updated_at  = NOW()
 WHERE d.flagged = TRUE
   AND d.flag_reason = 'Did not accept 3 consecutive requests'
   AND NOT EXISTS (
       SELECT 1 FROM fcm_tokens f
        WHERE f.user_id = d.doctor_id::text
          AND f.token IS NOT NULL
          AND length(f.token) > 0
   );
