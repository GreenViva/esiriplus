-- Fix infinite recursion: RLS policies on user_roles (and other tables) reference
-- user_roles to check portal role, causing circular dependency.
-- Solution: SECURITY DEFINER function that bypasses RLS for the role check.

CREATE OR REPLACE FUNCTION public.is_portal_user()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = ''
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles
    WHERE user_id = auth.uid()
    AND role_name IN ('admin', 'hr', 'finance', 'audit')
  );
$$;

-- Drop the recursive policy on user_roles
DROP POLICY IF EXISTS "Portal users can read user_roles" ON public.user_roles;

-- Recreate it using the SECURITY DEFINER function (no recursion)
CREATE POLICY "Portal users can read user_roles"
  ON public.user_roles FOR SELECT
  TO authenticated
  USING (public.is_portal_user());

-- Now update all other tables to use the function too (avoids nested RLS on user_roles)
DO $$
DECLARE
  _tbl text;
  _tables text[] := ARRAY[
    'admin_logs',
    'risk_flags',
    'doctor_ratings',
    'doctor_profiles',
    'doctor_device_bindings',
    'consultations',
    'patient_reports',
    'patient_sessions',
    'recovery_attempts',
    'service_access_payments',
    'doctor_earnings',
    'payments',
    'call_recharge_payments',
    'doctor_online_log'
  ];
BEGIN
  FOREACH _tbl IN ARRAY _tables LOOP
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = _tbl
    ) THEN
      CONTINUE;
    END IF;

    -- Drop old policy that used subquery on user_roles
    EXECUTE format(
      'DROP POLICY IF EXISTS "Portal users can read %1$s" ON public.%1$I',
      _tbl
    );

    -- Recreate using the SECURITY DEFINER function
    EXECUTE format(
      'CREATE POLICY "Portal users can read %1$s"
         ON public.%1$I FOR SELECT
         TO authenticated
         USING (public.is_portal_user())',
      _tbl
    );
  END LOOP;
END $$;
