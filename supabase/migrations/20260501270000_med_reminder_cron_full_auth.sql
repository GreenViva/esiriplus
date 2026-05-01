-- ============================================================================
-- Final cron auth fix — supersedes 20260501260000.
--
-- Two bugs were stacked on the medication-reminder-cron job:
--   1. Missing Authorization header → Supabase gateway rejected with
--      UNAUTHORIZED_NO_AUTH_HEADER, function never ran.
--   2. Wrong X-Cron-Secret value vs. what the function reads from env.
--
-- 20260501260000 tried current_setting('app.settings.…') which isn't set,
-- so it sent literal NULLs. This migration hardcodes both values in the
-- cron command. CRON_SECRET in Supabase project secrets has been rotated
-- to match (one-off via `supabase secrets set` on 2026-05-01).
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
    $cron$
    SELECT net.http_post(
        url     := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/medication-reminder-cron',
        headers := jsonb_build_object(
            'Content-Type',   'application/json',
            'Authorization',  'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MTMyMjc5NiwiZXhwIjoyMDg2ODk4Nzk2fQ.DkyaVhp-0-PsuTDQsycJIDVIHtFk7522zYMd_Ocgx9w',
            'X-Cron-Secret',  'a375791bc55596511dfb7229b3aafb3a6011443023ec01de'
        ),
        body    := '{}'::jsonb
    );
    $cron$
);
