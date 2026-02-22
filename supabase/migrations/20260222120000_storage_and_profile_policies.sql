-- Storage and profile RLS policies for doctor file uploads

-- doctor_profiles: allow doctors to update and read their own profile
DO $$ BEGIN
  CREATE POLICY "Doctors can update their own profile"
    ON public.doctor_profiles FOR UPDATE
    TO authenticated
    USING (doctor_id = auth.uid())
    WITH CHECK (doctor_id = auth.uid());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Doctors can read their own profile"
    ON public.doctor_profiles FOR SELECT
    TO authenticated
    USING (doctor_id = auth.uid());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
