-- Add SELECT policies for portal users on tables that the admin panel
-- now queries via the browser Supabase client (after static export migration).
-- Previously these were accessed via service role key; now they need RLS policies.

DO $$
DECLARE
  _tbl text;
  _tables text[] := ARRAY[
    'patient_sessions',
    'doctor_earnings',
    'call_recharge_payments',
    'doctor_device_bindings',
    'doctor_online_log',
    'prescriptions',
    'vital_signs',
    'user_roles'
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

-- user_roles also needs a self-read policy so users can check their own role
-- during login (before we know they have a portal role)
DO $$ BEGIN
  CREATE POLICY "Users can read own roles"
    ON public.user_roles FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
