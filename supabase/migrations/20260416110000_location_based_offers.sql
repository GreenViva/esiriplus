-- ============================================================================
-- Location-based offers
--
-- Admins can create offers targeting patients in a specific district (and
-- optionally ward). When a patient books a new consultation whose requested
-- service_district matches an active offer — and whose tier/service_type is
-- covered — the configured discount is applied (free / percent / fixed) and a
-- redemption row is recorded. A patient may redeem a given offer at most once.
--
-- Terminating an offer is a single UPDATE (is_active = false). The effect is
-- immediate on the next booking; no background job, no scheduled expiry.
-- ============================================================================

-- ── 1. Add requested-location columns to consultations + requests ────────────
-- service_region already assumed in clients (default 'TANZANIA'); add here so
-- the server can actually persist it. district/ward are nullable because older
-- rows won't have them and international bookings don't need them.

ALTER TABLE consultations
    ADD COLUMN IF NOT EXISTS service_region   TEXT NOT NULL DEFAULT 'TANZANIA',
    ADD COLUMN IF NOT EXISTS service_district TEXT,
    ADD COLUMN IF NOT EXISTS service_ward     TEXT;

ALTER TABLE consultation_requests
    ADD COLUMN IF NOT EXISTS service_region   TEXT NOT NULL DEFAULT 'TANZANIA',
    ADD COLUMN IF NOT EXISTS service_district TEXT,
    ADD COLUMN IF NOT EXISTS service_ward     TEXT;

CREATE INDEX IF NOT EXISTS idx_consultations_service_district
    ON consultations (service_district)
    WHERE service_district IS NOT NULL;

-- ── 2. location_offers ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS location_offers (
    offer_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    title           TEXT        NOT NULL,
    description     TEXT,
    region          TEXT        NOT NULL DEFAULT 'TANZANIA',
    district        TEXT        NOT NULL,
    ward            TEXT,                                  -- NULL = whole district
    service_types   TEXT[]      NOT NULL DEFAULT '{}',     -- empty = all service types
    tiers           TEXT[]      NOT NULL DEFAULT '{}',     -- empty = all tiers
    discount_type   TEXT        NOT NULL
                    CHECK (discount_type IN ('free', 'percent', 'fixed')),
    discount_value  INTEGER     NOT NULL DEFAULT 0,        -- ignored if 'free'
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by      UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    terminated_at   TIMESTAMPTZ,

    CONSTRAINT location_offers_percent_range
        CHECK (discount_type <> 'percent' OR (discount_value BETWEEN 1 AND 100)),
    CONSTRAINT location_offers_fixed_positive
        CHECK (discount_type <> 'fixed' OR discount_value > 0)
);

CREATE INDEX IF NOT EXISTS idx_location_offers_active_district
    ON location_offers (district, ward)
    WHERE is_active = TRUE;

-- ── 3. location_offer_redemptions ────────────────────────────────────────────
-- Single redemption per (offer, patient). patient_session_id is used because
-- that's the stable patient identity in this schema (see consultations table).
CREATE TABLE IF NOT EXISTS location_offer_redemptions (
    redemption_id       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id            UUID        NOT NULL REFERENCES location_offers(offer_id) ON DELETE CASCADE,
    patient_session_id  UUID        NOT NULL,
    consultation_id     UUID        REFERENCES consultations(consultation_id) ON DELETE SET NULL,
    original_price      INTEGER     NOT NULL,
    discounted_price    INTEGER     NOT NULL,
    redeemed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT location_offer_redemptions_unique_per_patient
        UNIQUE (offer_id, patient_session_id)
);

CREATE INDEX IF NOT EXISTS idx_offer_redemptions_patient
    ON location_offer_redemptions (patient_session_id);

-- ── 4. Helper: find the best active offer for a booking ──────────────────────
-- Returns the offer that (a) matches district (+ ward if offer specifies),
-- (b) covers the service_type (or applies to all), (c) covers the tier (or
-- applies to all), and (d) has not already been redeemed by the patient.
-- Ward-specific offers are preferred over district-wide ones.
CREATE OR REPLACE FUNCTION match_location_offer(
    p_patient_session_id UUID,
    p_district           TEXT,
    p_ward               TEXT,
    p_service_type       TEXT,
    p_tier               TEXT
)
RETURNS location_offers
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
AS $$
DECLARE
    v_offer location_offers;
BEGIN
    IF p_district IS NULL OR length(trim(p_district)) = 0 THEN
        RETURN NULL;
    END IF;

    SELECT o.*
      INTO v_offer
      FROM location_offers o
     WHERE o.is_active = TRUE
       AND lower(o.district) = lower(p_district)
       AND (o.ward IS NULL OR (p_ward IS NOT NULL AND lower(o.ward) = lower(p_ward)))
       AND (cardinality(o.service_types) = 0 OR p_service_type = ANY(o.service_types))
       AND (cardinality(o.tiers)         = 0 OR p_tier         = ANY(o.tiers))
       AND NOT EXISTS (
           SELECT 1 FROM location_offer_redemptions r
            WHERE r.offer_id = o.offer_id
              AND r.patient_session_id = p_patient_session_id
       )
     ORDER BY (o.ward IS NOT NULL) DESC,  -- prefer ward-specific
              o.created_at DESC
     LIMIT 1;

    RETURN v_offer;
END;
$$;

-- ── 4b. Helper: list all active offers the patient could apply ──────────────
-- Used by the patient UI to preview "30% off for your area" etc. on the
-- services screen. Returns every non-redeemed active offer for the patient's
-- district+tier so the client can decide which services a given offer covers.
CREATE OR REPLACE FUNCTION list_active_offers_for_patient(
    p_patient_session_id UUID,
    p_district           TEXT,
    p_ward               TEXT,
    p_tier               TEXT
)
RETURNS SETOF location_offers
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    SELECT o.*
      FROM location_offers o
     WHERE o.is_active = TRUE
       AND p_district IS NOT NULL
       AND lower(o.district) = lower(p_district)
       AND (o.ward IS NULL OR (p_ward IS NOT NULL AND lower(o.ward) = lower(p_ward)))
       AND (cardinality(o.tiers) = 0 OR p_tier = ANY(o.tiers))
       AND NOT EXISTS (
           SELECT 1 FROM location_offer_redemptions r
            WHERE r.offer_id = o.offer_id
              AND r.patient_session_id = p_patient_session_id
       )
     ORDER BY (o.ward IS NOT NULL) DESC, o.created_at DESC;
$$;

-- ── 5. updated_at trigger ────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION touch_location_offers_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    IF NEW.is_active = FALSE AND OLD.is_active = TRUE AND NEW.terminated_at IS NULL THEN
        NEW.terminated_at := now();
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_location_offers_updated_at ON location_offers;
CREATE TRIGGER trg_location_offers_updated_at
    BEFORE UPDATE ON location_offers
    FOR EACH ROW EXECUTE FUNCTION touch_location_offers_updated_at();

-- ── 6. RLS ───────────────────────────────────────────────────────────────────
ALTER TABLE location_offers             ENABLE ROW LEVEL SECURITY;
ALTER TABLE location_offer_redemptions  ENABLE ROW LEVEL SECURITY;

-- Admins: full CRUD on offers
DROP POLICY IF EXISTS location_offers_admin_all ON location_offers;
CREATE POLICY location_offers_admin_all ON location_offers
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM user_roles ur
             WHERE ur.user_id = auth.uid()
               AND ur.role_name IN ('admin')
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM user_roles ur
             WHERE ur.user_id = auth.uid()
               AND ur.role_name IN ('admin')
        )
    );

-- Authenticated users (incl. patient sessions) can read active offers
DROP POLICY IF EXISTS location_offers_read_active ON location_offers;
CREATE POLICY location_offers_read_active ON location_offers
    FOR SELECT
    USING (is_active = TRUE);

-- Redemptions: admins read all; service role handles inserts via edge function.
DROP POLICY IF EXISTS location_offer_redemptions_admin_read ON location_offer_redemptions;
CREATE POLICY location_offer_redemptions_admin_read ON location_offer_redemptions
    FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM user_roles ur
             WHERE ur.user_id = auth.uid()
               AND ur.role_name IN ('admin', 'finance', 'audit')
        )
    );

-- ── 7. Grants ────────────────────────────────────────────────────────────────
GRANT SELECT ON location_offers TO authenticated, anon;
GRANT ALL    ON location_offers TO service_role;
GRANT ALL    ON location_offer_redemptions TO service_role;
GRANT SELECT ON location_offer_redemptions TO authenticated;
GRANT EXECUTE ON FUNCTION match_location_offer(UUID, TEXT, TEXT, TEXT, TEXT) TO service_role, authenticated;
GRANT EXECUTE ON FUNCTION list_active_offers_for_patient(UUID, TEXT, TEXT, TEXT) TO service_role, authenticated;
