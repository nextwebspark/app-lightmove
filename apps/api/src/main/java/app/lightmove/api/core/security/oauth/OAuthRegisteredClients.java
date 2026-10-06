package app.lightmove.api.core.security.oauth;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The authorization server's view of {@link OAuthClient}, beneath {@link ClientMetadataDocumentClients}. Whatever a row says, the client it yields is public, needs
 * PKCE and consent, and gets rotating refresh tokens: none of that is a client's to choose.
 */
@Component
public class OAuthRegisteredClients implements RegisteredClientRepository {

    static final String CLIENT_TARGET = "oauth_client";

    private final OAuthClientJpaRepository clients;
    private final McpSettings settings;
    private final AuditService audit;

    public OAuthRegisteredClients(OAuthClientJpaRepository clients, LightMoveProperties properties,
                                  AuditService audit) {
        this.clients = clients;
        this.settings = properties.mcp();
        this.audit = audit;
    }

    /** Only dynamic registration saves through here; a metadata document's client is written by its own upsert. */
    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        ClientSettings registered = registeredClient.getClientSettings();
        String source = registered.getSetting(DynamicClientRegistrations.SOURCE_SETTING);
        OAuthClient client = clients.save(OAuthClient.registered(registeredClient.getClientId(),
                registeredClient.getClientName(),
                registered.getSetting(DynamicClientRegistrations.CLIENT_URI_SETTING),
                registered.getSetting(DynamicClientRegistrations.LOGO_URI_SETTING),
                List.copyOf(registeredClient.getRedirectUris()), List.copyOf(registeredClient.getScopes()),
                source == null ? OAuthClientSource.SEEDED : OAuthClientSource.valueOf(source)));
        audit.event(WorkspaceEventType.OAUTH_CLIENT_REGISTERED)
                .target(CLIENT_TARGET, client.getId().toString())
                .detail("clientId", client.getClientId())
                .detail("clientName", client.getClientName())
                .detail("redirectUris", client.getRedirectUris())
                .from(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                        ? attributes.getRequest() : null)
                .record();
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

    RegisteredClient toRegisteredClient(OAuthClient client) {
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
