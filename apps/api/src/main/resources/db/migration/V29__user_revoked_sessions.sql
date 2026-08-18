-- Settings → Security can end a session from another device. Not ADMIN_REVOKED (that says staff
-- intervened) and not LOGOUT (that is the device ending its own session).
--
-- An expected terminal state, so RevokeReason.indicatesTheftOnReplay() must keep answering false for
-- it: replaying a token revoked this way is a signed-out device catching up, not an attack.

ALTER TABLE app_lm_refresh_token
    DROP CONSTRAINT app_lm_refresh_token_revoked_reason_chk;

ALTER TABLE app_lm_refresh_token
    ADD CONSTRAINT app_lm_refresh_token_revoked_reason_chk
        CHECK (revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED',
                                  'ADMIN_REVOKED', 'USER_REVOKED'));
