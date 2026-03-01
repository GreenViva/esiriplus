-- 3.3: Fix credentials storage bucket — restrict read access
-- Previously: "Anyone can read credentials" used TO public, making credential
-- documents (medical licenses, certificates) publicly downloadable.
-- Now: Only the owner, admin, and HR roles can read credentials.

DROP POLICY IF EXISTS "Anyone can read credentials" ON storage.objects;

-- Owner can read their own credentials
CREATE POLICY "Owner can read own credentials"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'credentials'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- Admin and HR can read all credentials (for doctor verification)
CREATE POLICY "Admin and HR can read all credentials"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'credentials'
        AND EXISTS (
            SELECT 1 FROM public.user_roles
            WHERE user_roles.user_id = auth.uid()
            AND user_roles.role_name IN ('admin', 'hr')
        )
    );
