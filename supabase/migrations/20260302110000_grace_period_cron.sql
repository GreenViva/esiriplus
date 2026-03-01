-- Migration: Cron job to auto-expire grace periods
-- Runs every minute. Reverts consultations whose grace_period_end_at has passed
-- back to awaiting_extension so the doctor can end the session.

-- Enable pg_cron if not already enabled
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Create the function that the cron job calls
CREATE OR REPLACE FUNCTION fn_expire_grace_periods()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    expired_count integer;
BEGIN
    UPDATE consultations
    SET status = 'awaiting_extension'::consultation_status_enum,
        grace_period_end_at = NULL,
        updated_at = now()
    WHERE status = 'grace_period'::consultation_status_enum
      AND grace_period_end_at IS NOT NULL
      AND grace_period_end_at <= now();

    GET DIAGNOSTICS expired_count = ROW_COUNT;

    IF expired_count > 0 THEN
        RAISE LOG 'fn_expire_grace_periods: expired % grace periods', expired_count;
    END IF;
END;
$$;

-- Schedule: run every minute (idempotent — remove existing job first if present)
DO $$
BEGIN
    PERFORM cron.unschedule('expire-grace-periods');
EXCEPTION WHEN OTHERS THEN
    -- Job doesn't exist yet — that's fine
    NULL;
END;
$$;

SELECT cron.schedule(
    'expire-grace-periods',
    '* * * * *',
    $$SELECT fn_expire_grace_periods()$$
);
