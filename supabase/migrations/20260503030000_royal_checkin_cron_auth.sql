-- ============================================================================
-- Reschedule royal-checkin-cron with hardcoded Authorization + X-Cron-Secret
-- headers, matching the same fix applied to medication-reminder-cron in
-- 20260501270000. The current_setting('app.settings.*') GUCs are not
-- populated on this project, so the original schedule sent literal NULL
-- headers and got UNAUTHORIZED_NO_AUTH_HEADER from the gateway.
-- ============================================================================

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
    $cron$
    SELECT net.http_post(
        url     := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/royal-checkin-cron',
        headers := jsonb_build_object(
            'Content-Type',   'application/json',
            'Authorization',  'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MTMyMjc5NiwiZXhwIjoyMDg2ODk4Nzk2fQ.DkyaVhp-0-PsuTDQsycJIDVIHtFk7522zYMd_Ocgx9w',
            'X-Cron-Secret',  'a375791bc55596511dfb7229b3aafb3a6011443023ec01de'
        ),
        body    := '{}'::jsonb
    );
    $cron$
);
