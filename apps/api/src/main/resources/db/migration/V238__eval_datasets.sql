-- Eval datasets and their cases (#1319): what a prompt version is scored on before it goes live. Every case is
-- synthetic, so nothing here is a person's data. Platform tables: no workspace, and no reference into a tenant table.

CREATE TABLE app_lm_eval_dataset (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id   varchar(80)  NOT NULL,
    name        varchar(120) NOT NULL,
    description varchar(500),
    -- Null: shipped with a release.
    created_by  uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_eval_dataset_name_uk UNIQUE (prompt_id, name)
);

CREATE TABLE app_lm_eval_case (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id uuid         NOT NULL REFERENCES app_lm_eval_dataset (id),
    case_key   varchar(40)  NOT NULL,
    name       varchar(200) NOT NULL,
    tag        varchar(40),
    split      varchar(10)  NOT NULL CHECK (split IN ('DEV', 'HOLDOUT')),
    -- The one source there is. A real person's profile, note or transcript has no value to be stored under.
    source     varchar(12)  NOT NULL DEFAULT 'SYNTHETIC' CHECK (source = 'SYNTHETIC'),
    -- In the prompt's own prepared shape, which its eval subject checks before a case is added.
    input      jsonb        NOT NULL,
    -- Null for a prompt only a judge can score.
    expected   jsonb,
    created_by uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    retired_by uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    retired_at timestamptz,
    CONSTRAINT app_lm_eval_case_key_uk UNIQUE (dataset_id, case_key)
);

-- A run names the cases it scored, so a case is added and retired and never rewritten. Retiring sets retired_at
-- and retired_by together, once. Besides that an author column changes only to null, when its account is erased.
CREATE FUNCTION app_lm_eval_case_is_add_and_retire_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND (to_jsonb(NEW) - 'retired_at' - 'retired_by' - 'created_by')
           = (to_jsonb(OLD) - 'retired_at' - 'retired_by' - 'created_by')
       AND (NEW.created_by IS NOT DISTINCT FROM OLD.created_by OR NEW.created_by IS NULL)
       AND ((OLD.retired_at IS NULL AND NEW.retired_at IS NOT NULL)
            OR (NEW.retired_at IS NOT DISTINCT FROM OLD.retired_at
                AND (NEW.retired_by IS NOT DISTINCT FROM OLD.retired_by OR NEW.retired_by IS NULL))) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'app_lm_eval_case is add-and-retire only (attempted %)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_eval_case_add_and_retire_only
    BEFORE UPDATE OR DELETE ON app_lm_eval_case
    FOR EACH ROW EXECUTE FUNCTION app_lm_eval_case_is_add_and_retire_only();

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'EVAL_RUN',
        'Read the eval datasets, import and retire synthetic cases, and run a prompt version against them')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PLATFORM' AND a.name = 'EVAL_RUN'
WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
