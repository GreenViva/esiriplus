-- Storage policies for the message-attachments bucket.
-- Allows authenticated users (doctors with Supabase JWT, patients with custom JWT)
-- to upload and read attachments.

-- Ensure the bucket exists and is public (for reading via public URLs)
INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES ('message-attachments', 'message-attachments', true, 10485760)
ON CONFLICT (id) DO UPDATE SET public = true, file_size_limit = 10485760;

-- Allow authenticated users to upload files
CREATE POLICY "Authenticated users can upload attachments"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'message-attachments');

-- Allow authenticated users to update (upsert) their uploads
CREATE POLICY "Authenticated users can update attachments"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'message-attachments');

-- Allow public read access (bucket is public, URLs are shared in chat)
CREATE POLICY "Public read access for attachments"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'message-attachments');
