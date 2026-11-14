-- #928: every signup door asks for the terms tick. A first sign-in with a provider creates no account until it is
-- given: the identity the provider proved waits here for ten minutes, named by the hash of a cookie the browser holds.
-- Global, as the accounts it becomes are.
CREATE TABLE app_lm_pending_registration (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash      varchar(64)   NOT NULL,
    provider        varchar(32)   NOT NULL,
    subject         varchar(255)  NOT NULL,
    email           varchar(255)  NOT NULL CONSTRAINT app_lm_pending_registration_email_lower_chk
                                           CHECK (email = lower(email)),
    full_name       varchar(160)  NOT NULL,
    picture_url     varchar(2048),
    expires_at      timestamptz   NOT NULL,
    consumed_at     timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_pending_registration_token_uk UNIQUE (token_hash)
);

CREATE INDEX app_lm_pending_registration_identity_idx ON app_lm_pending_registration (provider, subject);
CREATE INDEX app_lm_pending_registration_expires_idx ON app_lm_pending_registration (expires_at);

-- The data processing agreement the founder accepted for the firm, when the deployment names one. A contract record,
-- so the workspace's tombstone keeps it.
ALTER TABLE app_lm_workspace
    ADD COLUMN dpa_accepted_at timestamptz,
    ADD COLUMN dpa_version     varchar(32),
    ADD COLUMN dpa_accepted_by uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT app_lm_workspace_dpa_chk CHECK ((dpa_accepted_at IS NULL) = (dpa_version IS NULL));
