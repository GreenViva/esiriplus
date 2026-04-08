-- Clear all consultation data for testing.
-- Safe to run: CASCADE handles consultation_requests FK references.
TRUNCATE TABLE consultations RESTART IDENTITY CASCADE;
TRUNCATE TABLE consultation_requests RESTART IDENTITY CASCADE;
