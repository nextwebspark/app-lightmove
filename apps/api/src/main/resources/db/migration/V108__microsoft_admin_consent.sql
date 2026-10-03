-- Microsoft's admin consent for Uncava's shared app (epic #642, story #645).
--
-- A customer's IT department approves the multi-tenant app once, through the link Settings → Integrations shows,
-- and Microsoft sends the approving admin back with the directory it approved for. That return is recorded here so
-- the page can say "Approved for your organisation". Informational only: it gates nothing, since a consultant's own
-- sign-in is what Microsoft actually checks.

ALTER TABLE app_lm_workspace_mail_integration
    ADD COLUMN admin_consented_at timestamptz,
    ADD COLUMN admin_consent_tenant_id varchar(64),
    ADD COLUMN admin_consented_by uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT app_lm_workspace_mail_integration_admin_consent_chk
        CHECK (admin_consented_at IS NULL OR provider = 'MICROSOFT');
