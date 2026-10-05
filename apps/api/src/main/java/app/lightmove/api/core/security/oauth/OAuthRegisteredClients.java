package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.McpSettings;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The authorization server's view of {@link OAuthClient}. Whatever a row says, the client it yields is public, needs
 * PKCE and consent, and gets rotating refresh tokens: none of that is a client's to choose.
 */
@Component
public class OAuthRegisteredClients implements RegisteredClientRepository {

    private final OAuthClientJpaRepository clients;
    private final McpSettings settings;

    public OAuthRegisteredClients(OAuthClientJpaRepository clients, LightMoveProperties properties) {
        this.clients = clients;
        this.settings = properties.mcp();
    }

    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        OAuthClient client = OAuthClient.registered(registeredClient.getClientId(),
                registeredClient.getClientName(), null, null,
                List.copyOf(registeredClient.getRedirectUris()), List.copyOf(registeredClient.getScopes()),
                OAuthClientSource.SEEDED);
        clients.save(client);
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findById(String id) {
        return clients.findById(UUID.fromString(id)).map(this::toRegisteredClient).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        return clients.findByClientId(clientId).map(this::toRegisteredClient).orElse(null);
    }

    private RegisteredClient toRegisteredClient(OAuthClient client) {
        return RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientIdIssuedAt(client.getCreatedAt())
                .clientName(client.getClientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(client.getRedirectUris()))
                .scopes(scopes -> scopes.addAll(client.getScopes()))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(settings.authorizationCodeTtl())
                        .accessTokenTimeToLive(settings.accessTokenTtl())
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .refreshTokenTimeToLive(settings.refreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();
    }
}
