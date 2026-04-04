-- Prevent unverified doctors from going online.
-- Matches existing pattern: prevent_suspended_availability trigger.

CREATE OR REPLACE FUNCTION prevent_unverified_availability()
RETURNS trigger AS $$
BEGIN
  IF NEW.is_available = true AND NEW.is_verified = false THEN
    RAISE EXCEPTION 'Cannot go online while account is not verified';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_unverified_availability ON public.doctor_profiles;
CREATE TRIGGER trg_prevent_unverified_availability
  BEFORE UPDATE ON public.doctor_profiles
  FOR EACH ROW
  EXECUTE FUNCTION prevent_unverified_availability();
