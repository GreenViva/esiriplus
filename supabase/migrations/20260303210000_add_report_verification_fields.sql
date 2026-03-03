-- Add verification_code and is_ai_generated to consultation_reports
ALTER TABLE consultation_reports
  ADD COLUMN IF NOT EXISTS verification_code TEXT,
  ADD COLUMN IF NOT EXISTS is_ai_generated BOOLEAN DEFAULT FALSE;

-- Index for fast verification lookups
CREATE INDEX IF NOT EXISTS idx_consultation_reports_verification_code
  ON consultation_reports (verification_code)
  WHERE verification_code IS NOT NULL;
