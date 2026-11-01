-- The doors that open before any workspace is known (#1085): a webhook names a grant, a calendar, a bot or a Stripe
-- customer; a public page names a slug; a credential arrives as a token whose hash is the lookup key. Once their
-- tables are under row-level security (V172) none of those rows can be read until the workspace is named — so each
-- door asks one of these first, and then opens WorkspaceScope on the workspace it was told.
--
-- Each answers ids and the workspace for one key, and nothing of the row: the hole a resolver makes in the fence is
-- only "which workspace holds this key", never its content. Every caller is TenantResolvers.
--
-- The maintenance flag is raised for the one query and put back as the caller had it. Not a function-level SET: since
-- Postgres 15 only a superuser may attach a placeholder setting to a function. set_config(…, true) is transaction-
-- local, so a resolver that fails mid-query takes the raised flag down with its transaction.
-- #256: once lm_migrate owns these tables its own policy answers, and a migration drops the flag from each.

CREATE FUNCTION app_lm_resolve_mailboxes_by_grant(p_grant_id text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT m.id, m.workspace_id FROM app_lm_mailbox_connection m WHERE m.grant_id = p_grant_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_mailboxes_by_booking_configuration(p_configuration_id text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT m.id, m.workspace_id FROM app_lm_mailbox_connection m
                  WHERE m.booking_configuration_id = p_configuration_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_mailboxes_by_recall_calendar(p_calendar_id text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT m.id, m.workspace_id FROM app_lm_mailbox_connection m
                  WHERE m.recall_calendar_id = p_calendar_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- Also how a new slug is checked for a collision: unique across every workspace, since the link names nothing else.
CREATE FUNCTION app_lm_resolve_mailbox_by_booking_slug(p_slug text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT m.id, m.workspace_id FROM app_lm_mailbox_connection m WHERE m.booking_slug = p_slug;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- A person's clock is one across their workspaces (V148): a profile change reaches each workspace's mailbox.
CREATE FUNCTION app_lm_resolve_mailboxes_of_user(p_user_id uuid)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT m.id, m.workspace_id FROM app_lm_mailbox_connection m WHERE m.user_id = p_user_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_mailbox_authorization(p_state_hash text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT a.id, a.workspace_id FROM app_lm_mailbox_authorization a WHERE a.state_hash = p_state_hash;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_recording_by_bot(p_bot_id text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT r.id, r.workspace_id FROM app_lm_meeting_recording r WHERE r.recall_bot_id = p_bot_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_recording_by_final_transcript(p_transcript_id text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT r.id, r.workspace_id FROM app_lm_meeting_recording r
                  WHERE r.final_transcript_id = p_transcript_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_api_key(p_token_hash text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT k.id, k.workspace_id FROM app_lm_api_key k WHERE k.token_hash = p_token_hash;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- Any of a grant's four hashes, or a refresh token it rotated away (the replay check). A grant waiting for consent
-- answers a null workspace, which its table's policy shows to anyone.
CREATE FUNCTION app_lm_resolve_oauth_authorization(p_token_hash text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT a.id, a.workspace_id FROM app_lm_oauth_authorization a
                  WHERE a.state_hash = p_token_hash OR a.code_hash = p_token_hash
                     OR a.access_token_hash = p_token_hash OR a.refresh_token_hash = p_token_hash
                 UNION
                 SELECT a.id, a.workspace_id FROM app_lm_oauth_retired_refresh_token r
                   JOIN app_lm_oauth_authorization a ON a.id = r.authorization_id
                  WHERE r.token_hash = p_token_hash;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_oauth_authorization_by_id(p_id uuid)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT a.id, a.workspace_id FROM app_lm_oauth_authorization a WHERE a.id = p_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- A password change ends every grant of the account, in whichever workspace it was given.
CREATE FUNCTION app_lm_resolve_oauth_authorizations_of_user(p_user_id uuid)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT a.id, a.workspace_id FROM app_lm_oauth_authorization a WHERE a.user_id = p_user_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

CREATE FUNCTION app_lm_resolve_invitation(p_token_hash text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT i.id, i.workspace_id FROM app_lm_invitation i WHERE i.token_hash = p_token_hash;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The topbar offers a signed-in person the invitations waiting for their address, from whichever workspace.
CREATE FUNCTION app_lm_resolve_pending_invitations_for(p_email text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT i.id, i.workspace_id FROM app_lm_invitation i
                  WHERE i.email = p_email AND i.status = 'PENDING';
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The workspace a Stripe customer bills; its id is that workspace too.
CREATE FUNCTION app_lm_resolve_billing_customer(p_stripe_customer_id text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT c.workspace_id, c.workspace_id FROM app_lm_billing_customer c
                  WHERE c.stripe_customer_id = p_stripe_customer_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- One trial per founder, whichever workspace it was in: asked of the founder's next workspace, which holds none.
CREATE FUNCTION app_lm_trial_started_by(p_user_id uuid)
    RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
    started boolean;
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    SELECT EXISTS (SELECT 1 FROM app_lm_workspace_subscription s WHERE s.trial_started_by = p_user_id) INTO started;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
    RETURN started;
END
$$;

-- The platform's library screen says how many firms hold their own copy of each template, and so will not see an
-- edit to the library's: a count per code, never which firms or what their copies say.
CREATE FUNCTION app_lm_count_firm_templates_by_code()
    RETURNS TABLE (code text, copies bigint)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT t.code::text, count(*) FROM app_lm_position_template t
                  WHERE t.workspace_id IS NOT NULL GROUP BY t.code;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION
    app_lm_resolve_mailboxes_by_grant(text), app_lm_resolve_mailboxes_by_booking_configuration(text),
    app_lm_resolve_mailboxes_by_recall_calendar(text), app_lm_resolve_mailbox_by_booking_slug(text),
    app_lm_resolve_mailboxes_of_user(uuid), app_lm_resolve_mailbox_authorization(text),
    app_lm_resolve_recording_by_bot(text), app_lm_resolve_recording_by_final_transcript(text),
    app_lm_resolve_api_key(text), app_lm_resolve_oauth_authorization(text),
    app_lm_resolve_oauth_authorization_by_id(uuid), app_lm_resolve_oauth_authorizations_of_user(uuid),
    app_lm_resolve_invitation(text), app_lm_resolve_pending_invitations_for(text),
    app_lm_resolve_billing_customer(text), app_lm_trial_started_by(uuid), app_lm_count_firm_templates_by_code()
    FROM PUBLIC;
