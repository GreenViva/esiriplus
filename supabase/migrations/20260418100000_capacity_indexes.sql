-- ============================================================================
-- Capacity-focused indexes (no schema/behavior changes).
--
-- Two supporting indexes identified by the capacity audit as the most
-- impactful cheap wins. Both are additive — they only accelerate existing
-- queries, never alter results. Safe to ship at any time.
--
-- 1. (doctor_id, status, report_submitted) on consultations
--    fn_sync_doctor_in_session() runs `EXISTS (SELECT 1 FROM consultations
--    WHERE doctor_id = X AND (status IN (...) OR (status='completed' AND
--    report_submitted=false)))` on every consultation update. The existing
--    idx_consultations_doctor_status covers (doctor_id, status) but forces a
--    heap lookup to filter report_submitted. Three-column index serves the
--    predicate entirely from the index.
--
-- 2. (is_available, verification_status) on doctor_profiles
--    Patient-facing doctor discovery filters verified + available doctors.
--    Today it full-scans — fine at a few hundred doctors, painful at a few
--    thousand. Cheap to add, pays forward.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_consultations_doctor_status_report
    ON consultations (doctor_id, status, report_submitted);

CREATE INDEX IF NOT EXISTS idx_doctor_profiles_available_verified
    ON doctor_profiles (is_available, verification_status);
