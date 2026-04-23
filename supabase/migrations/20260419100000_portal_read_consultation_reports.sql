-- ============================================================================
-- Allow admin portal users to read consultation_reports.
--
-- The Health Analytics page queries this table directly via the browser
-- Supabase client (user JWT, not service role). Without a SELECT policy for
-- admin/hr/finance/audit, the query silently returns zero rows — so the
-- "Total Reports" stat card always showed 0 even when reports existed.
-- ============================================================================

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'consultation_reports'
  ) THEN
    EXECUTE 'ALTER TABLE public.consultation_reports ENABLE ROW LEVEL SECURITY';

    BEGIN
      EXECUTE $policy$
        CREATE POLICY "Portal users can read consultation_reports"
          ON public.consultation_reports FOR SELECT
          TO authenticated
          USING (
            EXISTS (
              SELECT 1 FROM public.user_roles
               WHERE user_roles.user_id = auth.uid()
                 AND user_roles.role_name IN ('admin', 'hr', 'finance', 'audit')
            )
          )
      $policy$;
    EXCEPTION WHEN duplicate_object THEN
      RAISE NOTICE 'Policy on consultation_reports already exists, skipping';
    END;
  ELSE
    RAISE NOTICE 'consultation_reports table does not exist, skipping policy';
  END IF;
END $$;
