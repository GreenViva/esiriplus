-- Add SELECT policies for portal users on ALL tables that the admin panel
-- queries via the browser Supabase client (static export uses user JWT, not service role).

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
    'diagnoses',
    'patient_reports',
    'patient_sessions',
    'recovery_attempts',
    'service_access_payments',
    'doctor_earnings',
    'payments',
    'user_roles',
    'call_recharge_payments',
    'doctor_online_log',
    'prescriptions',
    'vital_signs'
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

    -- Enable RLS (idempotent)
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', _tbl);

    -- Create SELECT policy for portal users (admin, hr, finance, audit)
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
