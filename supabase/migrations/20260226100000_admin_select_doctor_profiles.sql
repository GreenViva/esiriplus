-- Allow portal users (admin, HR, finance, audit) to read all doctor profiles.
-- This enables Supabase Realtime postgres_changes events to flow to the
-- admin panel, so new doctor registrations appear automatically.

DO $$ BEGIN
  CREATE POLICY "Portal users can read all doctor profiles"
    ON public.doctor_profiles FOR SELECT
    TO authenticated
    USING (
      EXISTS (
        SELECT 1 FROM public.user_roles
        WHERE user_roles.user_id = auth.uid()
      )
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
