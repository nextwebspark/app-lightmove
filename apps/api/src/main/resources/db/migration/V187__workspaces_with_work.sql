-- Which workspaces a frequent job has work in (#1085). A job has no principal, and its tables are under row-level
-- security, so it used to open every workspace's scope on every tick to ask its own claim — a transaction per tenant
-- per tick, most of them answering nothing. Each of these is that question asked once, across the tenants, before any
-- scope: workspace ids out, nothing of the rows. The job then opens WorkspaceScope on each and claims there, under the
-- fence, exactly as before.
--
-- Each condition is wider than its job's claim, never narrower: a workspace named with nothing to claim costs one empty
-- pass, a workspace left out would leave its work stuck. Every caller is TenantResolvers. The maintenance flag is
-- raised and put back as V185's resolvers do, for V185's reasons.

-- The dispatcher's two loops: a run's next email or a consultant's own scheduled one due, and any claim still held,
-- which the loop stops or fails as uncertain once it is old enough.
CREATE FUNCTION app_lm_workspaces_with_sends_due(p_at timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT e.workspace_id FROM app_lm_outreach_enrollment e
         WHERE e.status IN ('SCHEDULED', 'ACTIVE') AND (e.next_send_at <= p_at OR e.sending_since IS NOT NULL)
        UNION
        SELECT m.workspace_id FROM app_lm_outreach_message m
         WHERE m.status = 'SCHEDULED' AND (m.send_at <= p_at OR m.sending_since IS NOT NULL);
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The due scan: an open task falling due soon or gone overdue within the lookback, not yet announced as both.
CREATE FUNCTION app_lm_workspaces_with_tasks_falling_due(p_after timestamptz, p_until timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT t.workspace_id FROM app_lm_task t
         WHERE t.status = 'OPEN' AND t.due_at > p_after AND t.due_at <= p_until
           AND (t.due_soon_notified_at IS NULL OR t.overdue_notified_at IS NULL);
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The bell's delayed emails: an unread, unarchived row past its delay and not yet emailed.
CREATE FUNCTION app_lm_workspaces_with_notification_emails_due(p_at timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT n.workspace_id FROM app_lm_notification n
         WHERE n.email_due_at <= p_at AND n.emailed_at IS NULL AND n.read_at IS NULL AND n.archived_at IS NULL;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The live copilot's coach: a bot in a call whose copilot is listening.
CREATE FUNCTION app_lm_workspaces_with_copilot_listening()
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT r.workspace_id FROM app_lm_meeting_recording r
         WHERE r.live_copilot AND r.live_status = 'LISTENING' AND r.status IN ('JOINING', 'RECORDING');
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- Stripe's seat quantity owed a sync, the longest-waiting first.
CREATE FUNCTION app_lm_workspaces_owing_seat_sync(p_before timestamptz, p_limit integer)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT s.workspace_id FROM app_lm_workspace_subscription s
         WHERE s.seat_sync_due_at <= p_before ORDER BY s.seat_sync_due_at LIMIT p_limit;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The ledger's sweep: an unsettled hold past its time, or a grant past its expiry still holding credits.
CREATE FUNCTION app_lm_workspaces_with_lapsed_credits(p_at timestamptz, p_limit integer)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        (SELECT h.workspace_id FROM app_lm_credit_hold h WHERE h.status = 'OPEN' AND h.expires_at <= p_at
         UNION
         SELECT g.workspace_id FROM app_lm_credit_grant g WHERE g.remaining > 0 AND g.expires_at <= p_at)
        LIMIT p_limit;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION
    app_lm_workspaces_with_sends_due(timestamptz),
    app_lm_workspaces_with_tasks_falling_due(timestamptz, timestamptz),
    app_lm_workspaces_with_notification_emails_due(timestamptz),
    app_lm_workspaces_with_copilot_listening(),
    app_lm_workspaces_owing_seat_sync(timestamptz, integer),
    app_lm_workspaces_with_lapsed_credits(timestamptz, integer)
    FROM PUBLIC;
