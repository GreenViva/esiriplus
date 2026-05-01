-- ============================================================================
-- Allow a doctor to read prescriptions on their own consultations.
--
-- The original `prescriptions` table policy (migration 20260404300000) only
-- granted SELECT to portal users (admins/HR), so the Android doctor flow —
-- which queries `rest/v1/prescriptions?consultation_id=eq.X` from the Royal
-- Clients medication-reminder picker — got back zero rows even when the
-- table actually had the matching rows. This adds a focused policy that
-- lets a doctor see only the prescriptions tied to a consultation they own.
-- Same pattern is added for `diagnoses` so future doctor-side surfaces
-- (consultation history, follow-up context) work too.
-- ============================================================================

CREATE POLICY "Doctors can read their own consultation prescriptions"
    ON public.prescriptions FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.consultations c
             WHERE c.consultation_id = prescriptions.consultation_id
               AND c.doctor_id = auth.uid()
        )
    );

CREATE POLICY "Doctors can read their own consultation diagnoses"
    ON public.diagnoses FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.consultations c
             WHERE c.consultation_id = diagnoses.consultation_id
               AND c.doctor_id = auth.uid()
        )
    );
