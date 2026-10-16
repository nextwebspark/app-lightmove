-- Every status is the workspace's to retire: the seeded ones were only a starting point. Their system_key
-- stays, as the wire key the extension, the public API and old timeline lines still speak.
ALTER TABLE app_lm_workspace_candidate_status
    DROP CONSTRAINT app_lm_workspace_candidate_status_system_live_chk;

-- Which status an event moves a candidate to, per workspace. No row is the default: an email sent moves to
-- the seeded Contacted, a booked call to the seeded Engaged, and a reply moves nobody.
CREATE TABLE app_lm_workspace_status_automation (
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    trigger       varchar(16) NOT NULL
        CONSTRAINT app_lm_workspace_status_automation_trigger_chk
            CHECK (trigger IN ('EMAIL_SENT', 'REPLIED', 'CALL_BOOKED')),
    status_id     uuid        REFERENCES app_lm_workspace_candidate_status (id) ON DELETE SET NULL,
    enabled       boolean     NOT NULL,
    updated_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, trigger),
    CONSTRAINT app_lm_workspace_status_automation_target_chk CHECK (NOT enabled OR status_id IS NOT NULL)
);

CREATE INDEX app_lm_workspace_status_automation_status_idx
    ON app_lm_workspace_status_automation (status_id) WHERE status_id IS NOT NULL;

COMMENT ON TABLE app_lm_workspace_status_automation IS
    'Per workspace, the status an outreach event moves a candidate forward to. Tenant data: filter by workspace_id.';
