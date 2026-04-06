-- ============================================================================
-- Enforce follow-up limits at the DATABASE level (cannot be bypassed by code).
--
-- Rules:
--   Economy: 1 follow-up per original consultation, no chaining
--   Royal:   Unlimited follow-ups within the 14-day window
--
-- Two triggers:
--   1. Block Economy follow-up chaining and enforce 1-follow-up limit on INSERT
--   2. Prevent follow-up consultations from getting their own follow_up_expiry
-- ============================================================================

-- ── Trigger 1: Enforce Economy follow-up limit on consultation creation ──────

CREATE OR REPLACE FUNCTION enforce_economy_followup_limit()
RETURNS TRIGGER AS $$
DECLARE
  v_parent_tier   TEXT;
  v_root_id       UUID;
  v_current_id    UUID;
  v_depth         INT := 0;
  v_existing      INT;
BEGIN
  -- Only applies to follow-up consultations (those with a parent)
  IF NEW.parent_consultation_id IS NULL THEN
    RETURN NEW;
  END IF;

  -- Get the parent consultation's tier
  SELECT service_tier INTO v_parent_tier
    FROM consultations
    WHERE consultation_id = NEW.parent_consultation_id;

  -- Royal tier: no limits, allow
  IF UPPER(COALESCE(v_parent_tier, 'ECONOMY')) = 'ROYAL' THEN
    RETURN NEW;
  END IF;

  -- Economy tier: walk up the chain to find the root consultation
  v_current_id := NEW.parent_consultation_id;
  LOOP
    SELECT parent_consultation_id INTO v_root_id
      FROM consultations
      WHERE consultation_id = v_current_id;

    EXIT WHEN v_root_id IS NULL OR v_depth >= 10;
    v_current_id := v_root_id;
    v_depth := v_depth + 1;
  END LOOP;

  -- Block chaining: Economy follow-ups of follow-ups are not allowed
  IF v_depth > 0 THEN
    RAISE EXCEPTION 'Economy consultations do not allow follow-ups of follow-ups'
      USING ERRCODE = 'check_violation';
  END IF;

  -- Count existing follow-ups of the root consultation
  SELECT COUNT(*) INTO v_existing
    FROM consultations
    WHERE parent_consultation_id = v_current_id;

  IF v_existing >= 1 THEN
    RAISE EXCEPTION 'Economy consultations are limited to 1 follow-up'
      USING ERRCODE = 'check_violation';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_economy_followup_limit ON consultations;
CREATE TRIGGER trg_enforce_economy_followup_limit
  BEFORE INSERT ON consultations
  FOR EACH ROW
  EXECUTE FUNCTION enforce_economy_followup_limit();


-- ── Trigger 2: Prevent follow-up consultations from getting follow_up_expiry ─

CREATE OR REPLACE FUNCTION prevent_followup_expiry_on_children()
RETURNS TRIGGER AS $$
BEGIN
  -- If this consultation has a parent (is a follow-up), never allow follow_up_expiry
  IF NEW.parent_consultation_id IS NOT NULL AND NEW.follow_up_expiry IS NOT NULL THEN
    NEW.follow_up_expiry := NULL;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_followup_expiry_on_children ON consultations;
CREATE TRIGGER trg_prevent_followup_expiry_on_children
  BEFORE INSERT OR UPDATE ON consultations
  FOR EACH ROW
  EXECUTE FUNCTION prevent_followup_expiry_on_children();
