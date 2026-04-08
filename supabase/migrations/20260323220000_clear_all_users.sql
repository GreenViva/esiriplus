-- Delete ALL users including admin. Full reset.
DO $$
DECLARE
    deleted_count integer;
BEGIN
    -- Disable audit triggers
    ALTER TABLE user_roles DISABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_verification;

    -- Truncate all data tables
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
        patient_sessions,
        doctor_profiles,
        user_roles
    CASCADE;

    BEGIN TRUNCATE TABLE appointments CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE doctor_availability_slots CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE fcm_tokens CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE recovery_attempts CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE email_verifications CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;

    -- Re-enable triggers
    ALTER TABLE user_roles ENABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_verification;

    -- Delete ALL auth users
    DELETE FROM auth.users;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Deleted % auth users', deleted_count;
END $$;
