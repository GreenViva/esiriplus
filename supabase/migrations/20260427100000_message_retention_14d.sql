-- 14-day message retention.
--
-- Drops chat messages whose `created_at` is older than now() - 14 days. The
-- delete cascades to attachment rows via the existing FK on
-- attachments.message_id (verified in the v1.1 schema snapshot). Patient
-- consultation rows are NOT touched — only the message bodies — so the
-- consultation history list still renders, the per-session detail screen
-- just shows the empty-state when its messages have aged out.
--
-- Cron schedule below mirrors the lift-expired-suspensions pattern (pg_cron
-- + pg_net). The actual delete logic lives in fn_cleanup_expired_messages
-- so the policy is expressed once, in one place.

CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

CREATE OR REPLACE FUNCTION fn_cleanup_expired_messages()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_deleted integer;
BEGIN
    DELETE FROM messages
    WHERE created_at < (now() - INTERVAL '14 days');

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

REVOKE ALL ON FUNCTION fn_cleanup_expired_messages() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_cleanup_expired_messages() TO service_role;

COMMENT ON FUNCTION fn_cleanup_expired_messages() IS
    '14-day TTL for chat messages. Called daily by the cleanup-expired-messages edge function via pg_cron.';

-- Daily at 03:15 UTC (a quiet hour for Tanzania, ~06:15 EAT). Run the cron
-- via the cleanup-expired-messages edge function so the policy is testable
-- from the function logs and authed against CRON_SECRET like the other
-- scheduled jobs.
SELECT cron.schedule(
  'cleanup-expired-messages',
  '15 3 * * *',
  $$
    SELECT net.http_post(
      url := 'https://nzzvphhqbcscoetzfzkd.supabase.co/functions/v1/cleanup-expired-messages',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Cron-Secret', current_setting('app.settings.cron_secret', true)
      ),
      body := '{}'::jsonb
    );
  $$
);
