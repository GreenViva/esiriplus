-- 3.1: Fix messages RLS — restrict to consultation participants
-- Previously: USING(true) allowed any authenticated user to read ALL messages.
-- Now: Doctors can only read messages for their own consultations.
--       INSERT/UPDATE/DELETE only via service_role (edge functions).
--       Patient Realtime won't get RLS-filtered events (they use polling via edge function).

-- Drop the overly permissive policy
DROP POLICY IF EXISTS "authenticated_access_messages" ON messages;

-- Doctors can SELECT messages for consultations they're assigned to
CREATE POLICY "doctors_read_own_messages" ON messages
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM consultations c
            WHERE c.consultation_id = messages.consultation_id
            AND c.doctor_id = auth.uid()
        )
    );

-- 3.1b: Fix typing_indicators RLS
DROP POLICY IF EXISTS "authenticated_access_typing" ON typing_indicators;

-- Doctors can SELECT typing indicators for their consultations
CREATE POLICY "doctors_read_own_typing" ON typing_indicators
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM consultations c
            WHERE c.consultation_id = typing_indicators.consultation_id
            AND c.doctor_id = auth.uid()
        )
    );

-- Doctors can INSERT/UPDATE their own typing indicator
CREATE POLICY "doctors_write_own_typing" ON typing_indicators
    FOR ALL
    TO authenticated
    USING (user_id = auth.uid()::text)
    WITH CHECK (user_id = auth.uid()::text);
