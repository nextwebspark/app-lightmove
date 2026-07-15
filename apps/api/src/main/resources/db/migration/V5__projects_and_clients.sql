-- Projects and clients: the minimal mandate surface the Workspace screen needs.
-- A client is the hiring entity a mandate is run for — today just a name to hang projects off,
-- created inline from the New-project modal; the registry form is a separate build.
-- Pipeline counts (companies / candidates) are deliberately absent: no pipeline tables exist yet.

CREATE TABLE app_lm_client (
    id           uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    workspace_id uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    name         varchar(160)  NOT NULL,
    hq_country   varchar(64),
    created_by   uuid          NOT NULL REFERENCES app_lm_user (id),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    version      bigint        NOT NULL DEFAULT 0
);

-- One record per hiring entity per workspace; the modal's create-inline path must find, not duplicate.
CREATE UNIQUE INDEX app_lm_client_workspace_name_uk ON app_lm_client (workspace_id, lower(name));

CREATE TABLE app_lm_project (
    id             uuid PRIMARY KEY      DEFAULT gen_random_uuid(),
    workspace_id   uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    client_id      uuid          NOT NULL REFERENCES app_lm_client (id),
    position_title varchar(160)  NOT NULL,
    stage          varchar(32)   NOT NULL DEFAULT 'BRIEF'
        CONSTRAINT app_lm_project_stage_chk CHECK (stage IN
            ('BRIEF', 'UNIVERSE', 'LOCKED', 'MAPPING', 'OUTREACH', 'DELIVERED', 'CLOSED')),
    target_date    date,
    created_by     uuid          NOT NULL REFERENCES app_lm_user (id),
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    version        bigint        NOT NULL DEFAULT 0
);

CREATE INDEX app_lm_project_workspace_idx ON app_lm_project (workspace_id);
CREATE INDEX app_lm_project_client_idx    ON app_lm_project (client_id);

-- Who works a mandate. References the membership row, not the user, so a project team physically
-- cannot contain a member of another workspace. The project role (LEAD/MEMBER) is orthogonal to the
-- workspace role — a workspace RESEARCHER may lead a project.
CREATE TABLE app_lm_project_member (
    id         uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    project_id uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    member_id  uuid         NOT NULL REFERENCES app_lm_workspace_member (id) ON DELETE CASCADE,
    role       varchar(32)  NOT NULL DEFAULT 'MEMBER'
        CONSTRAINT app_lm_project_member_role_chk CHECK (role IN ('LEAD', 'MEMBER')),
    added_by   uuid         REFERENCES app_lm_user (id),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_project_member_uk UNIQUE (project_id, member_id)
);

-- Exactly one lead per project, enforced where it cannot be forgotten.
CREATE UNIQUE INDEX app_lm_project_member_lead_uk ON app_lm_project_member (project_id) WHERE role = 'LEAD';
CREATE INDEX app_lm_project_member_member_idx ON app_lm_project_member (member_id);

DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['app_lm_client', 'app_lm_project', 'app_lm_project_member']
    LOOP
        EXECUTE format(
                'CREATE TRIGGER %I_touch BEFORE UPDATE ON %I
                 FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at()', tbl, tbl);
    END LOOP;
END $$;
