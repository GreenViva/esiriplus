-- Fix infinite recursion in RLS policies on doctor_profiles.
-- The "Portal users can read all doctor profiles" policy queries user_roles,
-- which causes recursive policy evaluation during UPDATE operations.
-- Replace it with a simple USING(true) for authenticated users — this is safe
-- because the UPDATE policy still restricts writes to doctor_id = auth.uid().

-- Drop the problematic portal users policy
DROP POLICY IF EXISTS "Portal users can read all doctor profiles" ON public.doctor_profiles;

-- Drop the narrow per-doctor SELECT policy too (superseded by the broader one)
DROP POLICY IF EXISTS "Doctors can read their own profile" ON public.doctor_profiles;

-- Single SELECT policy: any authenticated user can read doctor profiles.
-- Patients need to browse/search doctors, doctors need their own profile,
-- admins need to see all profiles for verification.
DO $$ BEGIN
  CREATE POLICY "Authenticated users can read doctor profiles"
    ON public.doctor_profiles FOR SELECT
    TO authenticated
    USING (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
