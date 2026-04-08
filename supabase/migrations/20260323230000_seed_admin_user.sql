-- Seed the initial admin user via Supabase Auth.

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

DO $$
DECLARE
    v_user_id uuid;
BEGIN
    SELECT id INTO v_user_id FROM auth.users WHERE email = 'grnerick@gmail.com';

    IF v_user_id IS NOT NULL THEN
        UPDATE auth.users SET
            encrypted_password = extensions.crypt('admin@2026', extensions.gen_salt('bf')),
            email_confirmed_at = COALESCE(email_confirmed_at, now()),
            updated_at = now()
        WHERE id = v_user_id;
    ELSE
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password,
            email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
            created_at, updated_at, confirmation_token, recovery_token
        ) VALUES (
            '00000000-0000-0000-0000-000000000000', gen_random_uuid(),
            'authenticated', 'authenticated', 'grnerick@gmail.com',
            extensions.crypt('admin@2026', extensions.gen_salt('bf')),
            now(), '{"provider": "email", "providers": ["email"]}'::jsonb,
            '{"full_name": "Admin"}'::jsonb, now(), now(), '', ''
        )
        RETURNING id INTO v_user_id;

        INSERT INTO auth.identities (
            id, user_id, provider_id, identity_data, provider,
            last_sign_in_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_user_id, 'grnerick@gmail.com',
            jsonb_build_object('sub', v_user_id::text, 'email', 'grnerick@gmail.com'),
            'email', now(), now(), now()
        );
    END IF;

    -- Delete any existing roles for this user, then insert admin
    DELETE FROM user_roles WHERE user_id = v_user_id;
    INSERT INTO user_roles (user_id, role_name) VALUES (v_user_id, 'admin');

    RAISE NOTICE 'Admin ready: id=%, email=grnerick@gmail.com', v_user_id;
END $$;
