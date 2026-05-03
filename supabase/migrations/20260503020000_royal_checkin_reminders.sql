-- ============================================================================
-- Royal-client check-in reminders for doctors.
--
-- Spec (product call 2026-05-03):
--   Doctors with at least one Royal consultation in its active 14-day
--   follow-up window (status=completed, service_tier=ROYAL,
--   follow_up_expiry > now) should get a check-in reminder three times
--   a day at 08:00 / 13:00 / 18:00 EAT.
--
--   Each slot fires up to 3 times — the slot's nominal time, then again
--   at +5 and +10 minutes — until the doctor acknowledges by tapping
--   "Open Royal Clients" on the notification. Once acknowledged, that
--   slot is closed for the day; the next slot opens on its own schedule.
--
--   Skip rules: banned, suspended, or currently in_session doctors
--   are not pinged. is_available=false IS still pinged (a doctor who
--   stopped taking new patients still owes their existing Royal
--   patients a check-in).
-- ============================================================================

CREATE TABLE IF NOT EXISTS royal_checkin_reminders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id           UUID NOT NULL REFERENCES doctor_profiles(doctor_id) ON DELETE CASCADE,
    -- slot_date is in EAT (Africa/Dar_es_Salaam). Storing as DATE keeps the
    -- daily-uniqueness constraint clean and avoids timezone math elsewhere.
    slot_date           DATE NOT NULL,
    -- 8 = 08:00 slot, 13 = 13:00 slot, 18 = 18:00 slot.
    slot_hour           SMALLINT NOT NULL CHECK (slot_hour IN (8, 13, 18)),
    -- 1, 2, or 3 — incremented as the cron resends at +0, +5, +10 minutes.
    attempts_sent       SMALLINT NOT NULL DEFAULT 0
                        CHECK (attempts_sent BETWEEN 0 AND 3),
    last_sent_at        TIMESTAMPTZ,
    acknowledged_at     TIMESTAMPTZ,
    royal_client_count  INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doctor_id, slot_date, slot_hour)
);

-- Hot path: cron picks unacknowledged rows for today and decides whether to
-- escalate. Index supports both the "still open" lookup and admin compliance
-- queries by doctor.
CREATE INDEX IF NOT EXISTS idx_royal_checkin_unack
    ON royal_checkin_reminders(slot_date, slot_hour)
    WHERE acknowledged_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_royal_checkin_doctor_date
    ON royal_checkin_reminders(doctor_id, slot_date);

-- ── Helper: count active-window Royal clients for a doctor right now ──────
-- Used by both the cron (to decide whether the doctor gets pinged at all)
-- and the doctor client (to render the "N Royal check-ins" pill).
CREATE OR REPLACE FUNCTION active_royal_client_count(p_doctor_id UUID)
RETURNS INTEGER
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COUNT(*)::INTEGER
      FROM consultations c
     WHERE c.doctor_id::text = p_doctor_id::text
       AND c.service_tier = 'ROYAL'
       AND c.status::text = 'completed'
       AND c.follow_up_expiry > NOW();
$$;

GRANT EXECUTE ON FUNCTION active_royal_client_count(UUID) TO authenticated, service_role;

-- ── RLS: doctors can read their own rows; cron uses service-role ─────────
ALTER TABLE royal_checkin_reminders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Doctors read their own royal checkins" ON royal_checkin_reminders;
CREATE POLICY "Doctors read their own royal checkins"
    ON royal_checkin_reminders FOR SELECT TO authenticated
    USING (doctor_id::text = (SELECT auth.uid()::text));

-- ── pg_cron: every minute, call the royal-checkin-cron edge function ─────
DO $$
BEGIN
    PERFORM cron.unschedule(jobid)
      FROM cron.job
     WHERE jobname = 'royal-checkin-cron';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

SELECT cron.schedule(
    'royal-checkin-cron',
    '* * * * *',
    $$
    SELECT net.http_post(
        url     := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/royal-checkin-cron',
        headers := jsonb_build_object(
            'Content-Type',   'application/json',
            'Authorization',  'Bearer ' || current_setting('app.settings.edge_fn_bearer_jwt', true),
            'X-Cron-Secret',  current_setting('app.settings.cron_secret', true)
        ),
        body    := '{}'::jsonb
    );
    $$
);
