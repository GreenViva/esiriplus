-- Add suspension_reason column for doctor suspensions
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS suspension_reason TEXT;
