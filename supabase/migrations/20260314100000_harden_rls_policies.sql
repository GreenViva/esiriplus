-- Security hardening: Remove overly permissive RLS policies.
--
-- risk_flags and doctor_online_log had "FOR ALL USING(true)" policies,
-- meaning ANY authenticated user (including patients and doctors) could
-- INSERT, UPDATE, or DELETE records — e.g., a doctor could delete their
-- own warning/suspension flags.
--
-- After this migration:
--   - Portal admins can still SELECT (via policies from 20260307200000)
--   - Edge functions and triggers use service_role (bypasses RLS entirely)
--   - No authenticated user can write to these tables directly
--
-- Also adds explicit deny-all policies on tables that should only be
-- accessed via service_role (defense-in-depth).

-- ─── 1. Fix risk_flags ──────────────────────────────────────────────────────────
-- Drop the policy that allowed ANY authenticated user full CRUD access.
-- The portal SELECT policy from 20260307200000 remains active for admin reads.
-- All writes come through edge functions (service_role) or SECURITY DEFINER triggers.
DROP POLICY IF EXISTS risk_flags_service_all ON risk_flags;

-- ─── 2. Fix doctor_online_log ───────────────────────────────────────────────────
-- Same issue. Writes come through the log-doctor-online edge function (service_role).
DROP POLICY IF EXISTS online_log_service_all ON doctor_online_log;

-- ─── 3. Explicit deny on email_verifications ────────────────────────────────────
-- Table has RLS enabled but no policies (empty = implicit deny).
-- Adding explicit deny as defense-in-depth so a future migration can't
-- accidentally add a permissive policy without noticing.
-- Only edge functions (send-doctor-otp, verify-doctor-otp) access via service_role.
DO $$
BEGIN
    CREATE POLICY "Deny all client access to email_verifications"
        ON email_verifications FOR ALL
        TO authenticated
        USING (false)
        WITH CHECK (false);
EXCEPTION WHEN duplicate_object THEN
    RAISE NOTICE 'email_verifications deny policy already exists';
END $$;

-- ─── 4. Explicit deny on performance_metrics ────────────────────────────────────
-- Writes come via log-performance-metrics edge function (service_role).
-- Reads come via get_performance_stats RPC function (SECURITY DEFINER).
-- No direct client access needed.
DO $$
BEGIN
    CREATE POLICY "Deny all client access to performance_metrics"
        ON performance_metrics FOR ALL
        TO authenticated
        USING (false)
        WITH CHECK (false);
EXCEPTION WHEN duplicate_object THEN
    RAISE NOTICE 'performance_metrics deny policy already exists';
END $$;
