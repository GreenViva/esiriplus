-- Create fcm_tokens table for push notification delivery.
-- Used by both doctors (user_id = auth UUID) and patients (user_id = session_id).
CREATE TABLE IF NOT EXISTS fcm_tokens (
  user_id text PRIMARY KEY,
  token text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- RLS: service_role only (edge functions handle reads/writes)
ALTER TABLE fcm_tokens ENABLE ROW LEVEL SECURITY;
