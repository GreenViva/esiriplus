-- Enable Supabase Realtime for health analytics tables so the admin panel
-- receives live updates for consultations, diagnoses, and patient reports.
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE consultations;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE diagnoses;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE patient_reports;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;
