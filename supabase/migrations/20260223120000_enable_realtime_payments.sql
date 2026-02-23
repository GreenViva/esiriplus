-- Enable Supabase Realtime for the payments table so the admin panel
-- receives live updates when payments are created or updated.
ALTER PUBLICATION supabase_realtime ADD TABLE payments;
