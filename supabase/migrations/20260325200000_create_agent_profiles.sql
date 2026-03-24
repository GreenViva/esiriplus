-- Create agent_profiles table for eSIRIPlus Agents feature.
-- Agents are intermediaries who help patients access the platform.
-- They authenticate via Supabase Auth (like doctors) with email/password.

CREATE TABLE IF NOT EXISTS agent_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    mobile_number TEXT NOT NULL,
    email TEXT NOT NULL,
    place_of_residence TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(agent_id),
    UNIQUE(email)
);

-- Add agent_id to consultations (nullable; null for normal patient consultations)
ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS agent_id UUID REFERENCES auth.users(id);

-- Add agent role to user_roles
-- (user_roles already supports any role_name text, no enum change needed)

-- RLS
ALTER TABLE agent_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "agents_read_own_profile"
    ON agent_profiles FOR SELECT
    USING (agent_id = auth.uid());

CREATE POLICY "service_role_full_access_agents"
    ON agent_profiles FOR ALL
    USING (true)
    WITH CHECK (true);

-- Updated_at trigger
CREATE TRIGGER trg_agent_profiles_updated_at
    BEFORE UPDATE ON agent_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
