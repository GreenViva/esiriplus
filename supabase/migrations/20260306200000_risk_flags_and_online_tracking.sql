-- Migration: Risk flags table, doctor online time tracking, and auto-flagging.

-- ─── 1. Risk flags table ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS risk_flags (
  flag_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id UUID NOT NULL REFERENCES doctor_profiles(doctor_id) ON DELETE CASCADE,
  flag_type TEXT NOT NULL,          -- 'hr_warning', 'hr_suspension', 'hr_ban', 'low_online_time'
  severity TEXT NOT NULL DEFAULT 'medium',  -- 'low', 'medium', 'high'
  title TEXT NOT NULL,
  description TEXT,
  is_resolved BOOLEAN NOT NULL DEFAULT false,
  resolved_by UUID,
  resolved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_risk_flags_doctor ON risk_flags(doctor_id);
CREATE INDEX IF NOT EXISTS idx_risk_flags_open ON risk_flags(is_resolved, created_at DESC);

-- RLS: only service role can read/write (accessed via admin panel's service client)
ALTER TABLE risk_flags ENABLE ROW LEVEL SECURITY;

CREATE POLICY risk_flags_service_all ON risk_flags
  FOR ALL USING (true) WITH CHECK (true);

-- ─── 2. Doctor online log table ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS doctor_online_log (
  log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id UUID NOT NULL REFERENCES doctor_profiles(doctor_id) ON DELETE CASCADE,
  went_online_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  went_offline_at TIMESTAMPTZ,
  duration_minutes INTEGER
);

CREATE INDEX IF NOT EXISTS idx_online_log_doctor_date
  ON doctor_online_log(doctor_id, went_online_at DESC);

ALTER TABLE doctor_online_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY online_log_service_all ON doctor_online_log
  FOR ALL USING (true) WITH CHECK (true);

-- ─── 3. Trigger: auto-create risk flags from HR admin actions ─────────────────
CREATE OR REPLACE FUNCTION fn_risk_flag_from_admin_log()
RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
  v_flag_type TEXT;
  v_severity TEXT;
  v_title TEXT;
  v_description TEXT;
  v_doctor_id UUID;
  v_details JSONB;
BEGIN
  -- Only handle doctor governance actions
  IF NEW.action NOT IN ('warn_doctor', 'suspend_doctor', 'ban_doctor') THEN
    RETURN NEW;
  END IF;

  -- Target ID is the doctor_id
  v_doctor_id := NEW.target_id::UUID;
  v_details := COALESCE(NEW.details, '{}'::JSONB);

  CASE NEW.action
    WHEN 'warn_doctor' THEN
      v_flag_type := 'hr_warning';
      v_severity := 'medium';
      v_title := 'Doctor warned by HR';
      v_description := COALESCE(v_details->>'message', 'Warning issued');
    WHEN 'suspend_doctor' THEN
      v_flag_type := 'hr_suspension';
      v_severity := 'high';
      v_title := 'Doctor suspended by HR';
      v_description := COALESCE(
        v_details->>'reason',
        'Suspended for ' || COALESCE(v_details->>'days', '?') || ' days'
      );
    WHEN 'ban_doctor' THEN
      v_flag_type := 'hr_ban';
      v_severity := 'high';
      v_title := 'Doctor banned by HR';
      v_description := COALESCE(v_details->>'reason', 'Permanently banned');
  END CASE;

  INSERT INTO risk_flags (doctor_id, flag_type, severity, title, description)
  VALUES (v_doctor_id, v_flag_type, v_severity, v_title, v_description);

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_risk_flag_from_admin_log ON admin_logs;
CREATE TRIGGER trg_risk_flag_from_admin_log
  AFTER INSERT ON admin_logs
  FOR EACH ROW
  WHEN (NEW.action IN ('warn_doctor', 'suspend_doctor', 'ban_doctor'))
  EXECUTE FUNCTION fn_risk_flag_from_admin_log();

-- ─── 4. Daily cron: flag doctors with < 4h online time ────────────────────────
CREATE OR REPLACE FUNCTION fn_flag_low_online_doctors()
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
  v_yesterday DATE := (now() AT TIME ZONE 'Africa/Dar_es_Salaam')::DATE - 1;
  v_start TIMESTAMPTZ := v_yesterday::TIMESTAMPTZ;
  v_end TIMESTAMPTZ := (v_yesterday + 1)::TIMESTAMPTZ;
  v_count INTEGER := 0;
BEGIN
  -- For each verified, non-banned, non-suspended doctor:
  -- check if they were online >= 240 minutes yesterday.
  -- If not, create a low_online_time risk flag.
  INSERT INTO risk_flags (doctor_id, flag_type, severity, title, description)
  SELECT
    dp.doctor_id,
    'low_online_time',
    'low',
    'Low online time',
    'Doctor was online for ' ||
      COALESCE(online.total_minutes, 0) ||
      ' minutes on ' || v_yesterday::TEXT ||
      ' (minimum: 240 minutes)'
  FROM doctor_profiles dp
  LEFT JOIN (
    SELECT
      doctor_id,
      COALESCE(SUM(
        EXTRACT(EPOCH FROM (
          LEAST(COALESCE(went_offline_at, now()), v_end) -
          GREATEST(went_online_at, v_start)
        )) / 60.0
      ), 0)::INTEGER AS total_minutes
    FROM doctor_online_log
    WHERE went_online_at < v_end
      AND (went_offline_at IS NULL OR went_offline_at > v_start)
    GROUP BY doctor_id
  ) online ON online.doctor_id = dp.doctor_id
  WHERE dp.is_verified = true
    AND dp.is_banned = false
    AND (dp.suspended_until IS NULL OR dp.suspended_until < now())
    AND COALESCE(online.total_minutes, 0) < 240
    -- Don't double-flag for the same day
    AND NOT EXISTS (
      SELECT 1 FROM risk_flags rf
      WHERE rf.doctor_id = dp.doctor_id
        AND rf.flag_type = 'low_online_time'
        AND rf.created_at >= v_start
        AND rf.created_at < v_end
    );

  GET DIAGNOSTICS v_count = ROW_COUNT;

  IF v_count > 0 THEN
    RAISE LOG 'fn_flag_low_online_doctors: flagged % doctors for low online time on %',
      v_count, v_yesterday;
  END IF;
END;
$$;

-- Schedule: run daily at 00:05 EAT (21:05 UTC)
DO $$
BEGIN
  PERFORM cron.unschedule('flag-low-online-doctors');
EXCEPTION WHEN OTHERS THEN
  NULL;
END;
$$;

SELECT cron.schedule(
  'flag-low-online-doctors',
  '5 21 * * *',
  $$SELECT fn_flag_low_online_doctors()$$
);

-- ─── 5. Backfill: create risk flags for existing warned/suspended/banned doctors
INSERT INTO risk_flags (doctor_id, flag_type, severity, title, description, created_at)
SELECT
  al.target_id::UUID,
  CASE al.action
    WHEN 'warn_doctor' THEN 'hr_warning'
    WHEN 'suspend_doctor' THEN 'hr_suspension'
    WHEN 'ban_doctor' THEN 'hr_ban'
  END,
  CASE al.action
    WHEN 'warn_doctor' THEN 'medium'
    ELSE 'high'
  END,
  CASE al.action
    WHEN 'warn_doctor' THEN 'Doctor warned by HR'
    WHEN 'suspend_doctor' THEN 'Doctor suspended by HR'
    WHEN 'ban_doctor' THEN 'Doctor banned by HR'
  END,
  COALESCE(
    al.details->>'message',
    al.details->>'reason',
    al.action
  ),
  al.created_at
FROM admin_logs al
WHERE al.action IN ('warn_doctor', 'suspend_doctor', 'ban_doctor')
  AND al.target_id IS NOT NULL
ON CONFLICT DO NOTHING;
