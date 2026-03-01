-- 4.1: Add missing Postgres indexes for common query patterns
-- These indexes dramatically improve performance for chat message fetching,
-- doctor dashboard queries, and patient consultation lookups.

CREATE INDEX IF NOT EXISTS idx_messages_consultation_created
    ON messages (consultation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_consultations_doctor_status
    ON consultations (doctor_id, status);

CREATE INDEX IF NOT EXISTS idx_consultations_patient_status
    ON consultations (patient_session_id, status);
