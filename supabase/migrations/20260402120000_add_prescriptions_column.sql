-- Add prescriptions JSONB column to consultation_reports
ALTER TABLE consultation_reports
  ADD COLUMN IF NOT EXISTS prescriptions JSONB DEFAULT '[]'::jsonb;
