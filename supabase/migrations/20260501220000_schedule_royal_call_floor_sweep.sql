-- ============================================================================
-- Schedule fn_sweep_royal_call_floor() to run daily at 00:05 Africa/Dar_es_Salaam
-- (= 21:05 UTC). Runs after midnight Dar so "yesterday" is fully settled.
--
-- Uses pg_cron (Supabase has it enabled by default in the cron schema).
-- The function operates against `royal_call_floor_misses` with idempotent
-- inserts, so accidental double-runs are safe.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Drop any previous schedule with the same name so this migration is rerunnable.
DO $$
BEGIN
    PERFORM cron.unschedule(jobid)
      FROM cron.job
     WHERE jobname = 'royal_call_floor_sweep_daily';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

SELECT cron.schedule(
    'royal_call_floor_sweep_daily',
    '5 21 * * *',                           -- minute hour dow ... (UTC). 21:05 UTC = 00:05 Africa/Dar_es_Salaam.
    $$SELECT public.fn_sweep_royal_call_floor();$$
);
