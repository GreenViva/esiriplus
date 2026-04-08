-- Add can_serve_as_gp flag to doctor_profiles for specialists who also serve as GPs
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS can_serve_as_gp BOOLEAN NOT NULL DEFAULT false;

-- Add doctor_earnings to realtime publication for live dashboard updates
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'doctor_earnings'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE doctor_earnings;
  END IF;
END $$;

ALTER TABLE doctor_earnings REPLICA IDENTITY FULL;
