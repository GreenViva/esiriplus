-- Add booking fields to consultations table for appointment booking flow
ALTER TABLE consultations
  ADD COLUMN IF NOT EXISTS consultation_type text DEFAULT 'both',
  ADD COLUMN IF NOT EXISTS chief_complaint text DEFAULT '',
  ADD COLUMN IF NOT EXISTS preferred_language text DEFAULT 'en';
