package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every tunable the application has, in one typed tree.
 *
 * <p>Records make these immutable and fail-fast: a typo in a key surfaces at startup as a binding
 * error, not at 3am as a {@code null} in a token expiry calculation.
 */
@ConfigurationProperties(prefix = "lightmove")
public record LightMoveProperties(
        Auth auth,
        Email email,
        Web web
) {

    public record Auth(
            Jwt jwt,
            Cookie cookie,
            Lockout lockout,
            RateLimit rateLimit,

            /** How long an access token is usable. Short by design — revocation is via the refresh token. */
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("30d") Duration refreshTokenTtl,
            @DefaultValue("24h") Duration verificationTokenTtl,
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
            @DefaultValue("12") int bcryptStrength
    ) {

        public record Jwt(
                @DefaultValue("lightmove") String issuer,
                /** Spring Resource locations, so classpath:, file: and env-injected PEM all work unchanged. */
                @DefaultValue("file:.keys/jwt-private.pem") String privateKeyLocation,
                @DefaultValue("file:.keys/jwt-public.pem") String publicKeyLocation
        ) {}

        public record Cookie(
                @DefaultValue("lm_refresh") String name,
                /**
                 * Scoped to the auth endpoints, so the refresh token is not attached to every ordinary
                 * API call — it is only ever on the wire when it is actually being redeemed.
                 */
                @DefaultValue("/api/v1/auth") String path,
                @DefaultValue("true") boolean httpOnly,
                /** Must be true in production. False locally only because dev runs over plain http. */
                @DefaultValue("true") boolean secure,
                @DefaultValue("Strict") String sameSite,
                String domain
        ) {}

        public record Lockout(
                @DefaultValue("5") int maxFailedAttempts,
                @DefaultValue("15m") Duration duration
        ) {}

        public record RateLimit(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("10") int loginAttemptsPerMinute,
                @DefaultValue("5") int signupAttemptsPerHour,
                @DefaultValue("3") int verificationResendsPerHour
        ) {}
    }

    public record Email(
            /**
             * {@code log} prints the message to the console; {@code resend} actually sends it.
             *
             * <p>Defaults to {@code resend} so a deployment sends real mail without an extra env var to
             * remember. The test profile and {@code application-local.yml.example} pin {@code log}, so the
             * build and a fresh clone stay runnable without a provider account.
             */
            @DefaultValue("resend") String provider,
            @DefaultValue("LightMove") String fromName,
            @DefaultValue("noreply@lightmove.ai") String fromAddress,
            Resend resend,
            Validation validation
    ) {

        public record Resend(
                String apiKey,
                @DefaultValue("https://api.resend.com") String baseUrl
        ) {}

        public record Validation(
                /**
                 * Reject addresses whose domain publishes no MX record. Cheap (one DNS lookup), and it
                 * catches the overwhelmingly common case of a typo'd domain before we waste a send and
                 * a bounce on it.
                 */
                @DefaultValue("true") boolean mxCheckEnabled,

                /**
                 * Reject consumer email providers (gmail, outlook, …), so signup requires a work address.
                 *
                 * <p>Off by default: we currently accept any domain at signup. The consequence worth
                 * knowing is that the domain no longer reliably groups colleagues — {@code gmail.com}
                 * groups the entire world — so a consumer signup simply creates its own fresh workspace.
                 * Set to {@code true} (or the {@code EMAIL_BLOCK_PUBLIC_DOMAINS} env var) to require work
                 * addresses again.
                 */
                @DefaultValue("false") boolean blockPublicDomains,

                /**
                 * Overrides the bundled consumer-provider list entirely. Leave empty to use the bundled
                 * one — which is the sane default, and what most deployments want.
                 */
                @DefaultValue("") List<String> publicDomains,

                /** Added to the bundled consumer-provider list. The usual way to extend it. */
                @DefaultValue("") List<String> extraPublicDomains,

                @DefaultValue("true") boolean blockDisposableDomains,

                /** Supplements the bundled disposable list; for domains we learn about in production. */
                @DefaultValue("") List<String> extraDisposableDomains
        ) {}
    }

    public record Web(
            /** Origin of the SPA. Used to build verification links and to whitelist CORS. */
            @DefaultValue("http://localhost:5173") String baseUrl,
            @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
            /** Where the Google OAuth2 success handler drops the browser once tokens are minted. */
            @DefaultValue("/auth/callback") String oauthSuccessPath,

            /**
             * How many reverse proxies sit in front of this application.
             *
             * <p>Zero — the default — means we are exposed directly and {@code X-Forwarded-For} is
             * attacker-controlled, so it is ignored entirely. Behind one load balancer, set 1: the
             * balancer appends the peer it saw, so the last entry is the only one it wrote and the only
             * one that cannot be forged.
             *
             * <p>Get this number wrong upwards and you trust a hop the client supplied; the rate
             * limiter's per-IP budget then becomes free to bypass and the audit log records fiction.
             */
            @DefaultValue("0") int trustedProxyCount
    ) {}
}
