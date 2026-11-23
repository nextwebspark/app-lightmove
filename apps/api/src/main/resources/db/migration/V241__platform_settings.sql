-- Application variables (#1314): a platform super admin's overrides of allowlisted lightmove.* values, and every
-- change made to one. The allowlist, its bounds and the defaults are code and yml; no row means the yml value.
-- Platform tables: no workspace, and no reference into a tenant table.

CREATE TABLE app_lm_platform_setting (
    key        varchar(120) PRIMARY KEY,
    value      text         NOT NULL,
    updated_by uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- Both values are what served before and after, so a reset reads as the default it went back to on that day.
CREATE TABLE app_lm_platform_setting_change (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key        varchar(120) NOT NULL,
    old_value  text         NOT NULL,
    new_value  text         NOT NULL,
    note       varchar(500) NOT NULL,
    changed_by uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    changed_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX app_lm_platform_setting_change_key_idx
    ON app_lm_platform_setting_change (key, id DESC);

-- A change is never rewritten or removed. UPDATE of the author column alone is let through: erasing an account sets
-- changed_by null, which must not be refused.
CREATE FUNCTION app_lm_platform_setting_change_is_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (to_jsonb(NEW) - 'changed_by') = (to_jsonb(OLD) - 'changed_by') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% is append-only (attempted %)', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_platform_setting_change_append_only
    BEFORE UPDATE OR DELETE ON app_lm_platform_setting_change
    FOR EACH ROW EXECUTE FUNCTION app_lm_platform_setting_change_is_append_only();

COMMENT ON TABLE app_lm_platform_setting IS
    'Overrides of allowlisted application variables, set from the platform console. No row: the yml value serves.';
COMMENT ON TABLE app_lm_platform_setting_change IS
    'Every change and reset of an application variable, with its note. Append-only.';

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'PLATFORM_SETTINGS_MANAGE',
        'Read the application variables and their history, change one and reset it to its default')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PLATFORM' AND a.name = 'PLATFORM_SETTINGS_MANAGE'
WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
