-- Enable Realtime on doctor_profiles and admin_logs tables
-- so admin and HR dashboards stay in sync automatically.
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE doctor_profiles;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE admin_logs;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE doctor_ratings;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
