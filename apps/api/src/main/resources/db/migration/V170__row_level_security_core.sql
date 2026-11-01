-- Row-level security on the people and position core (#1084): the database's own backstop to the
-- application's workspace filters, which stay. Every connection the application borrows is set to the workspace it
-- serves (app.workspace_id, TenantAwareDataSource); a row of another workspace, or any row with no workspace set,
-- is neither read nor written. The design, and what changes with #256, is in the db-ops skill.
--
-- FORCE, because lm_app still owns these tables until #256 and an owner is otherwise exempt.

-- NULLIF: once a custom setting has been set in a session it reads back as '' after the transaction, not
-- NULL, and ''::uuid throws.
CREATE FUNCTION app_lm_current_workspace() RETURNS uuid
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT NULLIF(current_setting('app.workspace_id', true), '')::uuid $$;

-- Flyway's own session sets this (spring.flyway.init-sqls, the deploy step's -initSql), so a later backfill
-- still reaches every workspace's rows: Flyway runs as the same forced owner. #256 replaces it with a policy
-- for lm_migrate and drops it — anyone can call set_config, so it is no boundary once the owner is not lm_app.
CREATE FUNCTION app_lm_rls_maintenance() RETURNS boolean
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT coalesce(current_setting('app.rls_maintenance', true), '') = 'on' $$;

CREATE FUNCTION app_lm_tenant_visible(row_workspace_id uuid) RETURNS boolean
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT row_workspace_id = app_lm_current_workspace() OR app_lm_rls_maintenance() $$;

-- The two grid tables carry their project's workspace, so their policy is a plain comparison rather than a
-- probe of the project on every line. Filled from the project by trigger, so neither Hibernate nor
-- TriageCompanyWriter's native insert names it, and held to the project's by a composite key.
CREATE UNIQUE INDEX app_lm_project_id_workspace_uk ON app_lm_project (id, workspace_id);

ALTER TABLE app_lm_project_candidate ADD COLUMN workspace_id uuid;
UPDATE app_lm_project_candidate c SET workspace_id = p.workspace_id FROM app_lm_project p WHERE p.id = c.project_id;
ALTER TABLE app_lm_project_candidate
    ALTER COLUMN workspace_id SET NOT NULL,
    ADD CONSTRAINT app_lm_project_candidate_project_workspace_fk
        FOREIGN KEY (project_id, workspace_id) REFERENCES app_lm_project (id, workspace_id) ON DELETE CASCADE;

ALTER TABLE app_lm_project_triage_company ADD COLUMN workspace_id uuid;
UPDATE app_lm_project_triage_company t SET workspace_id = p.workspace_id FROM app_lm_project p WHERE p.id = t.project_id;
ALTER TABLE app_lm_project_triage_company
    ALTER COLUMN workspace_id SET NOT NULL,
    ADD CONSTRAINT app_lm_project_triage_company_project_workspace_fk
        FOREIGN KEY (project_id, workspace_id) REFERENCES app_lm_project (id, workspace_id) ON DELETE CASCADE;

-- Read through the project's own policy: a project of another workspace is not found, the column stays null
-- and the insert is refused.
CREATE FUNCTION app_lm_fill_workspace_from_project() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    SELECT workspace_id INTO NEW.workspace_id FROM app_lm_project WHERE id = NEW.project_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_lm_project_candidate_fill_workspace BEFORE INSERT ON app_lm_project_candidate
    FOR EACH ROW EXECUTE FUNCTION app_lm_fill_workspace_from_project();
CREATE TRIGGER app_lm_project_triage_company_fill_workspace BEFORE INSERT ON app_lm_project_triage_company
    FOR EACH ROW EXECUTE FUNCTION app_lm_fill_workspace_from_project();

DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['app_lm_project', 'app_lm_project_candidate', 'app_lm_project_triage_company',
                               'app_lm_person', 'app_lm_person_note', 'app_lm_person_document', 'app_lm_company_note']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('CREATE POLICY app_lm_tenant ON %I
                            USING (app_lm_tenant_visible(workspace_id))
                            WITH CHECK (app_lm_tenant_visible(workspace_id))', tbl);
    END LOOP;

    -- A person's children have no workspace of their own: they are visible exactly where their person is, the
    -- probe answered by the person's policy. Every read of them is keyed by person, so it is a primary-key lookup.
    FOREACH tbl IN ARRAY ARRAY['app_lm_person_contact', 'app_lm_person_tag', 'app_lm_person_document_version',
                               'app_lm_person_photo']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('CREATE POLICY app_lm_tenant ON %I
                            USING (EXISTS (SELECT 1 FROM app_lm_person p WHERE p.id = person_id))
                            WITH CHECK (EXISTS (SELECT 1 FROM app_lm_person p WHERE p.id = person_id))', tbl);
    END LOOP;
END
$$;
