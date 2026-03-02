-- Migration: Add video_room_id to consultations + create video_calls table
-- Required by the videosdk-token edge function.

-- ─── 1. Add video_room_id column to consultations ────────────────────────────
ALTER TABLE consultations
  ADD COLUMN IF NOT EXISTS video_room_id TEXT;

-- ─── 2. Create video_calls table ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS video_calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultations(consultation_id) ON DELETE CASCADE,
    room_id TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for lookup by consultation
CREATE INDEX IF NOT EXISTS idx_video_calls_consultation_id
  ON video_calls(consultation_id);

-- ─── 3. RLS policies for video_calls ─────────────────────────────────────────
ALTER TABLE video_calls ENABLE ROW LEVEL SECURITY;

-- Doctors can read their own consultation video calls
CREATE POLICY "Doctors can view their video calls"
  ON video_calls FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM consultations c
      WHERE c.consultation_id = video_calls.consultation_id
        AND c.doctor_id = auth.uid()
    )
  );

-- Service role can do anything (edge functions use service role)
CREATE POLICY "Service role full access on video_calls"
  ON video_calls FOR ALL
  USING (auth.role() = 'service_role')
  WITH CHECK (auth.role() = 'service_role');
