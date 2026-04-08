-- Delete all users except admins.
-- Preserves admin accounts in auth.users, user_roles, and doctor_profiles.

DO $$
DECLARE
    admin_ids uuid[];
    deleted_auth_count integer;
BEGIN
    -- Capture admin user IDs
    SELECT array_agg(user_id) INTO admin_ids
    FROM user_roles WHERE role_name = 'admin';

    RAISE NOTICE 'Admin user IDs to preserve: %', admin_ids;

    -- Disable audit triggers to avoid FK issues during bulk delete
    ALTER TABLE user_roles DISABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_verification;

    -- Truncate all user-data tables
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

    BEGIN TRUNCATE TABLE appointments CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE doctor_availability_slots CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE fcm_tokens CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE recovery_attempts CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE email_verifications CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;

    -- Delete non-admin doctor_profiles and user_roles
    DELETE FROM doctor_profiles
    WHERE doctor_id != ALL(COALESCE(admin_ids, ARRAY[]::uuid[]));

    DELETE FROM user_roles
    WHERE role_name != 'admin';

    -- Re-enable triggers
    ALTER TABLE user_roles ENABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_verification;

    -- Delete non-admin Supabase Auth users
    DELETE FROM auth.users
    WHERE id != ALL(COALESCE(admin_ids, ARRAY[]::uuid[]));

    GET DIAGNOSTICS deleted_auth_count = ROW_COUNT;
    RAISE NOTICE 'Deleted % non-admin auth users', deleted_auth_count;
END $$;
