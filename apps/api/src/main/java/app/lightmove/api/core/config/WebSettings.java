package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the SPA lives and how requests reach us — {@code lightmove.web.*}. */
public record WebSettings(
        /** Origin of the SPA. Used to build verification links and to whitelist CORS. */
        @DefaultValue("http://localhost:5173") String baseUrl,
        @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
        /** Where the OAuth2 success handler drops the browser once tokens are minted. */
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
