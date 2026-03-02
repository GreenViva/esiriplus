-- Clear all user data EXCEPT admin users.
-- Preserves schema, functions, triggers, and admin accounts.

DO $$
DECLARE
    admin_ids uuid[];
    deleted_auth_count integer;
BEGIN
    -- Step 1: Capture admin user IDs
    SELECT array_agg(user_id) INTO admin_ids
    FROM user_roles WHERE role_name = 'admin';

    RAISE NOTICE 'Admin user IDs to preserve: %', admin_ids;

    -- Step 2: Disable audit triggers to avoid FK issues
    ALTER TABLE user_roles DISABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles DISABLE TRIGGER audit_doctor_verification;

    -- Step 3: Truncate all data tables (no admin data in these)
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

    -- Truncate newer tables (ignore if they don't exist)
    BEGIN TRUNCATE TABLE appointments CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE doctor_availability_slots CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE fcm_tokens CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;
    BEGIN TRUNCATE TABLE recovery_attempts CASCADE; EXCEPTION WHEN undefined_table THEN NULL; END;

    -- Step 4: Delete non-admin doctor_profiles
    DELETE FROM doctor_profiles
    WHERE doctor_id != ALL(COALESCE(admin_ids, ARRAY[]::uuid[]));

    -- Step 5: Delete non-admin user_roles
    DELETE FROM user_roles
    WHERE role_name != 'admin';

    -- Step 6: Re-enable triggers
    ALTER TABLE user_roles ENABLE TRIGGER audit_user_role;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_registered;
    ALTER TABLE doctor_profiles ENABLE TRIGGER audit_doctor_verification;

    -- Step 7: Delete non-admin Supabase Auth users
    DELETE FROM auth.users
    WHERE id != ALL(COALESCE(admin_ids, ARRAY[]::uuid[]));

    GET DIAGNOSTICS deleted_auth_count = ROW_COUNT;
    RAISE NOTICE 'Deleted % non-admin auth users', deleted_auth_count;
END $$;
