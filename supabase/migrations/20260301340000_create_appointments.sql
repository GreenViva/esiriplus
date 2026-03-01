-- Migration: Appointments table for scheduled consultations

-- Drop any pre-existing appointment objects from earlier attempts
DROP TABLE IF EXISTS appointments CASCADE;
DROP TYPE IF EXISTS appointment_status CASCADE;
DROP TYPE IF EXISTS appointment_status_enum CASCADE;

CREATE TYPE appointment_status AS ENUM (
  'booked',
  'confirmed',
  'in_progress',
  'completed',
  'missed',
  'cancelled',
  'rescheduled'
);

CREATE TABLE IF NOT EXISTS appointments (
  appointment_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id           UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  patient_session_id  UUID NOT NULL,
  scheduled_at        TIMESTAMPTZ NOT NULL,
  duration_minutes    SMALLINT NOT NULL DEFAULT 15,
  status              appointment_status NOT NULL DEFAULT 'booked',
  service_type        TEXT NOT NULL,
  consultation_type   TEXT NOT NULL DEFAULT 'chat',
  chief_complaint     TEXT NOT NULL DEFAULT '',
  consultation_fee    INTEGER NOT NULL DEFAULT 0,
  consultation_id     UUID,  -- linked after session starts
  rescheduled_from    UUID REFERENCES appointments(appointment_id),
  reminders_sent      TEXT[] NOT NULL DEFAULT '{}',
  grace_period_minutes SMALLINT NOT NULL DEFAULT 5,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Prevent double-booking: only one active appointment per doctor per time slot
CREATE UNIQUE INDEX IF NOT EXISTS idx_appointments_no_double_book
  ON appointments (doctor_id, scheduled_at)
  WHERE status IN ('booked', 'confirmed', 'in_progress');

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_appointments_doctor_status
  ON appointments (doctor_id, status);

CREATE INDEX IF NOT EXISTS idx_appointments_patient_status
  ON appointments (patient_session_id, status);

CREATE INDEX IF NOT EXISTS idx_appointments_scheduled_at
  ON appointments (scheduled_at);

CREATE INDEX IF NOT EXISTS idx_appointments_status_scheduled
  ON appointments (status, scheduled_at);

-- RLS policies
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;

-- Doctors can see their own appointments
CREATE POLICY "Doctors see own appointments"
  ON appointments
  FOR SELECT
  USING (auth.uid() = doctor_id);

-- Doctors can update their own appointments (status transitions)
CREATE POLICY "Doctors update own appointments"
  ON appointments
  FOR UPDATE
  USING (auth.uid() = doctor_id)
  WITH CHECK (auth.uid() = doctor_id);

-- Service role has full access (edge functions use service role)
CREATE POLICY "Service role full access on appointments"
  ON appointments
  FOR ALL
  USING (auth.role() = 'service_role')
  WITH CHECK (auth.role() = 'service_role');

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION fn_update_appointment_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_appointment_updated ON appointments;
CREATE TRIGGER trg_appointment_updated
  BEFORE UPDATE ON appointments
  FOR EACH ROW
  EXECUTE FUNCTION fn_update_appointment_timestamp();

-- Enable realtime for appointment status changes
ALTER PUBLICATION supabase_realtime ADD TABLE appointments;
