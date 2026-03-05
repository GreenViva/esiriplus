-- Clear ALL users and dependent data to start fresh.
-- Preserves schema, functions, triggers, service_tiers, app_config.

DO $$
DECLARE
    deleted_auth_count integer;
BEGIN
    -- Step 1: Disable audit triggers to avoid FK issues during deletion
    ALTER TABLE user_roles DISABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_verification;

    -- Step 2: Truncate all dependent data tables
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
        consultation_requests,
        call_recharge_payments,
        service_access_payments,
        payments,
        doctor_availability,
        doctor_credentials,
        doctor_device_bindings,
        patient_reports,
        patient_sessions
    CASCADE;

    -- Truncate newer tables (ignore if they don't exist yet)
    BEGIN TRUNCATE TABLE appointments CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE doctor_availability_slots CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE fcm_tokens CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE recovery_attempts CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;

    -- Step 3: Delete ALL doctor_profiles
    DELETE FROM doctor_profiles;

    -- Step 4: Delete ALL user_roles
    DELETE FROM user_roles;

    -- Step 5: Re-enable triggers
    ALTER TABLE user_roles ENABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_verification;

    -- Step 6: Delete ALL auth users
    DELETE FROM auth.users;

    GET DIAGNOSTICS deleted_auth_count = ROW_COUNT;
    RAISE NOTICE 'Deleted % auth users (fresh start)', deleted_auth_count;
END $$;
