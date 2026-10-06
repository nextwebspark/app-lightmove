package app.lightmove.api.core.security.jwt;

import app.lightmove.api.core.config.JwtSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Wires JWT signing and verification onto Spring Security's own Nimbus support.
 *
 * <p>Native, as asked: no jjwt, no java-jwt. {@code spring-security-oauth2-jose} already ships Nimbus,
 * already validates signature, expiry and issuer, and is maintained by the same team as the filter
 * chain that consumes it. Adding a third-party JWT library here would mean owning a second, unaudited
 * parser on the most security-critical path in the application, to do the same job.
 */
@Configuration
public class JwtConfig {

    /**
     * Generating a keypair is a development convenience and is permitted only where it is one.
     *
     * <p>Which profiles count is decided here rather than inside the provider, because "am I in
     * production" is a question about how the application was started, not about keys.
     */
    private static final Set<String> PROFILES_THAT_MAY_GENERATE_KEYS = Set.of("local", "dev", "test");

    private static final OAuth2TokenValidator<Jwt> NO_AUDIENCE = jwt -> jwt.getAudience() == null
            || jwt.getAudience().isEmpty()
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "A session token carries no audience", null));

    @Bean
    RsaKeyProvider rsaKeyProvider(LightMoveProperties properties, ResourceLoader resourceLoader,
                                  Environment environment) {
        JwtSettings jwt = properties.auth().jwt();
        return new RsaKeyProvider(jwt.privateKeyLocation(), jwt.publicKeyLocation(), resourceLoader,
                mayGenerateKeys(environment));
    }

    /** Shared with the MCP authorization server's key, which follows the same rule. */
    public static boolean mayGenerateKeys(Environment environment) {
        List<String> active = List.of(environment.getActiveProfiles());

        // No profile at all is `java -jar` with nothing set — which is how a production container is
        // usually launched, and exactly the case that must not quietly generate its own keys.
        return !active.isEmpty() && PROFILES_THAT_MAY_GENERATE_KEYS.containsAll(active);
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeyProvider keys) {
        JWK jwk = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                // A key id now means a second key can be introduced later and tokens signed by the old
                // one still verify while they drain. Retrofitting rotation onto unkeyed tokens means
                // signing everybody out.
                .keyID(UUID.nameUUIDFromBytes(keys.publicKey().getEncoded()).toString())
                .build();

        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    /**
     * Verifies signature, expiry and our issuer, and refuses any token carrying an audience: a session token has
     * none, and an MCP token always does, so one can never stand in for the other even if the keys were shared.
     */
    @Bean
    JwtDecoder jwtDecoder(RsaKeyProvider keys, LightMoveProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.auth().jwt().issuer()),
                NO_AUDIENCE));
        return decoder;
    }
}
