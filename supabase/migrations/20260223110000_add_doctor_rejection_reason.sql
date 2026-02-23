-- Add rejection_reason column to doctor_profiles so admins can distinguish
-- rejected doctors from pending ones. NULL = pending, non-NULL = rejected.
ALTER TABLE doctor_profiles ADD COLUMN rejection_reason text;
