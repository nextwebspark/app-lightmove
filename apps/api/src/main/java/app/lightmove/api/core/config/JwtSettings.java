package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Issuer and key material for the access tokens we mint — {@code lightmove.auth.jwt.*}. */
public record JwtSettings(
        @DefaultValue("lightmove") String issuer,
        /** Spring Resource locations, so classpath:, file: and env-injected PEM all work unchanged. */
        @DefaultValue("file:.keys/jwt-private.pem") String privateKeyLocation,
        @DefaultValue("file:.keys/jwt-public.pem") String publicKeyLocation
) {}
