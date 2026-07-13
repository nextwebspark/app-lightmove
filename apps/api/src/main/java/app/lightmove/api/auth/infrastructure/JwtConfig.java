package app.lightmove.api.auth.infrastructure;

import app.lightmove.api.common.config.LightMoveProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

    @Bean
    RsaKeyProvider rsaKeyProvider(LightMoveProperties properties, ResourceLoader resourceLoader) {
        return new RsaKeyProvider(properties.auth().jwt(), resourceLoader);
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
     * Verifies signature and expiry. The issuer check is added in {@link SecurityConfig} so that a
     * token minted by some other service that happens to share our key still gets rejected.
     */
    @Bean
    JwtDecoder jwtDecoder(RsaKeyProvider keys) {
        return NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
    }
}
