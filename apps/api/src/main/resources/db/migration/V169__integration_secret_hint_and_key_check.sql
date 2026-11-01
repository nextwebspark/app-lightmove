-- Settings → Integrations shows a saved own app as read-only rows: the secret's last four characters (the identifier
-- Google's console shows; the rest stays ciphertext), who saved it and when, and whether the provider accepted the
-- keys the last time it was asked — by Save and verify, Verify again, or a mailbox refresh it refused.
-- keys_refused_with is the provider's code (AADSTS7000215, invalid_client), null when it accepted.
-- No backfill: no SQL can read the ciphertext, so a secret saved before this reads its hint at runtime.
ALTER TABLE app_lm_workspace_mail_integration
    ADD COLUMN secret_hint varchar(4),
    ADD COLUMN secret_saved_at timestamptz,
    ADD COLUMN secret_saved_by uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD COLUMN keys_checked_at timestamptz,
    ADD COLUMN keys_refused_with varchar(100),
    ADD CONSTRAINT app_lm_workspace_mail_integration_secret_facts_chk
        CHECK (mode = 'OWN' OR (secret_hint IS NULL AND secret_saved_at IS NULL AND secret_saved_by IS NULL
                                AND keys_checked_at IS NULL AND keys_refused_with IS NULL)),
    ADD CONSTRAINT app_lm_workspace_mail_integration_key_check_chk
        CHECK (keys_refused_with IS NULL OR keys_checked_at IS NOT NULL);
