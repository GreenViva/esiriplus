-- Migration: Add patient health profile and symptoms to consultation requests
-- Allows patients to share symptoms and health profile data with doctors.

ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS symptoms TEXT;
ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS patient_age_group TEXT;
ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS patient_sex TEXT;
ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS patient_blood_group TEXT;
ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS patient_allergies TEXT;
ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS patient_chronic_conditions TEXT;
