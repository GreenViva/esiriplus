-- Schedule the medication reminder cron job (every minute)
-- Finds due medication timetable entries, assigns nurses, triggers calls.

SELECT cron.unschedule('medication-reminder-cron')
WHERE EXISTS (
  SELECT 1 FROM cron.job WHERE jobname = 'medication-reminder-cron'
);

SELECT cron.schedule(
  'medication-reminder-cron',
  '* * * * *',
  $$
  SELECT net.http_post(
    url    := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/medication-reminder-cron',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Cron-Secret', 'a375791bc55596511dfb7229b3aafb3a6011443023ec01de'
    ),
    body   := '{}'::jsonb
  );
  $$
);
