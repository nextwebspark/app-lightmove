-- The prompt registry (#1316): every system prompt as immutable versions, a label naming the one that serves, a daily
-- count of the calls each version answered, and the counter every instance watches to reload platform configuration.
-- Platform tables: no workspace, and no reference into a tenant table.

CREATE TABLE app_lm_platform_prompt_version (
    prompt_id      varchar(80)  NOT NULL,
    version        integer      NOT NULL CHECK (version > 0),
    body           text         NOT NULL,
    -- Both null: follow the deployment's configured model.
    provider       varchar(40),
    model_id       varchar(80),
    -- Null, or a key left out: the calling feature's own default.
    settings       jsonb,
    origin         varchar(16)  NOT NULL CHECK (origin IN ('BUNDLED', 'EDITED')),
    bundled_sha256 char(64),
    change_note    varchar(300),
    created_by     uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (prompt_id, version),
    CONSTRAINT app_lm_platform_prompt_version_model CHECK ((provider IS NULL) = (model_id IS NULL)),
    CONSTRAINT app_lm_platform_prompt_version_bundled CHECK ((origin = 'BUNDLED') = (bundled_sha256 IS NOT NULL))
);

-- One version per shipped text: two instances booting on the same release record it once.
CREATE UNIQUE INDEX app_lm_platform_prompt_version_bundled_idx
    ON app_lm_platform_prompt_version (prompt_id, bundled_sha256) WHERE bundled_sha256 IS NOT NULL;

CREATE TABLE app_lm_platform_prompt_label (
    prompt_id varchar(80)  NOT NULL,
    label     varchar(40)  NOT NULL,
    version   integer      NOT NULL,
    moved_by  uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    moved_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (prompt_id, label),
    FOREIGN KEY (prompt_id, version) REFERENCES app_lm_platform_prompt_version (prompt_id, version)
);

CREATE TABLE app_lm_platform_prompt_label_move (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prompt_id    varchar(80)  NOT NULL,
    label        varchar(40)  NOT NULL,
    from_version integer,
    to_version   integer      NOT NULL,
    reason       varchar(500),
    moved_by     uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    moved_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX app_lm_platform_prompt_label_move_prompt_idx
    ON app_lm_platform_prompt_label_move (prompt_id, id DESC);

-- A version is never rewritten and a move never unsaid. Per row, and UPDATE of the author column alone is let
-- through: erasing an account sets created_by / moved_by null, which must not be refused.
CREATE FUNCTION app_lm_platform_prompt_is_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF TG_TABLE_NAME = 'app_lm_platform_prompt_version'
           AND (to_jsonb(NEW) - 'created_by') = (to_jsonb(OLD) - 'created_by') THEN
            RETURN NEW;
        END IF;
        IF TG_TABLE_NAME = 'app_lm_platform_prompt_label_move'
           AND (to_jsonb(NEW) - 'moved_by') = (to_jsonb(OLD) - 'moved_by') THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION '% is append-only (attempted %)', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_platform_prompt_version_append_only
    BEFORE UPDATE OR DELETE ON app_lm_platform_prompt_version
    FOR EACH ROW EXECUTE FUNCTION app_lm_platform_prompt_is_append_only();

CREATE TRIGGER app_lm_platform_prompt_label_move_append_only
    BEFORE UPDATE OR DELETE ON app_lm_platform_prompt_label_move
    FOR EACH ROW EXECUTE FUNCTION app_lm_platform_prompt_is_append_only();

-- Which version and model answered, per day (UTC). Counts only: no prompt and no answer.
CREATE TABLE app_lm_platform_prompt_call_daily (
    prompt_id         varchar(80) NOT NULL,
    version           integer     NOT NULL,
    provider          varchar(40) NOT NULL,
    model_id          varchar(80) NOT NULL,
    day               date        NOT NULL,
    calls             bigint      NOT NULL DEFAULT 0,
    input_tokens      bigint      NOT NULL DEFAULT 0,
    output_tokens     bigint      NOT NULL DEFAULT 0,
    finish_stop       bigint      NOT NULL DEFAULT 0,
    finish_max_tokens bigint      NOT NULL DEFAULT 0,
    finish_safety     bigint      NOT NULL DEFAULT 0,
    finish_other      bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (prompt_id, version, provider, model_id, day)
);

-- Raised in the transaction that changes platform configuration; every instance reloads a scope whose number moved.
CREATE TABLE app_lm_platform_config_version (
    scope   varchar(40) PRIMARY KEY,
    version bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_platform_prompt_version IS
    'Immutable versions of each system prompt: BUNDLED from a release''s prompts/*.st, EDITED from the platform console.';
COMMENT ON TABLE app_lm_platform_prompt_label IS
    'Which version of a prompt serves: the production label.';
COMMENT ON TABLE app_lm_platform_prompt_call_daily IS
    'AI calls per prompt version, model and UTC day. Counts and token totals only, never content.';
