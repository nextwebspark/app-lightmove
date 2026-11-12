-- Two-factor authentication, part 1 (epic #1080, story #1081): an authenticator-app factor per person, its
-- recovery codes, and the short-lived challenge a correct password earns instead of a session.
--
-- Account data, not tenant data: a factor is the person's, whatever workspaces they belong to (V81), because
-- sign-in happens before a workspace is chosen. Every read filters by the principal's user id.

CREATE TABLE app_lm_user_mfa_factor (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    -- The shared secret, held encrypted (core/crypto, bound to the user), never returned after setup or logged.
    secret_encrypted text        NOT NULL,
    -- Null while setup waits for its first code; a pending row is replaced by the next setup.
    enabled_at       timestamptz,
    -- The last 30-second step a code was accepted for: a code is refused again within the same step.
    last_used_step   bigint,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_user_mfa_factor_user_uk UNIQUE (user_id)
);

COMMENT ON TABLE app_lm_user_mfa_factor IS
    'A person''s authenticator-app factor. Account data: every read filters by user_id, never by workspace.';

CREATE TRIGGER app_lm_user_mfa_factor_touch
    BEFORE UPDATE ON app_lm_user_mfa_factor
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Each code a SHA-256 hash, spent by one conditional update so two sign-ins cannot both use it.
CREATE TABLE app_lm_user_mfa_recovery_code (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    code_hash  varchar(64) NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_user_mfa_recovery_code_uk UNIQUE (user_id, code_hash)
);

-- What a correct password earns a person with a factor: single use, a few minutes, stored as its hash only.
CREATE TABLE app_lm_mfa_challenge (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_mfa_challenge_token_uk UNIQUE (token_hash)
);

CREATE INDEX app_lm_mfa_challenge_user_idx ON app_lm_mfa_challenge (user_id);

-- Turning two-factor on or off ends the person's other sessions: an ordinary end, not theft on replay.
ALTER TABLE app_lm_refresh_token
    DROP CONSTRAINT app_lm_refresh_token_revoked_reason_chk;

ALTER TABLE app_lm_refresh_token
    ADD CONSTRAINT app_lm_refresh_token_revoked_reason_chk
        CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED',
                                  'ADMIN_REVOKED', 'USER_REVOKED', 'SUPERSEDED', 'MFA_CHANGED'));
