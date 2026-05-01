-- ============================================================================
-- DRUG INTERACTION as a standalone specialty
--
-- Until now, drug_interaction existed as a service category in the patient
-- app (Room service_tiers, app_config price keys) but the Postgres
-- service_type_enum was missing the value, so:
--   - no doctor could register with specialty="Drug Interaction"
--   - no consultation row could be created with service_type='drug_interaction'
--
-- This migration adds the enum value. The companion changes ship in:
--   - supabase/functions/register-doctor/index.ts  (SPECIALTY_TO_ENUM)
--   - feature/auth/.../DoctorRegistrationScreen.kt (Specialties + services)
-- ============================================================================

ALTER TYPE service_type_enum ADD VALUE IF NOT EXISTS 'drug_interaction';
