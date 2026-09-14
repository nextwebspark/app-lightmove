-- Grants or revokes a LightMove platform role (V51) for one existing account. Driven by
-- grant-platform-role.sh, which passes :email, :role (SUPER_ADMIN) and :mode ('grant' or 'revoke').
--
-- Deliberately not something the application can do: a platform role reaches every workspace's shared
-- library, and an app that could mint one would hand that to whoever compromised it. This file runs as
-- the table's owner — lm_app today, postgres once harden.sql has reassigned it.
--
-- Each change is written to the audit trail by hand, since no request carries it.

\set ON_ERROR_STOP on

SELECT set_config('lightmove.email', :'email', false);
SELECT set_config('lightmove.role', :'role', false);
SELECT set_config('lightmove.mode', :'mode', false);

DO $$
DECLARE
    target_email text := lower(current_setting('lightmove.email'));
    role_name    text := current_setting('lightmove.role');
    granting     boolean := current_setting('lightmove.mode') = 'grant';
    target_user  uuid;
    target_role  uuid;
    changed      integer;
BEGIN
    SELECT id INTO target_user FROM app_lm_user WHERE email = target_email;
    IF target_user IS NULL THEN
        RAISE EXCEPTION 'No LightMove account for %. They must sign up (or accept an invitation) first.', target_email;
    END IF;

    SELECT id INTO target_role FROM app_lm_role WHERE scope = 'PLATFORM' AND name = role_name;
    IF target_role IS NULL THEN
        RAISE EXCEPTION 'No PLATFORM role named %.', role_name;
    END IF;

    IF granting THEN
        INSERT INTO app_lm_user_platform_role (user_id, role_id)
        VALUES (target_user, target_role)
        ON CONFLICT DO NOTHING;
    ELSE
        DELETE FROM app_lm_user_platform_role WHERE user_id = target_user AND role_id = target_role;
    END IF;
    GET DIAGNOSTICS changed = ROW_COUNT;

    IF changed = 0 THEN
        RAISE NOTICE '% already % % — nothing changed.', target_email,
            CASE WHEN granting THEN 'holds' ELSE 'does not hold' END, role_name;
        RETURN;
    END IF;

    INSERT INTO app_lm_audit_event (event_type, outcome, target_type, target_id, metadata)
    VALUES (CASE WHEN granting THEN 'PLATFORM_ROLE_GRANTED' ELSE 'PLATFORM_ROLE_REVOKED' END,
            'SUCCESS', 'user', target_user::text,
            jsonb_build_object('role', role_name, 'by', session_user));

    RAISE NOTICE '% % %.', CASE WHEN granting THEN 'Granted' ELSE 'Revoked' END, role_name, target_email;
END;
$$;
