-- The position brief: what the Position screen edits. One per project (the mandate IS the role),
-- kept off app_lm_project so the list query stays lean — this is a large sparse document only one
-- screen reads. Criteria/competencies/benefits are owned ordered lists (replace-list API), so they
-- carry no identity of their own: composite PK (position_id, sort_order), no timestamps.
--
-- Positions are seeded in Java from a role-template library at project creation; projects that
-- predate this migration get theirs lazily on first GET (no SQL backfill — it would duplicate the
-- template-matching logic here).

CREATE TABLE app_lm_position (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       uuid        NOT NULL UNIQUE REFERENCES app_lm_project (id) ON DELETE CASCADE,
    mandate_reason   varchar(32) NOT NULL DEFAULT 'NEW_ROLE'
        CONSTRAINT app_lm_position_reason_chk CHECK (mandate_reason IN
            ('NEW_ROLE', 'BACKFILL', 'SUCCESSION', 'RESTRUCTURING')),
    internal_context text,
    narrative        text,
    reports_to       varchar(160),
    direct_reports   varchar(160),
    team_size        varchar(120),
    location         varchar(120),
    employment_type  varchar(80),
    start_target     date,
    salary_min       bigint,
    salary_max       bigint,
    currency         varchar(3)  NOT NULL DEFAULT 'USD',
    notice           varchar(120),
    bonus_target     varchar(160),
    ltip             varchar(160),
    confidential     boolean     NOT NULL DEFAULT false,
    locked_at        timestamptz,
    locked_by        uuid        REFERENCES app_lm_user (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_position_touch BEFORE UPDATE ON app_lm_position
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE TABLE app_lm_position_benefit (
    position_id uuid        NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    sort_order  integer     NOT NULL,
    label       varchar(80) NOT NULL,
    PRIMARY KEY (position_id, sort_order)
);

CREATE TABLE app_lm_position_criterion (
    position_id uuid         NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL,
    text        varchar(300) NOT NULL,
    mode        varchar(16)  NOT NULL
        CONSTRAINT app_lm_position_criterion_mode_chk CHECK (mode IN ('REQUIRED', 'PREFERRED')),
    from_brief  boolean      NOT NULL DEFAULT false,
    PRIMARY KEY (position_id, sort_order)
);

-- One ordered list for both panels; panel is a field, sort_order spans the whole collection
-- (@OrderColumn owns it). The service splits by panel when building the response.
CREATE TABLE app_lm_position_competency (
    position_id uuid         NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL,
    panel       varchar(16)  NOT NULL
        CONSTRAINT app_lm_position_competency_panel_chk CHECK (panel IN ('TECHNICAL', 'BEHAVIOURAL')),
    name        varchar(120) NOT NULL,
    weight      integer      NOT NULL
        CONSTRAINT app_lm_position_competency_weight_chk CHECK (weight BETWEEN 0 AND 100),
    PRIMARY KEY (position_id, sort_order)
);

-- Unlocking a locked brief invalidates downstream benchmarking, so it is not part of PROJECT_EDIT:
-- only the project ADMIN role grants it (plus the standing workspace-admin bypass).
INSERT INTO app_lm_action (scope, name, description)
VALUES ('PROJECT', 'POSITION_UNLOCK', 'Unlock a locked position brief for editing');

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PROJECT' AND a.name = 'POSITION_UNLOCK'
WHERE r.scope = 'PROJECT' AND r.name = 'ADMIN';
