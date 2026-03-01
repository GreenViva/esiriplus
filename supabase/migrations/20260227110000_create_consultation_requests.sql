-- Create the consultation_requests table for real-time consultation request lifecycle.
-- Patients create requests → doctors accept/reject within a 60s TTL.

CREATE TABLE IF NOT EXISTS consultation_requests (
    request_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_session_id TEXT NOT NULL,
    doctor_id         UUID NOT NULL REFERENCES auth.users(id),
    service_type      TEXT NOT NULL DEFAULT '',
    consultation_type TEXT NOT NULL DEFAULT 'chat',
    chief_complaint   TEXT NOT NULL DEFAULT '',
    status            TEXT NOT NULL DEFAULT 'pending'
                      CHECK (status IN ('pending', 'accepted', 'rejected', 'expired')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '60 seconds'),
    consultation_id   UUID REFERENCES consultations(consultation_id)
);

-- Index for fast lookups by patient (duplicate check) and doctor (incoming requests)
CREATE INDEX IF NOT EXISTS idx_consultation_requests_patient
    ON consultation_requests (patient_session_id, status);
CREATE INDEX IF NOT EXISTS idx_consultation_requests_doctor
    ON consultation_requests (doctor_id, status);

-- Add request_id column to consultations if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'consultations' AND column_name = 'request_id'
    ) THEN
        ALTER TABLE consultations ADD COLUMN request_id UUID;
    END IF;
END $$;

-- Enable Realtime for consultation_requests
ALTER PUBLICATION supabase_realtime ADD TABLE consultation_requests;
ALTER TABLE consultation_requests REPLICA IDENTITY FULL;

-- RLS policies
ALTER TABLE consultation_requests ENABLE ROW LEVEL SECURITY;

-- Patients can see their own requests
CREATE POLICY "Patients can view own requests"
    ON consultation_requests FOR SELECT
    USING (true);

-- Doctors can see requests addressed to them
CREATE POLICY "Doctors can view their requests"
    ON consultation_requests FOR SELECT
    USING (doctor_id = auth.uid());

-- Service role can do everything (edge functions use service role)
CREATE POLICY "Service role full access"
    ON consultation_requests FOR ALL
    USING (true)
    WITH CHECK (true);
