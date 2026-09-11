-- The role-template library is edited in the app rather than by migration.
--
-- V42 left workspace_id nullable for exactly this. NULL is the LightMove library, edited by a platform
-- super admin (V51); a non-null row is one firm's, edited by its workspace admin. A firm's row sharing a
-- library row's code is that firm's copy and shadows the library row in every read — so an edit to the
-- library reaches every firm that never customised the template, and none of the firms that did. There
-- is no foreign key between the two: a library row is identified by its code alone (V42's partial unique
-- index), and a firm's copy outlives an archived library row.
--
-- revised_at moves only when a template's content is saved, unlike updated_at, which archiving touches
-- too. customised_from is the library row's revised_at at the moment a firm took its copy; a library
-- revision after it is what the Templates screen reports as "library updated". It is NULL on library
-- rows and on templates a firm wrote from scratch, which is how the two kinds of firm row are told apart.

ALTER TABLE app_lm_position_template
    ADD COLUMN revised_at      timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN revised_by      uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD COLUMN customised_from timestamptz;

COMMENT ON COLUMN app_lm_position_template.customised_from IS
    'On a workspace copy of a library template: the library row''s revised_at when the copy was taken. NULL on library rows and on a workspace''s own templates.';

-- A firm hiding a library template it does not want offered. A row of its own rather than a copy of the
-- template with active = false: showing it again must hand back the live library template, not a
-- snapshot of the one that was hidden.
CREATE TABLE app_lm_position_template_hidden (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    code         varchar(64) NOT NULL,
    hidden_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,

    CONSTRAINT app_lm_position_template_hidden_uk UNIQUE (workspace_id, code)
);

COMMENT ON TABLE app_lm_position_template_hidden IS
    'Library templates a workspace has hidden from its picker and from title matching, by code.';
