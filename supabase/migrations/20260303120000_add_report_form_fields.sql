-- Add columns to consultation_reports for the new report form fields
ALTER TABLE consultation_reports
  ADD COLUMN IF NOT EXISTS diagnosed_problem TEXT,
  ADD COLUMN IF NOT EXISTS category TEXT,
  ADD COLUMN IF NOT EXISTS severity TEXT,
  ADD COLUMN IF NOT EXISTS treatment_plan TEXT,
  ADD COLUMN IF NOT EXISTS follow_up_recommended BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS further_notes TEXT,
  ADD COLUMN IF NOT EXISTS presenting_symptoms TEXT,
  ADD COLUMN IF NOT EXISTS doctor_name TEXT,
  ADD COLUMN IF NOT EXISTS patient_session_id TEXT,
  ADD COLUMN IF NOT EXISTS consultation_date TIMESTAMPTZ;
