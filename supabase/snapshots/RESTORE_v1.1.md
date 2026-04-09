# Restore to Stable v1.1 (2026-04-09)

## 1. Restore code
```bash
git reset --hard stable-v1.1-2026-04-09
```

## 2. Redeploy ALL edge functions
```bash
npx supabase functions deploy handle-consultation-request
npx supabase functions deploy manage-consultation
npx supabase functions deploy generate-consultation-report
npx supabase functions deploy handle-messages
npx supabase functions deploy list-doctors
npx supabase functions deploy logout
npx supabase functions deploy reset-password
npx supabase functions deploy login-doctor
npx supabase functions deploy login-agent
npx supabase functions deploy register-doctor
npx supabase functions deploy register-agent
npx supabase functions deploy send-doctor-otp
npx supabase functions deploy verify-doctor-otp
npx supabase functions deploy create-patient-session
npx supabase functions deploy refresh-patient-session
npx supabase functions deploy send-push-notification
npx supabase functions deploy update-fcm-token
npx supabase functions deploy log-doctor-online
npx supabase functions deploy videosdk-token
npx supabase functions deploy mpesa-stk-push
npx supabase functions deploy mpesa-callback
npx supabase functions deploy service-access-payment
npx supabase functions deploy extend-session
npx supabase functions deploy get-patient-reports
```

## 3. Restore DB columns (if dropped)
```sql
-- v1.0 columns
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS can_serve_as_gp BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE consultation_reports ADD COLUMN IF NOT EXISTS patient_age TEXT;
ALTER TABLE consultation_reports ADD COLUMN IF NOT EXISTS patient_gender TEXT;

-- v1.1 follow-up reopen columns
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS follow_up_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS follow_up_max INTEGER NOT NULL DEFAULT 1;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS is_reopened BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE consultations ADD COLUMN IF NOT EXISTS last_reopened_at TIMESTAMPTZ;

-- Backfill tier-based max
UPDATE consultations SET follow_up_max = -1 WHERE UPPER(COALESCE(service_tier, 'ECONOMY')) = 'ROYAL';
UPDATE consultations SET follow_up_max = 1 WHERE UPPER(COALESCE(service_tier, 'ECONOMY')) = 'ECONOMY';

CREATE INDEX IF NOT EXISTS idx_consultations_followup ON consultations (status, follow_up_expiry, follow_up_count, follow_up_max);
```

## 4. Restore RPC functions
```sql
-- reset_doctor_password (from v1.0)
CREATE OR REPLACE FUNCTION reset_doctor_password(p_doctor_id UUID, p_new_password TEXT)
RETURNS VOID AS $$
BEGIN
  UPDATE auth.users SET encrypted_password = crypt(p_new_password, gen_salt('bf')) WHERE id = p_doctor_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- reopen_consultation (v1.1)
-- See supabase/migrations/20260409100000_followup_reopen_model.sql for full source
-- OR run the CREATE OR REPLACE from the last applied version in the DB

-- set_followup_max_on_insert trigger (v1.1)
CREATE OR REPLACE FUNCTION set_followup_max_on_insert()
RETURNS trigger AS $$
BEGIN
    IF UPPER(COALESCE(NEW.service_tier, 'ECONOMY')) = 'ROYAL' THEN
        NEW.follow_up_max := -1;
    ELSE
        NEW.follow_up_max := 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_followup_max_trigger ON consultations;
CREATE TRIGGER set_followup_max_trigger
    BEFORE INSERT ON consultations FOR EACH ROW EXECUTE FUNCTION set_followup_max_on_insert();

-- validate_followup_reopen trigger (v1.1)
-- See migration file for full source
```

## 5. Rebuild and install Android
```bash
./gradlew installDebug
```
