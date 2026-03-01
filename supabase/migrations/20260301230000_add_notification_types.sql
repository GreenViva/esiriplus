-- Add notification types for doctor status changes
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_approved';
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_rejected';
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_suspended';
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_unsuspended';
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_banned';
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_unbanned';
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'doctor_warned';
