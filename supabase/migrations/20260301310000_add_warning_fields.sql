-- Add warning fields for doctor profile warnings
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS warning_message TEXT;
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS warning_at TIMESTAMPTZ;
