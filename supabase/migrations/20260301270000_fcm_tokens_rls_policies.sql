-- Allow authenticated users to manage their own FCM token.
-- The mobile app upserts tokens directly using the user's JWT.
-- Edge functions (service_role) bypass RLS and are unaffected.

CREATE POLICY "Users can insert own fcm token"
  ON public.fcm_tokens FOR INSERT
  TO authenticated
  WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY "Users can update own fcm token"
  ON public.fcm_tokens FOR UPDATE
  TO authenticated
  USING (user_id = auth.uid()::text)
  WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY "Users can read own fcm token"
  ON public.fcm_tokens FOR SELECT
  TO authenticated
  USING (user_id = auth.uid()::text);
