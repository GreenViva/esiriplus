-- Add APNs (Apple Push Notification service) token storage alongside the
-- existing FCM columns. Mirrors the existing fcm_token layout — patient
-- sessions get a column, doctors/admins get a column on fcm_tokens — so
-- send-push-notification can fan out to both Android and iOS in one query.
--
-- Backwards-compatible: the column is nullable; rows without an APNs token
-- continue to behave exactly as today.
--
-- NOTE: This file was reconstructed on 2026-04-23 from the remote migration
-- tracking table because the original file was pushed without being committed
-- to git. The migration is already applied on the linked remote project
-- (nzzvphhqbcscoetzfzkd / esiri-android-prod); this local copy exists so the
-- CLI migration history matches.

-- ── Patients ────────────────────────────────────────────────────────────────
ALTER TABLE patient_sessions
  ADD COLUMN IF NOT EXISTS apns_token TEXT;

-- ── Doctors / admins ────────────────────────────────────────────────────────
ALTER TABLE fcm_tokens
  ADD COLUMN IF NOT EXISTS apns_token TEXT;

-- The existing RLS policies on fcm_tokens cover the whole row by user_id, so
-- they apply transparently to apns_token. Edge functions use service_role and
-- bypass RLS — no policy changes needed.
