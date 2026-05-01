-- ============================================================================
-- Medication reminder: ring-first flow (2026-05-01).
--
-- Two-stage acceptance: cron rings the nurse for 60s ('nurse_ringing'); if
-- they accept it becomes 'nurse_accepted' and lands on their Medical
-- Reminder list; tapping Call moves to 'nurse_calling' and pushes the
-- patient. Patient never gets a heads-up push at the cron stage.
-- ============================================================================

-- ── 1. Extend status set ────────────────────────────────────────────────────

ALTER TABLE medication_reminder_events
    DROP CONSTRAINT IF EXISTS medication_reminder_events_status_check;

ALTER TABLE medication_reminder_events
    ADD CONSTRAINT medication_reminder_events_status_check
    CHECK (status IN (
        'pending',
        'nurse_ringing',        -- new: 60s ring window
        'nurse_accepted',       -- new: nurse picked up the invitation
        'nurse_notified',       -- legacy: kept for backward compat (treat as ringing)
        'nurse_calling',
        'completed',
        'no_nurse',
        'patient_unreachable',
        'failed'
    ));

-- ── 2. New timestamp columns ───────────────────────────────────────────────

ALTER TABLE medication_reminder_events
    ADD COLUMN IF NOT EXISTS nurse_accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ring_expires_at   TIMESTAMPTZ;

-- ── 3. Index for cron's stale-ring sweep ────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_med_events_ring_expiry
    ON medication_reminder_events (status, ring_expires_at)
    WHERE status = 'nurse_ringing';

-- ── 4. RLS: nurses also see ringing events targeted at them ────────────────
-- Existing 'nurses_read_assigned_events' uses nurse_id = auth.uid(); since the
-- ringing event has nurse_id set the moment the cron picks them, that policy
-- already covers ringing/accepted/calling rows. No change needed.
