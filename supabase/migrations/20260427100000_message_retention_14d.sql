-- 14-day message retention.
--
-- Drops chat messages whose `created_at` is older than now() - 14 days. The
-- delete cascades to attachment rows via the existing FK on
-- attachments.message_id (verified in the v1.1 schema snapshot). Patient
-- consultation rows are NOT touched — only the message bodies — so the
-- consultation history list still renders, the per-session detail screen
-- just shows the empty-state when its messages have aged out.
--
-- This file ships only the SQL function. The Supabase scheduled edge
-- function `cleanup-expired-messages` (see supabase/functions/) calls
-- this RPC daily; cron schedule is configured at deploy time in the
-- Supabase dashboard alongside `expire-followup-escrow`.

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
    '14-day TTL for chat messages. Called daily by the cleanup-expired-messages edge function.';
