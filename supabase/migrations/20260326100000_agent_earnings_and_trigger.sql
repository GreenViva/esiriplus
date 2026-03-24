-- Agent earnings system: 10% commission on consultations initiated by agents.

-- ── 1. Create agent_earnings table ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_earnings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID NOT NULL REFERENCES auth.users(id),
    consultation_id UUID NOT NULL REFERENCES consultations(consultation_id),
    amount INTEGER NOT NULL CHECK (amount > 0),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(consultation_id)
);

ALTER TABLE agent_earnings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "agents_read_own_earnings"
    ON agent_earnings FOR SELECT
    USING (agent_id = auth.uid());

CREATE POLICY "service_role_full_access_agent_earnings"
    ON agent_earnings FOR ALL
    USING (true) WITH CHECK (true);

CREATE TRIGGER trg_agent_earnings_updated_at
    BEFORE UPDATE ON agent_earnings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── 2. Add agent_id to consultation_requests ─────────────────────────────────
ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS agent_id UUID REFERENCES auth.users(id);

-- ── 3. Trigger: auto-create agent earnings on consultation completion ────────
CREATE OR REPLACE FUNCTION fn_auto_create_agent_earning()
RETURNS trigger AS $$
DECLARE
    v_pct INTEGER;
    v_amt INTEGER;
BEGIN
    IF NEW.status <> 'completed' OR OLD.status = 'completed' THEN
        RETURN NEW;
    END IF;
    IF NEW.agent_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NEW.consultation_fee <= 0 THEN
        RETURN NEW;
    END IF;

    v_pct := COALESCE(
        (SELECT config_value::INTEGER FROM app_config WHERE config_key = 'agent_commission_pct'),
        10
    );
    v_amt := FLOOR(NEW.consultation_fee * v_pct / 100.0);

    IF v_amt <= 0 THEN
        RETURN NEW;
    END IF;

    INSERT INTO agent_earnings (agent_id, consultation_id, amount, status)
    VALUES (NEW.agent_id, NEW.consultation_id, v_amt, 'pending')
    ON CONFLICT DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_auto_create_agent_earning ON consultations;
CREATE TRIGGER trg_auto_create_agent_earning
    AFTER UPDATE ON consultations
    FOR EACH ROW
    EXECUTE FUNCTION fn_auto_create_agent_earning();

-- ── 4. Update create_consultation_with_cleanup RPC to accept agent_id ────────
CREATE OR REPLACE FUNCTION create_consultation_with_cleanup(
    p_patient_session_id text,
    p_doctor_id uuid,
    p_service_type text,
    p_consultation_type text DEFAULT 'chat',
    p_chief_complaint text DEFAULT '',
    p_consultation_fee numeric DEFAULT 5000,
    p_request_expires_at timestamptz DEFAULT NULL,
    p_request_id uuid DEFAULT NULL,
    p_service_tier text DEFAULT 'ECONOMY',
    p_parent_consultation_id uuid DEFAULT NULL,
    p_agent_id uuid DEFAULT NULL
)
RETURNS SETOF consultations
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    closed_count integer;
    v_duration SMALLINT;
BEGIN
    v_duration := get_service_duration_minutes(p_service_type);

    UPDATE consultations
    SET status = 'completed'::consultation_status_enum,
        updated_at = now()
    WHERE patient_session_id = p_patient_session_id::uuid
      AND status NOT IN ('completed'::consultation_status_enum);

    GET DIAGNOSTICS closed_count = ROW_COUNT;

    RETURN QUERY
    INSERT INTO consultations (
        patient_session_id, doctor_id, service_type, service_tier,
        consultation_type, chief_complaint, status, consultation_fee,
        request_expires_at, request_id, session_start_time, scheduled_end_at,
        original_duration_minutes, extension_count,
        parent_consultation_id, agent_id,
        created_at, updated_at
    ) VALUES (
        p_patient_session_id::uuid, p_doctor_id,
        p_service_type::service_type_enum, p_service_tier,
        p_consultation_type, p_chief_complaint,
        'active'::consultation_status_enum, p_consultation_fee,
        p_request_expires_at, p_request_id,
        now(), now() + (v_duration || ' minutes')::interval,
        v_duration, 0,
        p_parent_consultation_id, p_agent_id,
        now(), now()
    )
    RETURNING *;
END;
$$;

-- ── 5. Seed commission config ────────────────────────────────────────────────
INSERT INTO app_config (config_key, config_value, description)
VALUES ('agent_commission_pct', '10', 'Agent commission percentage on consultation fees')
ON CONFLICT (config_key) DO NOTHING;
