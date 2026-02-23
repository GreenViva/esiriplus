-- Enable Supabase Realtime for health analytics tables so the admin panel
-- receives live updates for consultations, diagnoses, and patient reports.
ALTER PUBLICATION supabase_realtime ADD TABLE consultations;
ALTER PUBLICATION supabase_realtime ADD TABLE diagnoses;
ALTER PUBLICATION supabase_realtime ADD TABLE patient_reports;
