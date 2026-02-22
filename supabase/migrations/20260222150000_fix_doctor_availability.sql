-- Drop and recreate doctor_availability to ensure correct schema
DROP TABLE IF EXISTS public.doctor_availability;

CREATE TABLE public.doctor_availability (
    availability_id TEXT PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    is_available BOOLEAN NOT NULL DEFAULT false,
    availability_schedule JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_doctor_id
    ON public.doctor_availability (doctor_id);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_is_available
    ON public.doctor_availability (is_available);

-- Enable RLS
ALTER TABLE public.doctor_availability ENABLE ROW LEVEL SECURITY;

-- Doctors can read/write their own availability
CREATE POLICY "Doctors can manage their own availability"
    ON public.doctor_availability FOR ALL TO authenticated
    USING (doctor_id = auth.uid())
    WITH CHECK (doctor_id = auth.uid());

-- All authenticated users (patients) can read any doctor's availability
CREATE POLICY "Anyone can read doctor availability"
    ON public.doctor_availability FOR SELECT TO authenticated
    USING (true);
