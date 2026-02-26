-- Add missing columns to doctor_profiles that the mobile app and admin panel expect.
-- These columns were referenced in code but never created in the remote database.

ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS license_document_url TEXT;
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS certificates_url TEXT;
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS country TEXT;
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS country_code TEXT;
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS services TEXT;
