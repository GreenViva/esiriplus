-- Add missing columns to admin_logs that the admin panel and audit triggers need.
ALTER TABLE admin_logs ADD COLUMN IF NOT EXISTS target_type text;
ALTER TABLE admin_logs ADD COLUMN IF NOT EXISTS target_id text;
ALTER TABLE admin_logs ADD COLUMN IF NOT EXISTS details jsonb;
