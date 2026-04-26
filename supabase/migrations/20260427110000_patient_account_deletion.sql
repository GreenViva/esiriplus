-- Patient self-deletion with 30-day grace period.
--
-- Two-stage flow:
--   1. User taps "Delete account" → fn_mark_patient_for_deletion() stamps
--      deleted_at = now() and purge_at = now() + 30 days. Row stays
--      readable so the user can sign back in within 30 days to abort.
--   2. Daily cron (purge-deleted-patients edge function) calls
--      fn_purge_deleted_patients() which permanently removes the session
--      row plus everything it owns: messages, consultations,
--      consultation_requests, consultation_reports, appointments,
--      patient_reports.
--
-- Doctor-owned data referencing the patient (e.g. doctor_ratings,
-- doctor_earnings) is left untouched — those are authoritative records
-- the doctor's side needs. The patient's PII (their session row) is gone.

ALTER TABLE patient_sessions
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS purge_at   TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_patient_sessions_purge_at
  ON patient_sessions(purge_at) WHERE purge_at IS NOT NULL;

-- ── Mark for deletion ─────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_mark_patient_for_deletion(p_session_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_updated INTEGER;
BEGIN
    UPDATE patient_sessions
    SET deleted_at = now(),
        purge_at   = now() + INTERVAL '30 days',
        is_active  = FALSE
    WHERE session_id = p_session_id
      AND deleted_at IS NULL;

    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated > 0;
END;
$$;

REVOKE ALL ON FUNCTION fn_mark_patient_for_deletion(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_mark_patient_for_deletion(UUID) TO service_role;

COMMENT ON FUNCTION fn_mark_patient_for_deletion(UUID) IS
    'Soft-delete a patient session. Sets deleted_at, purge_at = +30d, is_active=false.';

-- ── Purge expired soft-deletes ────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_purge_deleted_patients()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_deleted INTEGER := 0;
    v_session_id UUID;
BEGIN
    FOR v_session_id IN
        SELECT session_id FROM patient_sessions
        WHERE purge_at IS NOT NULL AND purge_at <= now()
    LOOP
        -- Children first, parents last.
        DELETE FROM messages
            WHERE consultation_id IN (
                SELECT consultation_id FROM consultations
                WHERE patient_session_id = v_session_id
            );
        DELETE FROM consultation_reports
            WHERE consultation_id IN (
                SELECT consultation_id FROM consultations
                WHERE patient_session_id = v_session_id
            );
        DELETE FROM consultation_requests
            WHERE patient_session_id = v_session_id;
        DELETE FROM consultations
            WHERE patient_session_id = v_session_id;
        DELETE FROM appointments
            WHERE patient_session_id = v_session_id;
        DELETE FROM patient_reports
            WHERE patient_session_id = v_session_id;
        DELETE FROM patient_sessions
            WHERE session_id = v_session_id;

        v_deleted := v_deleted + 1;
    END LOOP;

    RETURN v_deleted;
END;
$$;

REVOKE ALL ON FUNCTION fn_purge_deleted_patients() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_purge_deleted_patients() TO service_role;

COMMENT ON FUNCTION fn_purge_deleted_patients() IS
    'Permanently deletes patient sessions whose 30-day grace period has expired.';

-- ── Schedule daily purge (03:30 UTC, ~06:30 EAT) ──────────────────────
SELECT cron.schedule(
  'purge-deleted-patients',
  '30 3 * * *',
  $$
    SELECT net.http_post(
      url := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/purge-deleted-patients',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Cron-Secret', current_setting('app.settings.cron_secret', true)
      ),
      body := '{}'::jsonb
    );
  $$
);
