-- Add recovery_hash column to patient_sessions for answer-based recovery lookup.
-- When patients set up security questions, all 5 answers are normalized, sorted by
-- question key, concatenated, and hashed (SHA-256). This hash is stored here.
-- During recovery, the patient re-enters all 5 answers, the same hash is computed,
-- and the matching session is found — no Patient ID or phone needed.

ALTER TABLE patient_sessions ADD COLUMN IF NOT EXISTS recovery_hash TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_patient_sessions_recovery_hash
    ON patient_sessions(recovery_hash);
