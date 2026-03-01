-- 3.2: Fix consultation_requests RLS
-- Previously: "Patients can view own requests" had USING(true) — any user saw all requests.
--             "Service role full access" had USING(true) — applied to ALL roles, not just service_role.
-- Service role bypasses RLS entirely, so it never needs a policy.

-- Drop broken policies
DROP POLICY IF EXISTS "Patients can view own requests" ON consultation_requests;
DROP POLICY IF EXISTS "Service role full access" ON consultation_requests;

-- Doctors can see requests addressed to them (already exists but recreate for safety)
DROP POLICY IF EXISTS "Doctors can view their requests" ON consultation_requests;
CREATE POLICY "Doctors can view their requests"
    ON consultation_requests FOR SELECT
    TO authenticated
    USING (doctor_id = auth.uid());

-- Doctors can update requests addressed to them (accept/reject)
CREATE POLICY "Doctors can update their requests"
    ON consultation_requests FOR UPDATE
    TO authenticated
    USING (doctor_id = auth.uid())
    WITH CHECK (doctor_id = auth.uid());
