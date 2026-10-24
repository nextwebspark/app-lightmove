-- Working hours and the daily email cap, a workspace's default and each consultant's own.
--
-- The calendar is the truth for when a consultant is busy; these say only when they work, which an empty calendar
-- cannot. A booking link offers its times inside them; a consultant booking a call themselves is never held to them.
-- Outlook's own working hours, where a direct Microsoft mailbox states them, come first and are never stored here.
--
-- weekly_hours is one document per row, {"SUNDAY": {"start": "09:00", "end": "18:00"}, ...}, a missing day closed
-- (shape owned by WeeklyHours). On the workspace row null means "not chosen": the week is then the one the firm's
-- country keeps, so no existing workspace needs a backfill. On a consultant's row every null column means "use the
-- workspace's".
--
-- daily_email_cap replaces app_lm_mailbox_connection.daily_cap, which was copied from the deployment's setting at a
-- first connect and never changed. That column is frozen: still written, since it is NOT NULL, never read. A
-- consultant's cap only ever lowers the workspace's; the service refuses one above it, and the lower of the two is
-- what a send counts against.

CREATE TABLE app_lm_workspace_availability (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    weekly_hours    jsonb
        CONSTRAINT app_lm_workspace_availability_hours_chk CHECK (weekly_hours IS NULL
            OR jsonb_typeof(weekly_hours) = 'object'),
    daily_email_cap integer
        CONSTRAINT app_lm_workspace_availability_cap_chk CHECK (daily_email_cap BETWEEN 1 AND 1000),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_availability_workspace_uk UNIQUE (workspace_id)
);

COMMENT ON TABLE app_lm_workspace_availability IS
    'A workspace''s default working hours and daily email cap per mailbox. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_availability_touch BEFORE UPDATE ON app_lm_workspace_availability
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE TABLE app_lm_member_availability (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    weekly_hours    jsonb
        CONSTRAINT app_lm_member_availability_hours_chk CHECK (weekly_hours IS NULL
            OR jsonb_typeof(weekly_hours) = 'object'),
    daily_email_cap integer
        CONSTRAINT app_lm_member_availability_cap_chk CHECK (daily_email_cap BETWEEN 1 AND 1000),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_member_availability_member_uk UNIQUE (workspace_id, user_id)
);

COMMENT ON TABLE app_lm_member_availability IS
    'A consultant''s own working hours and daily email cap in one workspace; a null column is the workspace''s. Tenant data: every read filters by workspace_id and user_id.';

CREATE TRIGGER app_lm_member_availability_touch BEFORE UPDATE ON app_lm_member_availability
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

COMMENT ON COLUMN app_lm_mailbox_connection.daily_cap IS
    'Frozen since V153: written at a first connect, never read. The cap is app_lm_workspace_availability / app_lm_member_availability.';
