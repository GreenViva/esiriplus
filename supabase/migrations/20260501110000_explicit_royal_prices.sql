-- ============================================================================
-- EXPLICIT ROYAL PRICES (drop the 10× multiplier)
--
-- Until now Royal price was computed as basePrice × 10 in PricingEngine.kt
-- and create-consultation/index.ts. That mapped Royal nurse to 30,000 TZS,
-- which was way underpriced for the premium positioning. From 2026-05-01,
-- each service has an explicit Royal price stored in app_config.
--
-- This migration only writes the data. The PricingEngine and the edge
-- function still need to be updated to consult these keys instead of
-- multiplying — that's a follow-up code change.
--
-- Also fills in two Economy prices that existed client-side but had no
-- app_config row: herbalist and drug_interaction (5,000 each, matches
-- DatabaseCallback.kt + ServicesViewModel.kt seed data).
-- ============================================================================

-- ── Economy (price_<service>) ───────────────────────────────────────────────
-- Existing rows are unchanged. The two below were missing.

INSERT INTO app_config (config_key, config_value, description) VALUES
    ('price_herbalist',         '5000',  'Herbalist consultation price (TZS)'),
    ('price_drug_interaction',  '5000',  'Drug interaction check price (TZS)')
ON CONFLICT (config_key) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description  = EXCLUDED.description;

-- ── Royal (royal_price_<service>) ───────────────────────────────────────────

INSERT INTO app_config (config_key, config_value, description) VALUES
    ('royal_price_nurse',             '322000', 'Royal nurse consultation price (TZS)'),
    ('royal_price_pharmacist',        '322000', 'Royal pharmacist consultation price (TZS)'),
    ('royal_price_clinical_officer',  '350000', 'Royal clinical officer consultation price (TZS)'),
    ('royal_price_gp',                '420000', 'Royal GP consultation price (TZS)'),
    ('royal_price_specialist',        '700000', 'Royal specialist consultation price (TZS)'),
    ('royal_price_psychologist',      '980000', 'Royal psychologist consultation price (TZS)'),
    ('royal_price_herbalist',         '350000', 'Royal herbalist consultation price (TZS)'),
    ('royal_price_drug_interaction',  '350000', 'Royal drug interaction check price (TZS)')
ON CONFLICT (config_key) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description  = EXCLUDED.description;
