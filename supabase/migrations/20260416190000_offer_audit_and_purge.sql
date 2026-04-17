-- ============================================================================
-- Audit columns + automatic recycle-bin purge for location offers.
--
-- Adds who-did-what columns so the admin panel can show "Deleted by X on
-- 2026-04-20" in the recycle bin. Schedules a daily pg_cron job that hard-
-- deletes bin entries older than 90 days so the row count doesn't grow
-- unbounded. Bulk "empty bin" is an admin-initiated DELETE — nothing to
-- schedule for that.
-- ============================================================================

-- 1. Audit columns ----------------------------------------------------------
ALTER TABLE location_offers
    ADD COLUMN IF NOT EXISTS deleted_by     UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS terminated_by  UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS restored_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS restored_by    UUID REFERENCES auth.users(id) ON DELETE SET NULL;

-- 2. Daily purge job --------------------------------------------------------
-- pg_cron is already enabled (see medication-reminder-cron). Re-register
-- idempotently.
SELECT cron.unschedule('offer-bin-purge-cron')
WHERE EXISTS (
    SELECT 1 FROM cron.job WHERE jobname = 'offer-bin-purge-cron'
);

SELECT cron.schedule(
    'offer-bin-purge-cron',
    '0 3 * * *',     -- 03:00 UTC every day (off-peak for TZ users)
    $$
    DELETE FROM location_offers
     WHERE deleted_at IS NOT NULL
       AND deleted_at < now() - interval '90 days';
    $$
);
