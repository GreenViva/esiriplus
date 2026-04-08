# Restore to Stable v1.0 (2026-04-09)

## 1. Restore Android + PWA code
```bash
git reset --hard stable-v1.0-2026-04-09
```

## 2. Redeploy ALL edge functions
```bash
cd supabase
npx supabase functions deploy create-patient-session
npx supabase functions deploy refresh-patient-session
npx supabase functions deploy login-doctor
npx supabase functions deploy register-doctor
npx supabase functions deploy login-agent
npx supabase functions deploy register-agent
npx supabase functions deploy send-doctor-otp
npx supabase functions deploy verify-doctor-otp
npx supabase functions deploy reset-password
npx supabase functions deploy logout
npx supabase functions deploy handle-consultation-request
npx supabase functions deploy handle-messages
npx supabase functions deploy manage-consultation
npx supabase functions deploy generate-consultation-report
npx supabase functions deploy get-patient-reports
npx supabase functions deploy list-doctors
npx supabase functions deploy send-push-notification
npx supabase functions deploy log-doctor-online
npx supabase functions deploy update-fcm-token
npx supabase functions deploy videosdk-token
npx supabase functions deploy mpesa-stk-push
npx supabase functions deploy mpesa-callback
npx supabase functions deploy service-access-payment
npx supabase functions deploy extend-session
```

## 3. Restore DB columns (if dropped)
```sql
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS can_serve_as_gp BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE consultation_reports ADD COLUMN IF NOT EXISTS patient_age TEXT;
ALTER TABLE consultation_reports ADD COLUMN IF NOT EXISTS patient_gender TEXT;
```

## 4. Restore RPC function (if dropped)
```sql
CREATE OR REPLACE FUNCTION reset_doctor_password(p_doctor_id UUID, p_new_password TEXT)
RETURNS VOID AS $$
BEGIN
  UPDATE auth.users SET encrypted_password = crypt(p_new_password, gen_salt('bf')) WHERE id = p_doctor_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

## 5. Rebuild and install Android
```bash
./gradlew installDebug
```
