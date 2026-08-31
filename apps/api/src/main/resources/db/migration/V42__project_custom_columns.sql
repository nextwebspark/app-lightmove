-- A mandate's grid stops being a fixed set of columns.
--
-- Every column the Companies screen shows today is one this codebase chose: a company's sector and
-- headcount, a person's title and seniority. A researcher arriving with a spreadsheet brings columns
-- nobody here chose — ethnicity, a client's own ranking, a notice period quoted in weeks — and the
-- import that drops them is an import that loses the half of the file the consultant cared about.
--
-- The obvious answer is DDL per project, and it is the wrong one: a table whose shape depends on
-- which tenant is asking cannot be migrated, indexed or reasoned about, and it hands the runtime role
-- the CREATE privilege ops/cloudsql/harden.sql exists to revoke. So the *values* live in a jsonb bag
-- on the row (V43) and this table holds only the definitions: what a project's extra columns are
-- called, what type they hold, and what order they sit in. The grid reads these and renders a column
-- per row of this table, so a column is real to the user and invisible to the schema.
--
-- Scoped to the project, because that is the scope the user asked for and the scope that makes sense:
-- a new mandate starts from the built-in columns alone, and a mandate that imported a file carrying
-- Ethnicity keeps an Ethnicity column for as long as it runs. Two mandates wanting the same extra
-- column is two rows here, not shared state — one of them renaming it must not rename the other's.

CREATE TABLE app_lm_project_custom_column (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,

    -- Which grid the column belongs to. A row on the Companies screen is a person at a company, so
    -- "Ethnicity" is a fact about the person and "Founded" a fact about the company; without this the
    -- import would have to guess which half of the row an unmapped column describes.
    target        varchar(16) NOT NULL
        CONSTRAINT app_lm_project_custom_column_target_chk
        CHECK (target IN ('COMPANY', 'CANDIDATE')),

    -- The jsonb key the values are written under. Slugged once from the label it was created with and
    -- never rewritten: renaming a header must not orphan every value already stored under the old key,
    -- which is the whole reason this is two columns rather than one.
    field_key     text        NOT NULL,
    -- What the grid header reads. Renameable, and the only half a user ever sees.
    label         text        NOT NULL,

    -- What a value in this column is. Deliberately four primitives and no select/multi-select: an
    -- option list is a second table and a second editor, and nothing in the import needs one yet.
    data_type     varchar(16) NOT NULL
        CONSTRAINT app_lm_project_custom_column_data_type_chk
        CHECK (data_type IN ('TEXT', 'NUMBER', 'DATE', 'BOOLEAN')),

    -- Where the column sits among its siblings. The built-in columns come first and are ordered by the
    -- frontend's own declaration; this orders only the custom ones after them.
    display_order integer     NOT NULL DEFAULT 0,
    -- Hidden is not deleted. Hiding takes a column off the grid and keeps every value in it, which is
    -- what a user reaching for "I do not want to see this" almost always means.
    hidden        boolean     NOT NULL DEFAULT false,

    created_by    uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_project_custom_column IS
    'The extra grid columns one mandate has defined. Values live in the rows'' custom_fields jsonb, never in DDL.';

COMMENT ON COLUMN app_lm_project_custom_column.field_key IS
    'The jsonb key values are stored under. Immutable — renaming the label must not orphan stored values.';

-- One definition per key per grid. This is what makes importing the same file twice idempotent: the
-- second run finds the column it created on the first rather than minting a near-duplicate.
CREATE UNIQUE INDEX app_lm_project_custom_column_key_uk
    ON app_lm_project_custom_column (project_id, target, field_key);

-- And one per *label*, case-insensitively, so "Ethnicity" and "ethnicity" cannot both sit in one
-- header row looking like a bug. The keys would differ; what a user sees would not.
CREATE UNIQUE INDEX app_lm_project_custom_column_label_uk
    ON app_lm_project_custom_column (project_id, target, lower(label));

-- The grid's only read: one project's columns for one grid, in the order they render.
CREATE INDEX app_lm_project_custom_column_order_idx
    ON app_lm_project_custom_column (project_id, target, display_order);

-- The foreign-key index V26 and V28 had to add after the fact for the earlier tables.
CREATE INDEX app_lm_project_custom_column_created_by_idx
    ON app_lm_project_custom_column (created_by);

CREATE TRIGGER app_lm_project_custom_column_touch BEFORE UPDATE ON app_lm_project_custom_column
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
