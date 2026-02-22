-- Add specialist_field column to doctor_profiles for free-text specialist type
ALTER TABLE public.doctor_profiles
ADD COLUMN IF NOT EXISTS specialist_field TEXT DEFAULT NULL;

COMMENT ON COLUMN public.doctor_profiles.specialist_field
IS 'Free-text specialist type (e.g. Dentist, Cardiologist) when specialty=specialist';
