package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** How the refresh-token cookie is written — {@code lightmove.auth.cookie.*}. */
public record CookieSettings(
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
