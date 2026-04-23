-- ============================================================================
-- Auto-flag doctors who fail to accept 3 consecutive consultation requests.
--
-- Why: admins need to know when a doctor is routinely ghosting incoming
-- requests (rejected or expired). Doing this client-side is unreliable —
-- timeouts fire from cron, rejections come from the doctor app, manual
-- closes come from edge functions. A Postgres trigger on
-- consultation_requests UPDATE is the one place every status transition
-- passes through, so we flag from there.
--
-- Flag condition: the doctor's three most-recent requests (by created_at)
-- are all in status IN ('rejected', 'expired'). Accepting a later request
-- resets the streak implicitly because the trigger only looks at the last
-- three — an "accepted" row would push rejections out of the top three.
--
-- An admin can unflag a doctor manually via doctor_profiles; this trigger
-- only ever SETS flagged=TRUE, it never clears it (avoids thrash if a
-- doctor barely recovers).
-- ============================================================================

-- ── 1. Schema additions ────────────────────────────────────────────────────
ALTER TABLE doctor_profiles
    ADD COLUMN IF NOT EXISTS flagged       BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS flag_reason   TEXT,
    ADD COLUMN IF NOT EXISTS flagged_at    TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_doctor_profiles_flagged
    ON doctor_profiles (flagged) WHERE flagged = TRUE;

-- ── 2. Trigger function ────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_auto_flag_doctor_rejection_streak()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_three_non_accepts INT;
BEGIN
    -- Fire only on transitions INTO rejected/expired.
    IF NEW.status NOT IN ('rejected', 'expired') THEN
        RETURN NEW;
    END IF;
    IF COALESCE(OLD.status, '') = NEW.status THEN
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

-- ── 3. Wire the trigger ────────────────────────────────────────────────────
DROP TRIGGER IF EXISTS trg_auto_flag_doctor_rejection_streak ON consultation_requests;
CREATE TRIGGER trg_auto_flag_doctor_rejection_streak
AFTER UPDATE OF status ON consultation_requests
FOR EACH ROW EXECUTE FUNCTION fn_auto_flag_doctor_rejection_streak();

-- Also fire on direct INSERTs that land in a terminal non-accept state
-- (rare, but edge functions sometimes short-circuit this way).
DROP TRIGGER IF EXISTS trg_auto_flag_doctor_rejection_streak_insert ON consultation_requests;
CREATE TRIGGER trg_auto_flag_doctor_rejection_streak_insert
AFTER INSERT ON consultation_requests
FOR EACH ROW
WHEN (NEW.status IN ('rejected', 'expired'))
EXECUTE FUNCTION fn_auto_flag_doctor_rejection_streak();

-- ── 4. Backfill existing data ──────────────────────────────────────────────
-- Doctors currently sitting on 3-in-a-row rejections/expires get flagged
-- immediately, not only after the next miss.
DO $backfill$
DECLARE
    v_doc RECORD;
BEGIN
    FOR v_doc IN
        SELECT DISTINCT doctor_id
          FROM consultation_requests
         WHERE status IN ('rejected', 'expired')
    LOOP
        PERFORM 1
           FROM (
               SELECT status
                 FROM consultation_requests
                WHERE doctor_id = v_doc.doctor_id
                ORDER BY created_at DESC
                LIMIT 3
           ) last_three
          HAVING COUNT(*) FILTER (WHERE status IN ('rejected', 'expired')) >= 3;
        IF FOUND THEN
            UPDATE doctor_profiles
               SET flagged     = TRUE,
                   flag_reason = COALESCE(flag_reason, 'Did not accept 3 consecutive requests'),
                   flagged_at  = COALESCE(flagged_at, NOW())
             WHERE doctor_id = v_doc.doctor_id
               AND flagged   = FALSE;
        END IF;
    END LOOP;
END
$backfill$;
