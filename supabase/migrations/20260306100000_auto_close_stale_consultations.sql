-- Migration: Auto-close stale consultations that are stuck in active-like statuses.
-- Consultations that have been 'active' for more than 2 hours, or
-- 'awaiting_extension' / 'grace_period' for more than 30 minutes,
-- are considered abandoned and should be auto-completed.

-- 1. Fix any currently stuck consultations immediately
UPDATE consultations
SET status = 'completed'::consultation_status_enum,
    updated_at = now()
WHERE status IN ('active'::consultation_status_enum,
                 'awaiting_extension'::consultation_status_enum,
                 'grace_period'::consultation_status_enum)
  AND (
    -- Active for more than 2 hours
    (status = 'active'::consultation_status_enum
     AND updated_at < now() - interval '2 hours')
    OR
    -- Awaiting extension or grace period for more than 30 minutes
    (status IN ('awaiting_extension'::consultation_status_enum,
                'grace_period'::consultation_status_enum)
     AND updated_at < now() - interval '30 minutes')
  );

-- 2. Create function to auto-close stale consultations
CREATE OR REPLACE FUNCTION fn_close_stale_consultations()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    closed_count integer;
BEGIN
    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        updated_at = now()
    WHERE (
        -- Active consultations older than 2 hours
        (status = 'active'::consultation_status_enum
         AND updated_at < now() - interval '2 hours')
        OR
        -- Awaiting extension for more than 30 minutes (both parties left)
        (status = 'awaiting_extension'::consultation_status_enum
         AND updated_at < now() - interval '30 minutes')
        OR
        -- Grace period for more than 30 minutes (payment never completed)
        (status = 'grace_period'::consultation_status_enum
         AND updated_at < now() - interval '30 minutes')
    );

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    IF closed_count > 0 THEN
        RAISE LOG 'fn_close_stale_consultations: auto-closed % stale consultations', closed_count;
    END IF;
END;
$$;

-- 3. Schedule cron job: run every 5 minutes
DO $$
BEGIN
    PERFORM cron.unschedule('close-stale-consultations');
EXCEPTION WHEN OTHERS THEN
    NULL;
END;
$$;

SELECT cron.schedule(
    'close-stale-consultations',
    '*/5 * * * *',
    $$SELECT fn_close_stale_consultations()$$
);
