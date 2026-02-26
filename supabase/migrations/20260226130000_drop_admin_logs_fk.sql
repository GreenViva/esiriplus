-- Drop the foreign key constraint on admin_logs.admin_id.
-- The audit triggers use a fallback UUID (00000000-...) for service-role
-- operations where auth.uid() is null, which doesn't exist in auth.users.
-- admin_logs is a logging table and doesn't need strict referential integrity.
ALTER TABLE admin_logs DROP CONSTRAINT IF EXISTS admin_logs_admin_id_fkey;
