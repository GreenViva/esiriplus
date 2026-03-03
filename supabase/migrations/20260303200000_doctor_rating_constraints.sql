-- Doctor rating system: CHECK constraints, RLS policies, aggregation trigger
-- The doctor_ratings table already exists (created in MIGRATION_7_8).

-- CHECK: rating must be 1-5
DO $$ BEGIN
  ALTER TABLE doctor_ratings ADD CONSTRAINT chk_rating_range CHECK (rating >= 1 AND rating <= 5);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- CHECK: comment mandatory when rating <= 3
DO $$ BEGIN
  ALTER TABLE doctor_ratings ADD CONSTRAINT chk_comment_required_low_rating
    CHECK (rating > 3 OR (comment IS NOT NULL AND length(trim(comment)) > 0));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Index for admin queries: doctor_id + created_at DESC
CREATE INDEX IF NOT EXISTS idx_doctor_ratings_doctor_created
  ON doctor_ratings (doctor_id, created_at DESC);

-- Ensure UNIQUE on consultation_id (one rating per consultation)
CREATE UNIQUE INDEX IF NOT EXISTS idx_doctor_ratings_consultation_unique
  ON doctor_ratings (consultation_id);

-- Enable RLS
ALTER TABLE doctor_ratings ENABLE ROW LEVEL SECURITY;

-- Admin/HR/Audit can read all ratings + comments
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE policyname = 'Portal users can read doctor_ratings'
  ) THEN
    CREATE POLICY "Portal users can read doctor_ratings"
      ON doctor_ratings FOR SELECT TO authenticated
      USING (
        EXISTS (
          SELECT 1 FROM user_roles
          WHERE user_id = auth.uid()
          AND role_name IN ('admin', 'hr', 'audit')
        )
      );
  END IF;
END $$;

-- No SELECT policy for doctors → they cannot read individual ratings
-- No UPDATE/DELETE policies → ratings are immutable after submission
-- INSERT is done via service_role client in edge function, bypasses RLS

-- Aggregation trigger: update doctor_profiles.average_rating and total_ratings on INSERT
CREATE OR REPLACE FUNCTION update_doctor_rating_aggregates()
RETURNS TRIGGER AS $$
DECLARE
  v_avg DOUBLE PRECISION;
  v_total INTEGER;
BEGIN
  SELECT AVG(rating)::DOUBLE PRECISION, COUNT(*)::INTEGER
    INTO v_avg, v_total
    FROM doctor_ratings
    WHERE doctor_id = NEW.doctor_id;

  UPDATE doctor_profiles
    SET average_rating = COALESCE(v_avg, 0),
        total_ratings = COALESCE(v_total, 0),
        updated_at = NOW()
    WHERE doctor_id = NEW.doctor_id;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_update_rating_aggregates ON doctor_ratings;
CREATE TRIGGER trg_update_rating_aggregates
  AFTER INSERT ON doctor_ratings
  FOR EACH ROW
  EXECUTE FUNCTION update_doctor_rating_aggregates();
