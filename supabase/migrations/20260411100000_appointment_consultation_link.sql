-- ============================================================================
-- Link consultation requests to appointments so that:
--   1. A patient can start a consultation directly from an appointment
--   2. One appointment can only serve one consultation (enforced via guard)
--   3. Appointment status updates to 'in_progress' when consultation starts
-- ============================================================================

-- Add appointment_id to consultation_requests
ALTER TABLE consultation_requests
  ADD COLUMN IF NOT EXISTS appointment_id UUID REFERENCES appointments(appointment_id);

-- Index for fast lookup when accepting a request
CREATE INDEX IF NOT EXISTS idx_consultation_requests_appointment_id
  ON consultation_requests (appointment_id)
  WHERE appointment_id IS NOT NULL;
