-- ============================================================================
-- FOLLOW-UP REOPEN MODEL
--
-- Replaces the child-consultation model with a reopen model:
-- - Follow-ups reuse the SAME consultation (no new child rows)
-- - The consultation tracks follow_up_count and follow_up_max
-- - Economy: 1 reopen allowed, Royal: unlimited (-1)
-- - reopen_consultation() RPC handles the state transition
-- ============================================================================

-- 1. New columns
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS follow_up_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS follow_up_max INTEGER NOT NULL DEFAULT 1;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS is_reopened BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS last_reopened_at TIMESTAMPTZ;

-- Backfill based on tier
UPDATE consultations SET follow_up_max = -1 WHERE UPPER(COALESCE(service_tier, 'ECONOMY')) = 'ROYAL';
UPDATE consultations SET follow_up_max = 1 WHERE UPPER(COALESCE(service_tier, 'ECONOMY')) = 'ECONOMY';

CREATE INDEX IF NOT EXISTS idx_consultations_followup ON consultations (status, follow_up_expiry, follow_up_count, follow_up_max);

-- 2. Drop old follow-up enforcement triggers
DROP FUNCTION IF EXISTS enforce_economy_followup_limit() CASCADE;
DROP FUNCTION IF EXISTS prevent_followup_expiry_on_children() CASCADE;

-- 3. New validation trigger
CREATE OR REPLACE FUNCTION validate_followup_reopen()
RETURNS trigger AS $$
DECLARE v_consultation RECORD;
BEGIN
    IF NOT NEW.is_follow_up OR NEW.parent_consultation_id IS NULL THEN RETURN NEW; END IF;
    SELECT follow_up_count, follow_up_max, follow_up_expiry, status INTO v_consultation
      FROM consultations WHERE consultation_id = NEW.parent_consultation_id;
    IF v_consultation IS NULL THEN RAISE EXCEPTION 'Consultation not found' USING ERRCODE = 'P0001'; END IF;
    IF v_consultation.status <> 'completed' THEN RAISE EXCEPTION 'Consultation is not completed' USING ERRCODE = 'P0002'; END IF;
    IF v_consultation.follow_up_expiry IS NOT NULL AND v_consultation.follow_up_expiry < NOW() THEN
        RAISE EXCEPTION 'Follow-up window has expired' USING ERRCODE = 'P0003'; END IF;
    IF v_consultation.follow_up_max > 0 AND v_consultation.follow_up_count >= v_consultation.follow_up_max THEN
        RAISE EXCEPTION 'Follow-up limit reached' USING ERRCODE = 'P0004'; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER validate_followup_reopen_trigger
    BEFORE INSERT ON consultation_requests FOR EACH ROW EXECUTE FUNCTION validate_followup_reopen();

-- 4. reopen_consultation RPC (see full version in DB — applied directly)
-- 5. fn_auto_create_doctor_earning rewritten (see full version in DB — applied directly)
