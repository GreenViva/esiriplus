-- Comprehensive RLS fix: ensure user_roles has proper policies and all
-- portal-accessible tables have correct SELECT policies.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. Fix user_roles RLS (the table was recreated without policies)
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;

-- Users can read their own roles (needed for login flow)
DO $$ BEGIN
  CREATE POLICY "Users can read own roles"
    ON public.user_roles FOR SELECT
    TO authenticated
    USING (user_id = auth.uid());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Service role / portal insert (edge functions use service role, so this is
-- just a safety net for authenticated admin inserts)
DO $$ BEGIN
  CREATE POLICY "Service insert on user_roles"
    ON public.user_roles FOR INSERT
    TO authenticated
    WITH CHECK (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Service delete
DO $$ BEGIN
  CREATE POLICY "Service delete on user_roles"
    ON public.user_roles FOR DELETE
    TO authenticated
    USING (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. Helper function: is_portal_user() — avoids recursive RLS on user_roles
--    by using SECURITY DEFINER (runs as table owner, bypasses RLS)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION public.is_portal_user()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.user_roles
    WHERE user_id = auth.uid()
    AND role_name IN ('admin', 'hr', 'finance', 'audit')
  );
$$;

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. Ensure doctor_profiles readable by authenticated users
-- ═══════════════════════════════════════════════════════════════════════════

DO $$ BEGIN
  CREATE POLICY "Authenticated users can read doctor profiles"
    ON public.doctor_profiles FOR SELECT
    TO authenticated
    USING (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. Portal SELECT policies for all admin-panel tables (using is_portal_user)
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
DECLARE
  _tbl text;
  _tables text[] := ARRAY[
    'admin_logs',
    'risk_flags',
    'doctor_ratings',
    'doctor_device_bindings',
    'consultations',
    'diagnoses',
    'patient_reports',
    'patient_sessions',
    'recovery_attempts',
    'service_access_payments',
    'doctor_earnings',
    'payments',
    'user_roles',
    'call_recharge_payments',
    'doctor_online_log',
    'prescriptions',
    'vital_signs',
    'video_calls',
    'consultation_requests',
    'appointments',
    'doctor_availability_slots',
    'agent_profiles',
    'agent_earnings'
  ];
BEGIN
  FOREACH _tbl IN ARRAY _tables LOOP
    -- Skip if table doesn't exist
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = _tbl
    ) THEN
      RAISE NOTICE 'Skipping %: table does not exist', _tbl;
      CONTINUE;
    END IF;

    -- Enable RLS
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', _tbl);

    -- Create SELECT policy using is_portal_user() to avoid recursion
    BEGIN
      EXECUTE format(
        'CREATE POLICY "Portal users can read %1$s"
           ON public.%1$I FOR SELECT
           TO authenticated
           USING (public.is_portal_user())',
        _tbl
      );
    EXCEPTION WHEN duplicate_object THEN
      -- Policy exists — drop and recreate to use is_portal_user()
      EXECUTE format(
        'DROP POLICY "Portal users can read %1$s" ON public.%1$I', _tbl
      );
      EXECUTE format(
        'CREATE POLICY "Portal users can read %1$s"
           ON public.%1$I FOR SELECT
           TO authenticated
           USING (public.is_portal_user())',
        _tbl
      );
    END;
  END LOOP;
END $$;

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. Ensure admin_logs has all required columns
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS function_name TEXT;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS level TEXT;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS session_id TEXT;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS target_type TEXT;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS target_id TEXT;
ALTER TABLE public.admin_logs ADD COLUMN IF NOT EXISTS details JSONB;

-- ═══════════════════════════════════════════════════════════════════════════
-- 6. Ensure doctor_profiles has all columns the edge functions expect
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS verification_status TEXT DEFAULT 'pending';
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS warning_message TEXT;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS warning_at TIMESTAMPTZ;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT false;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS banned_at TIMESTAMPTZ;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS ban_reason TEXT;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMPTZ;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS suspension_reason TEXT;
ALTER TABLE public.doctor_profiles ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 7. Reload PostgREST schema cache
-- ═══════════════════════════════════════════════════════════════════════════

NOTIFY pgrst, 'reload schema';
