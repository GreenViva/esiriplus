-- Widen consultation_fee check to allow zero (edge cases: free follow-ups, promos).
-- The primary fee enforcement is via service_access_payments.
ALTER TABLE consultations DROP CONSTRAINT IF EXISTS consultations_consultation_fee_check;
ALTER TABLE consultations ADD CONSTRAINT consultations_consultation_fee_check CHECK (consultation_fee >= 0);
