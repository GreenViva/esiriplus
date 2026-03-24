-- Diagnostic: list all triggers on consultations table and their functions
DO $$
DECLARE
    r RECORD;
    func_body text;
BEGIN
    FOR r IN
        SELECT t.tgname, p.proname, p.prosrc
        FROM pg_trigger t
        JOIN pg_proc p ON t.tgfoid = p.oid
        WHERE t.tgrelid = 'consultations'::regclass
        AND NOT t.tgisinternal
    LOOP
        -- Check if the function body references doctor_earnings
        IF r.prosrc ILIKE '%doctor_earnings%' THEN
            RAISE NOTICE 'FOUND: trigger=% function=% body=%', r.tgname, r.proname, left(r.prosrc, 500);
        ELSE
            RAISE NOTICE 'trigger=% function=%', r.tgname, r.proname;
        END IF;
    END LOOP;
END $$;
