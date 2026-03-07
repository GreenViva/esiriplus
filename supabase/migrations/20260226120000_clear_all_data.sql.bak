-- Clear all user data while preserving schema, functions, and triggers.
-- Temporarily disable audit triggers to avoid FK issues during cleanup.

ALTER TABLE user_roles DISABLE TRIGGER audit_user_role;
ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_registered;
ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_verification;

TRUNCATE TABLE
  admin_logs,
  notification_history,
  notifications,
  messages,
  typing_indicators,
  video_calls,
  doctor_ratings,
  doctor_earnings,
  consultation_reports,
  consultations,
  appointments,
  call_recharge_payments,
  service_access_payments,
  payments,
  doctor_availability,
  doctor_credentials,
  doctor_device_bindings,
  doctor_profiles,
  patient_reports,
  patient_sessions,
  recovery_questions,
  user_roles
CASCADE;

ALTER TABLE user_roles ENABLE TRIGGER audit_user_role;
ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_registered;
ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_verification;
