-- The last warning sent ahead of an own app's client secret expiring (#650): the fewest days left it was sent
-- for (30, 7 or 0), so each threshold warns once. A new expiry date, or a return to the shared app, clears it.
ALTER TABLE app_lm_workspace_mail_integration
    ADD COLUMN secret_expiry_warned_days integer,
    ADD CONSTRAINT app_lm_workspace_mail_integration_expiry_warning_chk
        CHECK (secret_expiry_warned_days IS NULL OR secret_expires_on IS NOT NULL);
