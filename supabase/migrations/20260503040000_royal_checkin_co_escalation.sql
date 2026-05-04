-- ============================================================================
-- Royal check-in CO escalation
--
-- After 3 unacknowledged attempts at a slot (08:10 / 13:10 / 18:10), the
-- royal-checkin-cron escalates at HH:15 to an available clinical officer (CO).
-- The CO calls the doctor's Royal patients on their behalf; per-call earnings
-- of 2,000 TZS are credited only when the patient accepts AND the call lasts
-- > 60 seconds. Once the CO completes the escalation, the doctor receives a
-- compliance warning (via the existing doctor_profiles.warning_message
-- pipeline that auto-increments warning_count via trigger).
-- ============================================================================

-- Per-(doctor, slot) escalation tracking. UNIQUE prevents the cron from
-- creating duplicate escalations within the same slot/day if it ticks twice.
CREATE TABLE IF NOT EXISTS royal_checkin_escalations (
    escalation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_id UUID NOT NULL REFERENCES royal_checkin_reminders(id) ON DELETE CASCADE,
    doctor_id UUID NOT NULL REFERENCES doctor_profiles(doctor_id) ON DELETE CASCADE,
    slot_date DATE NOT NULL,
    slot_hour SMALLINT NOT NULL CHECK (slot_hour IN (8, 13, 18)),
    co_id UUID REFERENCES doctor_profiles(doctor_id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending','co_ringing','co_accepted','in_progress','completed','failed')),
    co_notified_at TIMESTAMPTZ,
    ring_expires_at TIMESTAMPTZ,
    co_accepted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    reassign_count SMALLINT NOT NULL DEFAULT 0 CHECK (reassign_count BETWEEN 0 AND 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doctor_id, slot_date, slot_hour)
);

CREATE INDEX IF NOT EXISTS idx_royal_checkin_escalations_status_ring
    ON royal_checkin_escalations (status, ring_expires_at)
    WHERE status = 'co_ringing';

CREATE INDEX IF NOT EXISTS idx_royal_checkin_escalations_co_active
    ON royal_checkin_escalations (co_id, status)
    WHERE status IN ('co_accepted','in_progress');

-- Per-patient call rows. One escalation has many calls (one per Royal patient
-- the CO chose to ring). Earning eligibility is computed inline so the
-- complete_escalation action can sum payable calls in a single query.
CREATE TABLE IF NOT EXISTS royal_checkin_escalation_calls (
    call_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escalation_id UUID NOT NULL REFERENCES royal_checkin_escalations(escalation_id) ON DELETE CASCADE,
    patient_session_id TEXT NOT NULL,
    consultation_id UUID,
    video_room_id TEXT,
    call_started_at TIMESTAMPTZ,
    call_ended_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    patient_accepted BOOLEAN NOT NULL DEFAULT false,
    qualifies_for_payment BOOLEAN GENERATED ALWAYS AS
        (patient_accepted AND COALESCE(duration_seconds, 0) > 60) STORED,
    earnings_credited BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_royal_checkin_calls_escalation
    ON royal_checkin_escalation_calls (escalation_id);

-- (No doctor_earnings unique-index touch needed — the per-(doctor,
-- consultation) uniqueness was dropped across the 2026-04-29 / 2026-05-01
-- migrations to support multiple earning rows per consultation. Royal
-- check-in escalation earnings just insert as plain rows with
-- earning_type='royal_checkin_escalation'.)

-- RLS — COs can read/update their own escalation rows; doctors can read
-- escalations that belong to them (so the dashboard can surface "covered by
-- CO" history).
ALTER TABLE royal_checkin_escalations ENABLE ROW LEVEL SECURITY;
ALTER TABLE royal_checkin_escalation_calls ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rce_co_select ON royal_checkin_escalations;
CREATE POLICY rce_co_select ON royal_checkin_escalations
    FOR SELECT USING (auth.uid() = co_id OR auth.uid() = doctor_id);

DROP POLICY IF EXISTS rcec_co_select ON royal_checkin_escalation_calls;
CREATE POLICY rcec_co_select ON royal_checkin_escalation_calls
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM royal_checkin_escalations e
            WHERE e.escalation_id = royal_checkin_escalation_calls.escalation_id
              AND (auth.uid() = e.co_id OR auth.uid() = e.doctor_id)
        )
    );

-- Touch updated_at on escalation row updates.
CREATE OR REPLACE FUNCTION fn_touch_royal_escalation_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_royal_escalation_updated_at ON royal_checkin_escalations;
CREATE TRIGGER trg_royal_escalation_updated_at
    BEFORE UPDATE ON royal_checkin_escalations
    FOR EACH ROW EXECUTE FUNCTION fn_touch_royal_escalation_updated_at();
