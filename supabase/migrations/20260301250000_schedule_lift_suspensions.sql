-- Enable pg_cron and pg_net for scheduled edge function calls
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

-- Schedule lift-expired-suspensions to run every 15 minutes.
-- NOTE: After running this migration, set the CRON_SECRET env var on your
-- Supabase project (Settings > Edge Functions > Secrets) and update the
-- cron_secret value below to match.
SELECT cron.schedule(
  'lift-expired-suspensions',
  '*/15 * * * *',
  $$
    SELECT net.http_post(
      url := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/lift-expired-suspensions',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Cron-Secret', current_setting('app.settings.cron_secret', true)
      ),
      body := '{}'::jsonb
    );
  $$
);
