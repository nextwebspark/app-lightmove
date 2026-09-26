package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Everything tunable about authentication — {@code lightmove.auth.*}. */
public record AuthSettings(
        JwtSettings jwt,
        CookieSettings cookie,
        LockoutSettings lockout,
        RateLimitSettings rateLimit,
        ExtensionSettings extension,

        /** Short by design: revocation is via the refresh token. */
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("24h") Duration verificationTokenTtl,

        /** Much shorter than {@link #verificationTokenTtl}: a reset link changes a credential. */
        @DefaultValue("30m") Duration passwordResetTokenTtl,
        @DefaultValue("7d") Duration invitationTtl,

        /** The authorisation-request cookie's lifetime: long enough for consent, short for a {@code code_verifier}. */
        @DefaultValue("10m") Duration oauthRequestTtl,

        /** Must stay on: without it anyone could claim {@code sara@nextwebspark.com} and join that firm. */
        @DefaultValue("true") boolean requireVerifiedEmail,

        /** Development only: skips proving the mailbox, so in production it defeats {@link #requireVerifiedEmail}. */
        @DefaultValue("false") boolean autoVerifyEmail,

        /** 12 ≈ 250ms per hash. */
        @DefaultValue("12") int bcryptStrength,

        OAuthQuirkSettings oauth
) {

    public AuthSettings {
        oauth = oauth == null ? new OAuthQuirkSettings(List.of(), List.of(), List.of()) : oauth;
        extension = extension == null ? ExtensionSettings.defaults() : extension;
    }
}
