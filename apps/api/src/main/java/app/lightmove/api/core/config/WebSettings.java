package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Where the SPA lives and how requests reach us — {@code lightmove.web.*}. */
public record WebSettings(
        /** Origin of the SPA. Used to build verification links and to whitelist CORS. */
        @DefaultValue("http://localhost:5173") String baseUrl,
        @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins,
        /** Where the OAuth2 success handler drops the browser once tokens are minted. */
        @DefaultValue("/auth/callback") String oauthSuccessPath,

        /**
         * Reverse proxies in front of the app. 0: {@code X-Forwarded-For} is attacker-controlled and ignored; behind one
         * balancer, 1 (the last entry is the only unforgeable one). Too high trusts a client-supplied hop, bypassing the
         * per-IP rate limit and falsifying the audit log.
         */
        @DefaultValue("0") int trustedProxyCount,

        /**
         * Refuse a JSON request body naming a field its DTO does not carry, rather than dropping it. Off is
         * the kill switch for a screen found sending a stray field after release. Never set on the shared
         * mapper: that one reads vendor responses, which grow fields without notice.
         */
        @DefaultValue("true") boolean refuseUnknownRequestFields,

        /** The largest JSON request body read, declared length or none (chunked); multipart has its own caps. */
        @DefaultValue("2MB") DataSize maxJsonBodySize
) {}
