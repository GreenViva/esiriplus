# Database Migration Changelog

## Version 9 (MIGRATION_8_9)
- Recreated `notifications` table with `readAt` timestamp replacing boolean `isRead`
- Added `video_calls` table (FK to consultations)
- Added `patient_reports` table (FK to consultations + patient_sessions)
- Added `typing_indicators` table with composite PK (consultationId, userId)

## Version 8 (MIGRATION_7_8)
- Added `doctor_ratings` table with UNIQUE constraint on consultationId (FK to doctor_profiles, consultations, patient_sessions)
- Added `doctor_earnings` table (FK to doctor_profiles, consultations)

## Version 7 (MIGRATION_6_7)
- Recreated `payments` table with FK to patient_sessions, mobile money fields (phoneNumber, transactionId)
- Added `service_access_payments` table for service-tier access payments
- Added `call_recharge_payments` table for mid-call top-ups (FK to consultations)

## Version 6 (MIGRATION_5_6)
- Recreated `doctor_profiles` with richer schema (bio, languages, license, ratings, verification)
- Added `doctor_availability` table (FK to doctor_profiles)
- Added `doctor_credentials` table (FK to doctor_profiles)

## Version 5 (MIGRATION_4_5)
- Recreated `consultations` with FK to patient_sessions (replacing user-based FK)
- Recreated `messages` with sender type/ID and sync flag
- Recreated dependent tables (`diagnoses`, `attachments`, `prescriptions`, `reviews`, `vital_signs`) with updated FK references

## Version 4 (MIGRATION_3_4)
- Added `patient_sessions` table for anonymous patient tracking (sessionId, tokenHash, demographics)

## Version 3 (MIGRATION_2_3)
- Added `sex`, `ageGroup`, `chronicConditions` columns to `patient_profiles`

## Version 2 (MIGRATION_1_2)
- Added `createdAt` column to `sessions` table

## Version 1
- Initial schema: users, sessions, consultations, payments, service_tiers, app_config, doctor_profiles, patient_profiles, messages, attachments, notifications, prescriptions, diagnoses, vital_signs, schedules, reviews, medical_records, audit_logs, providers
