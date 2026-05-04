-- Track when the patient actually joined the medication-reminder call so
-- the server can auto-credit the nurse only on real connections (instead of
-- trusting a "Mark complete" tap, which lets a nurse claim earnings on a
-- call the patient never picked up).

ALTER TABLE medication_reminder_events
    ADD COLUMN IF NOT EXISTS patient_joined_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_med_events_status_call
    ON medication_reminder_events (status, call_started_at)
    WHERE status = 'nurse_calling';
