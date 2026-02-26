-- Fix Realtime subscriptions for the admin panel.
-- The browser client (authenticated portal user) needs SELECT access
-- on every table it subscribes to, otherwise Supabase Realtime silently
-- drops the events.  Also set REPLICA IDENTITY FULL so UPDATE / DELETE
-- events include the full row, and add missing tables to the publication.

-----------------------------------------------------------------------
-- 1. REPLICA IDENTITY FULL on all tables the admin panel subscribes to
-----------------------------------------------------------------------
ALTER TABLE IF EXISTS public.doctor_profiles         REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.admin_logs              REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.consultations           REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.payments                REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.service_access_payments REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.diagnoses               REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.patient_reports         REPLICA IDENTITY FULL;
ALTER TABLE IF EXISTS public.doctor_ratings          REPLICA IDENTITY FULL;

-----------------------------------------------------------------------
-- 2. SELECT policies + RLS for portal users (skips non-existent tables)
-----------------------------------------------------------------------

-- Helper: create policy + enable RLS only if the table exists.
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

    -- Enable RLS
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', _tbl);

    -- Create SELECT policy for portal users (skip if already exists)
    BEGIN
      EXECUTE format(
        'CREATE POLICY "Portal users can read %1$s"
           ON public.%1$I FOR SELECT
           TO authenticated
           USING (
             EXISTS (
               SELECT 1 FROM public.user_roles
               WHERE user_roles.user_id = auth.uid()
             )
           )',
        _tbl
      );
    EXCEPTION WHEN duplicate_object THEN
      RAISE NOTICE 'Policy on % already exists, skipping', _tbl;
    END;
  END LOOP;
END $$;

-----------------------------------------------------------------------
-- 3. Add service_access_payments to the realtime publication
-----------------------------------------------------------------------
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE service_access_payments;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
