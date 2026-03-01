-- Fix: Patient Realtime events not delivered
--
-- Root cause: The "Patients can view own requests" policy was dropped in
-- 20260301110000_fix_consultation_requests_rls.sql and never replaced.
-- Supabase Realtime respects RLS, so patients received ZERO realtime events
-- on the consultation_requests table (including the critical ACCEPTED event).
--
-- This migration adds patient SELECT policies using auth.uid() which now
-- matches patient_session_id because patient JWTs include sub=session_id.
--
-- Type notes:
--   consultation_requests.patient_session_id → TEXT  (compare with auth.uid()::text)
--   consultations.patient_session_id         → UUID  (compare with auth.uid() directly)

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. consultation_requests — patient can see own requests (TEXT column)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE POLICY "Patients can view own requests"
    ON consultation_requests FOR SELECT
    TO authenticated
    USING (patient_session_id = auth.uid()::text);

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. messages — patient can read messages in their consultations (UUID join)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE POLICY "patients_read_own_messages"
    ON messages FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM consultations c
            WHERE c.consultation_id = messages.consultation_id
              AND c.patient_session_id = auth.uid()
        )
    );

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. consultations — patient can see own consultations (UUID column)
-- ═══════════════════════════════════════════════════════════════════════════
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'consultations'
          AND policyname = 'patients_read_own_consultations'
    ) THEN
        -- Check if RLS is enabled on consultations
        IF NOT EXISTS (
            SELECT 1 FROM pg_class
            WHERE relname = 'consultations' AND relrowsecurity = true
        ) THEN
            ALTER TABLE consultations ENABLE ROW LEVEL SECURITY;
        END IF;

        CREATE POLICY "patients_read_own_consultations"
            ON consultations FOR SELECT
            TO authenticated
            USING (patient_session_id = auth.uid());

        -- Doctors also need to see their own consultations
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies
            WHERE tablename = 'consultations'
              AND policyname = 'doctors_read_own_consultations'
        ) THEN
            CREATE POLICY "doctors_read_own_consultations"
                ON consultations FOR SELECT
                TO authenticated
                USING (doctor_id = auth.uid());
        END IF;
    END IF;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. typing_indicators — patient can read typing in their consultations
-- ═══════════════════════════════════════════════════════════════════════════
CREATE POLICY "patients_read_own_typing"
    ON typing_indicators FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM consultations c
            WHERE c.consultation_id = typing_indicators.consultation_id
              AND c.patient_session_id = auth.uid()
        )
    );

-- Patient needs to write their own typing indicators
CREATE POLICY "patients_write_own_typing"
    ON typing_indicators FOR ALL
    TO authenticated
    USING (user_id = auth.uid()::text)
    WITH CHECK (user_id = auth.uid()::text);
