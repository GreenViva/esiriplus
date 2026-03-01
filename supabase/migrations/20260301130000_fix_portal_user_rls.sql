-- 3.4: Fix portal user SELECT policies — add role_name filter
-- Previously: checked EXISTS(SELECT 1 FROM user_roles WHERE user_id = auth.uid())
-- which allowed ANY user with ANY role to read admin tables.
-- Now: requires role_name IN ('admin', 'hr', 'finance', 'audit').

DO $$
DECLARE
  _tbl text;
  _tables text[] := ARRAY[
    'admin_logs',
    'consultations',
    'payments',
    'service_access_payments',
    'diagnoses',
    'patient_reports',
    'doctor_ratings'
  ];
BEGIN
  FOREACH _tbl IN ARRAY _tables LOOP
    -- Skip if table doesn't exist
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = _tbl
    ) THEN
      RAISE NOTICE 'Skipping %: table does not exist', _tbl;
      CONTINUE;
    END IF;

    -- Drop the old permissive policy
    BEGIN
      EXECUTE format(
        'DROP POLICY IF EXISTS "Portal users can read %1$s" ON public.%1$I',
        _tbl
      );
    EXCEPTION WHEN undefined_object THEN
      NULL;
    END;

    -- Create new policy with role_name filter
    BEGIN
      EXECUTE format(
        'CREATE POLICY "Portal users can read %1$s"
           ON public.%1$I FOR SELECT
           TO authenticated
           USING (
             EXISTS (
               SELECT 1 FROM public.user_roles
               WHERE user_roles.user_id = auth.uid()
               AND user_roles.role_name IN (''admin'', ''hr'', ''finance'', ''audit'')
             )
           )',
        _tbl
      );
    EXCEPTION WHEN duplicate_object THEN
      RAISE NOTICE 'Policy on % already exists, skipping', _tbl;
    END;
  END LOOP;
END $$;
