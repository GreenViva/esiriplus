-- Migration: Cron jobs for appointment reminders and missed-appointment handling
-- Requires pg_cron and pg_net extensions (enabled by default on Supabase)

-- ============================================================================
-- Cron 1: Appointment reminders (every minute)
-- Sends 20/10/5/0 min reminders via edge function
-- ============================================================================
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
      'X-Cron-Secret', 'a375791bc55596511dfb7229b3aafb3a6011443023ec01de'
    ),
    body   := '{}'::jsonb
  );
  $$
);

-- ============================================================================
-- Cron 2: Handle missed appointments (every minute)
-- Marks overdue confirmed appointments as missed
-- ============================================================================
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
      'X-Cron-Secret', 'a375791bc55596511dfb7229b3aafb3a6011443023ec01de'
    ),
    body   := '{}'::jsonb
  );
  $$
);
