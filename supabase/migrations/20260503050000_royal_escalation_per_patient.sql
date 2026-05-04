-- ============================================================================
-- Royal check-in escalation: one patient per CO (not the whole slot)
--
-- The original design assigned the entire doctor's Royal patient list to a
-- single CO. Switching to per-patient: one escalation row per (doctor,
-- slot, patient), each rung to its own CO so multiple COs can cover the
-- same doctor's slot in parallel.
-- ============================================================================

ALTER TABLE royal_checkin_escalations
    ADD COLUMN IF NOT EXISTS patient_session_id TEXT,
    ADD COLUMN IF NOT EXISTS consultation_id UUID;

-- Backfill patient_session_id from the first call row (if any). Test rows
-- without calls get nulled and removed below.
UPDATE royal_checkin_escalations e
   SET patient_session_id = c.patient_session_id,
       consultation_id    = c.consultation_id
  FROM (
        SELECT DISTINCT ON (escalation_id)
               escalation_id, patient_session_id, consultation_id
          FROM royal_checkin_escalation_calls
         ORDER BY escalation_id, created_at ASC
       ) c
 WHERE c.escalation_id = e.escalation_id
   AND e.patient_session_id IS NULL;

-- Test escalations created before this migration with no calls placed are
-- discarded — they can't be uniquely identified per-patient anyway.
DELETE FROM royal_checkin_escalations WHERE patient_session_id IS NULL;

ALTER TABLE royal_checkin_escalations
    ALTER COLUMN patient_session_id SET NOT NULL;

-- Replace the per-slot uniqueness with per-(slot, patient) uniqueness so
-- the cron can create multiple escalations within the same slot.
ALTER TABLE royal_checkin_escalations
    DROP CONSTRAINT IF EXISTS royal_checkin_escalations_doctor_id_slot_date_slot_hour_key;

ALTER TABLE royal_checkin_escalations
    DROP CONSTRAINT IF EXISTS royal_checkin_escalations_doctor_slot_patient_uq;

ALTER TABLE royal_checkin_escalations
    ADD CONSTRAINT royal_checkin_escalations_doctor_slot_patient_uq
    UNIQUE (doctor_id, slot_date, slot_hour, patient_session_id);

CREATE INDEX IF NOT EXISTS idx_royal_escalations_doctor_slot
    ON royal_checkin_escalations (doctor_id, slot_date, slot_hour);
