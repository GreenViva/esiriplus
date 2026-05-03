-- ============================================================================
-- Missed-consultations bucket for the patient dashboard.
--
-- Scope (per product call 2026-05-02):
--   1. consultation_requests that expired without ever becoming a consultation
--   2. consultations the doctor accepted but never engaged with (no messages,
--      no video calls)
--   3. (deferred) call ended too short — handled later
--   4. service_access_payments paid but no consultation_request followed
--
-- Storage strategy: a single SQL function unions the three live sources at
-- read time. Each source table gets a `reconnect_consumed_at` marker so once
-- the patient retries from a missed entry, the source row is flagged consumed
-- and disappears from the bucket.
--
-- The retry creates a NEW consultation request linked to the missed entry via
-- consultations.reconnect_source_kind + reconnect_source_id. Only successful
-- follow-ups count against the Economy 1-follow-up cap (handled in the
-- handle-consultation-request edge function — this migration is data-only).
-- ============================================================================

-- ── Source-table markers ─────────────────────────────────────────────────────
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS reconnect_consumed_at TIMESTAMPTZ;

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS reconnect_consumed_at TIMESTAMPTZ;

ALTER TABLE service_access_payments
    ADD COLUMN IF NOT EXISTS reconnect_consumed_at TIMESTAMPTZ;

-- ── Reconnect-source linking on the new consultation ───────────────────────
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS reconnect_source_kind TEXT
        CHECK (reconnect_source_kind IS NULL
            OR reconnect_source_kind IN ('request_expired', 'no_engagement', 'paid_no_request'));

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS reconnect_source_id UUID;

-- Helpful index for the dashboard query — patients hit this per-load.
CREATE INDEX IF NOT EXISTS idx_consultation_requests_missed_lookup
    ON consultation_requests(patient_session_id, status)
    WHERE reconnect_consumed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_consultations_missed_lookup
    ON consultations(patient_session_id, status)
    WHERE reconnect_consumed_at IS NULL;

-- ── List active missed entries for a patient ───────────────────────────────
-- Schema drift note: across these tables, some id columns are UUID and some
-- are TEXT. We cast everything to TEXT in the function signature so the
-- migration applies cleanly regardless of historic column types.
CREATE OR REPLACE FUNCTION list_missed_for_patient(p_session_id TEXT)
RETURNS TABLE (
    source_kind     TEXT,
    source_id       TEXT,
    service_type    TEXT,
    service_tier    TEXT,
    consultation_fee INTEGER,
    doctor_id       TEXT,
    created_at      TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    -- (1) consultation_requests that timed out without an accepted doctor
    SELECT
        'request_expired'::TEXT                       AS source_kind,
        cr.request_id::text                           AS source_id,
        cr.service_type::text,
        COALESCE(cr.service_tier, 'ECONOMY')::text    AS service_tier,
        0::INTEGER                                    AS consultation_fee,
        cr.doctor_id::text                            AS doctor_id,
        cr.created_at
    FROM consultation_requests cr
    WHERE cr.patient_session_id::text = p_session_id
      AND cr.status = 'expired'
      AND cr.consultation_id IS NULL
      AND cr.reconnect_consumed_at IS NULL
      AND cr.created_at > NOW() - INTERVAL '14 days'

    UNION ALL

    -- (2) consultations the doctor accepted but never engaged with
    --     (status terminal, zero messages, no video call rows)
    SELECT
        'no_engagement'::TEXT                         AS source_kind,
        c.consultation_id::text                       AS source_id,
        c.service_type::text,
        COALESCE(c.service_tier, 'ECONOMY')::text     AS service_tier,
        COALESCE(c.consultation_fee, 0)::INTEGER      AS consultation_fee,
        c.doctor_id::text                             AS doctor_id,
        c.created_at
    FROM consultations c
    WHERE c.patient_session_id::text = p_session_id
      -- "completed" is the terminal state for both successful and abandoned
      -- consultations on this project (no separate cancelled/expired enum
      -- value). The other filters below (no messages, no video_calls)
      -- distinguish the abandoned case.
      AND c.status::text = 'completed'
      AND c.reconnect_consumed_at IS NULL
      AND c.created_at > NOW() - INTERVAL '14 days'
      AND NOT EXISTS (
          SELECT 1 FROM messages m
           WHERE m.consultation_id::text = c.consultation_id::text
      )
      AND NOT EXISTS (
          SELECT 1 FROM video_calls vc
           WHERE vc.consultation_id::text = c.consultation_id::text
      )

    UNION ALL

    -- (4) paid service_access_payment with no consultation_request created
    --     within a 5-minute grace window (so an in-progress flow doesn't show
    --     up as missed). status semantics depend on the payment integration —
    --     here we treat anything not in pending/failed/refunded as paid.
    SELECT
        'paid_no_request'::TEXT                       AS source_kind,
        sap.payment_id::text                          AS source_id,
        sap.service_type::text,
        'ECONOMY'::TEXT                               AS service_tier,
        COALESCE(sap.amount, 0)::INTEGER              AS consultation_fee,
        NULL::text                                    AS doctor_id,
        sap.created_at
    FROM service_access_payments sap
    WHERE sap.patient_session_id::text = p_session_id
      -- Cast to text so we don't have to know the project's payment_status_enum
      -- members at migration time. The set of "not paid" states is small and
      -- well-known; treat anything outside it as paid.
      AND sap.status::text NOT IN ('pending', 'failed')
      AND sap.reconnect_consumed_at IS NULL
      AND sap.created_at > NOW() - INTERVAL '14 days'
      AND sap.created_at < NOW() - INTERVAL '5 minutes'
      AND NOT EXISTS (
          SELECT 1 FROM consultation_requests cr
           WHERE cr.patient_session_id::text = sap.patient_session_id::text
             AND cr.service_type::text = sap.service_type::text
             AND cr.created_at >= sap.created_at
      )

    ORDER BY created_at DESC;
$$;

COMMENT ON FUNCTION list_missed_for_patient(TEXT) IS
    'Returns the active missed-consultation bucket for a patient. Three sources unioned: expired requests, no-engagement consultations, paid-but-no-request access. Each row is suppressed once its source table''s reconnect_consumed_at is set.';

GRANT EXECUTE ON FUNCTION list_missed_for_patient(TEXT) TO authenticated, service_role;
