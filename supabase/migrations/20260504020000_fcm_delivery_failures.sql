-- ============================================================================
-- FCM delivery failure tracking
--
-- send-push-notification logs every push that comes back with failed > 0
-- so we can detect chronically-unreachable devices (stale tokens, OEM
-- battery optimization killing the FCM socket, app uninstalled, etc.).
-- After 3 failures inside 24h the client surfaces an in-app banner asking
-- the user to re-grant permissions or reopen the app.
-- ============================================================================

CREATE TABLE IF NOT EXISTS fcm_delivery_failures (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    fcm_token TEXT NOT NULL,
    notification_type TEXT,
    error TEXT,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fcm_failures_user_recent
    ON fcm_delivery_failures (user_id, failed_at DESC);

-- Convenience RPC: returns true if the user has 3+ failures in the past
-- 24 hours. Client polls this on home-screen mount to decide whether to
-- show the "we couldn't reach your phone" banner.
CREATE OR REPLACE FUNCTION fcm_delivery_health(p_user_id TEXT)
RETURNS TABLE (
    failure_count INTEGER,
    last_error TEXT,
    last_failed_at TIMESTAMPTZ,
    is_stale BOOLEAN
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*)::INTEGER          AS failure_count,
        (SELECT error FROM fcm_delivery_failures
          WHERE user_id = p_user_id
          ORDER BY failed_at DESC LIMIT 1) AS last_error,
        (SELECT failed_at FROM fcm_delivery_failures
          WHERE user_id = p_user_id
          ORDER BY failed_at DESC LIMIT 1) AS last_failed_at,
        COUNT(*) >= 3              AS is_stale
      FROM fcm_delivery_failures
     WHERE user_id = p_user_id
       AND failed_at > NOW() - INTERVAL '24 hours';
END;
$$;

GRANT EXECUTE ON FUNCTION fcm_delivery_health(TEXT) TO authenticated, anon;

-- Auto-trim rows older than 30 days. Failures from last week aren't
-- actionable; keep the table small.
CREATE OR REPLACE FUNCTION fn_trim_fcm_failures()
RETURNS TRIGGER AS $$
BEGIN
    IF (random() < 0.01) THEN  -- 1% chance per insert; keeps cost amortized
        DELETE FROM fcm_delivery_failures WHERE failed_at < NOW() - INTERVAL '30 days';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_trim_fcm_failures ON fcm_delivery_failures;
CREATE TRIGGER trg_trim_fcm_failures
    AFTER INSERT ON fcm_delivery_failures
    FOR EACH ROW EXECUTE FUNCTION fn_trim_fcm_failures();
