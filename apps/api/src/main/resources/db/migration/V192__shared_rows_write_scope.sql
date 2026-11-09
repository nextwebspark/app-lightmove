-- A row of no workspace (#1173 review, R1). V190 let any workspace's session write the shared template library and
-- read or write an OAuth grant still waiting for its consent. Now only a connection the application opened for them
-- (SharedRows, app.shared_rows) reaches either: the library stays readable by every session, a pending grant by none.
-- TenantAwareDataSource sets the flag on every checkout, so it never outlives its borrower.

CREATE FUNCTION app_lm_shared_rows() RETURNS boolean
    LANGUAGE sql STABLE PARALLEL SAFE
    AS $$ SELECT current_setting('app.shared_rows', true) = 'on' OR app_lm_rls_maintenance() $$;

DROP POLICY app_lm_tenant ON app_lm_position_template;
CREATE POLICY app_lm_tenant_read ON app_lm_position_template FOR SELECT
    USING (workspace_id IS NULL OR app_lm_tenant_visible(workspace_id));
CREATE POLICY app_lm_tenant_insert ON app_lm_position_template FOR INSERT
    WITH CHECK (app_lm_tenant_visible(workspace_id) OR (workspace_id IS NULL AND app_lm_shared_rows()));
CREATE POLICY app_lm_tenant_update ON app_lm_position_template FOR UPDATE
    USING (app_lm_tenant_visible(workspace_id) OR (workspace_id IS NULL AND app_lm_shared_rows()))
    WITH CHECK (app_lm_tenant_visible(workspace_id) OR (workspace_id IS NULL AND app_lm_shared_rows()));
CREATE POLICY app_lm_tenant_delete ON app_lm_position_template FOR DELETE
    USING (app_lm_tenant_visible(workspace_id) OR (workspace_id IS NULL AND app_lm_shared_rows()));

-- A library template's keywords are read where it is, and written only where it may be.
DROP POLICY app_lm_tenant ON app_lm_position_template_keyword;
CREATE POLICY app_lm_tenant_read ON app_lm_position_template_keyword FOR SELECT
    USING (EXISTS (SELECT 1 FROM app_lm_position_template parent WHERE parent.id = template_id));
CREATE POLICY app_lm_tenant_write ON app_lm_position_template_keyword FOR ALL
    USING (EXISTS (SELECT 1 FROM app_lm_position_template parent WHERE parent.id = template_id
                   AND (parent.workspace_id IS NOT NULL OR app_lm_shared_rows())))
    WITH CHECK (EXISTS (SELECT 1 FROM app_lm_position_template parent WHERE parent.id = template_id
                        AND (parent.workspace_id IS NOT NULL OR app_lm_shared_rows())));

-- Its retired refresh hashes follow the grant through their own policy, unchanged.
DROP POLICY app_lm_tenant ON app_lm_oauth_authorization;
CREATE POLICY app_lm_tenant ON app_lm_oauth_authorization
    USING (app_lm_tenant_visible(workspace_id) OR (workspace_id IS NULL AND app_lm_shared_rows()))
    WITH CHECK (app_lm_tenant_visible(workspace_id) OR (workspace_id IS NULL AND app_lm_shared_rows()));
