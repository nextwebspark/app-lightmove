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

        /** How long an access token is usable. Short by design — revocation is via the refresh token. */
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("24h") Duration verificationTokenTtl,

        /**
         * Much shorter than {@link #verificationTokenTtl}: a verification link only proves a mailbox,
         * where a reset link <i>changes a credential</i> — a stale one sitting in an inbox is a
         * standing invitation to whoever reads that inbox later.
         */
        @DefaultValue("30m") Duration passwordResetTokenTtl,
        @DefaultValue("7d") Duration invitationTtl,

        /**
         * When true, an unverified user may sign in but cannot reach any workspace data.
         *
         * <p>On, and it must stay on. A user's email domain decides which organisation they belong
         * to, so an <i>unverified</i> address is just an unproven claim — without this gate anyone
         * could type {@code sara@nextwebspark.com} and be let into that firm's workspace. The
         * verification email is what turns the claim into evidence.
         */
        @DefaultValue("true") boolean requireVerifiedEmail,

        /**
         * Development only: a new signup is marked verified on the spot and no verification email is
         * sent. It skips one step — proving the mailbox — and moves nothing else: a join request still
         * waits for an admin, and the role is still the admin's to pick.
         *
         * <p>Off, and it must stay off outside a developer's machine. On in production, anyone could
         * claim {@code sara@nextwebspark.com} and be let into that firm's workspace — the address is
         * what decides which firm someone works at, and this is what proves the address. See
         * {@link #requireVerifiedEmail}.
         */
        @DefaultValue("false") boolean autoVerifyEmail,

        /** BCrypt cost. 12 ≈ 250ms per hash on current hardware — expensive for an attacker, tolerable for us. */
        @DefaultValue("12") int bcryptStrength,

        OAuthQuirkSettings oauth
) {

    /**
     * Both branches are absent from yml in the normal case: no provider needs a quirk until one does,
     * and the extension's defaults are the intended values rather than a placeholder.
     */
    public AuthSettings {
        oauth = oauth == null ? new OAuthQuirkSettings(List.of(), List.of(), List.of()) : oauth;
        extension = extension == null ? ExtensionSettings.defaults() : extension;
    }
}
