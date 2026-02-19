-- =====================================================
-- eSIRI — SECURE SESSION MANAGEMENT (SQL)
-- Clean version — no pg_cron dependency
-- Run this in Supabase SQL Editor
-- =====================================================

BEGIN;

-- =====================================================
-- STEP 1: Add missing columns to admin_logs
-- =====================================================

ALTER TABLE admin_logs
  ADD COLUMN IF NOT EXISTS created_at     TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS function_name  TEXT,
  ADD COLUMN IF NOT EXISTS level          TEXT DEFAULT 'info',
  ADD COLUMN IF NOT EXISTS session_id     UUID,
  ADD COLUMN IF NOT EXISTS action         TEXT,
  ADD COLUMN IF NOT EXISTS metadata       JSONB DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS error_message  TEXT,
  ADD COLUMN IF NOT EXISTS ip_address     TEXT;

-- =====================================================
-- STEP 2: Add missing columns to patient_sessions
-- =====================================================

ALTER TABLE patient_sessions
  ADD COLUMN IF NOT EXISTS refresh_token_hash       TEXT,
  ADD COLUMN IF NOT EXISTS refresh_expires_at       TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS last_refreshed_at        TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS legacy_id_hash           TEXT,
  ADD COLUMN IF NOT EXISTS is_legacy                BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_migrated              BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS migrated_at              TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS migrated_from_id         UUID,
  ADD COLUMN IF NOT EXISTS device_info              JSONB,
  ADD COLUMN IF NOT EXISTS ip_address               TEXT,
  ADD COLUMN IF NOT EXISTS fcm_token                TEXT,
  ADD COLUMN IF NOT EXISTS last_extended_at         TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS last_seen_at             TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS failed_validation_count  INTEGER DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_failed_at           TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS is_locked                BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS locked_at                TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS payment_env              TEXT DEFAULT 'mock';

-- =====================================================
-- STEP 3: Indexes
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_patient_sessions_token_hash
  ON patient_sessions(session_token_hash);

CREATE INDEX IF NOT EXISTS idx_patient_sessions_refresh_hash
  ON patient_sessions(refresh_token_hash);

CREATE INDEX IF NOT EXISTS idx_patient_sessions_expires_at
  ON patient_sessions(expires_at);

CREATE INDEX IF NOT EXISTS idx_patient_sessions_legacy_hash
  ON patient_sessions(legacy_id_hash)
  WHERE legacy_id_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_patient_sessions_locked
  ON patient_sessions(is_locked)
  WHERE is_locked = TRUE;

CREATE INDEX IF NOT EXISTS idx_admin_logs_function_action
  ON admin_logs(function_name, action, created_at DESC);

-- =====================================================
-- STEP 4: Trigger — auto-deactivate expired sessions
-- =====================================================

CREATE OR REPLACE FUNCTION public.deactivate_expired_sessions()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  IF NEW.expires_at < NOW() THEN
    NEW.is_active := FALSE;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_deactivate_expired ON patient_sessions;
CREATE TRIGGER trg_deactivate_expired
  BEFORE UPDATE OF expires_at ON patient_sessions
  FOR EACH ROW
  EXECUTE FUNCTION public.deactivate_expired_sessions();

-- =====================================================
-- STEP 5: Trigger — brute force lockout at 10 fails
-- =====================================================

CREATE OR REPLACE FUNCTION public.check_session_lockout()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  IF NEW.failed_validation_count >= 10 AND OLD.failed_validation_count < 10 THEN
    NEW.is_locked := TRUE;
    NEW.locked_at := NOW();
    NEW.is_active := FALSE;

    INSERT INTO admin_logs (
      function_name, level, session_id, action, metadata, created_at
    ) VALUES (
      'db-trigger',
      'warn',
      NEW.session_id,
      'session_locked_brute_force',
      jsonb_build_object(
        'failed_attempts', NEW.failed_validation_count,
        'locked_at', NOW()
      ),
      NOW()
    );
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_session_lockout ON patient_sessions;
CREATE TRIGGER trg_session_lockout
  BEFORE UPDATE OF failed_validation_count ON patient_sessions
  FOR EACH ROW
  EXECUTE FUNCTION public.check_session_lockout();

-- =====================================================
-- STEP 6: Token validation function
-- =====================================================

CREATE OR REPLACE FUNCTION public.validate_session_token(
  p_session_id  UUID,
  p_token_hash  TEXT
)
RETURNS TABLE(
  is_valid    BOOLEAN,
  session_id  UUID,
  is_active   BOOLEAN,
  is_locked   BOOLEAN,
  expires_at  TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_session patient_sessions%ROWTYPE;
BEGIN
  SELECT * INTO v_session
  FROM patient_sessions
  WHERE patient_sessions.session_id = p_session_id;

  IF NOT FOUND THEN
    RETURN QUERY SELECT FALSE, NULL::UUID, FALSE, FALSE, NULL::TIMESTAMPTZ;
    RETURN;
  END IF;

  IF v_session.is_locked THEN
    RETURN QUERY SELECT FALSE, v_session.session_id, FALSE, TRUE, v_session.expires_at;
    RETURN;
  END IF;

  IF v_session.expires_at < NOW() THEN
    UPDATE patient_sessions
    SET is_active = FALSE
    WHERE patient_sessions.session_id = p_session_id;
    RETURN QUERY SELECT FALSE, v_session.session_id, FALSE, FALSE, v_session.expires_at;
    RETURN;
  END IF;

  IF v_session.session_token_hash = p_token_hash THEN
    UPDATE patient_sessions
    SET
      failed_validation_count = 0,
      last_seen_at = NOW()
    WHERE patient_sessions.session_id = p_session_id;
    RETURN QUERY SELECT TRUE, v_session.session_id, TRUE, FALSE, v_session.expires_at;
  ELSE
    UPDATE patient_sessions
    SET
      failed_validation_count = failed_validation_count + 1,
      last_failed_at = NOW()
    WHERE patient_sessions.session_id = p_session_id;
    RETURN QUERY SELECT FALSE, v_session.session_id, v_session.is_active, FALSE, v_session.expires_at;
  END IF;
END;
$$;

-- =====================================================
-- STEP 7: Manual cleanup function
-- Call this instead of pg_cron:
-- SELECT public.cleanup_expired_sessions();
-- =====================================================

CREATE OR REPLACE FUNCTION public.cleanup_expired_sessions()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  deleted_count INTEGER;
BEGIN
  DELETE FROM patient_sessions
  WHERE expires_at < NOW()
  AND is_active = FALSE;

  GET DIAGNOSTICS deleted_count = ROW_COUNT;

  INSERT INTO admin_logs (function_name, level, action, metadata, created_at)
  VALUES (
    'cleanup_expired_sessions',
    'info',
    'sessions_cleaned',
    jsonb_build_object('deleted_count', deleted_count),
    NOW()
  );

  RETURN deleted_count;
END;
$$;

-- =====================================================
-- STEP 8: Suspicious sessions view
-- =====================================================

CREATE OR REPLACE VIEW public.suspicious_sessions AS
SELECT
  session_id,
  failed_validation_count,
  last_failed_at,
  is_locked,
  locked_at,
  ip_address,
  created_at
FROM patient_sessions
WHERE
  failed_validation_count >= 3
  OR is_locked = TRUE
ORDER BY failed_validation_count DESC, last_failed_at DESC;

-- =====================================================
-- STEP 9: Permissions
-- =====================================================

GRANT EXECUTE ON FUNCTION public.validate_session_token TO service_role;
GRANT EXECUTE ON FUNCTION public.validate_session_token TO authenticated;
GRANT EXECUTE ON FUNCTION public.cleanup_expired_sessions TO service_role;
GRANT SELECT ON public.suspicious_sessions TO authenticated;

COMMIT;

-- =====================================================
-- VERIFICATION
-- =====================================================

SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'patient_sessions'
AND column_name IN (
  'refresh_token_hash', 'refresh_expires_at', 'is_legacy',
  'is_migrated', 'is_locked', 'failed_validation_count',
  'ip_address', 'fcm_token', 'device_info', 'payment_env'
)
ORDER BY column_name;

SELECT trigger_name, event_object_table
FROM information_schema.triggers
WHERE trigger_schema = 'public'
AND trigger_name IN ('trg_deactivate_expired', 'trg_session_lockout');

SELECT routine_name
FROM information_schema.routines
WHERE routine_schema = 'public'
AND routine_name IN (
  'validate_session_token',
  'cleanup_expired_sessions',
  'deactivate_expired_sessions',
  'check_session_lockout'
);