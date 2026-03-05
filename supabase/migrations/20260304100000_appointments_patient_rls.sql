-- Migration: Add patient RLS policies to appointments table
-- Previously only doctors and service role had access.
-- Patients need to read their own appointments and cancel them.

-- Patients can see their own appointments
-- (Patient JWTs set sub = session_id, so auth.uid() returns session_id)
CREATE POLICY "Patients see own appointments"
  ON appointments
  FOR SELECT
  USING (auth.uid() = patient_session_id);

-- Patients can cancel their own appointments (status update only)
CREATE POLICY "Patients cancel own appointments"
  ON appointments
  FOR UPDATE
  USING (auth.uid() = patient_session_id)
  WITH CHECK (auth.uid() = patient_session_id);
