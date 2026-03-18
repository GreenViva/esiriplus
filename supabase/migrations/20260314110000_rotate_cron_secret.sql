-- Rotate cron secret: The old secret was hardcoded in Git history.
-- New secret has been set via: supabase secrets set CRON_SECRET=...
-- This migration updates all pg_cron jobs to use the new secret.

-- ─── 1. lift-expired-suspensions (every 15 min) ─────────────────────────────────
SELECT cron.unschedule('lift-expired-suspensions');

SELECT cron.schedule(
  'lift-expired-suspensions',
  '*/15 * * * *',
  $$
    SELECT net.http_post(
      url := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/lift-expired-suspensions',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Cron-Secret', '80514a1f778b22487067b9ec39f9581186c04eeae89e53b0'
      ),
      body := '{}'::jsonb
    );
  $$
);

-- ─── 2. appointment-reminders (every minute) ────────────────────────────────────
SELECT cron.unschedule('appointment-reminders')
WHERE EXISTS (
  SELECT 1 FROM cron.job WHERE jobname = 'appointment-reminders'
);

SELECT cron.schedule(
  'appointment-reminders',
  '* * * * *',
  $$
  SELECT net.http_post(
    url    := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/appointment-reminder',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Cron-Secret', '80514a1f778b22487067b9ec39f9581186c04eeae89e53b0'
    ),
    body   := '{}'::jsonb
  );
  $$
);

-- ─── 3. handle-missed-appointments (every minute) ───────────────────────────────
SELECT cron.unschedule('handle-missed-appointments')
WHERE EXISTS (
  SELECT 1 FROM cron.job WHERE jobname = 'handle-missed-appointments'
);

SELECT cron.schedule(
  'handle-missed-appointments',
  '* * * * *',
  $$
  SELECT net.http_post(
    url    := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/handle-missed-appointments',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Cron-Secret', '80514a1f778b22487067b9ec39f9581186c04eeae89e53b0'
    ),
    body   := '{}'::jsonb
  );
  $$
);
