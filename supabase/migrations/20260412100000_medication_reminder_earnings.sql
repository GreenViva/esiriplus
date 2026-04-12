-- Allow multiple medication_reminder earnings per nurse per consultation.
-- The existing UNIQUE(doctor_id, consultation_id) blocks this.
-- Replace with a partial unique that only applies to non-reminder earnings.

ALTER TABLE doctor_earnings DROP CONSTRAINT IF EXISTS doctor_earnings_doctor_consultation_uq;

-- Regular earnings: one per doctor per consultation (consultation, follow_up, substitute_follow_up)
CREATE UNIQUE INDEX IF NOT EXISTS doctor_earnings_doctor_consultation_uq
    ON doctor_earnings (doctor_id, consultation_id)
    WHERE earning_type NOT IN ('medication_reminder');

-- Medication reminder earnings: one per event (use event_id stored in notes or a new column)
-- For simplicity, allow unlimited per doctor+consultation for medication_reminder type.
-- Duplicates are prevented at the edge function level (event status check).
