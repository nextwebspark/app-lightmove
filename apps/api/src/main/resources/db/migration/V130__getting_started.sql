-- The Getting started checklist on My positions (#806). Each step ticks itself from the workspace's own rows;
-- this table holds only what those rows cannot: whether this person dismissed the card, which steps they
-- skipped, and when each step was first seen done — the trial's activation measure (signup to step N).

CREATE TABLE app_lm_getting_started (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id          uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    dismissed_at     timestamptz,
    skipped_steps    jsonb        NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT app_lm_getting_started_skipped_chk CHECK (
            jsonb_typeof(skipped_steps) = 'array'
            AND skipped_steps <@ '["OPEN_POSITION", "WRITE_BRIEF", "FIND_COMPANIES", "MAP_EXECUTIVES",
                                   "CONNECT_MAILBOX", "INVITE_COLLEAGUE"]'::jsonb),
    completed_steps  jsonb        NOT NULL DEFAULT '{}'::jsonb
        CONSTRAINT app_lm_getting_started_completed_chk CHECK (jsonb_typeof(completed_steps) = 'object'),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_getting_started_member_uk UNIQUE (workspace_id, user_id)
);

COMMENT ON TABLE app_lm_getting_started IS
    'One person''s Getting started checklist in one workspace. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_getting_started_touch BEFORE UPDATE ON app_lm_getting_started
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
