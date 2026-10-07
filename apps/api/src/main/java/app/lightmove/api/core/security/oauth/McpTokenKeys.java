package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import app.lightmove.api.core.security.jwt.JwtConfig;
import app.lightmove.api.core.security.jwt.RsaKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The MCP authorization server's signing key, apart from the session's: its own {@code kid}, published on the
 * authorization server's JWKS endpoint, and never a {@code JwtEncoder} bean — the authorization server would otherwise
 * find the session's encoder by type and sign MCP tokens with it.
 */
@Configuration
@ConditionalOnBooleanProperty(name = OAuthAuthorizationServerConfig.MCP_SWITCH)
public class McpTokenKeys {

    @Bean
    McpServerIdentity mcpServerIdentity(LightMoveProperties properties) {
        String issuer = McpSettings.stripTrailingSlash(properties.web().baseUrl());
        return new McpServerIdentity(issuer, properties.mcp().resourceUrlUnder(issuer));
    }

    @Bean
    RSAKey mcpSigningKey(LightMoveProperties properties, ResourceLoader resourceLoader, Environment environment) {
        McpSettings mcp = properties.mcp();
        RsaKeyProvider keys = new RsaKeyProvider(mcp.privateKeyLocation(), mcp.publicKeyLocation(), resourceLoader,
                JwtConfig.mayGenerateKeys(environment));
        return new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID("mcp-" + UUID.nameUUIDFromBytes(keys.publicKey().getEncoded()))
                .build();
    }

    /** The authorization server's JWKS endpoint reads this bean and publishes its public half only. */
    @Bean
    JWKSource<SecurityContext> mcpJwkSource(RSAKey mcpSigningKey) {
        return new ImmutableJWKSet<>(new JWKSet(mcpSigningKey));
    }

    @Bean
    McpTokenDecoder mcpTokenDecoder(RSAKey mcpSigningKey, McpServerIdentity identity) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(mcpSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(identity.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(identity.resourceUrl()))));
        return new McpTokenDecoder(decoder);
    }
}
