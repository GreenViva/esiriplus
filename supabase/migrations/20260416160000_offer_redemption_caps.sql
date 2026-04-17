-- ============================================================================
-- Optional redemption caps on location offers.
--
-- Admins can set `max_redemptions` to limit an offer to the first N patients;
-- once that many redemptions are recorded, the offer auto-terminates
-- (is_active = FALSE). If max_redemptions is NULL the offer is unlimited
-- (existing behaviour).
--
-- The enforce-and-increment trigger is atomic per offer row (FOR UPDATE lock)
-- so two patients racing for the last slot cannot both succeed.
-- ============================================================================

-- 1. Columns -----------------------------------------------------------------
ALTER TABLE location_offers
    ADD COLUMN IF NOT EXISTS max_redemptions  INTEGER,
    ADD COLUMN IF NOT EXISTS redemption_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE location_offers
    DROP CONSTRAINT IF EXISTS location_offers_max_redemptions_positive;
ALTER TABLE location_offers
    ADD CONSTRAINT location_offers_max_redemptions_positive
    CHECK (max_redemptions IS NULL OR max_redemptions > 0);

-- 2. Backfill redemption_count from existing rows ---------------------------
-- Idempotent: resets count to the actual number of redemption rows.
UPDATE location_offers o
   SET redemption_count = (
       SELECT COUNT(*) FROM location_offer_redemptions r
        WHERE r.offer_id = o.offer_id
   );

-- 3. Enforce-cap-and-increment trigger --------------------------------------
CREATE OR REPLACE FUNCTION enforce_offer_cap()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_max    INTEGER;
    v_count  INTEGER;
    v_active BOOLEAN;
BEGIN
    -- Lock the offer row so concurrent inserts serialise
    SELECT max_redemptions, redemption_count, is_active
      INTO v_max, v_count, v_active
      FROM location_offers
     WHERE offer_id = NEW.offer_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Offer % does not exist', NEW.offer_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF NOT v_active THEN
        RAISE EXCEPTION 'Offer % is not active', NEW.offer_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_max IS NOT NULL AND v_count >= v_max THEN
        RAISE EXCEPTION 'Offer % redemption cap reached', NEW.offer_id
            USING ERRCODE = 'check_violation';
    END IF;

    -- Increment, and auto-terminate if this insert fills the cap
    IF v_max IS NOT NULL AND v_count + 1 >= v_max THEN
        UPDATE location_offers
           SET redemption_count = v_count + 1,
               is_active        = FALSE,
               terminated_at    = COALESCE(terminated_at, now())
         WHERE offer_id = NEW.offer_id;
    ELSE
        UPDATE location_offers
           SET redemption_count = v_count + 1
         WHERE offer_id = NEW.offer_id;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_offer_cap ON location_offer_redemptions;
CREATE TRIGGER trg_enforce_offer_cap
    BEFORE INSERT ON location_offer_redemptions
    FOR EACH ROW
    EXECUTE FUNCTION enforce_offer_cap();
