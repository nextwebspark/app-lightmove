-- Row-level security on every remaining tenant table (#1085), V170's policies extended: a row of another workspace,
-- or any row with no workspace set, is neither read nor written. What stays out, and why, is
-- TenantTableGuard.EXCLUDED; the boot refuses a tenant table that is on neither list.
--
-- FORCE, for V170's reason: lm_app still owns these tables until #256.

DO $$
DECLARE
    tbl text;
    child record;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'app_lm_api_key', 'app_lm_assistant_thread', 'app_lm_assistant_turn', 'app_lm_billing_customer',
        'app_lm_billing_notice', 'app_lm_candidate_scorecard', 'app_lm_client', 'app_lm_client_representative',
        'app_lm_credit_balance', 'app_lm_credit_grant', 'app_lm_credit_hold', 'app_lm_credit_threshold_crossing',
        'app_lm_executive_sourcing_run', 'app_lm_interview_plan', 'app_lm_invitation', 'app_lm_mailbox_authorization',
        'app_lm_mailbox_connection', 'app_lm_meeting_recording', 'app_lm_member_availability', 'app_lm_notification',
        'app_lm_notification_digest', 'app_lm_notification_preference', 'app_lm_outreach_enrollment',
        'app_lm_outreach_message', 'app_lm_outreach_message_attachment', 'app_lm_outreach_sequence',
        'app_lm_person_activity', 'app_lm_person_meeting', 'app_lm_position_template_hidden', 'app_lm_task',
        'app_lm_usage_event', 'app_lm_workspace_availability', 'app_lm_workspace_candidate_stage',
        'app_lm_workspace_candidate_status', 'app_lm_workspace_candidate_tag', 'app_lm_workspace_mail_integration',
        'app_lm_workspace_status_automation', 'app_lm_workspace_subscription', 'app_lm_workspace_task_preset',
        'app_lm_workspace_task_rule', 'app_lm_zoom_connection']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('CREATE POLICY app_lm_tenant ON %I
                            USING (app_lm_tenant_visible(workspace_id))
                            WITH CHECK (app_lm_tenant_visible(workspace_id))', tbl);
    END LOOP;

    -- A shared library template, and a grant still waiting for its consent, belong to no workspace: neither is a
    -- firm's data. Who may edit the library is the platform authorizer's question, not isolation's.
    FOREACH tbl IN ARRAY ARRAY['app_lm_position_template', 'app_lm_oauth_authorization']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('CREATE POLICY app_lm_tenant ON %I
                            USING (workspace_id IS NULL OR app_lm_tenant_visible(workspace_id))
                            WITH CHECK (workspace_id IS NULL OR app_lm_tenant_visible(workspace_id))', tbl);
    END LOOP;

    -- Children carrying no workspace of their own are visible exactly where their parent is, the parent's own
    -- policy answering (and, two hops up, its parent's). Every read of them is keyed by the parent.
    FOR child IN SELECT * FROM (VALUES
        ('app_lm_position', 'app_lm_project', 'project_id'),
        ('app_lm_strategy', 'app_lm_project', 'project_id'),
        ('app_lm_strategy_search', 'app_lm_project', 'project_id'),
        ('app_lm_project_custom_column', 'app_lm_project', 'project_id'),
        ('app_lm_project_member', 'app_lm_project', 'project_id'),
        ('app_lm_project_pending_representative', 'app_lm_project', 'project_id'),
        ('app_lm_position_benefit', 'app_lm_position', 'position_id'),
        ('app_lm_position_competency', 'app_lm_position', 'position_id'),
        ('app_lm_position_criterion', 'app_lm_position', 'position_id'),
        ('app_lm_position_document', 'app_lm_position', 'position_id'),
        ('app_lm_position_org_node', 'app_lm_position', 'position_id'),
        ('app_lm_position_priority', 'app_lm_position', 'position_id'),
        ('app_lm_position_responsibility', 'app_lm_position', 'position_id'),
        ('app_lm_strategy_off_limits_company', 'app_lm_strategy', 'strategy_id'),
        ('app_lm_project_member_role', 'app_lm_project_member', 'project_member_id'),
        ('app_lm_outreach_sequence_step', 'app_lm_outreach_sequence', 'sequence_id'),
        ('app_lm_meeting_live_line', 'app_lm_meeting_recording', 'recording_id'),
        ('app_lm_meeting_copilot_viewer', 'app_lm_meeting_recording', 'recording_id'),
        ('app_lm_note_mention', 'app_lm_person_note', 'note_id'),
        ('app_lm_oauth_retired_refresh_token', 'app_lm_oauth_authorization', 'authorization_id'),
        ('app_lm_position_template_keyword', 'app_lm_position_template', 'template_id'),
        ('app_lm_candidate_contact', 'app_lm_project_candidate', 'candidate_id'),
        ('app_lm_candidate_photo', 'app_lm_project_candidate', 'candidate_id')
    ) AS t(child_table, parent_table, parent_key)
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', child.child_table);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', child.child_table);
        EXECUTE format('CREATE POLICY app_lm_tenant ON %1$I
                            USING (EXISTS (SELECT 1 FROM %2$I parent WHERE parent.id = %1$I.%3$I))
                            WITH CHECK (EXISTS (SELECT 1 FROM %2$I parent WHERE parent.id = %1$I.%3$I))',
                       child.child_table, child.parent_table, child.parent_key);
    END LOOP;
END
$$;

-- Guarded on ownership for V165's reason: once harden.sql has run these two belong to postgres, which lm_app
-- cannot ALTER, and there the same statements are harden.sql's (step 2b). Until then (every local, test and e2e
-- database, and production before harden.sql) Flyway owns them and applies them here.
DO $$
BEGIN
    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'app_lm_audit_event'::regclass)) = current_user THEN
        ALTER TABLE app_lm_audit_event ENABLE ROW LEVEL SECURITY;
        ALTER TABLE app_lm_audit_event FORCE ROW LEVEL SECURITY;
        -- Never refused: a sign-in is audited in the workspace it opens, a switch in the one it lands in, and an
        -- account's own events in none. Read where it is the reader's workspace, or the account's.
        CREATE POLICY app_lm_tenant_read ON app_lm_audit_event FOR SELECT
            USING (workspace_id IS NULL OR app_lm_tenant_visible(workspace_id));
        CREATE POLICY app_lm_tenant_write ON app_lm_audit_event FOR INSERT WITH CHECK (true);
    ELSE
        RAISE WARNING 'app_lm_audit_event is not owned by %, so its row-level security was not enabled: re-run ops/cloudsql/harden.sql as postgres.', current_user;
    END IF;
    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'app_lm_credit_entry'::regclass)) = current_user THEN
        ALTER TABLE app_lm_credit_entry ENABLE ROW LEVEL SECURITY;
        ALTER TABLE app_lm_credit_entry FORCE ROW LEVEL SECURITY;
        CREATE POLICY app_lm_tenant ON app_lm_credit_entry
            USING (app_lm_tenant_visible(workspace_id))
            WITH CHECK (app_lm_tenant_visible(workspace_id));
    ELSE
        RAISE WARNING 'app_lm_credit_entry is not owned by %, so its row-level security was not enabled: re-run ops/cloudsql/harden.sql as postgres.', current_user;
    END IF;
END
$$;
