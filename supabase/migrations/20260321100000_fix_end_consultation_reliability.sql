-- Migration: Fix consultation end reliability for iOS and Android
--
-- Fixes three issues that caused "nothing goes on" when doctor ends consultation:
--
-- 1. end_consultation() RPC was not idempotent: if the consultation was already
--    'completed' (e.g. timer cron ran first, or the button was tapped twice),
--    it threw "Consultation is not in an endable status". Both iOS and Android
--    got an error back and silently swallowed it, showing nothing in the UI.
--    Fix: return immediately if already completed.
--
-- 2. REPLICA IDENTITY DEFAULT on the consultations table means Supabase Realtime
--    only includes the PK columns in the OLD record of UPDATE events. Some
--    Supabase Realtime versions use the old record for server-side filter matching.
--    If the subscription filter is evaluated against the old record and the status
--    column was absent, both iOS and Android clients missed the 'completed' event.
--    Fix: set REPLICA IDENTITY FULL so all column values are broadcast in every
--    UPDATE event, giving both old and new records to the filter engine.
--
-- 3. No notification type entry existed for 'consultation_ended', so the
--    send-push-notification edge function may have rejected the push.
--    Fix: insert the missing type.

-- ─── 1. Make end_consultation() idempotent ────────────────────────────────────

CREATE OR REPLACE FUNCTION end_consultation(
    p_consultation_id uuid,
    p_doctor_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_status consultation_status_enum;
BEGIN
    SELECT status INTO v_status
    FROM consultations
    WHERE consultation_id = p_consultation_id
      AND doctor_id = p_doctor_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Consultation not found for this doctor'
            USING ERRCODE = 'P0001';
    END IF;

    -- Idempotent: already completed is a no-op success, not an error.
    -- This handles the case where the timer cron or another process already
    -- closed the consultation before the doctor's explicit end request arrived.
    IF v_status = 'completed'::consultation_status_enum THEN
        RETURN;
    END IF;

    -- Block ending during grace_period (patient is actively processing payment).
    IF v_status = 'grace_period'::consultation_status_enum THEN
        RAISE EXCEPTION 'Cannot end consultation while patient is processing payment'
            USING ERRCODE = 'P0001';
    END IF;

    -- Allow ending only from active or awaiting_extension.
    IF v_status NOT IN ('active'::consultation_status_enum,
                         'awaiting_extension'::consultation_status_enum) THEN
        RAISE EXCEPTION 'Consultation is not in an endable status (current: %)', v_status
            USING ERRCODE = 'P0001';
    END IF;

    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        session_end_time = now(),
        grace_period_end_at = NULL,
        updated_at = now()
    WHERE consultation_id = p_consultation_id;
END;
$$;

-- ─── 2. REPLICA IDENTITY FULL for consultations ───────────────────────────────
-- With DEFAULT, only PK columns appear in the WAL old-record for UPDATE events.
-- Supabase Realtime uses the old record for server-side row filter evaluation on
-- UPDATE events. Setting FULL ensures all column values (including `status`) are
-- present in both old and new records, so the eq("consultation_id", id) filter
-- and RLS checks are evaluated against the complete row on every UPDATE.
-- Consultations is a low-write table (one row per session) so WAL bloat is minimal.

ALTER TABLE consultations REPLICA IDENTITY FULL;

-- ─── 3. Register 'consultation_ended' in the notification type enum ──────────
-- The notifications table uses notification_type_enum. Adding the value here
-- ensures any notification log inserts (e.g. from send-push-notification) that
-- reference this type do not fail with an invalid enum cast.

ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'consultation_ended';
