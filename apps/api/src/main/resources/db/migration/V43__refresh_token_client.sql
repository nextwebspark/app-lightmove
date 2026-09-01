-- Which client a refresh-token family was opened for.
--
-- The fence at /auth/extension/refresh used to recognise an extension family by user_agent holding the
-- extension's session label — a display column deciding which route a credential may be redeemed on.
-- It worked only because TokenService refused to store that label for a web caller, since a web family
-- carrying it could never refresh again. A column no client supplies removes the guess and the refusal.

ALTER TABLE app_lm_refresh_token
    ADD COLUMN client varchar(32) NOT NULL DEFAULT 'WEB_APP';

-- Nothing but the pairing route was ever permitted to write that label, so it identifies the existing
-- extension families exactly.
UPDATE app_lm_refresh_token
SET client = 'BROWSER_EXTENSION'
WHERE user_agent = 'LightMove Capture (browser extension)';

ALTER TABLE app_lm_refresh_token
    ADD CONSTRAINT app_lm_refresh_token_client_chk
        CHECK (client IN ('WEB_APP', 'BROWSER_EXTENSION'));

-- Pairing again ends the extension session the account already held. An expected terminal state, so
-- RevokeReason.indicatesTheftOnReplay() must answer false for SUPERSEDED — the same argument V29 makes
-- for USER_REVOKED: a stale device replaying its token is catching up, not attacking.
ALTER TABLE app_lm_refresh_token
    DROP CONSTRAINT app_lm_refresh_token_revoked_reason_chk;

ALTER TABLE app_lm_refresh_token
    ADD CONSTRAINT app_lm_refresh_token_revoked_reason_chk
        CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED',
                                  'ADMIN_REVOKED', 'USER_REVOKED', 'SUPERSEDED'));
