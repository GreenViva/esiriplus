-- Enable Realtime on doctor_profiles and admin_logs tables
-- so admin and HR dashboards stay in sync automatically.

alter publication supabase_realtime add table doctor_profiles;
alter publication supabase_realtime add table admin_logs;
alter publication supabase_realtime add table doctor_ratings;
