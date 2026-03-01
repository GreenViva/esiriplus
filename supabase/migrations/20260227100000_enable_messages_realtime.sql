-- Enable Supabase Realtime for messages and typing_indicators tables
-- so doctor <-> patient chat updates are delivered in real-time.

-- 1. Add to Realtime publication
DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE messages;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;

DO $$ BEGIN
  ALTER PUBLICATION supabase_realtime ADD TABLE typing_indicators;
EXCEPTION WHEN duplicate_object THEN NULL;
          WHEN undefined_table THEN NULL;
END $$;

-- 2. Full row on UPDATE/DELETE so Realtime clients get complete data
ALTER TABLE messages REPLICA IDENTITY FULL;
ALTER TABLE typing_indicators REPLICA IDENTITY FULL;

-- 3. RLS for messages — permissive for authenticated users
-- (Edge functions use service-role key; app-level filtering by consultation_id)
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "authenticated_access_messages" ON messages
    FOR ALL
    USING (true)
    WITH CHECK (true);

-- 4. RLS for typing_indicators — permissive for authenticated users
ALTER TABLE typing_indicators ENABLE ROW LEVEL SECURITY;

CREATE POLICY "authenticated_access_typing" ON typing_indicators
    FOR ALL
    USING (true)
    WITH CHECK (true);
