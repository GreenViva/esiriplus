-- ============================================================================
-- Missed-consultation consumption rule, take 2.
--
-- Rationale (product call 2026-05-03):
--   The original implementation marked the missed source consumed at REQUEST
--   CREATION time, which meant a retry that nobody accepted would consume the
--   original entry and immediately churn into a fresh missed entry — same
--   problem, different id. The patient experience read as "the missed list
--   keeps changing under me" instead of "this one stays until a doctor
--   actually serves me".
--
--   Corrected behaviour:
--     • A retry stamps reconnect_source_kind/id onto the new
--       consultation_requests row but DOES NOT consume the source.
--     • The source is consumed only when the resulting request is accepted
--       by a doctor (handle-consultation-request `accept` action).
--     • The list_missed_for_patient RPC now excludes consultation_requests
--       that are themselves retries (reconnect_source_id IS NOT NULL) so an
--       expired retry doesn't re-add a new missed row on top of the
--       original one that's still in the bucket.
-- ============================================================================

ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS reconnect_source_kind TEXT
        CHECK (reconnect_source_kind IS NULL
            OR reconnect_source_kind IN ('request_expired', 'no_engagement', 'paid_no_request'));

ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS reconnect_source_id TEXT;

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
    -- (1) consultation_requests that timed out without an accepted doctor.
    --     Exclude requests that are themselves retries — those just point at
    --     an already-listed source row, surfacing them again would duplicate
    --     the bucket entry.
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
      AND cr.reconnect_source_id IS NULL
      AND cr.created_at > NOW() - INTERVAL '14 days'

    UNION ALL

    -- (2) consultations the doctor accepted but never engaged with
    --     (status terminal, zero messages, no video call rows).
    --     Retries can land here too — but only because the new consultation
    --     itself was abandoned, which is a fresh failure worth surfacing.
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
    --     within a 5-minute grace window. status semantics depend on the
    --     payment integration — treat anything not pending/failed as paid.
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
