-- ============================================================================
-- Add `notes` column to doctor_earnings.
--
-- The Royal earnings trigger writes a human-readable note for follow-up,
-- royal_call, and Royal initial earnings (e.g. "Royal call earning. call_id=…").
-- Without this column the trigger throws on every Royal completion. Column
-- is nullable so existing earnings remain valid.
-- ============================================================================

ALTER TABLE doctor_earnings
    ADD COLUMN IF NOT EXISTS notes TEXT;
