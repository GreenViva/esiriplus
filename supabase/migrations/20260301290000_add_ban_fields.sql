-- Add application-layer ban fields to doctor_profiles.
-- Ban is enforced at the app level (dashboard shows ban screen)
-- rather than at the Supabase Auth layer, so the doctor can
-- still log in and see the ban notice with reason and appeal info.

ALTER TABLE public.doctor_profiles
  ADD COLUMN IF NOT EXISTS is_banned    boolean      NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS banned_at    timestamptz,
  ADD COLUMN IF NOT EXISTS ban_reason   text;

-- Update the availability trigger to also block banned doctors.
CREATE OR REPLACE FUNCTION prevent_suspended_availability()
RETURNS trigger AS $$
BEGIN
  IF NEW.is_available = true THEN
    IF NEW.is_banned = true THEN
      RAISE EXCEPTION 'Cannot go online while account is banned';
    END IF;
    IF NEW.suspended_until IS NOT NULL AND NEW.suspended_until > now() THEN
      RAISE EXCEPTION 'Cannot go online while account is suspended until %', NEW.suspended_until;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
