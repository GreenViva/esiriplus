-- Query net._http_response for the most recent function HTTP responses
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT id, status_code, content, error_msg, created
          FROM net._http_response
         ORDER BY id DESC
         LIMIT 8
    LOOP
        RAISE NOTICE 'http %: code=% err=% body=%', r.id, r.status_code, COALESCE(r.error_msg, '(none)'), LEFT(COALESCE(r.content,'(null)'), 150);
    END LOOP;
END $$;
