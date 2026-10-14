-- Tasks, @mentions and notifications (epic #862). A next action is a task: V132's one free-text next action
-- per executive becomes an open task here, and the card reads the earliest open one. V132's columns stay,
-- unwritten, as a frozen copy until the cleanup migration drops them with V91's (#606).

-- The workspace's catalog a next action is picked from. Free text is a title only and never lands here.
CREATE TABLE app_lm_workspace_task_preset (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    label            varchar(80) NOT NULL
        CONSTRAINT app_lm_workspace_task_preset_label_chk CHECK (btrim(label) <> ''),
    kind             varchar(16) NOT NULL
        CONSTRAINT app_lm_workspace_task_preset_kind_chk
            CHECK (kind IN ('CALL', 'EMAIL', 'MEETING', 'FOLLOW_UP', 'TODO')),
    due_offset_days  integer
        CONSTRAINT app_lm_workspace_task_preset_due_chk CHECK (due_offset_days BETWEEN 0 AND 365),
    position         integer     NOT NULL,
    retired_at       timestamptz,
    created_by       uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_workspace_task_preset IS
    'A workspace''s next-action presets. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_task_preset_touch BEFORE UPDATE ON app_lm_workspace_task_preset
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE UNIQUE INDEX app_lm_workspace_task_preset_label_uk
    ON app_lm_workspace_task_preset (workspace_id, lower(label));

-- Every workspace starts from the same eight; one created later is seeded the first time its catalog is read.
INSERT INTO app_lm_workspace_task_preset (workspace_id, label, kind, due_offset_days, position)
SELECT w.id, preset.label, preset.kind, preset.due_offset_days, preset.position
FROM app_lm_workspace w
CROSS JOIN (VALUES ('Call to qualify interest', 'CALL', 1, 1),
                   ('Send follow-up email', 'EMAIL', 2, 2),
                   ('Schedule a call', 'MEETING', 1, 3),
                   ('Reference check', 'TODO', 5, 4),
                   ('Share profile with hiring manager', 'TODO', 1, 5),
                   ('Request CV', 'EMAIL', 2, 6),
                   ('Debrief after meeting', 'FOLLOW_UP', 0, 7),
                   ('Re-engage later', 'FOLLOW_UP', 30, 8))
    AS preset (label, kind, due_offset_days, position);

-- One job on one executive of one position. The person is kept beside the mapping, so unmapping someone
-- keeps their tasks readable from the person while the position's own read drops them.
CREATE TABLE app_lm_task (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id          uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id            uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    person_id             uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    candidate_id          uuid         REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    note_id               uuid         REFERENCES app_lm_person_note (id) ON DELETE SET NULL,
    preset_id             uuid         REFERENCES app_lm_workspace_task_preset (id) ON DELETE SET NULL,
    title                 varchar(200) NOT NULL
        CONSTRAINT app_lm_task_title_chk CHECK (btrim(title) <> ''),
    kind                  varchar(16)  NOT NULL
        CONSTRAINT app_lm_task_kind_chk CHECK (kind IN ('CALL', 'EMAIL', 'MEETING', 'FOLLOW_UP', 'TODO')),
    detail                varchar(500),
    assignee_user_id      uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    due_at                timestamptz,
    status                varchar(16)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT app_lm_task_status_chk CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    source                varchar(16)  NOT NULL
        CONSTRAINT app_lm_task_source_chk CHECK (source IN ('MANUAL', 'NOTE', 'RULE')),
    rule                  varchar(16)
        CONSTRAINT app_lm_task_rule_chk CHECK (rule IN ('REPLY', 'BOUNCE', 'BOOKED_CALL')),
    -- The event a rule filed the task for: a replayed webhook finds it taken and files nothing.
    rule_key              varchar(160),
    created_by            uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    completed_by          uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    completed_at          timestamptz,
    -- When the due-soon and overdue notices went; a moved due date clears both.
    due_soon_notified_at  timestamptz,
    overdue_notified_at   timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_task_rule_source_chk
        CHECK ((source = 'RULE') = (rule IS NOT NULL AND rule_key IS NOT NULL)),
    CONSTRAINT app_lm_task_done_chk CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);

COMMENT ON TABLE app_lm_task IS
    'Tasks on a position''s executives. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_task_touch BEFORE UPDATE ON app_lm_task
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE UNIQUE INDEX app_lm_task_rule_key_uk ON app_lm_task (workspace_id, rule_key) WHERE rule_key IS NOT NULL;
CREATE INDEX app_lm_task_assignee_idx ON app_lm_task (workspace_id, assignee_user_id, status, due_at);
CREATE INDEX app_lm_task_creator_idx ON app_lm_task (workspace_id, created_by, status, due_at);
CREATE INDEX app_lm_task_candidate_idx ON app_lm_task (project_id, candidate_id, status, due_at);
CREATE INDEX app_lm_task_open_due_idx ON app_lm_task (due_at) WHERE status = 'OPEN';

-- V132's next actions, as open tasks for whoever set them, or the position's first lead where nobody is
-- recorded. Their notices count as sent: a backlog of old dates is not news.
INSERT INTO app_lm_task (workspace_id, project_id, person_id, candidate_id, title, kind, assignee_user_id, due_at,
                         source, created_by, due_soon_notified_at, overdue_notified_at, created_at)
SELECT p.workspace_id, c.project_id, c.person_id, c.id, c.next_action, 'TODO',
       coalesce(c.next_action_set_by, lead.user_id), c.next_action_due, 'MANUAL', c.next_action_set_by,
       now(), now(), coalesce(c.updated_at, now())
FROM app_lm_project_candidate c
JOIN app_lm_project p ON p.id = c.project_id
LEFT JOIN LATERAL (
    SELECT wm.user_id
    FROM app_lm_project_member pm
    JOIN app_lm_project_member_role pmr ON pmr.project_member_id = pm.id
    JOIN app_lm_role r ON r.id = pmr.role_id AND r.name = 'LEAD'
    JOIN app_lm_workspace_member wm ON wm.id = pm.member_id
    WHERE pm.project_id = c.project_id
    ORDER BY pm.created_at
    LIMIT 1
) lead ON true
WHERE c.next_action IS NOT NULL
  AND coalesce(c.next_action_set_by, lead.user_id) IS NOT NULL;

-- Which outreach events file a task by themselves, per workspace. No row means on.
CREATE TABLE app_lm_workspace_task_rule (
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    rule          varchar(16) NOT NULL
        CONSTRAINT app_lm_workspace_task_rule_rule_chk CHECK (rule IN ('REPLY', 'BOUNCE', 'BOOKED_CALL')),
    enabled       boolean     NOT NULL,
    updated_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, rule)
);

-- Who a note mentions, as the server kept them: only people who may work the note's position.
CREATE TABLE app_lm_note_mention (
    note_id     uuid        NOT NULL REFERENCES app_lm_person_note (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (note_id, user_id)
);

-- One person's bell. A row names a task or a note and never quotes a note; rows sharing a group key merge
-- while unread, so three replies on one position are one row.
CREATE TABLE app_lm_notification (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    recipient_user_id  uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    kind               varchar(24) NOT NULL
        CONSTRAINT app_lm_notification_kind_chk
            CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'MENTIONED', 'OUTREACH_REPLIED',
                            'OUTREACH_BOUNCED', 'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED')),
    actor_user_id      uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    project_id         uuid        REFERENCES app_lm_project (id) ON DELETE CASCADE,
    person_id          uuid        REFERENCES app_lm_person (id) ON DELETE CASCADE,
    candidate_id       uuid        REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    task_id            uuid        REFERENCES app_lm_task (id) ON DELETE CASCADE,
    note_id            uuid        REFERENCES app_lm_person_note (id) ON DELETE SET NULL,
    group_key          varchar(200) NOT NULL,
    event_count        integer     NOT NULL DEFAULT 1,
    details            jsonb       NOT NULL DEFAULT '{}'::jsonb,
    latest_at          timestamptz NOT NULL DEFAULT now(),
    read_at            timestamptz,
    snoozed_until      timestamptz,
    archived_at        timestamptz,
    -- When an email may go if the row is still unread; null when this row is never emailed.
    email_due_at       timestamptz,
    emailed_at         timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_notification IS
    'One person''s notifications. Staff-only tenant data: every read filters by workspace_id and recipient.';

CREATE TRIGGER app_lm_notification_touch BEFORE UPDATE ON app_lm_notification
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- The merge target: at most one live row per group, so a second event lands on it rather than beside it.
CREATE UNIQUE INDEX app_lm_notification_live_group_uk
    ON app_lm_notification (recipient_user_id, workspace_id, group_key)
    WHERE read_at IS NULL AND archived_at IS NULL;
CREATE INDEX app_lm_notification_inbox_idx
    ON app_lm_notification (recipient_user_id, workspace_id, latest_at DESC) WHERE archived_at IS NULL;
CREATE INDEX app_lm_notification_email_due_idx
    ON app_lm_notification (email_due_at) WHERE emailed_at IS NULL AND email_due_at IS NOT NULL;

-- A person's choice per kind and channel; no row means on. DIGEST's email column is the daily digest.
CREATE TABLE app_lm_notification_preference (
    user_id       uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    kind          varchar(24) NOT NULL
        CONSTRAINT app_lm_notification_preference_kind_chk
            CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE', 'MENTIONED', 'OUTREACH_REPLIED', 'OUTREACH_BOUNCED',
                            'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED', 'DIGEST')),
    in_app        boolean     NOT NULL,
    email         boolean     NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, workspace_id, kind)
);

-- One digest per person per workspace per local day, claimed before it is sent.
CREATE TABLE app_lm_notification_digest (
    user_id       uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    digest_date   date        NOT NULL,
    sent_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, workspace_id, digest_date)
);

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED',
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED',
                        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_REASSIGNED', 'TASK_CANCELLED'));
