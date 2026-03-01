-- Unschedule the initial job and reschedule with the cron secret hardcoded
SELECT cron.unschedule('lift-expired-suspensions');

SELECT cron.schedule(
  'lift-expired-suspensions',
  '*/15 * * * *',
  $$
    SELECT net.http_post(
      url := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/lift-expired-suspensions',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Cron-Secret', 'a375791bc55596511dfb7229b3aafb3a6011443023ec01de'
      ),
      body := '{}'::jsonb
    );
  $$
);
