-- Retention of system rows (#921, part 1): RetentionSweeper deletes what docs/privacy/retention-schedule.md says is
-- held no longer — expired sign-in tokens and challenges, closed invitations, read notifications, sent digests, old
-- assistant chats, aged metered uses and the vendor caches.
--
-- Most of those tables are tenant data under forced row-level security (V184, V190), so a sweep reads and deletes
-- them a workspace at a time. Rather than walk every workspace, the sweep asks this resolver which workspaces hold a
-- row past its cutoff, in V189's shape: SECURITY DEFINER, the maintenance flag raised for its one query, and only the
-- workspace's id out (as both columns, the billing customer's idiom), never a row.

CREATE FUNCTION app_lm_resolve_workspaces_with_retention_due(p_invitations_before timestamptz,
                                                             p_notifications_before timestamptz,
                                                             p_digests_before timestamptz,
                                                             p_threads_before timestamptz,
                                                             p_usage_before timestamptz)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT due.workspace_id, due.workspace_id FROM (
            SELECT i.workspace_id FROM app_lm_invitation i
            WHERE (i.status = 'ACCEPTED' AND i.accepted_at < p_invitations_before)
               OR (i.status <> 'ACCEPTED' AND i.expires_at < p_invitations_before)
            UNION
            SELECT n.workspace_id FROM app_lm_notification n
            WHERE n.read_at < p_notifications_before OR n.archived_at < p_notifications_before
            UNION
            SELECT d.workspace_id FROM app_lm_notification_digest d WHERE d.sent_at < p_digests_before
            UNION
            SELECT t.workspace_id FROM app_lm_assistant_thread t WHERE t.updated_at < p_threads_before
            UNION
            SELECT u.workspace_id FROM app_lm_usage_event u WHERE u.created_at < p_usage_before
        ) due;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- What each sweep and the resolver scan by.
CREATE INDEX app_lm_refresh_token_expires_idx ON app_lm_refresh_token (expires_at);
CREATE INDEX app_lm_verification_token_expires_idx ON app_lm_verification_token (expires_at);
CREATE INDEX app_lm_mfa_challenge_expires_idx ON app_lm_mfa_challenge (expires_at);
CREATE INDEX app_lm_vendor_company_fetched_idx ON app_lm_vendor_company (fetched_at);
CREATE INDEX app_lm_invitation_expires_idx ON app_lm_invitation (expires_at);
CREATE INDEX app_lm_invitation_accepted_idx ON app_lm_invitation (accepted_at) WHERE accepted_at IS NOT NULL;
CREATE INDEX app_lm_notification_read_idx ON app_lm_notification (read_at) WHERE read_at IS NOT NULL;
CREATE INDEX app_lm_notification_archived_idx ON app_lm_notification (archived_at) WHERE archived_at IS NOT NULL;
CREATE INDEX app_lm_notification_digest_sent_idx ON app_lm_notification_digest (sent_at);
CREATE INDEX app_lm_assistant_thread_updated_idx ON app_lm_assistant_thread (updated_at);
CREATE INDEX app_lm_usage_event_created_idx ON app_lm_usage_event (created_at);
