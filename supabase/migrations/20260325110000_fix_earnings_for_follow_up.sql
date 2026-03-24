-- Fix: Allow zero-amount earnings for follow-up consultations.
-- The doctor_earnings table has CHECK (amount > 0) which blocks ending
-- follow-up consultations (fee=0). Two fixes:
-- 1. Relax the check to allow amount >= 0
-- 2. If there's a trigger inserting earnings on consultation completion,
--    make it skip zero-fee consultations

-- Relax the amount check constraint
ALTER TABLE doctor_earnings DROP CONSTRAINT IF EXISTS doctor_earnings_amount_check;
ALTER TABLE doctor_earnings ADD CONSTRAINT doctor_earnings_amount_check CHECK (amount >= 0);

-- Find and recreate any trigger that inserts earnings on consultation completion.
-- If a trigger function inserts into doctor_earnings with consultation_fee,
-- make it skip when fee = 0.

-- Check if there's a trigger function that does the insert:
CREATE OR REPLACE FUNCTION trg_record_doctor_earnings()
RETURNS trigger AS $$
BEGIN
    -- Only record earnings when consultation transitions to 'completed'
    -- and the fee is greater than 0 (skip free follow-ups).
    IF NEW.status = 'completed' AND
       (OLD.status IS NULL OR OLD.status != 'completed') AND
       NEW.consultation_fee > 0 THEN
        INSERT INTO doctor_earnings (doctor_id, consultation_id, amount, status)
        VALUES (NEW.doctor_id, NEW.consultation_id, NEW.consultation_fee, 'pending')
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Recreate the trigger (drop first to be idempotent)
DROP TRIGGER IF EXISTS trg_consultation_earnings ON consultations;
CREATE TRIGGER trg_consultation_earnings
    AFTER UPDATE ON consultations
    FOR EACH ROW
    EXECUTE FUNCTION trg_record_doctor_earnings();
