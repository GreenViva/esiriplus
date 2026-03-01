-- Migration: Structured doctor availability slots (weekly recurring schedule)

CREATE TABLE IF NOT EXISTS doctor_availability_slots (
  slot_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  day_of_week   SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0=Sunday
  start_time    TIME NOT NULL,
  end_time      TIME NOT NULL,
  buffer_minutes SMALLINT NOT NULL DEFAULT 5,
  is_active     BOOLEAN NOT NULL DEFAULT true,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT chk_end_after_start CHECK (end_time > start_time),
  CONSTRAINT uq_doctor_day_start UNIQUE (doctor_id, day_of_week, start_time)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_availability_slots_doctor
  ON doctor_availability_slots (doctor_id, is_active);

CREATE INDEX IF NOT EXISTS idx_availability_slots_day
  ON doctor_availability_slots (day_of_week, is_active);

-- RLS policies
ALTER TABLE doctor_availability_slots ENABLE ROW LEVEL SECURITY;

-- Doctors can manage their own slots
CREATE POLICY "Doctors manage own availability slots"
  ON doctor_availability_slots
  FOR ALL
  USING (auth.uid() = doctor_id)
  WITH CHECK (auth.uid() = doctor_id);

-- Patients (and anyone authenticated) can read active slots
CREATE POLICY "Anyone can read active availability slots"
  ON doctor_availability_slots
  FOR SELECT
  USING (is_active = true);

-- Service role has full access
CREATE POLICY "Service role full access on availability slots"
  ON doctor_availability_slots
  FOR ALL
  USING (auth.role() = 'service_role')
  WITH CHECK (auth.role() = 'service_role');

-- Auto-update updated_at on modification
CREATE OR REPLACE FUNCTION fn_update_availability_slot_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_availability_slot_updated ON doctor_availability_slots;
CREATE TRIGGER trg_availability_slot_updated
  BEFORE UPDATE ON doctor_availability_slots
  FOR EACH ROW
  EXECUTE FUNCTION fn_update_availability_slot_timestamp();
