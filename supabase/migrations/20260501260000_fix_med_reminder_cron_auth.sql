-- ============================================================================
-- Fix the medication-reminder-cron pg_cron job: it was sending only
-- Content-Type + X-Cron-Secret, but the Supabase Edge Functions gateway
-- requires an Authorization header on every request (separate from the
-- function's own X-Cron-Secret check). Without it the gateway rejects with
-- UNAUTHORIZED_NO_AUTH_HEADER and the function never even runs — meaning
-- timetables never fire and nurses never get rung.
--
-- Re-creates the job with Authorization: Bearer <service-role JWT>.
-- ============================================================================

DO $$
BEGIN
    PERFORM cron.unschedule(jobid)
      FROM cron.job
     WHERE jobname = 'medication-reminder-cron';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

SELECT cron.schedule(
    'medication-reminder-cron',
    '* * * * *',
    $$
    SELECT net.http_post(
        url     := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/medication-reminder-cron',
        headers := jsonb_build_object(
            'Content-Type',   'application/json',
            'Authorization',  'Bearer ' || current_setting('app.settings.service_role_key', true),
            'X-Cron-Secret',  current_setting('app.settings.cron_secret', true)
        ),
        body    := '{}'::jsonb
    );
    $$
);
