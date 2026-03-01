-- Prevent a suspended doctor from setting is_available = true.
-- This is a server-side safety net; the mobile app also blocks
-- the toggle, but this trigger ensures direct DB/API updates
-- cannot bypass the suspension.

CREATE OR REPLACE FUNCTION prevent_suspended_availability()
RETURNS trigger AS $$
BEGIN
  IF NEW.is_available = true
     AND NEW.suspended_until IS NOT NULL
     AND NEW.suspended_until > now()
  THEN
    RAISE EXCEPTION 'Cannot go online while account is suspended until %', NEW.suspended_until;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_suspended_availability
  BEFORE UPDATE ON public.doctor_profiles
  FOR EACH ROW
  EXECUTE FUNCTION prevent_suspended_availability();
