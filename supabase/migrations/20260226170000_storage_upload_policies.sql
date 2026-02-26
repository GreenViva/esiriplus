-- Storage policies for doctor file uploads.
-- Doctors need to upload to profile-photos and credentials buckets
-- during registration and profile updates.

-- profile-photos: authenticated users can upload/read their own files
DO $$ BEGIN
  CREATE POLICY "Authenticated users can upload profile photos"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (bucket_id = 'profile-photos' AND (storage.foldername(name))[1] = auth.uid()::text);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Authenticated users can update their profile photos"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (bucket_id = 'profile-photos' AND (storage.foldername(name))[1] = auth.uid()::text);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Anyone can read profile photos"
    ON storage.objects FOR SELECT
    TO public
    USING (bucket_id = 'profile-photos');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- credentials: authenticated users can upload/read their own files
DO $$ BEGIN
  CREATE POLICY "Authenticated users can upload credentials"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (bucket_id = 'credentials' AND (storage.foldername(name))[1] = auth.uid()::text);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Authenticated users can update their credentials"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (bucket_id = 'credentials' AND (storage.foldername(name))[1] = auth.uid()::text);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
  CREATE POLICY "Anyone can read credentials"
    ON storage.objects FOR SELECT
    TO public
    USING (bucket_id = 'credentials');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
