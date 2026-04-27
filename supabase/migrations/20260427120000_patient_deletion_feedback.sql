-- Optional feedback collected when a patient deletes their account.
--
-- Anonymous on purpose: no FK to patient_sessions because we want to keep
-- the feedback after the 30-day purge wipes the user's session row. The
-- admin only needs to see WHY users left in aggregate; identity is not
-- useful here.
--
-- `reasons` is a free-form text array of canonical codes the client
-- picks from a localized list (e.g. "no_longer_needed", "privacy",
-- "too_slow", "bad_experience", "other"). `comment` is the optional
-- free-text the user typed.

CREATE TABLE IF NOT EXISTS patient_deletion_feedback (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reasons     TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    comment     TEXT,
    locale      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_patient_deletion_feedback_created_at
    ON patient_deletion_feedback(created_at DESC);

ALTER TABLE patient_deletion_feedback ENABLE ROW LEVEL SECURITY;

-- Service role (admin panel + edge functions) has full access. Patient
-- writes go through the submit-deletion-feedback edge function which
-- uses the service client, so no anon/auth INSERT policy is needed.
DROP POLICY IF EXISTS "service role full access" ON patient_deletion_feedback;
CREATE POLICY "service role full access" ON patient_deletion_feedback
    FOR ALL TO service_role USING (TRUE) WITH CHECK (TRUE);

COMMENT ON TABLE patient_deletion_feedback IS
    'Anonymous reason-and-comment record submitted when a patient deletes their account.';
