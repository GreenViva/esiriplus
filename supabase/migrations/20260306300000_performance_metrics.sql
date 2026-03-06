-- Performance metrics collected from Android app and edge functions
CREATE TABLE IF NOT EXISTS performance_metrics (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    metric_type TEXT NOT NULL CHECK (metric_type IN ('api_response', 'edge_function', 'db_query', 'app_event')),
    endpoint    TEXT NOT NULL,
    method      TEXT,
    status_code INTEGER,
    latency_ms  INTEGER NOT NULL,
    success     BOOLEAN DEFAULT true,
    error_type  TEXT,
    app_version TEXT,
    platform    TEXT DEFAULT 'android',
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Indexes for time-series and aggregation queries
CREATE INDEX idx_perf_metrics_created_at ON performance_metrics (created_at DESC);
CREATE INDEX idx_perf_metrics_type       ON performance_metrics (metric_type, endpoint, created_at DESC);

-- RLS: service role bypasses; no direct client access
ALTER TABLE performance_metrics ENABLE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------
-- Aggregation function called by the admin panel via supabase.rpc()
-- Returns hourly buckets with avg/p95 latency, request count, error count.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_performance_stats(p_hours_ago INTEGER DEFAULT 24)
RETURNS TABLE (
    bucket         TIMESTAMPTZ,
    metric_type    TEXT,
    endpoint       TEXT,
    avg_latency_ms NUMERIC,
    p95_latency_ms NUMERIC,
    request_count  BIGINT,
    error_count    BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        date_trunc('hour', pm.created_at)                                           AS bucket,
        pm.metric_type,
        pm.endpoint,
        round(avg(pm.latency_ms)::numeric, 1)                                      AS avg_latency_ms,
        round(percentile_cont(0.95) WITHIN GROUP (ORDER BY pm.latency_ms)::numeric, 1) AS p95_latency_ms,
        count(*)::bigint                                                            AS request_count,
        count(*) FILTER (WHERE NOT pm.success)::bigint                              AS error_count
    FROM performance_metrics pm
    WHERE pm.created_at >= now() - make_interval(hours => p_hours_ago)
    GROUP BY 1, 2, 3
    ORDER BY bucket DESC, request_count DESC;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- Auto-cleanup: delete metrics older than 30 days (daily at 03:00 UTC)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_cleanup_old_metrics()
RETURNS void AS $$
BEGIN
    DELETE FROM performance_metrics WHERE created_at < now() - INTERVAL '30 days';
END;
$$ LANGUAGE plpgsql;

SELECT cron.schedule(
    'cleanup-old-performance-metrics',
    '0 3 * * *',
    'SELECT fn_cleanup_old_metrics()'
);
